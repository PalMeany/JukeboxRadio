package su.nuv.radio.paper;

import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.resource.ResourcePackStatus;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EquipmentSlot;
import su.nuv.radio.Radio;
import su.nuv.radio.platform.BlockKind;
import su.nuv.radio.platform.ClickKind;
import su.nuv.radio.platform.PackOffer;
import su.nuv.radio.platform.PackStatus;
import su.nuv.radio.relay.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/** Bukkit events the radio reacts to, forwarded to {@link Radio}. */
final class PaperListener implements Listener {

    /** How long a joining player may sit on the loading screen before being let in anyway. */
    private static final long CONFIGURE_WAIT_SECONDS = 180;

    private final Radio radio;
    private final PaperPlatform platform;

    PaperListener(Radio radio, PaperPlatform platform) {
        this.radio = radio;
        this.platform = platform;
    }

    // ------------------------------------------------------------------ jukeboxes and note blocks

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Block block = event.getClickedBlock();
        final Player player = event.getPlayer();
        if (block == null || !player.isSneaking()) {
            return;
        }
        final BlockKind kind = PaperPlatform.kind(block.getType());
        // respect protection plugins that deny using the block
        if (kind == BlockKind.OTHER || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        final boolean emptyHand = player.getInventory().getItemInMainHand().getType().isAir();
        if (this.radio.use(this.platform.wrap(player), PaperPlatform.pos(block), kind, true, emptyHand)) {
            event.setCancelled(true);
        }
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
        if (!this.radio.config().relay().enabled() || !(piston.getBlockData() instanceof Directional directional)) {
            return;
        }
        final List<BlockPos> linked = new ArrayList<>();
        for (Block block : blocks) {
            final BlockPos pos = PaperPlatform.pos(block);
            if (block.getType() == Material.NOTE_BLOCK && this.radio.relays().isRelay(pos)) {
                linked.add(pos);
            }
        }
        if (linked.isEmpty()) {
            return;
        }
        final BlockFace step = extending ? directional.getFacing() : directional.getFacing().getOppositeFace();
        this.radio.relays().relaysMoved(linked, step.getModX(), step.getModY(), step.getModZ());
    }

    private void gone(Block block) {
        final BlockKind kind = PaperPlatform.kind(block.getType());
        if (kind != BlockKind.OTHER) {
            this.radio.blockGone(PaperPlatform.pos(block), kind);
        }
    }

    // ------------------------------------------------------------------ menu

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PaperMenuView view)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        view.listener().clicked(event.getSlot(), kind(event.getClick()));
    }

    private static ClickKind kind(ClickType click) {
        return switch (click) {
            case LEFT -> ClickKind.LEFT;
            case RIGHT -> ClickKind.RIGHT;
            case SHIFT_LEFT -> ClickKind.SHIFT_LEFT;
            case SHIFT_RIGHT -> ClickKind.SHIFT_RIGHT;
            case MIDDLE -> ClickKind.MIDDLE;
            default -> click.isRightClick() ? ClickKind.RIGHT : ClickKind.OTHER;
        };
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof PaperMenuView) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof PaperMenuView view) {
            view.listener().closed();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.radio.quit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        this.radio.died(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ resource pack

    /**
     * Sends the pack while the player is still in the configuration phase and holds the connection
     * there until the client has loaded it. A server pack reloads every client resource; done after
     * join, the player stood in the world frozen (and killable) behind the loading screen.
     */
    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        final Optional<PackOffer> offer = this.radio.pack().offer();
        final UUID id = event.getConnection().getProfile().getId();
        if (offer.isEmpty() || id == null) {
            return;
        }
        final CompletableFuture<ResourcePackStatus> done = new CompletableFuture<>();
        this.radio.pack().sending(id);
        event.getConnection().getAudience().sendResourcePacks(PaperPlayer.request(offer.get(), (packId, status, audience) -> {
            if (!status.intermediate()) {
                done.complete(status);
            }
        }));
        final String name = event.getConnection().getProfile().getName();
        try {
            this.radio.packStatus(id, name, offer.get().id(), status(done.get(CONFIGURE_WAIT_SECONDS, TimeUnit.SECONDS)));
        } catch (TimeoutException slow) {
            // let them in; the pack keeps loading and the status event reports it later
            this.radio.packStatus(id, name, offer.get().id(), PackStatus.LOADING);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException impossible) {
            this.platform.logger().log(Level.FINE, "Radio pack status lost", impossible);
        }
    }

    /** Fallback when the configuration phase did not deliver it, e.g. the event was skipped. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.radio.joined(this.platform.wrap(event.getPlayer()));
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        this.radio.packStatus(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getID(),
                status(event.getStatus()));
    }

    private static PackStatus status(ResourcePackStatus status) {
        return switch (status) {
            case SUCCESSFULLY_LOADED -> PackStatus.LOADED;
            case DECLINED -> PackStatus.DECLINED;
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> PackStatus.FAILED;
            default -> PackStatus.LOADING;
        };
    }

    private static PackStatus status(PlayerResourcePackStatusEvent.Status status) {
        return switch (status) {
            case SUCCESSFULLY_LOADED -> PackStatus.LOADED;
            case DECLINED -> PackStatus.DECLINED;
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> PackStatus.FAILED;
            default -> PackStatus.LOADING;
        };
    }
}
