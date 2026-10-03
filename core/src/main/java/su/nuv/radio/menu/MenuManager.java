package su.nuv.radio.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import su.nuv.radio.catalog.CatalogRegistry;
import su.nuv.radio.config.Messages;
import su.nuv.radio.menu.art.CoverArt;
import su.nuv.radio.menu.art.CoverService;
import su.nuv.radio.menu.pack.PackDelivery;
import su.nuv.radio.platform.ClickKind;
import su.nuv.radio.platform.DialogSpec;
import su.nuv.radio.platform.RadioPlatform;
import su.nuv.radio.platform.RadioPlayer;
import su.nuv.radio.station.RadioStation;
import su.nuv.radio.station.StationKey;

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
public final class MenuManager {

    private final RadioPlatform platform;
    private final CatalogRegistry catalogs;
    private final CoverService covers;
    private final PackDelivery pack;
    private final boolean requirePack;
    private final Map<UUID, RadioMenu> menus = new HashMap<>();
    private final Map<UUID, StationKey> lastStation = new HashMap<>();

    public MenuManager(RadioPlatform platform, CatalogRegistry catalogs, CoverService covers, PackDelivery pack,
                       boolean requirePack) {
        this.platform = platform;
        this.catalogs = catalogs;
        this.covers = covers;
        this.pack = pack;
        this.requirePack = requirePack;
    }

    public void enable() {
        this.platform.repeat(1L, 1L, this::tick);
    }

    CatalogRegistry catalogs() {
        return this.catalogs;
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
        this.platform.sync(task);
    }

    void later(int ticks, Runnable task) {
        if (this.platform.isEnabled()) {
            this.platform.later(ticks, task);
        }
    }

    // ------------------------------------------------------------------ open / close

    public enum OpenResult { OPENED, PACK_PENDING, PACK_MISSING }

    public OpenResult open(RadioPlayer player, RadioStation station) {
        final PackDelivery.State state = this.pack.state(player.uuid());
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
        final RadioMenu previous = this.menus.remove(player.uuid());
        if (previous != null) {
            previous.release();
        }
        final RadioMenu menu = new RadioMenu(this, player, station);
        this.menus.put(player.uuid(), menu);
        this.lastStation.put(player.uuid(), station.key());
        menu.open();
        return OpenResult.OPENED;
    }

    /** Opens the menu with a query or link already submitted, as {@code /radio search} does. */
    public void openWithQuery(RadioPlayer player, RadioStation station, String query) {
        if (this.open(player, station) == OpenResult.OPENED) {
            final RadioMenu menu = this.menus.get(player.uuid());
            if (menu != null) {
                menu.submit(query);
            }
        }
    }

    public Optional<StationKey> lastStation(RadioPlayer player) {
        return Optional.ofNullable(this.lastStation.get(player.uuid()));
    }

    public void closeStation(StationKey key) {
        for (RadioMenu menu : new ArrayList<>(this.menus.values())) {
            if (menu.station().key().equals(key)) {
                this.menus.remove(menu.player().uuid());
                menu.release();
                if (!menu.isSuspended()) {
                    menu.player().closeScreen();
                }
            }
        }
    }

    public void closeAll() {
        for (RadioMenu menu : new ArrayList<>(this.menus.values())) {
            menu.release();
            if (!menu.isSuspended()) {
                menu.player().closeScreen();
            }
        }
        this.menus.clear();
    }

    /** Diagnostics: runs a slot click through the menu as if the player made it. */
    public void simulateClick(RadioPlayer player, int slot, boolean right) {
        final RadioMenu menu = this.menus.get(player.uuid());
        if (menu != null && !menu.isSuspended()) {
            menu.onClick(slot, right ? ClickKind.RIGHT : ClickKind.LEFT);
        }
    }

    // ------------------------------------------------------------------ search dialog

    /** Steps the menu aside and asks for a query or link in a native dialog. */
    void askQuery(RadioMenu menu, String previous) {
        final RadioPlayer player = menu.player();
        menu.suspend();
        player.showDialog(new DialogSpec(Component.text("Поиск музыки"),
                Component.text("Название трека, исполнитель или ссылка Spotify / Apple Music / YouTube: трек, альбом, плейлист."),
                300,
                List.of(new DialogSpec.TextInput("query", Component.text("Запрос или ссылка"), 300, 512,
                        previous == null ? "" : previous)),
                List.of(
                        new DialogSpec.Button(Component.text("Найти", NamedTextColor.GREEN), 100,
                                response -> this.afterDialog(player, menu, response.text("query"))),
                        new DialogSpec.Button(Component.text("Отмена"), 100,
                                response -> this.afterDialog(player, menu, null))),
                0));
        // Esc closes the dialog without a callback; forget the menu if it never comes back
        this.later(20 * 60 * 10, () -> {
            if (menu.isSuspended() && this.menus.get(player.uuid()) == menu) {
                this.menus.remove(player.uuid());
                menu.release();
            }
        });
    }

    private void afterDialog(RadioPlayer player, RadioMenu menu, String text) {
        if (!player.isOnline() || menu.isClosed() || this.menus.get(player.uuid()) != menu) {
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
                this.platform.logger().log(Level.WARNING, "Radio menu tick failed", error);
            }
        }
    }

    void clicked(RadioMenu menu, int slot, ClickKind click) {
        if (this.menus.get(menu.player().uuid()) != menu || menu.isSuspended()) {
            return;
        }
        try {
            menu.onClick(slot, click);
        } catch (RuntimeException error) {
            this.platform.logger().log(Level.WARNING, "Radio menu click failed", error);
            Messages.error(menu.player(), "Что-то пошло не так, подробности в консоли.");
        }
    }

    void closed(RadioMenu menu) {
        if (menu.isSuspended()) {
            return;
        }
        // a reopen (new title) closes the old view first; only drop the menu when nothing replaced it
        this.later(1, () -> {
            final UUID id = menu.player().uuid();
            if (this.menus.get(id) != menu || menu.isSuspended() || menu.isShown()) {
                return;
            }
            this.menus.remove(id);
            menu.release();
        });
    }

    /** The player left the server. */
    public void quit(UUID player) {
        final RadioMenu menu = this.menus.remove(player);
        if (menu != null) {
            menu.release();
        }
        this.lastStation.remove(player);
    }

    /** The player died: their screen is gone. */
    public void died(UUID player) {
        final RadioMenu menu = this.menus.remove(player);
        if (menu != null) {
            menu.release();
        }
    }
}
