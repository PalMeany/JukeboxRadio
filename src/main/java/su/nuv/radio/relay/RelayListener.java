package su.nuv.radio.relay;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import su.nuv.radio.config.Messages;

import java.util.ArrayList;
import java.util.List;

/** Shift + use on a note block opens its relay settings; breaking or pushing it updates the link. */
public final class RelayListener implements Listener {

    private final RelayManager relays;
    private final boolean requireEmptyHand;

    public RelayListener(RelayManager relays, boolean requireEmptyHand) {
        this.relays = relays;
        this.requireEmptyHand = requireEmptyHand;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Block block = event.getClickedBlock();
        final Player player = event.getPlayer();
        if (block == null || block.getType() != Material.NOTE_BLOCK || !player.isSneaking()) {
            return;
        }
        // respect protection plugins that deny using the block
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        if (this.requireEmptyHand && !player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("jukeboxradio.use")) {
            Messages.error(player, "Нет прав пользоваться радио.");
            return;
        }
        this.relays.openDialog(player, block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        this.gone(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        this.gone(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::gone);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::gone);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        this.pushed(event.getBlock(), event.getBlocks(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        this.pushed(event.getBlock(), event.getBlocks(), false);
    }

    /**
     * Moves links with pushed note blocks. The step comes from the piston's facing (blocks move
     * along it when extending, against it when retracting), not from the event's direction, whose
     * meaning differs between the two events.
     */
    private void pushed(Block piston, List<Block> blocks, boolean extending) {
        if (!(piston.getBlockData() instanceof Directional directional)) {
            return;
        }
        final List<BlockPos> linked = new ArrayList<>();
        for (Block block : blocks) {
            final BlockPos pos = BlockPos.of(block);
            if (block.getType() == Material.NOTE_BLOCK && this.relays.isRelay(pos)) {
                linked.add(pos);
            }
        }
        if (linked.isEmpty()) {
            return;
        }
        final BlockFace step = extending ? directional.getFacing() : directional.getFacing().getOppositeFace();
        this.relays.relaysMoved(linked, step.getModX(), step.getModY(), step.getModZ());
    }

    private void gone(Block block) {
        if (block.getType() == Material.NOTE_BLOCK) {
            this.relays.relayGone(BlockPos.of(block));
        }
    }
}
