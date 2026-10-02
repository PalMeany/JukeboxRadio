package su.nuv.radio.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Link clean-up shared by catalogs and the search box. */
public final class Links {

    private static final Pattern URL = Pattern.compile("(https?://\\S+|spotify:[a-z]+:[A-Za-z0-9]+)");

    private Links() {
    }

    /**
     * Trims the text and, when it contains a link among other words (as pasted share text often
     * does), keeps only the link.
     */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        final String trimmed = text.strip();
        final Matcher matcher = URL.matcher(trimmed);
        if (matcher.find()) {
            String link = matcher.group(1);
            while (!link.isEmpty() && ".,;)]>\"'".indexOf(link.charAt(link.length() - 1)) >= 0) {
                link = link.substring(0, link.length() - 1);
            }
            return link;
        }
        return trimmed;
    }

    public static boolean looksLikeLink(String text) {
        final String lower = clean(text).toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("spotify:")
                || lower.startsWith("www.") || lower.startsWith("youtu.be/") || lower.startsWith("open.spotify.com/")
                || lower.startsWith("music.apple.com/");
    }

    /** Adds a scheme to bare {@code www.} / host links so catalogs can parse them. */
    public static String withScheme(String link) {
        final String lower = link.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("spotify:")) {
            return link;
        }
        return "https://" + link;
    }
}
