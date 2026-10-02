package su.nuv.radio.catalog.spotify;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A parsed Spotify link: {@code open.spotify.com/<type>/<id>} or {@code spotify:<type>:<id>}. */
public record SpotifyLink(Type type, String id) {

    public enum Type {
        TRACK, ALBUM, PLAYLIST, ARTIST;

        public String path() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Pattern WEB = Pattern.compile(
            "^(?:https?://)?(?:open|play)\\.spotify\\.com/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?(?:embed/)?"
                    + "(track|album|playlist|artist)/([A-Za-z0-9]{10,32})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern URI = Pattern.compile(
            "^spotify:(track|album|playlist|artist):([A-Za-z0-9]{10,32})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT = Pattern.compile(
            "^(?:https?://)?(?:spotify\\.link|spoti\\.fi)/[A-Za-z0-9]+", Pattern.CASE_INSENSITIVE);

    public static Optional<SpotifyLink> parse(String link) {
        if (link == null) {
            return Optional.empty();
        }
        final String text = link.strip();
        Matcher matcher = WEB.matcher(text);
        if (!matcher.find()) {
            matcher = URI.matcher(text);
            if (!matcher.find()) {
                return Optional.empty();
            }
        }
        final Type type = Type.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
        return Optional.of(new SpotifyLink(type, matcher.group(2)));
    }

    /** Whether this is a share short link that must be followed before parsing. */
    public static boolean isShortLink(String link) {
        return link != null && SHORT.matcher(link.strip()).find();
    }

    public static boolean isSpotify(String link) {
        return parse(link).isPresent() || isShortLink(link);
    }

    public String webUrl() {
        return "https://open.spotify.com/" + this.type.path() + "/" + this.id;
    }
}
