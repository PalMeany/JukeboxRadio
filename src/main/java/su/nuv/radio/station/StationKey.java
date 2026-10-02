package su.nuv.radio.station;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.UUID;

/** A jukebox's identity: world and block coordinates. */
public record StationKey(UUID world, int x, int y, int z) {

    public static StationKey of(Block block) {
        return new StationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static StationKey of(Location location) {
        return new StationKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(),
                location.getBlockZ());
    }

    public String coordinates() {
        return this.x + " " + this.y + " " + this.z;
    }
}
