package su.nuv.radio.catalog;

import su.nuv.radio.catalog.model.CollectionPage;
import su.nuv.radio.catalog.model.ResolveResult;
import su.nuv.radio.catalog.model.SearchPage;
import su.nuv.radio.catalog.model.TrackMeta;
import su.nuv.radio.util.Links;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Every registered {@link MusicCatalog}, with routing: links go to the first catalog that claims
 * them, searches go to the configured provider (falling back to any catalog that can search).
 */
public final class CatalogRegistry {

    private final Map<String, MusicCatalog> catalogs = new LinkedHashMap<>();
    private final String searchProvider;

    public CatalogRegistry(String searchProvider) {
        this.searchProvider = searchProvider;
    }

    public void register(MusicCatalog catalog) {
        this.catalogs.put(catalog.id(), catalog);
    }

    public Optional<MusicCatalog> get(String id) {
        return Optional.ofNullable(this.catalogs.get(id));
    }

    public List<MusicCatalog> all() {
        return Collections.unmodifiableList(new ArrayList<>(this.catalogs.values()));
    }

    /** Whether the text looks like a link some catalog understands. */
    public boolean isLink(String text) {
        return this.find(text).isPresent();
    }

    public Optional<MusicCatalog> find(String link) {
        final String cleaned = Links.clean(link);
        return this.catalogs.values().stream().filter(c -> c.canResolve(cleaned)).findFirst();
    }

    public CompletableFuture<ResolveResult> resolve(String link) {
        final String cleaned = Links.clean(link);
        return this.find(cleaned)
                .map(catalog -> catalog.resolve(cleaned))
                .orElseGet(() -> CompletableFuture.failedFuture(
                        new CatalogException("Эта ссылка не поддерживается. Подходят ссылки Spotify, Apple Music и YouTube.")));
    }

    /** The catalog searches go to, or empty when none can search. */
    public Optional<MusicCatalog> searchCatalog() {
        final MusicCatalog preferred = this.catalogs.get(this.searchProvider);
        if (preferred != null && preferred.supportsSearch()) {
            return Optional.of(preferred);
        }
        return this.catalogs.values().stream().filter(MusicCatalog::supportsSearch).findFirst();
    }

    public CompletableFuture<SearchPage> search(String query, int offset, int limit) {
        return this.searchCatalog()
                .map(catalog -> catalog.search(query, offset, limit))
                .orElseGet(() -> CompletableFuture.failedFuture(
                        new CatalogException("Поиск не настроен: укажите ключи Spotify в config.yml.")));
    }

    /** The catalog album and playlist searches go to: the search provider if it can, else any that can. */
    public Optional<MusicCatalog> collectionSearchCatalog() {
        final MusicCatalog preferred = this.catalogs.get(this.searchProvider);
        if (preferred != null && preferred.supportsCollectionSearch()) {
            return Optional.of(preferred);
        }
        return this.catalogs.values().stream().filter(MusicCatalog::supportsCollectionSearch).findFirst();
    }

    public CompletableFuture<CollectionPage> searchCollections(String query, int offset, int limit) {
        return this.collectionSearchCatalog()
                .map(catalog -> catalog.searchCollections(query, offset, limit))
                .orElseGet(() -> CompletableFuture.failedFuture(new CatalogException("Поиск альбомов недоступен.")));
    }

    public CompletableFuture<TrackMeta> enrich(TrackMeta track) {
        final MusicCatalog catalog = this.catalogs.get(track.provider());
        if (catalog == null) {
            return CompletableFuture.completedFuture(track);
        }
        return catalog.enrich(track).exceptionally(error -> track);
    }

    public void shutdown() {
        this.catalogs.values().forEach(MusicCatalog::shutdown);
    }
}
