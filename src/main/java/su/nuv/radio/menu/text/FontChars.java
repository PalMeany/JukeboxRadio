package su.nuv.radio.menu.text;

import net.kyori.adventure.key.Key;
import su.nuv.radio.menu.MenuLayout;

/**
 * Private-use characters of the radio's fonts and the rules for where they land. The pack
 * builder and the title builder both read this, so a glyph is defined in exactly one place.
 */
public final class FontChars {

    /** Spaces, backgrounds and pixel glyphs. */
    public static final Key UI = Key.key(MenuLayout.NS, "ui");

    /** Negative advances -1, -2, -4 ... -512. */
    public static final char NEG = '';
    /** Positive advances 1, 2, 4 ... 512. */
    public static final char POS = '';
    public static final int SPACE_BITS = 10;

    public static final char BG_MAIN_L = '';
    public static final char BG_MAIN_R = '';
    public static final char BG_LIST_L = '';
    public static final char BG_LIST_R = '';
    public static final char BG_ZOOM_L = '';
    public static final char BG_ZOOM_R = '';

    /** Pixel glyph whose 2×2 dot has its top at container y = index; advance 3. */
    public static final char PIXEL_BASE = '';
    public static final int PIXEL_ROWS = 240;

    /**
     * Glyph tops the menu writes text at, one text font each. Every font costs the client a full
     * copy of the vanilla glyph sheets on each resource reload, so only these rows get one.
     * Derived from RadioMenu: playing screen (6, 8, hint 36+10i, title 21+10n, artist/album/
     * requester 23..73, times 95, queue 106/116), list screens (6, rowTop(r), rowTop(r)+8,
     * rowTop(2)+5) and zoom (title 12+10n, artist from 16, footer 104/114). Add a row here when
     * the layout gets a new one; any other top falls back to the nearest row.
     */
    private static final int[] TEXT_TOPS = {
            6, 8, 12, 16, 18, 21, 22, 23, 26, 31, 32, 33, 36, 41, 42, 43, 44, 46, 53, 54, 56, 59, 62, 63, 66,
            72, 73, 80, 90, 95, 98, 104, 106, 114, 116};

    private FontChars() {
    }

    public static char pixel(int y) {
        if (y < 0 || y >= PIXEL_ROWS) {
            throw new IllegalArgumentException("pixel row " + y);
        }
        return (char) (PIXEL_BASE + y);
    }

    public static int[] textTops() {
        return TEXT_TOPS.clone();
    }

    /** Font whose glyph tops sit at container y {@code top}, or the nearest row that has one. */
    public static Key textFont(int top) {
        int best = TEXT_TOPS[0];
        for (int candidate : TEXT_TOPS) {
            if (Math.abs(candidate - top) < Math.abs(best - top)) {
                best = candidate;
            }
        }
        return Key.key(MenuLayout.NS, "t" + best);
    }

    /** Ascent that puts a glyph's top at container y, for a glyph of the given native ascent. */
    public static int ascentFor(int top, int nativeAscent) {
        // vanilla draws the title's line top at y 6, a glyph top at line top + 7 - ascent
        return nativeAscent - (top - MenuLayout.TITLE_Y);
    }

    /** Spaces that add up to {@code advance}, e.g. -13 -> -8 -4 -1. */
    public static String spaces(int advance) {
        final StringBuilder out = new StringBuilder();
        int left = Math.abs(advance);
        final char base = advance < 0 ? NEG : POS;
        for (int bit = SPACE_BITS - 1; bit >= 0 && left > 0; bit--) {
            final int value = 1 << bit;
            while (left >= value) {
                out.append((char) (base + bit));
                left -= value;
            }
        }
        return out.toString();
    }
}
