package su.nuv.radio.fabric;

import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import su.nuv.radio.platform.MenuItem;
import su.nuv.radio.platform.MenuView;

/** The radio window as a vanilla six-row chest whose title is changed in place with an open-screen packet. */
final class FabricMenuView implements MenuView {

    private final ServerPlayer player;
    private final Listener listener;
    private final MenuItem[] items = new MenuItem[SIZE];
    private final SimpleContainer container = new SimpleContainer(SIZE);
    private RadioChestMenu menu;
    private boolean reopening;

    FabricMenuView(ServerPlayer player, Listener listener) {
        this.player = player;
        this.listener = listener;
    }

    Listener listener() {
        return this.listener;
    }

    void removed(RadioChestMenu closed) {
        if (closed == this.menu && !this.reopening) {
            this.listener.closed();
        }
    }

    @Override
    public void setItem(int slot, MenuItem item) {
        this.items[slot] = item;
        this.container.setItem(slot, FabricItems.stack(item, this.player.registryAccess()));
    }

    @Override
    public MenuItem getItem(int slot) {
        return this.items[slot];
    }

    @Override
    public void setContents(MenuItem[] contents) {
        for (int slot = 0; slot < SIZE; slot++) {
            this.setItem(slot, slot < contents.length ? contents[slot] : null);
        }
    }

    @Override
    public void open(Component title) {
        this.reopening = this.isOpen();
        try {
            this.player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> {
                this.menu = new RadioChestMenu(id, inventory, this.container, this);
                return this.menu;
            }, Texts.vanilla(title, this.player.registryAccess())));
        } finally {
            this.reopening = false;
        }
    }

    @Override
    public boolean updateTitle(Component title) {
        if (!this.isOpen()) {
            return false;
        }
        this.player.connection.send(new ClientboundOpenScreenPacket(this.menu.containerId, this.menu.getType(),
                Texts.vanilla(title, this.player.registryAccess())));
        this.menu.sendAllDataToRemote();
        return true;
    }

    @Override
    public boolean isOpen() {
        return this.menu != null && this.player.containerMenu == this.menu && !this.player.hasDisconnected();
    }
}
