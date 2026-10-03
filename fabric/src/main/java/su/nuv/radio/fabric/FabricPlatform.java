package su.nuv.radio.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import su.nuv.radio.Radio;
import su.nuv.radio.platform.BlockKind;
import su.nuv.radio.platform.RadioPlatform;
import su.nuv.radio.platform.RadioPlayer;
import su.nuv.radio.station.RadioStation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * {@link RadioPlatform} on a vanilla server: a tick scheduler, worlds keyed by a UUID derived from
 * the dimension id, and players wrapped as {@link FabricPlayer}.
 */
final class FabricPlatform implements RadioPlatform {

    private record Scheduled(long due, long period, Runnable task) {
    }

    static final Logger LOGGER = Slf4jHandler.install(Logger.getLogger("JukeboxRadio"));

    private final MinecraftServer server;
    private final FabricDialogs dialogs = new FabricDialogs();
    private final ConcurrentLinkedQueue<Runnable> next = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<Scheduled> timers = new PriorityQueue<>((a, b) -> Long.compare(a.due(), b.due()));
    private volatile boolean enabled = true;
    private long tick;

    FabricPlatform(MinecraftServer server) {
        this.server = server;
    }

    MinecraftServer server() {
        return this.server;
    }

    FabricDialogs dialogs() {
        return this.dialogs;
    }

    FabricPlayer wrap(ServerPlayer player) {
        return new FabricPlayer(this, player);
    }

    void disable() {
        this.enabled = false;
        this.next.clear();
        this.timers.clear();
    }

    /** Server thread, end of every tick. */
    void tick() {
        this.tick++;
        Runnable task;
        while ((task = this.next.poll()) != null) {
            this.run(task);
        }
        final List<Scheduled> again = new ArrayList<>();
        while (!this.timers.isEmpty() && this.timers.peek().due() <= this.tick) {
            final Scheduled due = this.timers.poll();
            this.run(due.task());
            if (due.period() > 0 && this.enabled) {
                again.add(new Scheduled(this.tick + due.period(), due.period(), due.task()));
            }
        }
        this.timers.addAll(again);
    }

    private void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException error) {
            LOGGER.log(java.util.logging.Level.WARNING, "Radio task failed", error);
        }
    }

    /**
     * Fabric has no events for explosions, fire or pistons: every two seconds the radio checks
     * that loaded stations and their relays still stand.
     */
    void watch(Radio radio) {
        this.repeat(40L, 40L, () -> {
            for (RadioStation station : radio.stations().all()) {
                this.check(radio, station.block(), BlockKind.JUKEBOX);
                for (su.nuv.radio.relay.BlockPos relay : station.relayPositions()) {
                    this.check(radio, relay, BlockKind.NOTE_BLOCK);
                }
            }
        });
    }

    private void check(Radio radio, su.nuv.radio.relay.BlockPos pos, BlockKind expected) {
        final ServerLevel level = this.level(pos.world()).orElse(null);
        final BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
        if (level != null && level.isLoaded(at) && kind(level.getBlockState(at)) != expected) {
            radio.blockGone(pos, expected);
        }
    }

    // ------------------------------------------------------------------ worlds

    static UUID worldId(ServerLevel level) {
        return UUID.nameUUIDFromBytes(("jukeboxradio:" + level.dimension().identifier()).getBytes(StandardCharsets.UTF_8));
    }

    Optional<ServerLevel> level(UUID world) {
        for (ServerLevel level : this.server.getAllLevels()) {
            if (worldId(level).equals(world)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    su.nuv.radio.relay.BlockPos pos(ServerLevel level, BlockPos pos) {
        return new su.nuv.radio.relay.BlockPos(worldId(level), pos.getX(), pos.getY(), pos.getZ());
    }

    static BlockKind kind(BlockState state) {
        if (state.is(Blocks.JUKEBOX)) {
            return BlockKind.JUKEBOX;
        }
        return state.is(Blocks.NOTE_BLOCK) ? BlockKind.NOTE_BLOCK : BlockKind.OTHER;
    }

    // ------------------------------------------------------------------ RadioPlatform

    @Override
    public Logger logger() {
        return LOGGER;
    }

    @Override
    public Path dataFolder() {
        return FabricLoader.getInstance().getConfigDir().resolve("jukeboxradio");
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public boolean isMainThread() {
        return this.server.isSameThread();
    }

    @Override
    public void runSync(Runnable task) {
        if (this.enabled) {
            this.next.add(task);
        }
    }

    @Override
    public void later(long ticks, Runnable task) {
        if (!this.enabled) {
            return;
        }
        if (this.isMainThread()) {
            this.timers.add(new Scheduled(this.tick + Math.max(1, ticks), 0, task));
        } else {
            this.next.add(() -> this.later(ticks, task));
        }
    }

    @Override
    public void repeat(long delay, long period, Runnable task) {
        if (!this.enabled) {
            return;
        }
        if (this.isMainThread()) {
            this.timers.add(new Scheduled(this.tick + Math.max(1, delay), Math.max(1, period), task));
        } else {
            this.next.add(() -> this.repeat(delay, period, task));
        }
    }

    @Override
    public Optional<RadioPlayer> player(UUID id) {
        return Optional.ofNullable(this.server.getPlayerList().getPlayer(id)).map(this::wrap);
    }

    @Override
    public Optional<RadioPlayer> playerExact(String name) {
        return Optional.ofNullable(this.server.getPlayerList().getPlayerByName(name)).map(this::wrap);
    }

    @Override
    public BlockKind blockAt(su.nuv.radio.relay.BlockPos pos) {
        final BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
        return this.level(pos.world())
                .filter(level -> level.isLoaded(at))
                .map(level -> kind(level.getBlockState(at)))
                .orElse(BlockKind.OTHER);
    }

    @Override
    public void silenceJukebox(su.nuv.radio.relay.BlockPos pos) {
        final BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
        this.level(pos.world()).ifPresent(level -> {
            if (level.isLoaded(at) && level.getBlockEntity(at) instanceof JukeboxBlockEntity jukebox
                    && jukebox.getSongPlayer().isPlaying()) {
                jukebox.getSongPlayer().stop(level, level.getBlockState(at));
            }
        });
    }

    @Override
    public Optional<Object> nativeWorld(UUID world) {
        return this.level(world).map(level -> level);
    }

    @Override
    public void playSound(su.nuv.radio.relay.BlockPos pos, String sound, float volume, float pitch) {
        final Identifier id = Identifier.tryParse(sound);
        if (id == null) {
            return;
        }
        final Optional<SoundEvent> event = BuiltInRegistries.SOUND_EVENT.getOptional(id);
        this.level(pos.world()).ifPresent(level -> event.ifPresent(found -> level.playSound(null, pos.centerX(),
                pos.centerY(), pos.centerZ(), found, SoundSource.RECORDS, volume, pitch)));
    }

    @Override
    public void noteParticle(su.nuv.radio.relay.BlockPos pos, int note) {
        this.level(pos.world()).ifPresent(level -> {
            if (level.isLoaded(new BlockPos(pos.x(), pos.y(), pos.z()))) {
                // for NOTE, a count of 0 turns xDist into the note colour
                level.sendParticles(ParticleTypes.NOTE, pos.centerX(), pos.centerY() + 0.7, pos.centerZ(), 0,
                        note / 24.0, 0, 0, 1);
            }
        });
    }

    @Override
    public String serverIp() {
        final String ip = this.server.getLocalIp();
        return ip == null ? "" : ip;
    }
}
