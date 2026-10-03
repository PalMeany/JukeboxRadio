package su.nuv.radio.relay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which relays of one jukebox are in reach, like a Wi-Fi mesh: a relay is active when it is within
 * {@code range} of the jukebox or of another active relay. Pure geometry, no Bukkit.
 */
public final class RelayNetwork {

    private RelayNetwork() {
    }

    /** The relays connected to {@code source} through a chain of hops no longer than {@code range}. */
    public static Set<BlockPos> active(BlockPos source, Collection<BlockPos> relays, double range) {
        final List<BlockPos> waiting = new ArrayList<>(relays);
        final Set<BlockPos> reached = new LinkedHashSet<>();
        final Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(source);
        while (!frontier.isEmpty()) {
            final BlockPos from = frontier.poll();
            for (int i = waiting.size() - 1; i >= 0; i--) {
                final BlockPos relay = waiting.get(i);
                if (from.distance(relay) <= range) {
                    waiting.remove(i);
                    reached.add(relay);
                    frontier.add(relay);
                }
            }
        }
        return reached;
    }

    /** Distance from {@code candidate} to the nearest node of the network: the jukebox or an active relay. */
    public static double gap(BlockPos candidate, BlockPos source, Collection<BlockPos> activeRelays) {
        double nearest = candidate.distance(source);
        for (BlockPos relay : activeRelays) {
            nearest = Math.min(nearest, candidate.distance(relay));
        }
        return nearest;
    }
}
