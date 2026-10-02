package su.nuv.radio.station;

import su.nuv.radio.catalog.model.TrackMeta;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** One queued track and who asked for it. {@link #meta()} may be upgraded once enrichment lands. */
public final class QueueEntry {

    private static final AtomicLong IDS = new AtomicLong();

    private final long serial = IDS.incrementAndGet();
    private final UUID requesterId;
    private final String requesterName;
    private volatile TrackMeta meta;
    private volatile boolean enriched;

    public QueueEntry(TrackMeta meta, UUID requesterId, String requesterName) {
        this.meta = meta;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
    }

    /** Unique per entry, so the same song queued twice is two entries. */
    public long serial() {
        return this.serial;
    }

    public TrackMeta meta() {
        return this.meta;
    }

    void upgrade(TrackMeta upgraded) {
        this.meta = upgraded;
        this.enriched = true;
    }

    boolean enriched() {
        return this.enriched;
    }

    void markEnriched() {
        this.enriched = true;
    }

    public UUID requesterId() {
        return this.requesterId;
    }

    public String requesterName() {
        return this.requesterName;
    }
}
