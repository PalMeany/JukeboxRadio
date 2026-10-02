package su.nuv.radio.catalog;

import su.nuv.radio.catalog.model.CollectionPage;
import su.nuv.radio.catalog.model.ResolveResult;
import su.nuv.radio.catalog.model.SearchPage;
import su.nuv.radio.catalog.model.TrackMeta;

import java.util.concurrent.CompletableFuture;

/**
 * A source of music metadata: turns links into tracks and answers searches. Audio is not its job:
 * the radio finds a stream for any {@link TrackMeta} through {@link su.nuv.radio.audio.AudioResolver}.
 *
 * <p>To plug in another service (for example a dedicated music-search API), implement this
 * interface, register it in {@link su.nuv.radio.RadioPlugin#buildCatalogs} and point
 * {@code catalog.search-provider} in config.yml at its {@link #id()}.
 *
 * <p>All methods may be called from any thread and must not block the caller: do the network work
 * on {@link su.nuv.radio.util.Http#executor()} or your own executor.
 */
public interface MusicCatalog {

    /** Short stable id used in config and in {@link TrackMeta#provider()}. */
    String id();

    /** Name shown to players, e.g. "Spotify". */
    String displayName();

    /** Whether this catalog understands the link. Must be cheap and must not do I/O. */
    boolean canResolve(String link);

    /** Resolves a link this catalog {@linkplain #canResolve accepts} into a track or a collection. */
    CompletableFuture<ResolveResult> resolve(String link);

    /** Whether {@link #search} is available with the current configuration. */
    boolean supportsSearch();

    /** Full-text track search. {@code offset} and {@code limit} count tracks. */
    CompletableFuture<SearchPage> search(String query, int offset, int limit);

    /** Whether {@link #searchCollections} is available with the current configuration. */
    default boolean supportsCollectionSearch() {
        return false;
    }

    /**
     * Album and playlist search. Hits carry a link that {@link #resolve} opens; {@code offset} and
     * {@code limit} count hits.
     */
    default CompletableFuture<CollectionPage> searchCollections(String query, int offset, int limit) {
        return CompletableFuture.failedFuture(new CatalogException(this.displayName() + " не ищет альбомы."));
    }

    /**
     * Fills in whatever a listing left out, typically the cover. Returns the same track when nothing
     * is missing or nothing can be found. Never fails: errors resolve to the input.
     */
    default CompletableFuture<TrackMeta> enrich(TrackMeta track) {
        return CompletableFuture.completedFuture(track);
    }

    /** Releases resources on plugin shutdown. */
    default void shutdown() {
    }
}
