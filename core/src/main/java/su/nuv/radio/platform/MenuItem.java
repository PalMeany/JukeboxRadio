package su.nuv.radio.platform;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * One slot of the radio menu: a paper item drawn by a model from the radio pack, with its tooltip
 * and the tints or numbers the model reads from {@code custom_model_data}.
 *
 * @param name   item name, or {@code null} for an item with its tooltip hidden
 * @param colors custom_model_data colours, 0xRRGGBB
 * @param floats custom_model_data floats
 */
public record MenuItem(Key model, Component name, List<Component> lore, List<Integer> colors, List<Float> floats) {

    public MenuItem {
        lore = lore == null ? List.of() : List.copyOf(lore);
        colors = colors == null ? List.of() : List.copyOf(colors);
        floats = floats == null ? List.of() : List.copyOf(floats);
    }

    public boolean tooltipHidden() {
        return this.name == null;
    }

    public MenuItem withModel(Key next) {
        return new MenuItem(next, this.name, this.lore, this.colors, this.floats);
    }
}
