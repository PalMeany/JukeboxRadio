package su.nuv.radio.menu.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Advance widths of the vanilla Minecraft font, enough to fit, wrap and truncate lines before
 * they reach the client. In GameUI a glyph scale of 1.0 is 8 canvas pixels per font pixel.
 */
public final class Glyphs {

    private static final String NARROW_2 = "!,.:;'|i";
    private static final String NARROW_3 = "l`";
    private static final String NARROW_4 = " It[]";
    private static final String NARROW_5 = "fk<>(){}\"*";
    private static final String WIDE_7 = "@~ЖжШшЩщЮюФфЫыМ";

    private static final Map<Character, Integer> TABLE = load();

    private Glyphs() {
    }

    private static Map<Character, Integer> load() {
        final Map<Character, Integer> table = new HashMap<>();
        try (InputStream in = Glyphs.class.getClassLoader().getResourceAsStream("font-advances.txt")) {
            if (in == null) {
                return table;
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                final String[] parts = line.split(" ");
                table.put((char) Integer.parseInt(parts[0], 16), Integer.parseInt(parts[1]));
            }
        } catch (IOException | RuntimeException ignored) {
            // fall back to the heuristic widths below
        }
        return table;
    }

    /** Advance of one character in font pixels, spacing included. */
    public static int advance(char c) {
        final Integer known = TABLE.get(c);
        if (known != null) {
            return known;
        }
        if (NARROW_2.indexOf(c) >= 0) {
            return 2;
        }
        if (NARROW_3.indexOf(c) >= 0) {
            return 3;
        }
        if (NARROW_4.indexOf(c) >= 0) {
            return 4;
        }
        if (NARROW_5.indexOf(c) >= 0) {
            return 5;
        }
        if (WIDE_7.indexOf(c) >= 0) {
            return 7;
        }
        if (Character.isIdeographic(c) || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_SYLLABLES) {
            return 9;
        }
        return 6;
    }

    /**
     * Characters the menu's text fonts carry: English and Russian, Latin-1 accents (é, ü — mostly
     * on the ASCII sheet anyway) and the punctuation the menu and track titles use. Every text font
     * re-anchors the vanilla sheets, so each extra character is baked once per font by the client.
     */
    public static boolean inMenuCharset(int c) {
        return (c >= 0x20 && c <= 0x7E)
                || (c >= 0xA0 && c <= 0xFF)
                || c == 0x401 || (c >= 0x410 && c <= 0x44F) || c == 0x451
                || c == 0x2013 || c == 0x2014 || c == 0x2018 || c == 0x2019
                || c == 0x201C || c == 0x201D || c == 0x201E || c == 0x2022 || c == 0x2026
                || c == 0x2116 || c == 0x2122 || c == 0x2192 || c == 0x2212;
    }

    /** Whether the menu's text fonts (shifted copies of the vanilla bitmap fonts) draw this character. */
    public static boolean supported(char c) {
        return c == ' ' || (TABLE.containsKey(c) && inMenuCharset(c));
    }

    /** Replaces characters the bitmap fonts lack, so pixel positions stay exact. */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                out.append(' ');
            } else if (supported(c)) {
                out.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                out.append('?');
            }
        }
        return out.toString();
    }

    /** Pixel advance of a string at GUI scale (font pixels = GUI pixels in a title). */
    public static int advanceSum(String text) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += advance(text.charAt(i));
        }
        return total;
    }

    /** Cuts to fit {@code maxWidth} GUI pixels, with an ellipsis when cut. */
    public static String fitPx(String text, int maxWidth) {
        final String safe = sanitize(text);
        if (advanceSum(safe) <= maxWidth) {
            return safe;
        }
        final int budget = maxWidth - advanceSum("…");
        int used = 0;
        int end = 0;
        while (end < safe.length() && used + advance(safe.charAt(end)) <= budget) {
            used += advance(safe.charAt(end));
            end++;
        }
        return safe.substring(0, end).stripTrailing() + "…";
    }

    /** Word wrap in GUI pixels, at most {@code maxLines}, last line ellipsised. */
    public static List<String> wrapPx(String text, int maxWidth, int maxLines) {
        return wrap(sanitize(text), 0.125, maxWidth, maxLines);
    }

    public static double width(String text, double scale) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += advance(text.charAt(i));
        }
        // the last glyph's trailing spacing column is not drawn
        return (total - 1) * 8 * scale;
    }

    public static double lineHeight(double scale) {
        return 8 * 8 * scale;
    }

    /** Cuts the text to fit {@code maxWidth}, ending with an ellipsis when anything was cut. */
    public static String fit(String text, double scale, double maxWidth) {
        if (text == null) {
            return "";
        }
        if (width(text, scale) <= maxWidth) {
            return text;
        }
        final String ellipsis = "…";
        final double budget = maxWidth - width(ellipsis, scale) - 8 * scale;
        int end = 0;
        double used = 0;
        while (end < text.length()) {
            final double next = advance(text.charAt(end)) * 8 * scale;
            if (used + next > budget) {
                break;
            }
            used += next;
            end++;
        }
        return text.substring(0, end).stripTrailing() + ellipsis;
    }

    /** Word-wraps into at most {@code maxLines}; the last line is truncated with an ellipsis. */
    public static List<String> wrap(String text, double scale, double maxWidth, int maxLines) {
        final List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        final String[] words = text.strip().split("\\s+");
        StringBuilder line = new StringBuilder();
        int index = 0;
        while (index < words.length) {
            final String candidate = line.isEmpty() ? words[index] : line + " " + words[index];
            if (width(candidate, scale) <= maxWidth) {
                line = new StringBuilder(candidate);
                index++;
                continue;
            }
            if (line.isEmpty()) {
                // a single word longer than the line
                line = new StringBuilder(words[index]);
                index++;
            }
            if (lines.size() == maxLines - 1) {
                break;
            }
            lines.add(line.toString());
            line = new StringBuilder();
        }
        if (index < words.length) {
            final StringBuilder rest = new StringBuilder(line);
            for (int i = index; i < words.length; i++) {
                rest.append(rest.isEmpty() ? "" : " ").append(words[i]);
            }
            lines.add(fit(rest.toString(), scale, maxWidth));
        } else if (!line.isEmpty()) {
            lines.add(fit(line.toString(), scale, maxWidth));
        }
        return lines;
    }
}
