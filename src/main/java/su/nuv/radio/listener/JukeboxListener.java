package su.nuv.radio.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import su.nuv.radio.RadioPlugin;
import su.nuv.radio.config.Messages;
import su.nuv.radio.station.StationKey;
import su.nuv.radio.menu.MenuManager;
import su.nuv.radio.relay.BlockPos;

import java.util.List;

/** Shift + use on a jukebox opens the radio; breaking the jukebox stops it. */
public final class JukeboxListener implements Listener {

    private final RadioPlugin plugin;
    private final boolean requireEmptyHand;

    public JukeboxListener(RadioPlugin plugin, boolean requireEmptyHand) {
        this.plugin = plugin;
        this.requireEmptyHand = requireEmptyHand;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Block block = event.getClickedBlock();
        final Player player = event.getPlayer();
        if (block == null || block.getType() != Material.JUKEBOX || !player.isSneaking()) {
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
        final MenuManager.OpenResult result = this.plugin.menus().open(player, this.plugin.stations().getOrCreate(block));
        if (result == MenuManager.OpenResult.PACK_PENDING) {
            Messages.info(player, "Загружается ресурс-пак радио — откройте проигрыватель ещё раз через пару секунд.");
        } else if (result == MenuManager.OpenResult.PACK_MISSING) {
            Messages.error(player, "Для меню нужен ресурс-пак радио. Разрешите ресурс-паки сервера (Мультиплеер → "
                    + "Настроить → Наборы ресурсов: Включены) или пользуйтесь /radio play <ссылка>.");
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
        this.goneAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        this.goneAll(event.blockList());
    }

    private void goneAll(List<Block> blocks) {
        for (Block block : blocks) {
            this.gone(block);
        }
    }

    private void gone(Block block) {
        if (block.getType() != Material.JUKEBOX) {
            return;
        }
        final StationKey key = StationKey.of(block);
        this.plugin.menus().closeStation(key);
        this.plugin.stations().remove(key);
        this.plugin.relays().jukeboxGone(BlockPos.of(key));
    }
}
