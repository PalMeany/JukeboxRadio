package su.nuv.radio;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import su.nuv.radio.audio.AudioCache;
import su.nuv.radio.audio.AudioEngine;
import su.nuv.radio.audio.AudioResolver;
import su.nuv.radio.catalog.CatalogRegistry;
import su.nuv.radio.catalog.apple.AppleMusicCatalog;
import su.nuv.radio.catalog.spotify.SpotifyCatalog;
import su.nuv.radio.catalog.youtube.YouTubeCatalog;
import su.nuv.radio.command.RadioCommand;
import su.nuv.radio.config.RadioConfig;
import su.nuv.radio.listener.JukeboxListener;
import su.nuv.radio.station.StationManager;
import su.nuv.radio.menu.MenuManager;
import su.nuv.radio.menu.art.CoverService;
import su.nuv.radio.menu.pack.PackBuilder;
import su.nuv.radio.menu.pack.PackDelivery;
import su.nuv.radio.relay.RelayListener;
import su.nuv.radio.relay.RelayManager;
import su.nuv.radio.relay.RelayStore;
import su.nuv.radio.util.Http;
import su.nuv.radio.util.NetProxy;

import java.util.logging.Level;

/**
 * JukeboxRadio: Shift + use on a jukebox opens the radio menu (a chest menu drawn by the plugin's
 * own resource pack); Simple Voice Chat plays the music at the block. Wiring only; every feature lives in its own package.
 */
public final class RadioPlugin extends JavaPlugin {

    private RadioConfig config;
    private CatalogRegistry catalogs;
    private AudioEngine engine;
    private StationManager stations;
    private MenuManager menus;
    private PackDelivery pack;
    private RelayManager relays;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.config = RadioConfig.from(this.getConfig());

        final NetProxy proxy = this.proxy();
        Http.useProxy(proxy);
        final RadioConfig.AudioSettings audio = this.config.audio();
        final AudioCache cache = audio.cacheMb() > 0
                ? new AudioCache(this.getDataFolder().toPath().resolve("cache"), audio.cacheMb() * 1024L * 1024L,
                this.getLogger())
                : null;
        this.engine = new AudioEngine(this.config.youtube(), proxy, cache == null ? null : cache.dir(), this.getLogger());
        this.catalogs = this.buildCatalogs();
        final su.nuv.radio.audio.YtDlp ytDlp = new su.nuv.radio.audio.YtDlp(this.getLogger(), audio.extraArgs(), proxy);
        if (audio.mode() != AudioResolver.Mode.LAVAPLAYER) {
            final java.nio.file.Path bin = this.getDataFolder().toPath().resolve("bin");
            // the first download is ~35 MB: do it off the main thread; AUTO plays through lavaplayer meanwhile
            Http.executor().execute(() -> ytDlp.install(audio.ytDlpPath(), bin, audio.autoDownload(), audio.autoUpdate()));
        }
        final AudioResolver resolver = new AudioResolver(this.engine, ytDlp, audio.mode(), cache, this.getLogger());
        if (cache != null) {
            Http.executor().execute(cache::trim);
        }

        final su.nuv.radio.voice.VoiceService voice =
                new su.nuv.radio.voice.VoiceService(this.getLogger(), this.config.voiceCategoryName());
        boolean voiceOk;
        try {
            voiceOk = Integrations.registerVoice(voice);
        } catch (NoClassDefFoundError missing) {
            voiceOk = false;
        }
        if (!voiceOk) {
            this.getLogger().severe("Simple Voice Chat is not installed: the radio will be silent. "
                    + "Install the voicechat plugin (Bukkit/Paper build).");
        }

        this.stations = new StationManager(this, this.catalogs, this.engine, resolver, voice, this.config.station());
        this.relays = new RelayManager(this, new RelayStore(this.getDataFolder().toPath().resolve("links.yml"),
                this.getLogger()), this.stations, this.config.relay(), this.config.station().distance());
        this.relays.enable();

        final PackBuilder.Built pack = new PackBuilder(this.config.menu().spinTicks()).build();
        try {
            PackBuilder.write(pack, this.getDataFolder().toPath().resolve("pack").resolve("jukeboxradio.zip"));
        } catch (java.io.IOException error) {
            this.getLogger().log(Level.WARNING, "Could not write the radio pack to disk", error);
        }
        this.pack = new PackDelivery(this, this.config.pack(), pack);
        this.pack.start();
        this.menus = new MenuManager(this, this.catalogs, new CoverService(this.getLogger()), this.pack,
                this.config.menu().requirePack());
        this.menus.enable();

        Bukkit.getPluginManager().registerEvents(new JukeboxListener(this, this.config.requireEmptyHand()), this);
        if (this.config.relay().enabled()) {
            Bukkit.getPluginManager().registerEvents(new RelayListener(this.relays, this.config.requireEmptyHand()), this);
        }
        final PluginCommand command = this.getCommand("radio");
        if (command != null) {
            final RadioCommand executor = new RadioCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        if (!this.config.spotify().hasCredentials()) {
            this.getLogger().warning("Spotify keys are empty: search falls back to YouTube Music; Spotify links "
                    + "still open through public embed pages. Fill spotify.client-id/client-secret in config.yml.");
        }
    }

    /** network.proxy, or null for direct connections; a bad value is reported and ignored. */
    private NetProxy proxy() {
        try {
            final NetProxy proxy = NetProxy.parse(this.config.proxy()).orElse(null);
            if (proxy != null) {
                this.getLogger().info("All radio traffic goes through proxy " + proxy.url());
            }
            return proxy;
        } catch (IllegalArgumentException error) {
            this.getLogger().severe("network.proxy ignored: " + error.getMessage() + ". Connecting directly.");
            return null;
        }
    }

    /**
     * Every metadata provider. To add another music service, implement
     * {@link su.nuv.radio.catalog.MusicCatalog}, register it here and set catalog.search-provider.
     */
    private CatalogRegistry buildCatalogs() {
        final CatalogRegistry registry = new CatalogRegistry(this.config.searchProvider());
        registry.register(new SpotifyCatalog(this.config.spotify(), this.getLogger()));
        registry.register(new AppleMusicCatalog(this.config.spotify().collectionLimit(), this.getLogger()));
        registry.register(new YouTubeCatalog(this.engine, this.config.spotify().collectionLimit()));
        return registry;
    }

    @Override
    public void onDisable() {
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

    public void sync(Runnable task) {
        if (!this.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
}
