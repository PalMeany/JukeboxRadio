package su.nuv.radio.station;

import java.util.UUID;

/** A jukebox's identity: world and block coordinates. */
public record StationKey(UUID world, int x, int y, int z) {

    public String coordinates() {
        return this.x + " " + this.y + " " + this.z;
    }
}
