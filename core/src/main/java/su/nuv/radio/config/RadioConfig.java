package su.nuv.radio.config;

import su.nuv.radio.audio.YoutubeSettings;
import su.nuv.radio.catalog.spotify.SpotifyConfig;
import su.nuv.radio.station.StationSettings;
import su.nuv.radio.menu.pack.PackDelivery;

import java.util.List;

/** config.yml, read once at startup. */
public record RadioConfig(
        String searchProvider,
        SpotifyConfig spotify,
        YoutubeSettings youtube,
        StationSettings station,
        MenuSettings menu,
        PackDelivery.Settings pack,
        boolean requireEmptyHand,
        String voiceCategoryName,
        AudioSettings audio,
        String proxy,
        RelaySettings relay
) {

    /**
     * Note blocks repeating a jukebox.
     *
     * @param range          a note block joins when within this many blocks of the jukebox or of another active relay
     * @param maxPerJukebox  note blocks one jukebox may have
     */
    public record RelaySettings(boolean enabled, double range, int maxPerJukebox) {
    }

    /** @param spinTicks ticks per disc revolution in the menu */
    public record MenuSettings(int spinTicks, boolean requirePack, su.nuv.radio.menu.MenuTheme theme) {
    }

    /** How YouTube audio is fetched. */
    public record AudioSettings(su.nuv.radio.audio.AudioResolver.Mode mode, String ytDlpPath, boolean autoDownload,
                                boolean autoUpdate, List<String> extraArgs, int cacheMb) {
    }

    public static RadioConfig from(YamlTree file) {
        final int collectionLimit = clamp(file.getInt("radio.collection-limit", 200), 1, 1000);
        final YamlTree spotify = file.section("spotify");
        final YamlTree youtube = file.section("youtube");
        final YamlTree radio = file.section("radio");
        final YamlTree ui = file.section("menu");
        return new RadioConfig(
                file.getString("catalog.search-provider", "spotify"),
                new SpotifyConfig(
                        spotify.getString("client-id", ""),
                        spotify.getString("client-secret", ""),
                        spotify.getString("market", "US"),
                        spotify.getBoolean("embed-fallback", true),
                        collectionLimit),
                new YoutubeSettings(
                        youtube.getStringList("clients").isEmpty()
                                ? List.of("MUSIC", "WEB", "MWEB", "WEBEMBEDDED", "ANDROID_VR", "TV")
                                : youtube.getStringList("clients"),
                        youtube.getString("oauth-refresh-token", ""),
                        youtube.getString("po-token", ""),
                        youtube.getString("visitor-data", ""),
                        youtube.getString("remote-cipher.url", ""),
                        youtube.getString("remote-cipher.password", "")),
                new StationSettings(
                        (float) Math.max(4, radio.getDouble("distance", 48)),
                        clamp(radio.getInt("default-volume", 70), 0, 100),
                        clamp(radio.getInt("max-queue", 500), 1, 10_000),
                        Math.max(0, radio.getInt("max-per-player", 0)),
                        clamp(radio.getInt("fail-skip-seconds", 3), 1, 60) * 20,
                        clamp(radio.getInt("idle-minutes", 10), 1, 1440)),
                new MenuSettings(
                        clamp(ui.getInt("spin-ticks", 64), 16, 400),
                        ui.getBoolean("require-pack", true),
                        su.nuv.radio.menu.MenuTheme.parse(ui.getString("theme", "vanilla"))),
                new PackDelivery.Settings(
                        packMode(file.getString("pack.mode", "self-host")),
                        file.getString("pack.bind-ip", "0.0.0.0"),
                        clamp(file.getInt("pack.port", 8166), 1, 65535),
                        file.getString("pack.public-address", "auto"),
                        file.getString("pack.external-url", ""),
                        file.getBoolean("pack.required", false),
                        file.getString("pack.prompt", "Ресурс-пак для меню радио в проигрывателях.")),
                file.getBoolean("open.require-empty-hand", false),
                file.getString("voice.category-name", "Радио"),
                new AudioSettings(
                        mode(file.getString("audio.youtube-playback", "auto")),
                        file.getString("audio.yt-dlp.path", ""),
                        file.getBoolean("audio.yt-dlp.auto-download", true),
                        file.getBoolean("audio.yt-dlp.auto-update", true),
                        file.getStringList("audio.yt-dlp.extra-args"),
                        Math.max(0, file.getInt("audio.cache-mb", 512))),
                file.getString("network.proxy", ""),
                new RelaySettings(
                        file.getBoolean("relay.enabled", true),
                        Math.max(4, file.getDouble("relay.range", 48)),
                        clamp(file.getInt("relay.max-per-jukebox", 16), 1, 256)));
    }

    private static su.nuv.radio.audio.AudioResolver.Mode mode(String value) {
        return switch (value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "yt-dlp", "ytdlp" -> su.nuv.radio.audio.AudioResolver.Mode.YT_DLP;
            case "lavaplayer", "builtin", "built-in" -> su.nuv.radio.audio.AudioResolver.Mode.LAVAPLAYER;
            default -> su.nuv.radio.audio.AudioResolver.Mode.AUTO;
        };
    }

    private static PackDelivery.Mode packMode(String value) {
        return switch (value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "external", "url" -> PackDelivery.Mode.EXTERNAL;
            case "none", "off", "manual" -> PackDelivery.Mode.NONE;
            default -> PackDelivery.Mode.SELF_HOST;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
