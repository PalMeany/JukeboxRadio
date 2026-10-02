package su.nuv.radio.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import su.nuv.radio.catalog.CatalogRegistry;
import su.nuv.radio.config.Messages;
import su.nuv.radio.menu.art.CoverArt;
import su.nuv.radio.menu.art.CoverService;
import su.nuv.radio.menu.pack.PackDelivery;
import su.nuv.radio.station.RadioStation;
import su.nuv.radio.station.StationKey;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Opens and closes radio menus, routes clicks, ticks animations and runs the search dialog. The
 * rest of the plugin talks to the menu only through this class.
 */
public final class MenuManager implements Listener {

    private final Plugin plugin;
    private final CatalogRegistry catalogs;
    private final CoverService covers;
    private final PackDelivery pack;
    private final TitleUpdater titles;
    private final boolean requirePack;
    private final Map<UUID, RadioMenu> menus = new HashMap<>();
    private final Map<UUID, StationKey> lastStation = new HashMap<>();

    public MenuManager(Plugin plugin, CatalogRegistry catalogs, CoverService covers, PackDelivery pack,
                       boolean requirePack) {
        this.plugin = plugin;
        this.catalogs = catalogs;
        this.covers = covers;
        this.pack = pack;
        this.requirePack = requirePack;
        this.titles = new TitleUpdater(plugin.getLogger());
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
    }

    CatalogRegistry catalogs() {
        return this.catalogs;
    }

    TitleUpdater titles() {
        return this.titles;
    }

    /** The prepared cover, or {@code null} while it loads; {@code whenReady} runs once it lands. */
    CoverArt cover(String url, Runnable whenReady) {
        if (url == null) {
            return null;
        }
        final Optional<CoverArt> ready = this.covers.now(url);
        if (ready.isPresent()) {
            return ready.get();
        }
        this.covers.get(url).thenAccept(art -> {
            if (art.isPresent()) {
                this.sync(whenReady);
            }
        });
        return null;
    }

