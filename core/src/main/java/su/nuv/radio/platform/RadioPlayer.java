package su.nuv.radio.platform;

import su.nuv.radio.relay.BlockPos;

import java.util.Optional;
import java.util.UUID;

/** An online player as the radio sees them. */
public interface RadioPlayer extends CommandSource {

    UUID uuid();

    boolean isOnline();

    @Override
    default Optional<RadioPlayer> player() {
        return Optional.of(this);
    }

    UUID world();

    double x();

    double y();

    double z();

    /** The jukebox the player looks at within {@code reach} blocks. */
    Optional<BlockPos> targetJukebox(int reach);

    /** A chest-sized radio window bound to this player; {@code listener} gets its clicks and closing. */
    MenuView createMenu(MenuView.Listener listener);

    /** Closes whatever container screen the player has open. */
    void closeScreen();

    void showDialog(DialogSpec dialog);

    /** Pushes a resource pack on top of the player's current ones. */
    void sendPack(PackOffer offer);

    /** Distance to a point in the same world, or infinity in another world. */
    default double distanceTo(UUID otherWorld, double px, double py, double pz) {
        if (!this.world().equals(otherWorld)) {
            return Double.POSITIVE_INFINITY;
        }
        final double dx = this.x() - px;
        final double dy = this.y() - py;
        final double dz = this.z() - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
