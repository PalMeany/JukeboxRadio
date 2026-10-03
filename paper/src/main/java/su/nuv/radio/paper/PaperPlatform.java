package su.nuv.radio.paper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import su.nuv.radio.platform.BlockKind;
import su.nuv.radio.platform.CommandSource;
import su.nuv.radio.platform.RadioPlatform;
import su.nuv.radio.platform.RadioPlayer;
import su.nuv.radio.relay.BlockPos;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/** {@link RadioPlatform} on Bukkit's scheduler, worlds and players. */
final class PaperPlatform implements RadioPlatform {

    private final RadioPlugin plugin;
    private final TitleUpdater titles;

    PaperPlatform(RadioPlugin plugin) {
        this.plugin = plugin;
        this.titles = new TitleUpdater(plugin.getLogger());
    }

    RadioPlugin plugin() {
        return this.plugin;
    }

    TitleUpdater titles() {
        return this.titles;
    }

    PaperPlayer wrap(Player player) {
        return new PaperPlayer(this, player);
    }

    CommandSource source(CommandSender sender) {
        return sender instanceof Player player ? this.wrap(player) : new PaperConsole(sender);
    }

    @Override
    public Logger logger() {
        return this.plugin.getLogger();
    }

    @Override
    public Path dataFolder() {
        return this.plugin.getDataFolder().toPath();
    }

    @Override
    public boolean isEnabled() {
        return this.plugin.isEnabled();
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void runSync(Runnable task) {
        if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(this.plugin, task);
        }
    }

    @Override
    public void later(long ticks, Runnable task) {
        if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(this.plugin, task, ticks);
        }
    }

    @Override
    public void repeat(long delay, long period, Runnable task) {
        Bukkit.getScheduler().runTaskTimer(this.plugin, task, delay, period);
    }

    @Override
    public Optional<RadioPlayer> player(UUID id) {
        return Optional.ofNullable(Bukkit.getPlayer(id)).map(this::wrap);
    }

    @Override
    public Optional<RadioPlayer> playerExact(String name) {
        return Optional.ofNullable(Bukkit.getPlayerExact(name)).map(this::wrap);
    }

    Optional<Block> block(BlockPos pos) {
        final World world = Bukkit.getWorld(pos.world());
        return world == null ? Optional.empty() : Optional.of(world.getBlockAt(pos.x(), pos.y(), pos.z()));
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    static BlockKind kind(Material type) {
        if (type == Material.JUKEBOX) {
            return BlockKind.JUKEBOX;
        }
        return type == Material.NOTE_BLOCK ? BlockKind.NOTE_BLOCK : BlockKind.OTHER;
    }

    @Override
    public BlockKind blockAt(BlockPos pos) {
        final World world = Bukkit.getWorld(pos.world());
        if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            return BlockKind.OTHER;
        }
        return kind(world.getBlockAt(pos.x(), pos.y(), pos.z()).getType());
    }

    @Override
    public void silenceJukebox(BlockPos pos) {
        this.block(pos).ifPresent(block -> {
            if (block.getState() instanceof Jukebox jukebox && jukebox.isPlaying()) {
                jukebox.stopPlaying();
            }
        });
    }

    @Override
    public Optional<Object> nativeWorld(UUID world) {
        return Optional.ofNullable(Bukkit.getWorld(world));
    }

    @Override
    public void playSound(BlockPos pos, String sound, float volume, float pitch) {
        final World world = Bukkit.getWorld(pos.world());
        final NamespacedKey key = NamespacedKey.fromString(sound);
        final Sound resolved = key == null ? null : Registry.SOUNDS.get(key);
        if (world != null && resolved != null) {
            world.playSound(new Location(world, pos.centerX(), pos.centerY(), pos.centerZ()), resolved, volume, pitch);
        }
    }

    @Override
    public void noteParticle(BlockPos pos, int note) {
        final World world = Bukkit.getWorld(pos.world());
        if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            return;
        }
        // for NOTE, a count of 0 turns offsetX into the note colour
        world.spawnParticle(Particle.NOTE, new Location(world, pos.centerX(), pos.centerY() + 0.7, pos.centerZ()), 0,
                note / 24.0, 0, 0, 1);
    }

    @Override
    public String serverIp() {
        return Bukkit.getIp();
    }
}
