package su.nuv.radio;

import su.nuv.radio.audio.AudioCache;
import su.nuv.radio.audio.AudioEngine;
import su.nuv.radio.audio.AudioResolver;
import su.nuv.radio.audio.YtDlp;
import su.nuv.radio.catalog.CatalogRegistry;
import su.nuv.radio.catalog.apple.AppleMusicCatalog;
import su.nuv.radio.catalog.spotify.SpotifyCatalog;
import su.nuv.radio.catalog.youtube.YouTubeCatalog;
import su.nuv.radio.command.RadioCommand;
import su.nuv.radio.config.ConfigFile;
import su.nuv.radio.config.Messages;
import su.nuv.radio.config.RadioConfig;
import su.nuv.radio.menu.MenuManager;
import su.nuv.radio.menu.Tone;
import su.nuv.radio.menu.art.CoverService;
import su.nuv.radio.menu.pack.PackArt;
import su.nuv.radio.menu.pack.PackBuilder;
import su.nuv.radio.menu.pack.PackDelivery;
import su.nuv.radio.platform.BlockKind;
import su.nuv.radio.platform.PackStatus;
import su.nuv.radio.platform.RadioPlatform;
import su.nuv.radio.platform.RadioPlayer;
import su.nuv.radio.relay.BlockPos;
import su.nuv.radio.relay.RelayManager;
import su.nuv.radio.relay.RelayStore;
import su.nuv.radio.station.StationKey;
import su.nuv.radio.station.StationManager;
import su.nuv.radio.util.Http;
import su.nuv.radio.util.NetProxy;
import su.nuv.radio.voice.VoiceService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

/**
 * JukeboxRadio: Shift + use on a jukebox opens the radio menu (a chest menu drawn by the radio's
 * own resource pack); Simple Voice Chat plays the music at the block. This class wires the
 * services together; a platform module creates it, feeds it events and calls {@link #disable}.
 */
public final class Radio {

    public static final String USE = "jukeboxradio.use";
    public static final String ADMIN = "jukeboxradio.admin";

    private final RadioPlatform platform;
    private final VoiceService voice;
    private RadioConfig config;
    private CatalogRegistry catalogs;
    private AudioEngine engine;
    private StationManager stations;
    private MenuManager menus;
    private PackDelivery pack;
    private RelayManager relays;
    private RadioCommand command;

    /** {@code voice} must already be registered with Simple Voice Chat by the platform, if it is installed. */
    public Radio(RadioPlatform platform, VoiceService voice) {
        this.platform = platform;
        this.voice = voice;
    }

    /** Reads config.yml; call before {@link #enable}, e.g. to name the voice chat volume slider. */
    public RadioConfig loadConfig() {
        if (this.config == null) {
            this.config = RadioConfig.from(ConfigFile.load(this.platform.dataFolder(), this.platform.logger()));
        }
        return this.config;
    }

    public void enable(boolean voiceInstalled) {
        final RadioConfig config = this.loadConfig();
        final Path data = this.platform.dataFolder();

        final NetProxy proxy = this.proxy();
        Http.useProxy(proxy);
        final RadioConfig.AudioSettings audio = config.audio();
        final AudioCache cache = audio.cacheMb() > 0
                ? new AudioCache(data.resolve("cache"), audio.cacheMb() * 1024L * 1024L, this.platform.logger())
                : null;
        this.engine = new AudioEngine(config.youtube(), proxy, cache == null ? null : cache.dir(), this.platform.logger());
        this.catalogs = this.buildCatalogs();
        final YtDlp ytDlp = new YtDlp(this.platform.logger(), audio.extraArgs(), proxy);
        if (audio.mode() != AudioResolver.Mode.LAVAPLAYER) {
            // the first download is ~35 MB: do it off the main thread; AUTO plays through lavaplayer meanwhile
            Http.executor().execute(() -> ytDlp.install(audio.ytDlpPath(), data.resolve("bin"), audio.autoDownload(),
                    audio.autoUpdate()));
        }
        final AudioResolver resolver = new AudioResolver(this.engine, ytDlp, audio.mode(), cache, this.platform.logger());
        if (cache != null) {
            Http.executor().execute(cache::trim);
        }

        this.voice.useWorlds(this.platform::nativeWorld);
        if (!voiceInstalled) {
            this.platform.logger().severe("Simple Voice Chat is not installed: the radio will be silent. "
                    + "Install voicechat on the server.");
        }

        this.stations = new StationManager(this.platform, this.catalogs, this.engine, resolver, this.voice, config.station());
        this.relays = new RelayManager(this.platform, new RelayStore(data.resolve("links.yml"), this.platform.logger()),
                this.stations, config.relay(), config.station().distance());
        this.relays.enable();

        Tone.use(config.menu().theme());
        PackArt.use(config.menu().theme());
        final PackBuilder.Built built = new PackBuilder(config.menu().spinTicks()).build();
        try {
            PackBuilder.write(built, data.resolve("pack").resolve("jukeboxradio.zip"));
        } catch (IOException error) {
            this.platform.logger().log(Level.WARNING, "Could not write the radio pack to disk", error);
        }
        this.pack = new PackDelivery(this.platform, config.pack(), built);
        this.pack.start();
        this.menus = new MenuManager(this.platform, this.catalogs, new CoverService(this.platform.logger()), this.pack,
                config.menu().requirePack());
        this.menus.enable();
        this.command = new RadioCommand(this);

        if (!config.spotify().hasCredentials()) {
            this.platform.logger().warning("Spotify keys are empty: search falls back to YouTube Music; Spotify links "
                    + "still open through public embed pages. Fill spotify.client-id/client-secret in config.yml.");
        }
    }

