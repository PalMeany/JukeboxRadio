package su.nuv.radio.station;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import su.nuv.radio.audio.AudioEngine;
import su.nuv.radio.audio.AudioResolver;
import su.nuv.radio.audio.FrameCushion;
import su.nuv.radio.audio.PcmFrames;
import su.nuv.radio.audio.Resync;
import su.nuv.radio.catalog.CatalogRegistry;
import su.nuv.radio.catalog.model.TrackMeta;
import su.nuv.radio.platform.RadioPlatform;
import su.nuv.radio.relay.BlockPos;
import su.nuv.radio.util.Http;
import su.nuv.radio.voice.VoiceService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;

/**
 * One jukebox's radio: a shared queue, a lavaplayer player and a voice chat channel at the block.
 * All public methods run on the server thread; lavaplayer and voice chat callbacks hop back onto it.
 */
public final class RadioStation {

    private final StationKey key;
    private final BlockPos block;
    private final RadioPlatform platform;
    private final CatalogRegistry catalogs;
    private final AudioResolver resolver;
    private final VoiceService voice;
    private final StationSettings settings;
    private final AudioPlayer player;

    private final List<QueueEntry> queue = new ArrayList<>();
    private final Deque<QueueEntry> history = new ArrayDeque<>();
    private final Set<StationListener> listeners = new CopyOnWriteArraySet<>();

    private volatile QueueEntry current;
    private volatile PlaybackState state = PlaybackState.IDLE;
    private volatile boolean disposed;
    private String lastError;
    private volatile int volume;
    private RepeatMode repeat = RepeatMode.OFF;
    private long loadToken;
    private long lastActivity = System.currentTimeMillis();
    private volatile VoiceService.VoiceOutput output;
    private final FrameCushion cushion = new FrameCushion();
    /** Note blocks repeating this jukebox; replaced whole on the server thread, read by the audio thread. */
    private volatile Map<BlockPos, VoiceService.RelayOutput> relays = Map.of();
    private volatile String linkCode;
    /** Audio thread only. */
    private final Resync resync = new Resync(System.currentTimeMillis());
    /** A relay joined: realign at the next quiet moment. */
    private volatile boolean relayJoined;

