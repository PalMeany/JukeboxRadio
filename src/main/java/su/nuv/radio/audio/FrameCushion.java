package su.nuv.radio.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import java.util.ArrayDeque;

/**
 * Decoded frames held between lavaplayer and the voice chat thread. A stream can arrive in bursts,
 * worst in its first second through a proxy; played straight, every gap is a stutter. Playback
 * starts, and restarts after running dry, only once {@link #PREBUFFER} frames are queued.
 * {@link #next} runs on the voice chat audio thread; {@link #reset} may come from any thread.
 */
public final class FrameCushion {

    /** 1.2 s queued before sound starts. */
    static final int PREBUFFER = 60;
    /** Up to 10 s pulled ahead of what is playing, on top of lavaplayer's own 5 s frame buffer. */
    static final int CAPACITY = 500;

    private final ArrayDeque<short[]> frames = new ArrayDeque<>();
    private boolean filling = true;
    private volatile boolean resetRequested;
    private volatile int queued;

    /**
     * The frame to play now: silence while (re)filling.
     *
     * @param trackOver lavaplayer has no track left to decode, so what is queued is all there is
     */
    public synchronized short[] next(AudioPlayer player, boolean trackOver) {
        if (this.resetRequested) {
            this.resetRequested = false;
            this.frames.clear();
            this.filling = true;
        }
        AudioFrame frame;
        while (this.frames.size() < CAPACITY && (frame = player.provide()) != null) {
            this.frames.add(PcmFrames.stereoBeToMono(frame.getData(), frame.getDataLength()));
        }
        if (this.filling && (this.frames.size() >= PREBUFFER || trackOver && !this.frames.isEmpty())) {
            this.filling = false;
        }
        final short[] out = this.filling ? null : this.frames.poll();
        if (out == null) {
            this.filling = true;
        }
        this.queued = this.frames.size();
        return out == null ? PcmFrames.silence() : out;
    }

    /** Whether frames of the last track are still waiting to be played. */
    public boolean hasTail() {
        return !this.resetRequested && this.queued > 0;
    }

    /** Drops everything queued, e.g. on skip, so the change is heard at once. */
    public void reset() {
        this.resetRequested = true;
        this.queued = 0;
    }

    /** How far the decoder is ahead of what listeners hear. */
    public long queuedMs() {
        return this.queued * 20L;
    }
}
