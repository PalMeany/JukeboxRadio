package su.nuv.radio.paper;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import su.nuv.radio.platform.MenuItem;

/** Menu items as paper with the radio pack's item models. */
final class PaperItems {

    private PaperItems() {
    }

    static ItemStack stack(MenuItem item) {
        if (item == null) {
            return null;
        }
        final ItemStack stack = ItemStack.of(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, item.model());
        if (!item.colors().isEmpty() || !item.floats().isEmpty()) {
            final CustomModelData.Builder data = CustomModelData.customModelData();
            item.colors().forEach(color -> data.addColor(Color.fromRGB(color & 0xFFFFFF)));
            item.floats().forEach(data::addFloat);
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        }
        if (item.tooltipHidden()) {
            stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
            return stack;
        }
        stack.setData(DataComponentTypes.ITEM_NAME, item.name());
        if (!item.lore().isEmpty()) {
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(item.lore()));
        }
        return stack;
    }

    static ItemStack[] stacks(MenuItem[] items) {
        final ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = stack(items[i]);
        }
        return out;
    }
}
