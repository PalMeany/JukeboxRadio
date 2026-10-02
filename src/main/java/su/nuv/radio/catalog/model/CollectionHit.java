package su.nuv.radio.catalog.model;

/**
 * An album or playlist found by a search. Its tracks are not loaded yet: {@link #link()} opens it
 * through {@link su.nuv.radio.catalog.CatalogRegistry#resolve} like a pasted link.
 *
 * @param subtitle what the provider says about it, e.g. "EP · GONE.Fludd · 2021"
 */
public record CollectionHit(String provider, TrackCollection.Kind kind, String title, String subtitle,
                            String coverUrl, String link) {
}
