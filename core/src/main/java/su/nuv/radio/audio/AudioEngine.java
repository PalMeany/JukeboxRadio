package su.nuv.radio.audio;

import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.Android;
import dev.lavalink.youtube.clients.AndroidMusic;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.Ios;
import dev.lavalink.youtube.clients.MWeb;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.TvHtml5Simply;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import dev.lavalink.youtube.clients.skeleton.Client;
import org.apache.http.HttpHost;
import su.nuv.radio.catalog.CatalogException;
import su.nuv.radio.util.NetProxy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lavaplayer set up to decode into 48 kHz stereo 16-bit PCM, the rate Simple Voice Chat plays at.
 * One manager for the whole server; one {@link AudioPlayer} per radio station.
 */
public final class AudioEngine {

    /**
     * 48 kHz stereo, 960 samples (20 ms) per frame, big-endian. Not StandardAudioDataFormats.COMMON_PCM_S16_BE:
     * that one is 44.1 kHz, which Simple Voice Chat played 8.8% fast and a semitone and a half high.
     */
    public static final Pcm16AudioDataFormat OUTPUT_FORMAT =
            new Pcm16AudioDataFormat(2, 48_000, PcmFrames.SAMPLES_PER_FRAME, true);

    private final AudioPlayerManager manager;
    private final YoutubeAudioSourceManager youtube;
    private final Logger logger;

    public AudioEngine(YoutubeSettings settings, NetProxy proxy, Logger logger) {
        this(settings, proxy, null, logger);
    }

    /** @param localRoot the only folder local files may be played from (the download cache), or null for none */
    public AudioEngine(YoutubeSettings settings, NetProxy proxy, Path localRoot, Logger logger) {
        this.logger = logger;
        this.manager = new DefaultAudioPlayerManager();
        final AudioConfiguration configuration = this.manager.getConfiguration();
        configuration.setOutputFormat(OUTPUT_FORMAT);
        configuration.setResamplingQuality(AudioConfiguration.ResamplingQuality.MEDIUM);
        this.manager.setFrameBufferDuration(5_000);
        this.manager.setTrackStuckThreshold(15_000);

        final YoutubeSourceOptions options = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectVideoIds(true)
                .setAllowDirectPlaylistIds(true);
        if (notBlank(settings.remoteCipherUrl())) {
            options.setRemoteCipher(settings.remoteCipherUrl(), blankToNull(settings.remoteCipherPassword()), null);
        }
        if (notBlank(settings.poToken()) && notBlank(settings.visitorData())) {
            Web.setPoTokenAndVisitorData(settings.poToken(), settings.visitorData());
        }
        this.youtube = new YoutubeAudioSourceManager(options, clients(settings.clients(), logger));
        if (notBlank(settings.oauthRefreshToken())) {
            this.youtube.useOauth2(settings.oauthRefreshToken(), true);
        }
        this.manager.registerSourceManager(this.youtube);
        // direct stream URLs, e.g. the audio URL yt-dlp extracts from a YouTube video
        this.manager.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY));
        if (localRoot != null) {
            this.manager.registerSourceManager(new CacheFileSource(localRoot.toAbsolutePath().normalize()));
        }
        if (proxy != null) {
            final HttpHost host = new HttpHost(proxy.host(), proxy.port(), "http");
            // reaches the direct-URL source; the YouTube source is not HttpConfigurable, so set it on its own
            this.manager.setHttpBuilderConfigurator(builder -> builder.setProxy(host));
            this.youtube.getHttpInterfaceManager().configureBuilder(builder -> builder.setProxy(host));
        }
    }

    private static Client[] clients(List<String> names, Logger logger) {
        final List<Client> clients = new ArrayList<>();
        for (String raw : names) {
            final Client client = switch (raw.strip().toUpperCase(Locale.ROOT)) {
                case "MUSIC" -> new Music();
                case "WEB" -> new Web();
                case "MWEB" -> new MWeb();
                case "WEBEMBEDDED", "WEB_EMBEDDED" -> new WebEmbedded();
                case "ANDROID" -> new Android();
                case "ANDROID_MUSIC", "ANDROIDMUSIC" -> new AndroidMusic();
                case "ANDROID_VR", "ANDROIDVR" -> new AndroidVr();
                case "IOS" -> new Ios();
                case "TV" -> new Tv();
                case "TVHTML5SIMPLY", "TV_SIMPLY" -> new TvHtml5Simply();
                default -> {
                    logger.warning("Unknown YouTube client '" + raw + "' in config.yml, skipped");
                    yield null;
                }
            };
            if (client != null) {
                clients.add(client);
            }
        }
        if (clients.isEmpty()) {
            clients.add(new Music());
            clients.add(new Web());
            clients.add(new AndroidVr());
        }
        return clients.toArray(Client[]::new);
    }

    public AudioPlayer createPlayer() {
        return this.manager.createPlayer();
    }

    /**
     * Loads a URL or a search identifier ({@code ytmsearch:...}, {@code ytsearch:...}). Completes
     * with a track, a playlist, or empty when nothing matched.
     */
    public CompletableFuture<Optional<AudioItem>> load(String identifier) {
        final CompletableFuture<Optional<AudioItem>> future = new CompletableFuture<>();
        this.manager.loadItem(identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(Optional.of(track));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                future.complete(Optional.of(playlist));
            }

            @Override
            public void noMatches() {
                future.complete(Optional.empty());
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                AudioEngine.this.logger.log(Level.FINE, "Load failed for " + identifier, exception);
                future.completeExceptionally(new CatalogException(friendly(exception), exception));
            }
        });
        return future;
    }

    static String friendly(FriendlyException exception) {
        final String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("sign in") || message.contains("age")) {
            return "YouTube требует вход в аккаунт для этого видео.";
        }
        if (message.contains("unavailable") || message.contains("private") || message.contains("removed")) {
            return "Видео недоступно.";
        }
        if (message.contains("playlist")) {
            return "Плейлист YouTube недоступен.";
        }
        return "YouTube не отдал этот трек.";
    }

    public void shutdown() {
        this.manager.shutdown();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return notBlank(value) ? value : null;
    }

    /** Plays files yt-dlp downloaded, and nothing outside that folder. */
    private static final class CacheFileSource extends LocalAudioSourceManager {

        private final Path root;

        CacheFileSource(Path root) {
            super(MediaContainerRegistry.DEFAULT_REGISTRY);
            this.root = root;
        }

        @Override
        public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
            final Path file;
            try {
                file = Path.of(reference.identifier).toAbsolutePath().normalize();
            } catch (RuntimeException notAPath) {
                return null;
            }
            return file.startsWith(this.root) && Files.isRegularFile(file) ? super.loadItem(manager, reference) : null;
        }
    }
}
