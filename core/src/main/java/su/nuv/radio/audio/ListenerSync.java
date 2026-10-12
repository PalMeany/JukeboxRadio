package su.nuv.radio.audio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps one listener's speakers in step. A jukebox and each of its relays are separate voice chat
 * streams on the client, each with its own delay. A stream the listener has heard for a while has
 * fallen behind by whatever the network held back; the stream of a speaker they have just walked
 * up to starts fresh. Both play the same frames, so the listener hears an echo.
 *
 * <p>{@link Resync} cures that for everyone at once, but only every half a minute or more, because
 * it interrupts every listener. Here the streams are ended for that one listener alone, right when
 * a second speaker comes into earshot: their client drops what the old streams had queued and
 * starts all of them together, and nobody else notices. Like {@link Resync}, it waits a moment for
 * a quiet frame, since ending a stream costs a short gap.
 *
 * <p>Frames are counted by {@link #beginFrame}; everything else is measured in them (20 ms each).
 */
public final class ListenerSync {

    /** A speaker not heard for this long has ended on the client and will start fresh. */
    static final int GONE_FRAMES = 15;
    /** At least this long between two realignments of one listener. */
    static final int MIN_GAP_FRAMES = 200;
    /** Up to here only near silence qualifies, up to {@link #FORCE_FRAMES} a soft passage, then any frame. */
    static final int QUIET_ONLY_FRAMES = 50;
    static final int FORCE_FRAMES = 150;
    private static final int FORGET_EVERY_FRAMES = 500;

    private static final class Ears {
        /** Speaker to the frame it last reached this listener. */
        final Map<Object, Long> heard = new HashMap<>();
        /** The frame a second speaker joined, or -1 when the listener's speakers are in step. */
        long joinedAt = -1;
        long realignedAt = Long.MIN_VALUE / 2;
    }

    private final Map<UUID, Ears> listeners = new HashMap<>();
    private long frame;
    /** While set, only these listeners are reached: the streams are being ended for them alone. */
    private Set<UUID> only;

    /**
     * Asked by every speaker for every listener in its range, each time it sends.
     *
     * @return whether the listener gets what the speaker is sending now
     */
    public synchronized boolean reaches(Object speaker, UUID listener) {
        if (this.only != null) {
            return this.only.contains(listener);
        }
        final Ears ears = this.listeners.computeIfAbsent(listener, id -> new Ears());
        final Long before = ears.heard.put(speaker, this.frame);
        if ((before == null || this.frame - before > GONE_FRAMES) && ears.joinedAt < 0) {
            for (Map.Entry<Object, Long> other : ears.heard.entrySet()) {
                if (!other.getKey().equals(speaker) && this.frame - other.getValue() <= GONE_FRAMES) {
                    ears.joinedAt = this.frame;
                    break;
                }
            }
        }
        return true;
    }

    /**
     * Starts the next frame. Call once per frame, before any speaker sends it.
     *
     * @return the listeners whose streams should be ended now, before this frame goes out
     */
    public synchronized Set<UUID> beginFrame(short[] next) {
        this.frame++;
        if (this.frame % FORGET_EVERY_FRAMES == 0) {
            final long stale = this.frame - FORGET_EVERY_FRAMES;
            this.listeners.values().removeIf(ears -> ears.heard.values().stream().allMatch(at -> at < stale));
        }
        Set<UUID> due = Set.of();
        int peak = -1;
        for (Map.Entry<UUID, Ears> entry : this.listeners.entrySet()) {
            final Ears ears = entry.getValue();
            if (ears.joinedAt < 0 || this.frame - ears.realignedAt < MIN_GAP_FRAMES) {
                continue;
            }
            final long waited = this.frame - ears.joinedAt;
            if (peak < 0) {
                peak = Resync.peak(next);
            }
            final int loudest = waited >= FORCE_FRAMES ? Integer.MAX_VALUE : waited >= QUIET_ONLY_FRAMES ? 2_000 : 400;
            if (peak > loudest) {
                continue;
            }
            ears.joinedAt = -1;
            ears.realignedAt = this.frame;
            if (due.isEmpty()) {
                due = new HashSet<>();
            }
            due.add(entry.getKey());
        }
        return due;
    }

    /** From now until {@link #everyone}, speakers reach only these listeners. */
    public synchronized void only(Set<UUID> listeners) {
        this.only = Set.copyOf(listeners);
    }

    public synchronized void everyone() {
        this.only = null;
    }
}
