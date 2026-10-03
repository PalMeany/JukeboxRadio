package su.nuv.radio.catalog.youtube;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import su.nuv.radio.audio.AudioEngine;
import su.nuv.radio.catalog.CatalogException;
import su.nuv.radio.catalog.MusicCatalog;
import su.nuv.radio.catalog.model.CollectionHit;
import su.nuv.radio.catalog.model.CollectionPage;
import su.nuv.radio.catalog.model.ResolveResult;
import su.nuv.radio.catalog.model.SearchPage;
import su.nuv.radio.catalog.model.TrackCollection;
import su.nuv.radio.catalog.model.TrackMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** YouTube videos and playlists, read through lavaplayer's YouTube source. */
public final class YouTubeCatalog implements MusicCatalog {

    public static final String ID = "youtube";
    private static final Pattern LINK = Pattern.compile(
            "^(?:https?://)?(?:www\\.|m\\.|music\\.)?(?:youtube\\.com/(?:watch|playlist|shorts/|live/|embed/)|youtu\\.be/)",
            Pattern.CASE_INSENSITIVE);

    private final AudioEngine engine;
    private final int collectionLimit;
    /** The last album search, so paging through it does not ask YouTube again. */
    private volatile CachedHits lastHits;

    private record CachedHits(String query, List<CollectionHit> hits) {
    }

    public YouTubeCatalog(AudioEngine engine, int collectionLimit) {
        this.engine = engine;
        this.collectionLimit = collectionLimit;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "YouTube";
    }

    @Override
    public boolean canResolve(String link) {
        return link != null && LINK.matcher(link.strip()).find();
    }

    @Override
    public CompletableFuture<ResolveResult> resolve(String link) {
        return this.engine.load(link.strip()).thenApply(item -> item.map(this::toResult)
                .orElseThrow(() -> new CatalogException("По ссылке YouTube ничего не найдено.")));
    }

    private ResolveResult toResult(AudioItem item) {
        if (item instanceof AudioTrack track) {
            return new ResolveResult.Single(toMeta(track.getInfo()));
        }
        final AudioPlaylist playlist = (AudioPlaylist) item;
        final List<TrackMeta> tracks = new ArrayList<>();
        for (AudioTrack track : playlist.getTracks()) {
            if (tracks.size() >= this.collectionLimit) {
                break;
            }
            tracks.add(toMeta(track.getInfo()));
        }
        if (tracks.isEmpty()) {
            throw new CatalogException("Плейлист YouTube пуст.");
        }
        final String cover = tracks.getFirst().coverUrl();
        return new ResolveResult.Many(new TrackCollection(ID, playlist.getName(), TrackCollection.Kind.PLAYLIST,
                playlist.getName(), "YouTube", cover, null, tracks, playlist.getTracks().size()));
    }

    public static TrackMeta toMeta(AudioTrackInfo info) {
        String author = info.author == null ? "" : info.author;
        if (author.endsWith(" - Topic")) {
            author = author.substring(0, author.length() - " - Topic".length());
        }
        final String cover = info.artworkUrl != null && !info.artworkUrl.isBlank()
                ? info.artworkUrl
                : "https://i.ytimg.com/vi/" + info.identifier + "/mqdefault.jpg";
        return new TrackMeta(ID, info.identifier, info.title, author.isBlank() ? List.of() : List.of(author), null,
                info.isStream ? 0 : info.length, cover, info.uri, info.uri);
    }

    @Override
    public boolean supportsSearch() {
        return true;
    }

    @Override
    public CompletableFuture<SearchPage> search(String query, int offset, int limit) {
        return this.engine.load("ytmsearch:" + query).thenApply(item -> {
            final List<TrackMeta> all = new ArrayList<>();
            item.ifPresent(found -> {
                if (found instanceof AudioPlaylist playlist) {
                    playlist.getTracks().forEach(track -> all.add(toMeta(track.getInfo())));
                } else if (found instanceof AudioTrack track) {
                    all.add(toMeta(track.getInfo()));
                }
            });
            final int from = Math.min(offset, all.size());
            final int to = Math.min(all.size(), from + limit);
            return new SearchPage(query, offset, limit, all.size(), all.subList(from, to));
        });
    }

    @Override
    public boolean supportsCollectionSearch() {
        return true;
    }

    @Override
    public CompletableFuture<CollectionPage> searchCollections(String query, int offset, int limit) {
        final CachedHits cached = this.lastHits;
        final CompletableFuture<List<CollectionHit>> all = cached != null && cached.query().equals(query)
                ? CompletableFuture.completedFuture(cached.hits())
                : YtMusicSearch.search(query).thenApply(hits -> {
                    this.lastHits = new CachedHits(query, List.copyOf(hits));
                    return hits;
                });
        return all.thenApply(hits -> {
            final int from = Math.min(offset, hits.size());
            final int to = Math.min(hits.size(), from + limit);
            return new CollectionPage(query, offset, limit, hits.size(), hits.subList(from, to));
        });
    }
}
