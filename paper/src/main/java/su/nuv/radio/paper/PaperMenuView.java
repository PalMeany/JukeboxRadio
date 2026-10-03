package su.nuv.radio.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import su.nuv.radio.platform.MenuItem;
import su.nuv.radio.platform.MenuView;

/**
 * The radio window as a Bukkit chest. A Bukkit inventory's title is fixed at creation, so a reopen
 * builds a fresh one with the new title and the current items.
 */
final class PaperMenuView implements MenuView, InventoryHolder {

    private final PaperPlatform platform;
    private final Player player;
    private final Listener listener;
    private final MenuItem[] items = new MenuItem[SIZE];
    private Inventory inventory;

    PaperMenuView(PaperPlatform platform, Player player, Listener listener) {
        this.platform = platform;
        this.player = player;
        this.listener = listener;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Проигрыватель"));
    }

    Listener listener() {
        return this.listener;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }

    @Override
    public void setItem(int slot, MenuItem item) {
        this.items[slot] = item;
        this.inventory.setItem(slot, PaperItems.stack(item));
    }

    @Override
    public MenuItem getItem(int slot) {
        return this.items[slot];
    }

    @Override
    public void setContents(MenuItem[] contents) {
        for (int slot = 0; slot < SIZE; slot++) {
            this.items[slot] = slot < contents.length ? contents[slot] : null;
        }
        this.inventory.setContents(PaperItems.stacks(this.items));
    }

    @Override
    public void open(Component title) {
        final Inventory fresh = Bukkit.createInventory(this, SIZE, title);
        fresh.setContents(this.inventory.getContents());
        this.inventory = fresh;
        this.player.openInventory(fresh);
    }

    @Override
    public boolean updateTitle(Component title) {
        return this.platform.titles().update(this.player, title);
    }

    @Override
    public boolean isOpen() {
        return this.player.isOnline() && this.player.getOpenInventory().getTopInventory().getHolder(false) == this;
    }
}
