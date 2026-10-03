package su.nuv.radio.catalog.apple;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Apple Music links without keys. Tracks, albums and artists come from the public iTunes Lookup
 * API; playlists are not in it, so they are read from the {@code serialized-server-data} JSON of
 * the music.apple.com page (the first 100 tracks). Audio is found on YouTube Music like Spotify's.
 */
public final class AppleMusicCatalog implements MusicCatalog {

    public static final String ID = "apple";
    private static final String LOOKUP = "https://itunes.apple.com/lookup";
    private static final Pattern SERVER_DATA = Pattern.compile(
            "<script type=\"application/json\" id=\"serialized-server-data\">(.*?)</script>", Pattern.DOTALL);
    private static final int ARTIST_TOP = 25;
    private static final Pattern ARTIST_SPLIT = Pattern.compile("\\s*,\\s*|\\s+&\\s+");

    private final int collectionLimit;
    private final Logger logger;

    public AppleMusicCatalog(int collectionLimit, Logger logger) {
        this.collectionLimit = collectionLimit;
        this.logger = logger;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Apple Music";
    }

    @Override
    public boolean canResolve(String link) {
        return AppleMusicLink.isAppleMusic(link);
    }

    @Override
    public boolean supportsSearch() {
        return false;
    }

    @Override
    public CompletableFuture<SearchPage> search(String query, int offset, int limit) {
        return CompletableFuture.failedFuture(new CatalogException("Apple Music не ищет, только открывает ссылки."));
    }

    @Override
    public CompletableFuture<ResolveResult> resolve(String link) {
        final AppleMusicLink parsed = AppleMusicLink.parse(link).orElse(null);
        if (parsed == null) {
            return CompletableFuture.failedFuture(new CatalogException("Не удалось разобрать ссылку Apple Music."));
        }
        final CompletableFuture<ResolveResult> result = switch (parsed.type()) {
            case TRACK -> this.track(parsed).thenApply(ResolveResult.Single::new);
            case ALBUM -> this.album(parsed).thenApply(ResolveResult.Many::new);
            case ARTIST -> this.artist(parsed).thenApply(ResolveResult.Many::new);
            case PLAYLIST -> this.playlist(parsed).thenApply(ResolveResult.Many::new);
        };
        return result.exceptionally(error -> {
            final Throwable cause = Http.unwrap(error);
            if (cause instanceof CatalogException catalog) {
                throw catalog;
            }
            if (cause instanceof Http.HttpStatusException status && status.status() == 404) {
                throw new CatalogException("По ссылке ничего не найдено.");
            }
            this.logger.log(Level.FINE, "Apple Music lookup failed", cause);
            throw new CatalogException("Apple Music сейчас недоступен.", cause);
        });
    }

    // ------------------------------------------------------------------ iTunes Lookup API

    private CompletableFuture<List<JsonObject>> lookup(AppleMusicLink link, String extra) {
        final String url = LOOKUP + "?id=" + link.id() + "&country=" + link.storefront() + extra;
        return Http.getJson(Http.request(url).GET().build()).thenApply(json -> Json.objects(Json.arr(json, "results")));
    }

