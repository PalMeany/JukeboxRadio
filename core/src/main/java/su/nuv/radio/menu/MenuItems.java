package su.nuv.radio.menu;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import su.nuv.radio.menu.pack.Models;
import su.nuv.radio.menu.pack.PackArt;
import su.nuv.radio.platform.MenuItem;

import java.util.ArrayList;
import java.util.List;

/**
 * The items the menu shows: every one is paper with an item model from the radio pack, so it looks
 * like a button, a disc or a piece of cover, and carries its own tooltip.
 */
public final class MenuItems {

    private MenuItems() {
    }

    private static MenuItem named(Key model, String name, List<String> lore, List<Integer> colors, List<Float> floats) {
        final List<Component> lines = new ArrayList<>();
        if (lore != null) {
            for (String line : lore) {
                lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        return new MenuItem(model, Component.text(name, NamedTextColor.WHITE), lines, colors, floats);
    }

    private static MenuItem silent(Key model, List<Integer> colors) {
        return new MenuItem(model, null, List.of(), colors, List.of());
    }

    public static MenuItem button(String icon, PackArt.ButtonStyle style, String name, String... lore) {
        return named(Models.button(icon, style), name, List.of(lore), List.of(), List.of());
    }

    /** A button's tooltip lines given as a list. */
    public static MenuItem button(String icon, PackArt.ButtonStyle style, String name, List<String> lore) {
        return named(Models.button(icon, style), name, lore, List.of(), List.of());
    }

    public static MenuItem disc(Key model, int labelColor) {
        return silent(model, List.of(labelColor & 0xFFFFFF));
    }

    public static MenuItem miniDisc(Key model, int labelColor, String title, List<String> lore) {
        return named(model, title, lore, List.of(labelColor & 0xFFFFFF), List.of());
    }

    public static MenuItem arm(int pose) {
        return silent(Models.arm(pose), List.of());
    }

    /** One 8×8 tile of a cover; {@code rgb} holds 64 colours, row-major. */
    public static MenuItem patch(boolean tile, int[] rgb, String name, List<String> lore) {
        final List<Integer> colors = new ArrayList<>(rgb.length);
        for (int color : rgb) {
            colors.add(color & 0xFFFFFF);
        }
        final Key model = tile ? Models.PATCH_TILE : Models.PATCH_SLOT;
        if (name == null) {
            return silent(model, colors);
        }
        return named(model, name, lore, colors, List.of());
    }

    /** An invisible item that only carries a tooltip, laid over text the title draws. */
    public static MenuItem hit(String name, List<String> lore) {
        return named(Models.CLEAR, name, lore, List.of(), List.of());
    }

    public static MenuItem volume(int percent) {
        return named(Models.VOLUME, "Громкость: " + percent + "%", List.of("ЛКМ — тише · ПКМ — громче"), List.of(),
                List.of((float) Math.round(percent / 10f)));
    }

    /** Colour a line of a tooltip with the vanilla accent for errors. */
    public static TextColor error() {
        return TextColor.color(0xFF5555);
    }
}
