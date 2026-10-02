package su.nuv.radio.catalog.apple;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed Apple Music link: {@code music.apple.com/<storefront>/<type>/<slug>/<id>}, where a track
 * is either {@code song/<id>} or an album link with {@code ?i=<trackId>}. Legacy
 * {@code itunes.apple.com} and {@code geo.}/{@code embed.} hosts are accepted too.
 *
 * @param storefront two-letter store country, lower case ("us" when the link has none)
 */
public record AppleMusicLink(Type type, String id, String storefront) {

    public enum Type {
        TRACK, ALBUM, PLAYLIST, ARTIST
    }

    private static final Pattern WEB = Pattern.compile(
            "^(?:https?://)?(?:(?:geo|embed|beta)\\.)?(?:music|itunes)\\.apple\\.com/(?:([a-z]{2})/)?"
                    + "(album|song|playlist|artist)/(?:[^/?#]+/)?(pl\\.[A-Za-z0-9._-]+|\\d+)(?:[^#]*?[?&]i=(\\d+))?",
            Pattern.CASE_INSENSITIVE);

    public static Optional<AppleMusicLink> parse(String link) {
        if (link == null) {
            return Optional.empty();
        }
        final Matcher matcher = WEB.matcher(link.strip());
        if (!matcher.find()) {
            return Optional.empty();
        }
        final String store = matcher.group(1) == null ? "us" : matcher.group(1).toLowerCase(Locale.ROOT);
        final String kind = matcher.group(2).toLowerCase(Locale.ROOT);
        final String id = matcher.group(3);
        final String track = matcher.group(4);
        final Type type = switch (kind) {
            case "song" -> Type.TRACK;
            case "album" -> track != null ? Type.TRACK : Type.ALBUM;
            case "playlist" -> Type.PLAYLIST;
            default -> Type.ARTIST;
        };
        if (type == Type.PLAYLIST != id.startsWith("pl.")) {
            return Optional.empty();
        }
        return Optional.of(new AppleMusicLink(type, track != null ? track : id, store));
    }

    public static boolean isAppleMusic(String link) {
        return parse(link).isPresent();
    }

    /** Page URL for a playlist; Apple redirects the missing slug. */
    public String webUrl() {
        return switch (this.type) {
            case TRACK -> "https://music.apple.com/" + this.storefront + "/song/" + this.id;
            case ALBUM -> "https://music.apple.com/" + this.storefront + "/album/" + this.id;
            case PLAYLIST -> "https://music.apple.com/" + this.storefront + "/playlist/" + this.id;
            case ARTIST -> "https://music.apple.com/" + this.storefront + "/artist/" + this.id;
        };
    }
}
