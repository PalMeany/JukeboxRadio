package su.nuv.radio.catalog.model;

import java.util.List;

/**
 * One page of album and playlist search results.
 *
 * @param total how many results the provider reports in total, {@code -1} when unknown
 */
public record CollectionPage(String query, int offset, int limit, int total, List<CollectionHit> items) {

    public CollectionPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean hasNext() {
        return this.total < 0 ? this.items.size() >= this.limit : this.offset + this.items.size() < this.total;
    }
}
