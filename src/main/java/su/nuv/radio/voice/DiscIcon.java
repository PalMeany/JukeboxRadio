package su.nuv.radio.voice;

/** 16×16 ARGB icon for the voice chat volume category: a small music disc. */
final class DiscIcon {

    private static final String[] ROWS = {
            "................",
            ".....######.....",
            "...##aaaaaa##...",
            "..#aabbbbbbaa#..",
            "..#abbaaaabba#..",
            ".#abbarrrrabba#.",
            ".#abarrrrrraba#.",
            ".#abarrkkrraba#.",
            ".#abarrkkrraba#.",
            ".#abarrrrrraba#.",
            ".#abbarrrrabba#.",
            "..#abbaaaabba#..",
            "..#aabbbbbbaa#..",
            "...##aaaaaa##...",
            ".....######.....",
            "................",
    };

    private DiscIcon() {
    }

    static int[][] pixels() {
        final int[][] out = new int[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                out[x][y] = switch (ROWS[y].charAt(x)) {
                    case '#' -> 0xFF0F0F0F;
                    case 'a' -> 0xFF262626;
                    case 'b' -> 0xFF3A3A3A;
                    case 'r' -> 0xFFE8A33D;
                    case 'k' -> 0xFF0F0F0F;
                    default -> 0x00000000;
                };
            }
        }
        return out;
    }
}
