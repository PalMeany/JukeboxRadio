package su.nuv.radio.platform;

import su.nuv.radio.relay.BlockPos;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/** The server the radio runs on: threads, players, worlds and the native screens it can show. */
public interface RadioPlatform {

    Logger logger();

    /** Where the radio keeps config.yml, links.yml, the pack, yt-dlp and the audio cache. */
    Path dataFolder();

    /** False once the radio is shutting down: scheduled work must not start any more. */
    boolean isEnabled();

    boolean isMainThread();

    /** Runs {@code task} on the server thread on the next tick. */
    void runSync(Runnable task);

    /** Runs {@code task} on the server thread after {@code ticks} ticks. */
    void later(long ticks, Runnable task);

    /** Runs {@code task} on the server thread every {@code period} ticks, first after {@code delay}. */
    void repeat(long delay, long period, Runnable task);

    /** Runs {@code task} on the server thread: now when already there, otherwise on the next tick. */
    default void sync(Runnable task) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.isMainThread()) {
            task.run();
        } else {
            this.runSync(task);
        }
    }

    Optional<RadioPlayer> player(UUID id);

    Optional<RadioPlayer> playerExact(String name);

    /** What kind of block stands at {@code pos}, or {@link BlockKind#OTHER} when the chunk is not loaded. */
    BlockKind blockAt(BlockPos pos);

    /** Stops a vanilla disc the jukebox may be playing, so it does not play over the radio. */
    void silenceJukebox(BlockPos pos);

    /** The platform's own world object for {@code world}, as Simple Voice Chat's {@code fromServerLevel} wants it. */
    Optional<Object> nativeWorld(UUID world);

    /** A one-shot sound at the block centre. {@code sound} is a vanilla sound event id. */
    void playSound(BlockPos pos, String sound, float volume, float pitch);

    /** A note particle over the block, coloured by {@code note} (0..24). */
    void noteParticle(BlockPos pos, int note);

    /** The server's own address from its settings, or blank when it binds every interface. */
    String serverIp();
}
