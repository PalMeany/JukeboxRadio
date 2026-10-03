package su.nuv.radio.fabric;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import su.nuv.radio.platform.MenuItem;

import java.util.LinkedHashSet;
import java.util.List;

/** Menu items as paper with the radio pack's item models. */
final class FabricItems {

    private FabricItems() {
    }

    static ItemStack stack(MenuItem item, HolderLookup.Provider registries) {
        if (item == null) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(item.model().namespace(), item.model().value()));
        if (!item.colors().isEmpty() || !item.floats().isEmpty()) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(item.floats(), List.of(), List.of(), item.colors()));
        }
        if (item.tooltipHidden()) {
            stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
            return stack;
        }
        stack.set(DataComponents.ITEM_NAME, Texts.vanilla(item.name(), registries));
        if (!item.lore().isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(item.lore().stream()
                    .map(line -> Texts.vanilla(line, registries))
                    .toList()));
        }
        return stack;
    }
}