    public RadioStation(StationKey key, RadioPlatform platform, CatalogRegistry catalogs, AudioEngine engine,
                        AudioResolver resolver, VoiceService voice, StationSettings settings) {
        this.key = key;
        this.block = BlockPos.of(key);
        this.platform = platform;
        this.catalogs = catalogs;
        this.resolver = resolver;
        this.voice = voice;
        this.settings = settings;
        this.volume = settings.defaultVolume();
        this.player = engine.createPlayer();
        // volume is applied on output (see nextFrame), after the cushion, so lavaplayer stays at 100
        this.player.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackEnd(AudioPlayer p, AudioTrack track, AudioTrackEndReason reason) {
                if (reason.mayStartNext) {
                    RadioStation.this.sync(RadioStation.this::trackFinished);
                }
            }

            @Override
            public void onTrackException(AudioPlayer p, AudioTrack track, FriendlyException exception) {
                RadioStation.this.platform.logger().log(Level.FINE, "Playback error", exception);
                RadioStation.this.sync(() -> RadioStation.this.lastError = "Трек оборвался: YouTube прервал поток.");
            }

            @Override
            public void onTrackStuck(AudioPlayer p, AudioTrack track, long thresholdMs) {
                RadioStation.this.sync(RadioStation.this::skip);
            }
        });
    }

    // ------------------------------------------------------------------ read side

    public StationKey key() {
        return this.key;
    }

    /** The jukebox block; the sound comes from its centre. */
    public BlockPos block() {
        return this.block;
    }

    public QueueEntry current() {
        return this.current;
    }

    public PlaybackState state() {
        return this.state;
    }

    public String lastError() {
        return this.lastError;
    }

    public List<QueueEntry> queue() {
        return Collections.unmodifiableList(this.queue);
    }

    public int volume() {
        return this.volume;
    }

    public RepeatMode repeat() {
        return this.repeat;
    }

    public boolean hasHistory() {
        return !this.history.isEmpty();
    }

    public boolean voiceReady() {
        return this.voice.isReady();
    }

    /** The code note blocks type to repeat this jukebox, or null before one is assigned. */
    public String linkCode() {
        return this.linkCode;
    }

    public void setLinkCode(String code) {
        this.linkCode = code;
    }

    /** Note blocks currently repeating this jukebox (linked and in reach). */
    public java.util.Set<BlockPos> relayPositions() {
        return this.relays.keySet();
    }

    /**
     * Makes exactly {@code wanted} (position to volume) repeat this jukebox: keeps the outputs that
     * stay, updates their volume, opens the new ones and closes the rest.
     */
    public void setRelays(Map<BlockPos, Integer> wanted, float distance) {
        if (this.disposed) {
            return;
        }
        final Map<BlockPos, VoiceService.RelayOutput> current = this.relays;
        final Map<BlockPos, VoiceService.RelayOutput> next = new HashMap<>();
        wanted.forEach((pos, volume) -> {
            VoiceService.RelayOutput out = current.get(pos);
            if (out == null) {
                out = this.voice.openRelay(pos.world(), pos.centerX(), pos.centerY(), pos.centerZ(), distance);
                this.relayJoined = true;
            }
            if (out != null) {
                out.setVolume(volume);
                next.put(pos, out);
            }
        });
        this.relays = Map.copyOf(next);
        current.forEach((pos, out) -> {
            if (!next.containsKey(pos)) {
                out.close();
            }
        });
        this.fire(StationListener.Change.SETTINGS);
    }

    /** Position in the current track, in milliseconds. */
    public long positionMs() {
        final AudioTrack playing = this.player.getPlayingTrack();
        // the decoder runs ahead of the listeners by what the cushion holds
        return playing == null ? 0 : Math.max(0, playing.getPosition() - this.cushion.queuedMs());
    }

    /** Length of the current track; the audio's own length wins over metadata once known. */
    public long durationMs() {
        final AudioTrack playing = this.player.getPlayingTrack();
        if (playing != null && playing.getDuration() > 0 && playing.getDuration() < Long.MAX_VALUE / 2) {
            return playing.getDuration();
        }
        final QueueEntry entry = this.current;
        return entry == null ? 0 : entry.meta().durationMs();
    }

    public long queueDurationMs() {
        long total = 0;
        for (QueueEntry entry : this.queue) {
            total += entry.meta().durationMs();
        }
        return total;
    }

    public long lastActivity() {
        return this.lastActivity;
    }

    public void addListener(StationListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(StationListener listener) {
        this.listeners.remove(listener);
    }

    public boolean hasListeners() {
        return !this.listeners.isEmpty();
    }

    // ------------------------------------------------------------------ queue edits

    /**
     * Appends tracks. Returns how many were accepted; the rest were cut by the queue caps.
     *
     * @param next put them right after the current track instead of at the end
     */
    public int enqueue(List<TrackMeta> tracks, UUID requester, String requesterName, boolean next) {
        this.touch();
        int accepted = 0;
        int mine = (int) this.queue.stream().filter(e -> requester.equals(e.requesterId())).count();
        final List<QueueEntry> added = new ArrayList<>();
        for (TrackMeta track : tracks) {
            if (this.queue.size() + added.size() >= this.settings.maxQueue()) {
                break;
            }
            if (this.settings.maxPerPlayer() > 0 && mine >= this.settings.maxPerPlayer()) {
                break;
            }
            added.add(new QueueEntry(track, requester, requesterName));
            mine++;
            accepted++;
        }
        if (added.isEmpty()) {
            return 0;
        }
        if (next) {
            this.queue.addAll(0, added);
        } else {
            this.queue.addAll(added);
        }
        this.enrichAhead();
        this.fire(StationListener.Change.QUEUE);
        if (this.current == null) {
            this.advance();
        }
        return accepted;
    }

    /** Plays the track right now, keeping the queue behind it. */
    public void playNow(TrackMeta track, UUID requester, String requesterName) {
        this.touch();
        this.cushion.reset();
        this.pushHistory();
        this.start(new QueueEntry(track, requester, requesterName));
    }

    /** Jumps to a queued entry, dropping everything before it. */
    public void jumpTo(long serial) {
        this.touch();
        for (int i = 0; i < this.queue.size(); i++) {
            if (this.queue.get(i).serial() == serial) {
                final QueueEntry entry = this.queue.get(i);
                this.queue.subList(0, i + 1).clear();
                this.cushion.reset();
                this.pushHistory();
                this.start(entry);
                this.fire(StationListener.Change.QUEUE);
                return;
            }
        }
    }

    public void remove(long serial) {
        this.touch();
        if (this.queue.removeIf(e -> e.serial() == serial)) {
            this.enrichAhead();
            this.fire(StationListener.Change.QUEUE);
        }
    }

    public void moveUp(long serial) {
        this.touch();
        for (int i = 1; i < this.queue.size(); i++) {
            if (this.queue.get(i).serial() == serial) {
                Collections.swap(this.queue, i, i - 1);
                this.fire(StationListener.Change.QUEUE);
                return;
            }
        }
    }

    public void shuffle() {
        this.touch();
        Collections.shuffle(this.queue);
        this.enrichAhead();
        this.fire(StationListener.Change.QUEUE);
    }

    public void clearQueue() {
        this.touch();
        this.queue.clear();
        this.fire(StationListener.Change.QUEUE);
    }

    // ------------------------------------------------------------------ transport

    public void skip() {
        this.touch();
        this.cushion.reset();
        this.pushHistory();
        this.advanceIgnoringRepeatOne();
    }

    /** Restarts the track after 5 seconds in, otherwise goes back to the previous one. */
    public void previous() {
        this.touch();
        this.cushion.reset();
        final AudioTrack playing = this.player.getPlayingTrack();
        if (playing != null && playing.getPosition() > 5_000 || this.history.isEmpty()) {
            if (playing != null && playing.isSeekable()) {
                playing.setPosition(0);
                this.fire(StationListener.Change.STATE);
            }
            return;
        }
        final QueueEntry previous = this.history.pop();
        if (this.current != null) {
            this.queue.addFirst(this.current);
        }
        this.start(previous);
        this.fire(StationListener.Change.QUEUE);
    }

    public void togglePause() {
        this.touch();
        if (this.state == PlaybackState.PLAYING) {
            this.player.setPaused(true);
            this.state = PlaybackState.PAUSED;
            if (this.output != null) {
                this.output.stop();
            }
            this.flushRelays();
        } else if (this.state == PlaybackState.PAUSED) {
            this.player.setPaused(false);
            this.state = PlaybackState.PLAYING;
            this.ensureOutput();
        } else if (this.state == PlaybackState.IDLE && !this.queue.isEmpty()) {
            this.advance();
            return;
        }
        this.fire(StationListener.Change.STATE);
    }

    /** Tries the current track again, e.g. after a failed load. */
    public void retry() {
        this.touch();
        this.cushion.reset();
        if (this.current != null) {
            this.start(this.current);
        }
    }

    /** Stops playback and forgets the current track; the queue stays. */
    public void stop() {
        this.touch();
        this.cushion.reset();
        this.loadToken++;
        this.player.stopTrack();
        this.player.setPaused(false);
        if (this.current != null) {
            this.pushHistory();
        }
        this.current = null;
        this.state = PlaybackState.IDLE;
        if (this.output != null) {
            this.output.stop();
        }
        this.flushRelays();
        this.fire(StationListener.Change.TRACK);
    }

    public void setVolume(int value) {
        this.touch();
        this.volume = Math.max(0, Math.min(100, value));
        this.fire(StationListener.Change.SETTINGS);
    }

    public void cycleRepeat() {
        this.touch();
        this.repeat = this.repeat.next();
        this.fire(StationListener.Change.SETTINGS);
    }

    // ------------------------------------------------------------------ playback internals

    private void trackFinished() {
        if (this.disposed) {
            return;
        }
        if (this.repeat == RepeatMode.ONE && this.current != null) {
            this.start(this.current);
            return;
        }
        this.pushHistory();
        this.advanceIgnoringRepeatOne();
    }

    private void advanceIgnoringRepeatOne() {
        if (this.repeat == RepeatMode.ALL && this.current != null) {
            this.queue.add(new QueueEntry(this.current.meta(), this.current.requesterId(), this.current.requesterName()));
        }
        this.advance();
    }

    private void advance() {
        if (this.queue.isEmpty()) {
            this.loadToken++;
            this.player.stopTrack();
            this.current = null;
            // no output.stop(): the voice thread plays out what the cushion still holds, then ends
            this.state = PlaybackState.IDLE;
            this.fire(StationListener.Change.TRACK);
            return;
        }
        final QueueEntry next = this.queue.removeFirst();
        this.start(next);
        this.enrichAhead();
        this.fire(StationListener.Change.QUEUE);
    }

    private void pushHistory() {
        if (this.current != null) {
            this.history.push(this.current);
            while (this.history.size() > 30) {
                this.history.removeLast();
            }
        }
    }

    private void start(QueueEntry entry) {
        final long token = ++this.loadToken;
        this.current = entry;
        this.state = PlaybackState.LOADING;
        this.lastError = null;
        this.player.setPaused(false);
        this.player.stopTrack();
        this.silenceVanillaJukebox();
        this.fire(StationListener.Change.TRACK);
        this.enrich(entry);
        this.resolver.resolve(entry.meta()).whenComplete((track, error) -> this.sync(() -> {
            if (token != this.loadToken || this.disposed) {
                return;
            }
            if (error != null) {
                this.fail(Http.playerMessage(error, "Не удалось загрузить трек."));
                return;
            }
            this.ensureOutput();
            this.player.playTrack(track);
            this.state = PlaybackState.PLAYING;
            this.fire(StationListener.Change.STATE);
            if (!this.queue.isEmpty()) {
                this.resolver.prefetch(this.queue.getFirst().meta());
            }
        }));
    }

    private void fail(String message) {
        this.lastError = message;
        this.state = PlaybackState.FAILED;
        this.fire(StationListener.Change.STATE);
        final long token = this.loadToken;
        this.platform.later(this.settings.failSkipTicks(), () -> {
            if (token == this.loadToken && this.state == PlaybackState.FAILED && !this.disposed) {
                this.pushHistory();
                this.advance();
            }
        });
    }

    private void ensureOutput() {
        if (this.output == null) {
            this.output = this.voice.open(this.block.world(), this.block.centerX(), this.block.centerY(),
                    this.block.centerZ(), this.settings.distance(), this::nextFrame);
            if (this.output == null) {
                this.lastError = "Simple Voice Chat не запущен — звука не будет.";
                return;
            }
        }
        this.output.start();
    }

    /** Voice chat audio thread, every 20 ms. */
    private short[] nextFrame() {
        if (this.disposed) {
            return null;
        }
        final PlaybackState now = this.state;
        if (now == PlaybackState.PLAYING || now == PlaybackState.LOADING) {
            // while the next track loads, the previous one's tail still plays from the cushion
            return this.emit(this.cushion.next(this.player, this.player.getPlayingTrack() == null));
        }
        if (now == PlaybackState.IDLE && this.cushion.hasTail()) {
            // the queue ran out: finish the last track, then end playback
            return this.emit(this.cushion.next(this.player, true));
        }
        this.flushRelays();
        return null;
    }

    /**
     * Voice chat audio thread: hands the frame to every relay (station volume, then the relay's
     * own), then returns the jukebox's copy. Relays get the very frame the jukebox plays, so they
     * never drift apart.
     */
    private short[] emit(short[] frame) {
        final Map<BlockPos, VoiceService.RelayOutput> relays = this.relays;
        if (!relays.isEmpty()) {
            this.realignIfDue(frame, relays);
        }
        final int stationVolume = this.volume;
        for (VoiceService.RelayOutput relay : relays.values()) {
            final short[] copy = PcmFrames.applyVolume(frame.clone(), stationVolume);
            try {
                relay.send(PcmFrames.applyVolume(copy, relay.volume()));
            } catch (RuntimeException error) {
                this.platform.logger().log(Level.FINE, "Relay send failed", error);
            }
        }
        return PcmFrames.applyVolume(frame, stationVolume);
    }

    /**
     * Ends the jukebox's stream and every relay's at the same moment, at a quiet frame, so clients
     * drop what each stream had queued and all of them start again in step (see {@link Resync}).
     */
    private void realignIfDue(short[] frame, Map<BlockPos, VoiceService.RelayOutput> relays) {
        final long now = System.currentTimeMillis();
        if (this.relayJoined) {
            this.relayJoined = false;
            this.resync.hurry(now);
        }
        if (!this.resync.due(frame, now)) {
            return;
        }
        final VoiceService.VoiceOutput main = this.output;
        if (main != null) {
            main.flushChannel();
        }
        relays.values().forEach(VoiceService.RelayOutput::flush);
    }

    private void flushRelays() {
        for (VoiceService.RelayOutput relay : this.relays.values()) {
            relay.flush();
        }
    }

    private void silenceVanillaJukebox() {
        this.platform.silenceJukebox(this.block);
    }

    // ------------------------------------------------------------------ metadata

    /** Fetches covers for the current track and the next few, which the hotbar shows. */
    private void enrichAhead() {
        for (int i = 0; i < Math.min(9, this.queue.size()); i++) {
            this.enrich(this.queue.get(i));
        }
    }

    public void enrich(QueueEntry entry) {
        if (entry.enriched()) {
            return;
        }
        entry.markEnriched();
        this.catalogs.enrich(entry.meta()).thenAccept(upgraded -> this.sync(() -> {
            if (upgraded != entry.meta()) {
                entry.upgrade(upgraded);
                this.fire(StationListener.Change.META);
            }
        }));
    }

    // ------------------------------------------------------------------ plumbing

    private void touch() {
        this.lastActivity = System.currentTimeMillis();
    }

    private void fire(StationListener.Change change) {
        for (StationListener listener : this.listeners) {
            try {
                listener.stationChanged(this, change);
            } catch (RuntimeException error) {
                this.platform.logger().log(Level.WARNING, "Radio UI update failed", error);
            }
        }
    }

    private void sync(Runnable task) {
        if (this.disposed) {
            return;
        }
        this.platform.sync(() -> {
            if (!this.disposed) {
                task.run();
            }
        });
    }

    public boolean isPlaying() {
        return this.state == PlaybackState.PLAYING || this.state == PlaybackState.LOADING;
    }

    public void dispose() {
        this.disposed = true;
        this.loadToken++;
        this.listeners.clear();
        if (this.output != null) {
            this.output.stop();
        }
        final Map<BlockPos, VoiceService.RelayOutput> closing = this.relays;
        this.relays = Map.of();
        closing.values().forEach(VoiceService.RelayOutput::close);
        this.player.destroy();
    }
}
