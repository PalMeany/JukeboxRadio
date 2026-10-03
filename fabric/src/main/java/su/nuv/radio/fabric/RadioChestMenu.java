package su.nuv.radio.fabric;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import su.nuv.radio.platform.ClickKind;
import su.nuv.radio.platform.MenuView;

/** A six-row chest that never moves items: every click goes to the radio and the client is resynced. */
final class RadioChestMenu extends ChestMenu {

    private final FabricMenuView view;

    RadioChestMenu(int containerId, Inventory inventory, SimpleContainer container, FabricMenuView view) {
        super(MenuType.GENERIC_9x6, containerId, inventory, container, 6);
        this.view = view;
    }

    FabricMenuView view() {
        return this.view;
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < MenuView.SIZE) {
            this.view.listener().clicked(slotIndex, kind(input, buttonNum));
        }
        this.setCarried(ItemStack.EMPTY);
        this.sendAllDataToRemote();
    }

    private static ClickKind kind(ContainerInput input, int button) {
        return switch (input) {
            case PICKUP -> button == 1 ? ClickKind.RIGHT : ClickKind.LEFT;
            case QUICK_MOVE -> button == 1 ? ClickKind.SHIFT_RIGHT : ClickKind.SHIFT_LEFT;
            case CLONE -> ClickKind.MIDDLE;
            default -> ClickKind.OTHER;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.view.removed(this);
    }
}
