package su.nuv.radio.relay;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import su.nuv.radio.station.StationKey;

import java.util.Optional;
import java.util.UUID;

/** A block position in a world: a jukebox or a relay note block. */
public record BlockPos(UUID world, int x, int y, int z) {

    public static BlockPos of(Block block) {
        return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos of(StationKey key) {
        return new BlockPos(key.world(), key.x(), key.y(), key.z());
    }

    public StationKey stationKey() {
        return new StationKey(this.world, this.x, this.y, this.z);
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(this.world, this.x + dx, this.y + dy, this.z + dz);
    }

    /** Distance between block centres, or infinity across worlds. */
    public double distance(BlockPos other) {
        if (!this.world.equals(other.world)) {
            return Double.POSITIVE_INFINITY;
        }
        final double dx = this.x - other.x;
        final double dy = this.y - other.y;
        final double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Optional<Block> block() {
        final World world = Bukkit.getWorld(this.world);
        return world == null ? Optional.empty() : Optional.of(world.getBlockAt(this.x, this.y, this.z));
    }

    /** Centre of the block, where the relay's sound comes from. */
    public Optional<Location> center() {
        final World world = Bukkit.getWorld(this.world);
        return world == null ? Optional.empty() : Optional.of(new Location(world, this.x + 0.5, this.y + 0.5, this.z + 0.5));
    }

    public String coordinates() {
        return this.x + " " + this.y + " " + this.z;
    }

    /** "world-uuid,x,y,z", the storage form (no dots: YAML paths split on them). */
    public String serialize() {
        return this.world + "," + this.x + "," + this.y + "," + this.z;
    }

    public static Optional<BlockPos> parse(String text) {
        final String[] parts = text == null ? new String[0] : text.split(",");
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BlockPos(UUID.fromString(parts[0].strip()), Integer.parseInt(parts[1].strip()),
                    Integer.parseInt(parts[2].strip()), Integer.parseInt(parts[3].strip())));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }
}