    private CompletableFuture<TrackMeta> track(AppleMusicLink link) {
        return this.lookup(link, "").thenApply(results -> results.stream()
                .map(AppleMusicCatalog::lookupTrack)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new CatalogException("Трек не найден в Apple Music.")));
    }

    private CompletableFuture<TrackCollection> album(AppleMusicLink link) {
        return this.lookup(link, "&entity=song&limit=200").thenApply(results -> {
            final JsonObject album = results.stream()
                    .filter(r -> "collection".equals(Json.str(r, "wrapperType")))
                    .findFirst()
                    .orElseThrow(() -> new CatalogException("Альбом не найден в Apple Music."));
            final List<TrackMeta> tracks = results.stream()
                    .filter(r -> r != album)
                    .sorted(Comparator.comparingLong((JsonObject r) -> Json.num(r, "discNumber", 1))
                            .thenComparingLong(r -> Json.num(r, "trackNumber", 0)))
                    .map(AppleMusicCatalog::lookupTrack)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            return this.collection(link, TrackCollection.Kind.ALBUM, Json.str(album, "collectionName"),
                    Json.str(album, "artistName"), cover(Json.str(album, "artworkUrl100")), tracks);
        });
    }

    private CompletableFuture<TrackCollection> artist(AppleMusicLink link) {
        // the lookup lists an artist's songs most popular first; past the top the order is arbitrary
        final int limit = Math.max(1, Math.min(ARTIST_TOP, this.collectionLimit));
        return this.lookup(link, "&entity=song&limit=" + limit).thenApply(results -> {
            final JsonObject artist = results.stream()
                    .filter(r -> "artist".equals(Json.str(r, "wrapperType")))
                    .findFirst()
                    .orElseThrow(() -> new CatalogException("Исполнитель не найден в Apple Music."));
            final List<TrackMeta> tracks = results.stream()
                    .map(AppleMusicCatalog::lookupTrack)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            final String cover = tracks.isEmpty() ? null : tracks.getFirst().coverUrl();
            return this.collection(link, TrackCollection.Kind.ARTIST, Json.str(artist, "artistName"),
                    "Популярные треки", cover, tracks);
        });
    }

    /** A song row of a lookup answer, or null for albums, artists, videos and unplayable rows. */
    public static TrackMeta lookupTrack(JsonObject row) {
        if (!"track".equals(Json.str(row, "wrapperType")) || !"song".equals(Json.str(row, "kind"))) {
            return null;
        }
        final long id = Json.num(row, "trackId", 0);
        if (id <= 0) {
            return null;
        }
        final String url = Json.str(row, "trackViewUrl");
        return new TrackMeta(ID, Long.toString(id), Json.str(row, "trackName"), splitArtists(Json.str(row, "artistName")),
                Json.str(row, "collectionName"), Json.num(row, "trackTimeMillis", 0),
                cover(Json.str(row, "artworkUrl100")), url == null ? null : url.replace("&uo=4", ""), null);
    }

    // ------------------------------------------------------------------ playlist page

    private CompletableFuture<TrackCollection> playlist(AppleMusicLink link) {
        return Http.getText(Http.request(link.webUrl()).header("Accept", "text/html").GET().build())
                .thenApply(html -> this.parsePlaylistPage(link, html));
    }

    public TrackCollection parsePlaylistPage(AppleMusicLink link, String html) {
        final Matcher matcher = SERVER_DATA.matcher(html);
        if (!matcher.find()) {
            throw new CatalogException("Apple Music не отдал данные плейлиста.");
        }
        final JsonElement root = JsonParser.parseString(matcher.group(1));
        final JsonArray pages = Json.arr(root, "data");
        final JsonObject page = pages.isEmpty() ? null : Json.obj(pages.get(0), "data");
        String title = null;
        String owner = null;
        String cover = null;
        final List<TrackMeta> tracks = new ArrayList<>();
        for (JsonObject section : Json.objects(Json.arr(page, "sections"))) {
            final String kind = Json.str(section, "itemKind");
            final List<JsonObject> items = Json.objects(Json.arr(section, "items"));
            if ("containerDetailHeaderLockup".equals(kind) && !items.isEmpty()) {
                final JsonObject header = items.getFirst();
                title = Json.str(header, "title");
                owner = String.join(", ", titles(Json.arr(header, "subtitleLinks")));
                cover = artwork(header);
            } else if ("trackLockup".equals(kind)) {
                for (JsonObject item : items) {
                    final TrackMeta track = pageTrack(item);
                    if (track != null) {
                        tracks.add(track);
                    }
                }
            }
        }
        if (title == null && tracks.isEmpty()) {
            throw new CatalogException("По ссылке ничего не найдено.");
        }
        return this.collection(link, TrackCollection.Kind.PLAYLIST, title, owner, cover, tracks);
    }

    private static TrackMeta pageTrack(JsonObject item) {
        final JsonObject descriptor = Json.obj(item, "contentDescriptor");
        if (!"song".equals(Json.str(descriptor, "kind"))) {
            return null;
        }
        final String id = Json.str(Json.obj(descriptor, "identifiers"), "storeAdamID");
        if (id == null) {
            return null;
        }
        List<String> artists = titles(Json.arr(item, "subtitleLinks"));
        if (artists.isEmpty()) {
            artists = splitArtists(Json.str(item, "artistName"));
        }
        final List<String> albums = titles(Json.arr(item, "tertiaryLinks"));
        return new TrackMeta(ID, id, Json.str(item, "title"), artists, albums.isEmpty() ? null : albums.getFirst(),
                Json.num(item, "duration", 0), artwork(item), Json.str(descriptor, "url"), null);
    }

    private static List<String> titles(JsonArray links) {
        final List<String> out = new ArrayList<>();
        for (JsonObject link : Json.objects(links)) {
            final String title = Json.str(link, "title");
            if (title != null && !title.isBlank()) {
                out.add(title.strip());
            }
        }
        return out;
    }

    /** Artwork URL template ({@code {w}x{h}bb.{f}}) filled in at 300 px. */
    private static String artwork(JsonObject item) {
        final String template = Json.str(Json.obj(Json.obj(item, "artwork"), "dictionary"), "url");
        return template == null ? null
                : template.replace("{w}", "300").replace("{h}", "300").replace("{f}", "jpg").replace("{c}", "bb");
    }

    // ------------------------------------------------------------------ shared

    private TrackCollection collection(AppleMusicLink link, TrackCollection.Kind kind, String title, String owner,
                                       String cover, List<TrackMeta> tracks) {
        if (tracks.isEmpty()) {
            throw new CatalogException("В этой подборке нет доступных треков.");
        }
        final List<TrackMeta> capped = tracks.size() > this.collectionLimit
                ? List.copyOf(tracks.subList(0, this.collectionLimit)) : tracks;
        return new TrackCollection(ID, link.id(), kind, title, owner, cover, link.webUrl(), capped, tracks.size());
    }

    /** "A, B & C" into [A, B, C]. */
    public static List<String> splitArtists(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ARTIST_SPLIT.split(line.strip())).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** 100 px lookup artwork resized to 300 px, enough for the 32×32 menu cover. */
    private static String cover(String url) {
        return url == null ? null : url.replace("100x100bb", "300x300bb");
    }
}
