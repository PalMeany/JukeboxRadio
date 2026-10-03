package su.nuv.radio.audio;

/**
 * Converts lavaplayer's 20 ms frames (48 kHz, 16-bit big-endian, stereo) to the mono frames Simple
 * Voice Chat expects: 960 signed shorts.
 */
public final class PcmFrames {

    public static final int SAMPLES_PER_FRAME = 960;

    private PcmFrames() {
    }

    /**
     * Downmixes an interleaved stereo big-endian frame. Shorter input is zero-padded, so a partial
     * last frame never throws.
     */
    public static short[] stereoBeToMono(byte[] data, int length) {
        final short[] out = new short[SAMPLES_PER_FRAME];
        final int frames = Math.min(SAMPLES_PER_FRAME, length / 4);
        for (int i = 0; i < frames; i++) {
            final int base = i * 4;
            final int left = (short) (((data[base] & 0xFF) << 8) | (data[base + 1] & 0xFF));
            final int right = (short) (((data[base + 2] & 0xFF) << 8) | (data[base + 3] & 0xFF));
            out[i] = (short) ((left + right) >> 1);
        }
        return out;
    }

    /**
     * Scales a frame in place for a 0..100 volume. Perceived loudness grows roughly as amplitude^0.6
     * (Stevens), so a gain of (volume/100)^(5/3) makes loudness follow the slider evenly: 50 sounds
     * half as loud as 100, and every step of the buttons changes it by the same amount. Applied on
     * output, a change is heard at once, not after the cushion.
     */
    public static short[] applyVolume(short[] frame, int volume) {
        if (volume >= 100) {
            return frame;
        }
        final double gain = volume <= 0 ? 0 : Math.pow(volume / 100.0, 5.0 / 3.0);
        for (int i = 0; i < frame.length; i++) {
            frame[i] = (short) Math.round(frame[i] * gain);
        }
        return frame;
    }

    public static short[] silence() {
        return new short[SAMPLES_PER_FRAME];
    }
}
