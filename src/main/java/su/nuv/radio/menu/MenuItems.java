package su.nuv.radio.menu;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import su.nuv.radio.menu.pack.Models;
import su.nuv.radio.menu.pack.PackArt;

import java.util.ArrayList;
import java.util.List;

/**
 * The items the menu shows: every one is paper with an item model from the radio pack, so it looks
 * like a button, a disc or a piece of cover, and carries its own tooltip.
 */
public final class MenuItems {

    private MenuItems() {
    }

    private static ItemStack base(Key model) {
        final ItemStack stack = ItemStack.of(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, model);
        return stack;
    }

    private static ItemStack named(ItemStack stack, String name, List<String> lore) {
        stack.setData(DataComponentTypes.ITEM_NAME, Component.text(name, NamedTextColor.WHITE));
        if (lore != null && !lore.isEmpty()) {
            final List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        }
        return stack;
    }

    private static ItemStack silent(ItemStack stack) {
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return stack;
    }

    public static ItemStack button(String icon, PackArt.ButtonStyle style, String name, String... lore) {
        return named(base(Models.button(icon, style)), name, List.of(lore));
    }

    /** A button's tooltip lines given as a list. */
    public static ItemStack button(String icon, PackArt.ButtonStyle style, String name, List<String> lore) {
        return named(base(Models.button(icon, style)), name, lore);
    }

    public static ItemStack disc(Key model, int labelColor) {
        final ItemStack stack = base(model);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                .addColor(Color.fromRGB(labelColor & 0xFFFFFF)).build());
        return silent(stack);
    }

    public static ItemStack miniDisc(Key model, int labelColor, String title, List<String> lore) {
        final ItemStack stack = base(model);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                .addColor(Color.fromRGB(labelColor & 0xFFFFFF)).build());
        return named(stack, title, lore);
    }

    public static ItemStack arm(int pose) {
        return silent(base(Models.arm(pose)));
    }

    /** One 8×8 tile of a cover; {@code rgb} holds 64 colours, row-major. */
    public static ItemStack patch(boolean tile, int[] rgb, String name, List<String> lore) {
        final ItemStack stack = base(tile ? Models.PATCH_TILE : Models.PATCH_SLOT);
        final CustomModelData.Builder colors = CustomModelData.customModelData();
        for (int color : rgb) {
            colors.addColor(Color.fromRGB(color & 0xFFFFFF));
        }
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, colors.build());
        if (name == null) {
            return silent(stack);
        }
        return named(stack, name, lore);
    }

    /** An invisible item that only carries a tooltip, laid over text the title draws. */
    public static ItemStack hit(String name, List<String> lore) {
        return named(base(Models.CLEAR), name, lore);
    }

    public static ItemStack volume(int percent) {
        final ItemStack stack = base(Models.VOLUME);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                .addFloat(Math.round(percent / 10f)).build());
        return named(stack, "Громкость: " + percent + "%", List.of("ЛКМ — тише · ПКМ — громче"));
    }

    /** Colour a line of a tooltip with the vanilla accent for errors. */
    public static TextColor error() {
        return TextColor.color(0xFF5555);
    }
}