    void sync(Runnable task) {
        if (!this.plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this.plugin, task);
        }
    }

    void later(int ticks, Runnable task) {
        if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(this.plugin, task, ticks);
        }
    }

    // ------------------------------------------------------------------ open / close

    public enum OpenResult { OPENED, PACK_PENDING, PACK_MISSING }

    public OpenResult open(Player player, RadioStation station) {
        final PackDelivery.State state = this.pack.state(player);
        if (this.requirePack && state != PackDelivery.State.LOADED) {
            if (state == PackDelivery.State.DECLINED || state == PackDelivery.State.FAILED) {
                this.pack.send(player);
                return OpenResult.PACK_MISSING;
            }
            if (state == PackDelivery.State.UNKNOWN) {
                this.pack.send(player);
            }
            return OpenResult.PACK_PENDING;
        }
        final RadioMenu previous = this.menus.remove(player.getUniqueId());
        if (previous != null) {
            previous.release();
        }
        final RadioMenu menu = new RadioMenu(this, player, station);
        this.menus.put(player.getUniqueId(), menu);
        this.lastStation.put(player.getUniqueId(), station.key());
        menu.open();
        return OpenResult.OPENED;
    }

    /** Opens the menu with a query or link already submitted, as {@code /radio search} does. */
    public void openWithQuery(Player player, RadioStation station, String query) {
        if (this.open(player, station) == OpenResult.OPENED) {
            final RadioMenu menu = this.menus.get(player.getUniqueId());
            if (menu != null) {
                menu.submit(query);
            }
        }
    }

    public Optional<StationKey> lastStation(Player player) {
        return Optional.ofNullable(this.lastStation.get(player.getUniqueId()));
    }

    public void closeStation(StationKey key) {
        for (RadioMenu menu : new ArrayList<>(this.menus.values())) {
            if (menu.station().key().equals(key)) {
                this.menus.remove(menu.player().getUniqueId());
                menu.release();
                if (!menu.isSuspended()) {
                    menu.player().closeInventory();
                }
            }
        }
    }

    public void closeAll() {
        for (RadioMenu menu : new ArrayList<>(this.menus.values())) {
            menu.release();
            if (!menu.isSuspended()) {
                menu.player().closeInventory();
            }
        }
        this.menus.clear();
    }

    /** Diagnostics: runs a slot click through the menu as if the player made it. */
    public void simulateClick(Player player, int slot, boolean right) {
        final RadioMenu menu = this.menus.get(player.getUniqueId());
        if (menu != null && !menu.isSuspended()) {
            menu.onClick(slot, right ? org.bukkit.event.inventory.ClickType.RIGHT : org.bukkit.event.inventory.ClickType.LEFT);
        }
    }

    // ------------------------------------------------------------------ search dialog

    /** Steps the menu aside and asks for a query or link in a native dialog. */
    void askQuery(RadioMenu menu, String previous) {
        final Player player = menu.player();
        menu.suspend();
        final ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10))
                .build();
        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Поиск музыки"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Название трека, исполнитель или ссылка Spotify / Apple Music / YouTube: трек, альбом, плейлист."),
                                300)))
                        .inputs(List.of(DialogInput.text("query", Component.text("Запрос или ссылка"))
                                .width(300)
                                .maxLength(512)
                                .initial(previous == null ? "" : previous)
                                .build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("Найти", NamedTextColor.GREEN))
                                .width(100)
                                .action(DialogAction.customClick((response, audience) -> {
                                    final String text = response.getText("query");
                                    this.sync(() -> this.afterDialog(player, menu, text));
                                }, once))
                                .build(),
                        ActionButton.builder(Component.text("Отмена"))
                                .width(100)
                                .action(DialogAction.customClick((response, audience) ->
                                        this.sync(() -> this.afterDialog(player, menu, null)), once))
                                .build())));
        player.showDialog(dialog);
        // Esc closes the dialog without a callback; forget the menu if it never comes back
        this.later(20 * 60 * 10, () -> {
            if (menu.isSuspended() && this.menus.get(player.getUniqueId()) == menu) {
                this.menus.remove(player.getUniqueId());
                menu.release();
            }
        });
    }

    private void afterDialog(Player player, RadioMenu menu, String text) {
        if (!player.isOnline() || menu.isClosed() || this.menus.get(player.getUniqueId()) != menu) {
            return;
        }
        if (text != null && !text.isBlank()) {
            menu.submit(text);
        }
        menu.resume();
    }

    // ------------------------------------------------------------------ events

    private void tick() {
        for (RadioMenu menu : new ArrayList<>(this.menus.values())) {
            try {
                menu.tick();
            } catch (RuntimeException error) {
                this.plugin.getLogger().log(Level.WARNING, "Radio menu tick failed", error);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof RadioMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        try {
            menu.onClick(event.getSlot(), event.getClick());
        } catch (RuntimeException error) {
            this.plugin.getLogger().log(Level.WARNING, "Radio menu click failed", error);
            Messages.error(event.getWhoClicked(), "Что-то пошло не так, подробности в консоли.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof RadioMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof RadioMenu menu)) {
            return;
        }
        if (menu.isSuspended()) {
            return;
        }
        // a reopen (new title) closes the old view first; only drop the menu when nothing replaced it
        this.later(1, () -> {
            final Player player = menu.player();
            if (this.menus.get(player.getUniqueId()) != menu || menu.isSuspended()) {
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof RadioMenu open)
                    || open != menu) {
                this.menus.remove(player.getUniqueId());
                menu.release();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        final RadioMenu menu = this.menus.remove(event.getPlayer().getUniqueId());
        if (menu != null) {
            menu.release();
        }
        this.lastStation.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        final RadioMenu menu = this.menus.remove(event.getPlayer().getUniqueId());
        if (menu != null) {
            menu.release();
        }
    }
}
