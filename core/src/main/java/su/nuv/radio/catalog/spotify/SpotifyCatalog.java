package su.nuv.radio.catalog.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import su.nuv.radio.catalog.CatalogException;
import su.nuv.radio.catalog.MusicCatalog;
import su.nuv.radio.catalog.model.ResolveResult;
import su.nuv.radio.catalog.model.SearchPage;
import su.nuv.radio.catalog.model.TrackCollection;
import su.nuv.radio.catalog.model.TrackMeta;
import su.nuv.radio.util.Http;
import su.nuv.radio.util.Json;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spotify metadata: Web API (client credentials) first, public embed pages and oEmbed as fallbacks.
 *
 * <p>Since the February–March 2026 Development Mode changes the Web API caps search at 10 results
 * per call, drops ISRC codes and only lists the items of playlists the app owner owns. Playlists
 * and albums therefore fall back to {@code open.spotify.com/embed/...}, and covers of tracks read
 * from an embed track list are filled in lazily through {@link #enrich}.
 */
public final class SpotifyCatalog implements MusicCatalog {

    public static final String ID = "spotify";
    private static final String API = "https://api.spotify.com/v1";
    private static final int SEARCH_MAX = 10;
    private static final Pattern NEXT_DATA = Pattern.compile(
            "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
    private static final Pattern SHARE_TARGET = Pattern.compile(
            "https://open\\.spotify\\.com/(track|album|playlist|artist)/[A-Za-z0-9]+");

    private final SpotifyConfig config;
    private final Logger logger;
    private final SpotifyToken token;

    public SpotifyCatalog(SpotifyConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.token = new SpotifyToken(config);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Spotify";
    }

    @Override
    public boolean canResolve(String link) {
        return SpotifyLink.isSpotify(link);
    }

    @Override
    public boolean supportsSearch() {
        return this.config.hasCredentials();
    }

    // ------------------------------------------------------------------ resolve

    @Override
    public CompletableFuture<ResolveResult> resolve(String link) {
        final CompletableFuture<SpotifyLink> parsed = SpotifyLink.isShortLink(link)
                ? this.followShortLink(link)
                : SpotifyLink.parse(link).map(CompletableFuture::completedFuture)
                .orElseGet(() -> CompletableFuture.failedFuture(new CatalogException("Не удалось разобрать ссылку Spotify.")));
        return parsed.thenCompose(this::resolveParsed);
    }

    private CompletableFuture<ResolveResult> resolveParsed(SpotifyLink link) {
        return switch (link.type()) {
            case TRACK -> this.withFallback(
                    () -> this.apiTrack(link.id()).thenApply(ResolveResult.Single::new),
                    () -> this.embedTrack(link.id()).thenApply(ResolveResult.Single::new));
            case ALBUM -> this.withFallback(
                    () -> this.apiAlbum(link.id()).thenApply(ResolveResult.Many::new),
                    () -> this.embedCollection(link).thenApply(ResolveResult.Many::new));
            case PLAYLIST -> this.withFallback(
                    () -> this.apiPlaylist(link.id()).thenApply(ResolveResult.Many::new),
                    () -> this.embedCollection(link).thenApply(ResolveResult.Many::new));
            case ARTIST -> this.embedCollection(link).<ResolveResult>thenApply(ResolveResult.Many::new);
        };
    }

    private <T> CompletableFuture<T> withFallback(java.util.function.Supplier<CompletableFuture<T>> api,
                                                  java.util.function.Supplier<CompletableFuture<T>> embed) {
        if (!this.config.hasCredentials()) {
            return this.config.embedFallback() ? embed.get()
                    : CompletableFuture.failedFuture(new CatalogException("Spotify не настроен: нет ключей в config.yml."));
        }
        return api.get().handle((value, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(value);
            }
            final Throwable cause = Http.unwrap(error);
            if (this.config.embedFallback()) {
                this.logger.fine(() -> "Spotify API failed (" + cause.getMessage() + "), trying embed page");
                return embed.get();
            }
            return CompletableFuture.<T>failedFuture(cause);
        }).thenCompose(future -> future);
    }

    private CompletableFuture<SpotifyLink> followShortLink(String link) {
        return Http.send(Http.request(link.startsWith("http") ? link : "https://" + link).GET().build())
                .thenApply(response -> {
                    final Optional<SpotifyLink> direct = SpotifyLink.parse(response.uri().toString());
                    if (direct.isPresent()) {
                        return direct.get();
                    }
                    final Matcher matcher = SHARE_TARGET.matcher(response.body());
                    if (matcher.find()) {
                        return SpotifyLink.parse(matcher.group()).orElseThrow();
                    }
                    throw new CatalogException("Короткая ссылка Spotify никуда не ведёт.");
                });
    }

    // ------------------------------------------------------------------ Web API

    private CompletableFuture<JsonElement> api(String pathAndQuery) {
        return this.token.get().thenCompose(bearer -> Http.getJson(Http.request(API + pathAndQuery)
                .header("Authorization", "Bearer " + bearer)
                .GET().build()));
    }

    private String marketParam(char joiner) {
        final String market = this.config.market();
        return market == null || market.isBlank() ? "" : joiner + "market=" + Http.encode(market);
    }

    private CompletableFuture<TrackMeta> apiTrack(String id) {
        return this.api("/tracks/" + id + this.marketParam('?')).thenApply(json -> {
            final TrackMeta track = parseApiTrack(json, null, null);
            if (track == null) {
                throw new CatalogException("Трек не найден в Spotify.");
            }
            return track;
        });
    }

    private CompletableFuture<TrackCollection> apiAlbum(String id) {
        return this.api("/albums/" + id + this.marketParam('?')).thenCompose(json -> {
            final String name = Json.str(json, "name");
            final String cover = largestImage(Json.arr(json, "images"));
            final String owner = artistNames(Json.arr(json, "artists")).stream().findFirst().orElse("");
            final JsonObject page = Json.obj(json, "tracks");
            final List<TrackMeta> tracks = new ArrayList<>();
            addApiTracks(Json.arr(page, "items"), tracks, name, cover);
            final int total = (int) Json.num(page, "total", tracks.size());
            return this.pageRest("/albums/" + id + "/tracks", tracks.size(), total, tracks, name, cover)
                    .thenApply(all -> new TrackCollection(ID, id, TrackCollection.Kind.ALBUM, name, owner, cover,
                            "https://open.spotify.com/album/" + id, all, total));
        });
    }

    private CompletableFuture<TrackCollection> apiPlaylist(String id) {
        return this.api("/playlists/" + id + "?fields=name,images,owner(display_name),external_urls" + this.marketParam('&'))
                .thenCompose(meta -> this.api("/playlists/" + id + "/items?limit=50" + this.marketParam('&'))
                        .thenCompose(page -> {
                            final String name = Json.str(meta, "name");
                            final String cover = largestImage(Json.arr(meta, "images"));
                            final String owner = Json.str(Json.obj(meta, "owner"), "display_name");
                            final List<TrackMeta> tracks = new ArrayList<>();
                            addPlaylistItems(Json.arr(page, "items"), tracks);
                            if (tracks.isEmpty()) {
                                // Dev Mode answers other people's playlists without items.
                                throw new CatalogException("Spotify не отдаёт треки этого плейлиста.");
                            }
                            final int total = (int) Json.num(page, "total", tracks.size());
                            return this.pagePlaylist(id, tracks.size(), total, tracks)
                                    .thenApply(all -> new TrackCollection(ID, id, TrackCollection.Kind.PLAYLIST, name,
                                            owner, cover, "https://open.spotify.com/playlist/" + id, all, total));
                        }));
    }

    private CompletableFuture<List<TrackMeta>> pageRest(String path, int offset, int total, List<TrackMeta> acc,
                                                        String album, String cover) {
        if (offset >= total || acc.size() >= this.config.collectionLimit()) {
            return CompletableFuture.completedFuture(this.cap(acc));
        }
        return this.api(path + "?limit=50&offset=" + offset + this.marketParam('&')).thenCompose(page -> {
            final int before = acc.size();
            addApiTracks(Json.arr(page, "items"), acc, album, cover);
            if (acc.size() == before) {
                return CompletableFuture.completedFuture(this.cap(acc));
            }
            return this.pageRest(path, offset + 50, total, acc, album, cover);
        });
    }

    private CompletableFuture<List<TrackMeta>> pagePlaylist(String id, int offset, int total, List<TrackMeta> acc) {
        if (offset >= total || acc.size() >= this.config.collectionLimit()) {
            return CompletableFuture.completedFuture(this.cap(acc));
        }
        return this.api("/playlists/" + id + "/items?limit=50&offset=" + offset + this.marketParam('&')).thenCompose(page -> {
            final int before = acc.size();
            addPlaylistItems(Json.arr(page, "items"), acc);
            if (acc.size() == before) {
                return CompletableFuture.completedFuture(this.cap(acc));
            }
            return this.pagePlaylist(id, offset + 50, total, acc);
        });
    }

    private List<TrackMeta> cap(List<TrackMeta> tracks) {
        return tracks.size() > this.config.collectionLimit()
                ? List.copyOf(tracks.subList(0, this.config.collectionLimit())) : tracks;
    }

    private static void addApiTracks(JsonArray items, List<TrackMeta> out, String album, String cover) {
        for (JsonObject item : Json.objects(items)) {
            final TrackMeta track = parseApiTrack(item, album, cover);
            if (track != null) {
                out.add(track);
            }
        }
    }

    private static void addPlaylistItems(JsonArray items, List<TrackMeta> out) {
        for (JsonObject item : Json.objects(items)) {
            // The 2026 rename moved the object from "track" to "item"; accept both.
            JsonObject track = Json.obj(item, "item");
            if (track == null) {
                track = Json.obj(item, "track");
            }
            if (track != null && !"episode".equals(Json.str(track, "type"))) {
                final TrackMeta parsed = parseApiTrack(track, null, null);
                if (parsed != null) {
                    out.add(parsed);
                }
            }
        }
    }

    static TrackMeta parseApiTrack(JsonElement json, String albumName, String albumCover) {
        final String id = Json.str(json, "id");
        if (id == null) {
            return null;
        }
        final JsonObject album = Json.obj(json, "album");
        final String cover = album != null ? largestImage(Json.arr(album, "images")) : albumCover;
        final String albumTitle = album != null ? Json.str(album, "name") : albumName;
        return new TrackMeta(ID, id, Json.str(json, "name"), artistNames(Json.arr(json, "artists")), albumTitle,
                Json.num(json, "duration_ms", 0), cover, "https://open.spotify.com/track/" + id, null);
    }

    private static List<String> artistNames(JsonArray artists) {
        final List<String> names = new ArrayList<>();
        for (JsonObject artist : Json.objects(artists)) {
            final String name = Json.str(artist, "name");
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private static String largestImage(JsonArray images) {
        String best = null;
        long bestWidth = -1;
        for (JsonObject image : Json.objects(images)) {
            final long width = Math.max(Json.num(image, "width", 0), Json.num(image, "maxWidth", 0));
            // 300 px is plenty for a 32x32 pixel cover; prefer it over 640.
            final long score = width == 300 ? 10_000 : width;
            if (Json.str(image, "url") != null && score > bestWidth) {
                best = Json.str(image, "url");
                bestWidth = score;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ search

    @Override
    public CompletableFuture<SearchPage> search(String query, int offset, int limit) {
        if (!this.config.hasCredentials()) {
            return CompletableFuture.failedFuture(new CatalogException("Поиск Spotify не настроен: нет ключей в config.yml."));
        }
        final int capped = Math.max(1, Math.min(SEARCH_MAX, limit));
        final String path = "/search?type=track&q=" + Http.encode(query) + "&limit=" + capped + "&offset="
                + Math.max(0, offset) + this.marketParam('&');
        return this.api(path).thenApply(json -> {
            final JsonObject tracks = Json.obj(json, "tracks");
            final List<TrackMeta> items = new ArrayList<>();
            addApiTracks(Json.arr(tracks, "items"), items, null, null);
            final int total = (int) Json.num(tracks, "total", -1);
            return new SearchPage(query, offset, capped, total, items);
        });
    }

    // ------------------------------------------------------------------ enrich

    @Override
    public CompletableFuture<TrackMeta> enrich(TrackMeta track) {
        if (track.coverUrl() != null && track.album() != null && track.durationMs() > 0) {
            return CompletableFuture.completedFuture(track);
        }
        final CompletableFuture<TrackMeta> full = this.config.hasCredentials()
                ? this.apiTrack(track.id())
                : this.embedTrack(track.id());
        return full.thenApply(found -> merge(track, found))
                .exceptionallyCompose(error -> this.oembedCover(track.id()).thenApply(track::withCover))
                .exceptionally(error -> track);
    }

    private static TrackMeta merge(TrackMeta base, TrackMeta found) {
        TrackMeta result = base;
        if (result.coverUrl() == null) {
            result = result.withCover(found.coverUrl());
        }
        if (result.album() == null) {
            result = result.withAlbum(found.album());
        }
        if (result.durationMs() <= 0) {
            result = result.withDuration(found.durationMs());
        }
        return result;
    }

    private CompletableFuture<String> oembedCover(String id) {
        final String url = "https://open.spotify.com/oembed?url=" + Http.encode("https://open.spotify.com/track/" + id);
        return Http.getJson(Http.request(url).GET().build()).thenApply(json -> {
            final String thumb = Json.str(json, "thumbnail_url");
            if (thumb == null) {
                throw new CompletionException(new IllegalStateException("no thumbnail"));
            }
            return thumb;
        });
    }

    // ------------------------------------------------------------------ embed pages

    private CompletableFuture<JsonObject> embedEntity(SpotifyLink.Type type, String id) {
        final String url = "https://open.spotify.com/embed/" + type.path() + "/" + id;
        return Http.getText(Http.request(url).header("Accept", "text/html").GET().build()).thenApply(html -> {
            final Matcher matcher = NEXT_DATA.matcher(html);
            if (!matcher.find()) {
                throw new CatalogException("Spotify не отдал данные по ссылке.");
            }
            final JsonElement root = JsonParser.parseString(matcher.group(1));
            final JsonElement entity = Json.path(root, "props", "pageProps", "state", "data", "entity");
            if (!(entity instanceof JsonObject object)) {
                throw new CatalogException("По ссылке ничего не найдено.");
            }
            return object;
        }).exceptionally(error -> {
            final Throwable cause = Http.unwrap(error);
            if (cause instanceof CatalogException catalog) {
                throw catalog;
            }
            if (cause instanceof Http.HttpStatusException status && status.status() == 404) {
                throw new CatalogException("По ссылке ничего не найдено.");
            }
            this.logger.log(Level.FINE, "Spotify embed failed", cause);
            throw new CatalogException("Spotify сейчас недоступен.", cause);
        });
    }

    private CompletableFuture<TrackMeta> embedTrack(String id) {
        return this.embedEntity(SpotifyLink.Type.TRACK, id).thenApply(entity -> {
            final List<String> artists = artistNames(Json.arr(entity, "artists"));
            final String title = Optional.ofNullable(Json.str(entity, "name")).orElse(Json.str(entity, "title"));
            return new TrackMeta(ID, id, title, artists, null, Json.num(entity, "duration", 0),
                    embedCover(entity), "https://open.spotify.com/track/" + id, null);
        });
    }

    private CompletableFuture<TrackCollection> embedCollection(SpotifyLink link) {
        return this.embedEntity(link.type(), link.id()).thenApply(entity -> {
            final String title = Optional.ofNullable(Json.str(entity, "name")).orElse(Json.str(entity, "title"));
            final String subtitle = Json.str(entity, "subtitle");
            final String cover = embedCover(entity);
            final TrackCollection.Kind kind = switch (link.type()) {
                case ALBUM -> TrackCollection.Kind.ALBUM;
                case ARTIST -> TrackCollection.Kind.ARTIST;
                default -> TrackCollection.Kind.PLAYLIST;
            };
            final List<TrackMeta> tracks = new ArrayList<>();
            for (JsonObject item : Json.objects(Json.arr(entity, "trackList"))) {
                final String uri = Json.str(item, "uri");
                if (uri == null || !uri.startsWith("spotify:track:")) {
                    continue;
                }
                final String trackId = uri.substring("spotify:track:".length());
                final String artistsLine = Json.str(item, "subtitle");
                final List<String> artists = artistsLine == null ? List.of()
                        : Arrays.stream(artistsLine.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
                tracks.add(new TrackMeta(ID, trackId, Json.str(item, "title"), artists,
                        kind == TrackCollection.Kind.ALBUM ? title : null, Json.num(item, "duration", 0),
                        kind == TrackCollection.Kind.ALBUM ? cover : null,
                        "https://open.spotify.com/track/" + trackId, null));
            }
            if (tracks.isEmpty()) {
                throw new CatalogException("В этой подборке нет доступных треков.");
            }
            final List<TrackMeta> capped = this.cap(tracks);
            return new TrackCollection(ID, link.id(), kind, title, subtitle, cover, link.webUrl(), capped, tracks.size());
        });
    }

    private static String embedCover(JsonObject entity) {
        final String fromVisual = largestImage(Json.arr(Json.obj(entity, "visualIdentity"), "image"));
        if (fromVisual != null) {
            return fromVisual;
        }
        return largestImage(Json.arr(Json.obj(entity, "coverArt"), "sources"));
    }

    // ------------------------------------------------------------------ token

    /** Client-credentials token, refreshed a minute before it expires. */
    private static final class SpotifyToken {

        private final SpotifyConfig config;
        private String value;
        private long expiresAt;
        private CompletableFuture<String> pending;

        SpotifyToken(SpotifyConfig config) {
            this.config = config;
        }

        synchronized CompletableFuture<String> get() {
            if (this.value != null && System.currentTimeMillis() < this.expiresAt) {
                return CompletableFuture.completedFuture(this.value);
            }
            if (this.pending != null && !this.pending.isDone()) {
                return this.pending;
            }
            final String basic = Base64.getEncoder().encodeToString(
                    (this.config.clientId() + ":" + this.config.clientSecret()).getBytes(StandardCharsets.UTF_8));
            final HttpRequest request = Http.request("https://accounts.spotify.com/api/token")
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(Http.form(Map.of("grant_type", "client_credentials"))))
                    .build();
            this.pending = Http.getJson(request).thenApply(json -> {
                final String token = Json.str(json, "access_token");
                if (token == null) {
                    throw new CatalogException("Spotify отклонил ключи из config.yml.");
                }
                synchronized (this) {
                    this.value = token;
                    this.expiresAt = System.currentTimeMillis() + (Json.num(json, "expires_in", 3600) - 60) * 1000L;
                }
                return token;
            }).exceptionally(error -> {
                final Throwable cause = Http.unwrap(error);
                if (cause instanceof Http.HttpStatusException status && (status.status() == 400 || status.status() == 401)) {
                    throw new CatalogException("Spotify отклонил ключи из config.yml.", cause);
                }
                throw cause instanceof RuntimeException runtime ? runtime : new CompletionException(cause);
            });
            return this.pending;
        }
    }
}
