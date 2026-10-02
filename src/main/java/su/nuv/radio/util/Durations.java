package su.nuv.radio.util;

/** Clock-style formatting for track lengths. */
public final class Durations {

    private Durations() {
    }

    /** {@code 3:07}, {@code 1:02:44}; {@code --:--} when unknown. */
    public static String clock(long ms) {
        if (ms <= 0) {
            return "--:--";
        }
        final long totalSeconds = ms / 1000;
        final long hours = totalSeconds / 3600;
        final long minutes = (totalSeconds % 3600) / 60;
        final long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    /** Russian plural for "трек": 1 трек, 2 трека, 5 треков. */
    public static String tracks(int count) {
        final int mod100 = Math.abs(count) % 100;
        final int mod10 = mod100 % 10;
        final String word;
        if (mod100 >= 11 && mod100 <= 14) {
            word = "треков";
        } else if (mod10 == 1) {
            word = "трек";
        } else if (mod10 >= 2 && mod10 <= 4) {
            word = "трека";
        } else {
            word = "треков";
        }
        return count + " " + word;
    }
}
