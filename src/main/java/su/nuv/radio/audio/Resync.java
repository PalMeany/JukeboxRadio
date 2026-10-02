package su.nuv.radio.audio;

/**
 * When to realign a jukebox and its relays. Simple Voice Chat clients queue each sound stream
 * without a bound: after a network stall a burst of packets waits in the queue and that stream
 * plays late from then on. Streams stall and restart at different times (a relay's stream restarts
 * when the player walks back into its range), so the jukebox and its relays drift apart. Ending
 * every stream at once makes clients drop their queues together and start again in step.
 *
 * <p>Ending a stream costs a short gap while the client refills, so it waits for a quiet frame:
 * after {@link #MIN_GAP_MS} only near silence (track ends, breaks) qualifies, the longer it waits
 * the louder a frame may be, and after {@link #FORCE_MS} any frame does. Voice chat audio thread only.
 */
public final class Resync {

    static final long MIN_GAP_MS = 30_000;
    static final long FORCE_MS = 240_000;
    /** Two quiet frames in a row, so a single dip inside a sound does not count. */
    private static final int QUIET_FRAMES = 2;

    private long lastMs;
    private int quietRun;

    public Resync(long nowMs) {
        this.lastMs = nowMs;
    }

    /** Whether to end every stream before this frame goes out. */
    public boolean due(short[] frame, long nowMs) {
        final long waited = nowMs - this.lastMs;
        if (waited < MIN_GAP_MS) {
            this.quietRun = 0;
            return false;
        }
        final int loudest = waited >= FORCE_MS ? Integer.MAX_VALUE : waited >= 90_000 ? 2_000 : 400;
        this.quietRun = peak(frame) <= loudest ? this.quietRun + 1 : 0;
        if (this.quietRun < QUIET_FRAMES) {
            return false;
        }
        this.restart(nowMs);
        return true;
    }

    /** Realign at the next quiet frame, without waiting out {@link #MIN_GAP_MS}: a stream just joined. */
    public void hurry(long nowMs) {
        this.lastMs = Math.min(this.lastMs, nowMs - MIN_GAP_MS);
    }

    /** The streams just started fresh anyway (playback began or was flushed). */
    public void restart(long nowMs) {
        this.lastMs = nowMs;
        this.quietRun = 0;
    }

    static int peak(short[] frame) {
        int peak = 0;
        for (short sample : frame) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}
