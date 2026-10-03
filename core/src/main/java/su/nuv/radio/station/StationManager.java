package su.nuv.radio.station;

import su.nuv.radio.audio.AudioEngine;
import su.nuv.radio.audio.AudioResolver;
import su.nuv.radio.catalog.CatalogRegistry;
import su.nuv.radio.platform.RadioPlatform;
import su.nuv.radio.voice.VoiceService;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Stations by jukebox. Stations are created on first use and dropped when idle or broken. */
public final class StationManager {

    private final Map<StationKey, RadioStation> stations = new HashMap<>();
    private final RadioPlatform platform;
    private final CatalogRegistry catalogs;
    private final AudioEngine engine;
    private final AudioResolver resolver;
    private final VoiceService voice;
    private final StationSettings settings;
    private Consumer<RadioStation> onCreate = station -> {
    };

    public StationManager(RadioPlatform platform, CatalogRegistry catalogs, AudioEngine engine, AudioResolver resolver,
                          VoiceService voice, StationSettings settings) {
        this.platform = platform;
        this.catalogs = catalogs;
        this.engine = engine;
        this.resolver = resolver;
        this.voice = voice;
        this.settings = settings;
        platform.repeat(20L * 60, 20L * 60, this::sweepIdle);
    }

    /** Runs for every new station, after it is registered: relays attach here. */
    public void onCreate(Consumer<RadioStation> hook) {
        this.onCreate = hook;
    }

    public RadioStation getOrCreate(StationKey key) {
        RadioStation station = this.stations.get(key);
        if (station == null) {
            station = new RadioStation(key, this.platform, this.catalogs, this.engine, this.resolver, this.voice,
                    this.settings);
            this.stations.put(key, station);
            this.onCreate.accept(station);
        }
        return station;
    }

    public Optional<RadioStation> get(StationKey key) {
        return Optional.ofNullable(this.stations.get(key));
    }

    public Collection<RadioStation> all() {
        return List.copyOf(this.stations.values());
    }

    public void remove(StationKey key) {
        final RadioStation station = this.stations.remove(key);
        if (station != null) {
            station.dispose();
        }
    }

    private void sweepIdle() {
        final long cutoff = System.currentTimeMillis() - this.settings.idleMinutes() * 60_000L;
        this.stations.values().removeIf(station -> {
            final boolean idle = !station.isPlaying() && !station.hasListeners() && station.lastActivity() < cutoff;
            if (idle) {
                station.dispose();
            }
            return idle;
        });
    }

    public void shutdown() {
        this.stations.values().forEach(RadioStation::dispose);
        this.stations.clear();
    }
}