    /** network.proxy, or null for direct connections; a bad value is reported and ignored. */
    private NetProxy proxy() {
        try {
            final NetProxy proxy = NetProxy.parse(this.config.proxy()).orElse(null);
            if (proxy != null) {
                this.platform.logger().info("All radio traffic goes through proxy " + proxy.url());
            }
            return proxy;
        } catch (IllegalArgumentException error) {
            this.platform.logger().severe("network.proxy ignored: " + error.getMessage() + ". Connecting directly.");
            return null;
        }
    }

    /**
     * Every metadata provider. To add another music service, implement
     * {@link su.nuv.radio.catalog.MusicCatalog}, register it here and set catalog.search-provider.
     */
    private CatalogRegistry buildCatalogs() {
        final CatalogRegistry registry = new CatalogRegistry(this.config.searchProvider());
        registry.register(new SpotifyCatalog(this.config.spotify(), this.platform.logger()));
        registry.register(new AppleMusicCatalog(this.config.spotify().collectionLimit(), this.platform.logger()));
        registry.register(new YouTubeCatalog(this.engine, this.config.spotify().collectionLimit()));
        return registry;
    }

    public void disable() {
        if (this.menus != null) {
            this.menus.closeAll();
        }
        if (this.pack != null) {
            this.pack.stop();
        }
        if (this.stations != null) {
            this.stations.shutdown();
        }
        if (this.relays != null) {
            this.relays.shutdown();
        }
        if (this.catalogs != null) {
            this.catalogs.shutdown();
        }
        if (this.engine != null) {
            this.engine.shutdown();
        }
        Http.executor().shutdownNow();
    }

    // ------------------------------------------------------------------ platform events

    /**
     * Shift + use on a jukebox or a note block. Returns true when the radio took the click, so
     * the platform cancels the vanilla interaction.
     */
    public boolean use(RadioPlayer player, BlockPos pos, BlockKind kind, boolean sneaking, boolean emptyHand) {
        if (!sneaking || kind == BlockKind.OTHER) {
            return false;
        }
        if (kind == BlockKind.NOTE_BLOCK && !this.config.relay().enabled()) {
            return false;
        }
        if (this.config.requireEmptyHand() && !emptyHand) {
            return false;
        }
        if (!player.hasPermission(USE)) {
            Messages.error(player, "Нет прав пользоваться радио.");
            return true;
        }
        if (kind == BlockKind.NOTE_BLOCK) {
            this.relays.openDialog(player, pos);
            return true;
        }
        final MenuManager.OpenResult result = this.menus.open(player, this.stations.getOrCreate(pos.stationKey()));
        if (result == MenuManager.OpenResult.PACK_PENDING) {
            Messages.info(player, "Загружается ресурс-пак радио — откройте проигрыватель ещё раз через пару секунд.");
        } else if (result == MenuManager.OpenResult.PACK_MISSING) {
            Messages.error(player, "Для меню нужен ресурс-пак радио. Разрешите ресурс-паки сервера (Мультиплеер → "
                    + "Настроить → Наборы ресурсов: Включены) или пользуйтесь /radio play <ссылка>.");
        }
        return true;
    }

    /** A jukebox or note block was broken, burnt or blown up. */
    public void blockGone(BlockPos pos, BlockKind kind) {
        if (kind == BlockKind.JUKEBOX) {
            final StationKey key = pos.stationKey();
            this.menus.closeStation(key);
            this.stations.remove(key);
            this.relays.jukeboxGone(pos);
        } else if (kind == BlockKind.NOTE_BLOCK) {
            this.relays.relayGone(pos);
        }
    }

    public void joined(RadioPlayer player) {
        this.pack.joined(player);
    }

    public void quit(UUID player) {
        this.menus.quit(player);
        this.pack.quit(player);
    }

    public void died(UUID player) {
        this.menus.died(player);
    }

    public void packStatus(UUID player, String name, UUID packId, PackStatus status) {
        this.pack.status(player, name, packId, status);
    }

    // ------------------------------------------------------------------ services

    public RadioPlatform platform() {
        return this.platform;
    }

    public RadioConfig config() {
        return this.config;
    }

    public StationManager stations() {
        return this.stations;
    }

    public RelayManager relays() {
        return this.relays;
    }

    public MenuManager menus() {
        return this.menus;
    }

    public CatalogRegistry catalogs() {
        return this.catalogs;
    }

    public PackDelivery pack() {
        return this.pack;
    }

    public RadioCommand command() {
        return this.command;
    }

    public void sync(Runnable task) {
        this.platform.sync(task);
    }
}
