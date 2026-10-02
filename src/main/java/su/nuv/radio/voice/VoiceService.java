package su.nuv.radio.voice;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.bukkit.Location;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The Simple Voice Chat side: a volume category players can turn down in their voice chat
 * settings, and one locational channel per playing jukebox.
 */
public final class VoiceService implements VoicechatPlugin {

    public static final String CATEGORY = "jukeboxradio";

    private final Logger logger;
    private final String categoryName;
    private volatile VoicechatServerApi server;

    public VoiceService(Logger logger, String categoryName) {
        this.logger = logger;
        this.categoryName = categoryName;
    }

    @Override
    public String getPluginId() {
        return "jukeboxradio";
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.logger.info("Simple Voice Chat API connected");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            this.server = event.getVoicechat();
            final VolumeCategory category = this.server.volumeCategoryBuilder()
                    .setId(CATEGORY)
                    .setName(this.categoryName)
                    .setDescription("Громкость радио в проигрывателях")
                    .setIcon(DiscIcon.pixels())
                    .build();
            this.server.registerVolumeCategory(category);
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> this.server = null);
    }

    public boolean isReady() {
        return this.server != null;
    }

    /**
     * Opens a channel at the block centre. {@code frames} is polled every 20 ms from the voice chat
     * audio thread; returning {@code null} ends playback.
     */
    public VoiceOutput open(Location location, float distance, Supplier<short[]> frames) {
        final VoicechatServerApi api = this.server;
        if (api == null || location.getWorld() == null) {
            return null;
        }
        final ServerLevel level = api.fromServerLevel(location.getWorld());
        final Position position = api.createPosition(location.getX(), location.getY(), location.getZ());
        final LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), level, position);
        if (channel == null) {
            return null;
        }
        channel.setCategory(CATEGORY);
        channel.setDistance(distance);
        return new VoiceOutput(api, channel, frames);
    }

    /**
     * Opens a relay channel at a note block. Unlike {@link #open}, it has no player of its own: the
     * station pushes each frame it plays, so every relay stays in step with the jukebox.
     */
    public RelayOutput openRelay(Location location, float distance) {
        final VoicechatServerApi api = this.server;
        if (api == null || location.getWorld() == null) {
            return null;
        }
        final ServerLevel level = api.fromServerLevel(location.getWorld());
        final Position position = api.createPosition(location.getX(), location.getY(), location.getZ());
        final LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), level, position);
        if (channel == null) {
            return null;
        }
        channel.setCategory(CATEGORY);
        channel.setDistance(distance);
        return new RelayOutput(channel, api.createEncoder());
    }

    /** A note block repeating a jukebox, with its own volume (0..100). */
    public static final class RelayOutput {

        private final LocationalAudioChannel channel;
        private final OpusEncoder encoder;
        private volatile int volume = 100;
        private boolean closed;
        private boolean flushed = true;

        RelayOutput(LocationalAudioChannel channel, OpusEncoder encoder) {
            this.channel = channel;
            this.encoder = encoder;
        }

        public int volume() {
            return this.volume;
        }

        public void setVolume(int volume) {
            this.volume = Math.max(0, Math.min(100, volume));
        }

        /** Voice chat audio thread: sends one 20 ms frame, already scaled. */
        public synchronized void send(short[] frame) {
            if (this.closed) {
                return;
            }
            this.channel.send(this.encoder.encode(frame));
            this.flushed = false;
        }

        /** Tells listeners the stream paused, so the next frame starts cleanly. */
        public synchronized void flush() {
            if (this.closed || this.flushed) {
                return;
            }
            this.channel.flush();
            this.encoder.resetState();
            this.flushed = true;
        }

        public synchronized void close() {
            if (this.closed) {
                return;
            }
            this.flush();
            this.closed = true;
            this.encoder.close();
        }
    }

    /** One jukebox's channel. The voice chat player is recreated each time playback restarts. */
    public static final class VoiceOutput {

        private final VoicechatServerApi api;
        private final LocationalAudioChannel channel;
        private final Supplier<short[]> frames;
        private AudioPlayer player;

        VoiceOutput(VoicechatServerApi api, LocationalAudioChannel channel, Supplier<short[]> frames) {
            this.api = api;
            this.channel = channel;
            this.frames = frames;
        }

        public synchronized void start() {
            if (this.player != null && this.player.isPlaying()) {
                return;
            }
            this.player = this.api.createAudioPlayer(this.channel, this.api.createEncoder(), this.frames);
            this.player.startPlaying();
        }

        public synchronized void stop() {
            if (this.player != null) {
                this.player.stopPlaying();
                this.player = null;
            }
        }

        public synchronized boolean isPlaying() {
            return this.player != null && this.player.isPlaying();
        }

        public void setDistance(float distance) {
            this.channel.setDistance(distance);
        }

        /** Ends the stream for listeners; the next frame starts a new one. Call from the audio thread. */
        public void flushChannel() {
            this.channel.flush();
        }
    }
}
