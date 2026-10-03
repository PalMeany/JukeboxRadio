package su.nuv.radio.menu.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import su.nuv.radio.menu.MenuLayout;

/**
 * Composes a container title that draws a whole screen: background glyphs, text at any line,
 * pixel runs. Tracks the pen's x in container pixels and moves it with negative/positive spaces.
 * Every glyph is emitted in white unless coloured, because vanilla tints titles #404040.
 */
public final class TitleBuilder {

    public enum Align {
        LEFT, CENTER, RIGHT
    }

    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private final TextComponent.Builder out = Component.text();
    private int x = MenuLayout.TITLE_X;

    public int x() {
        return this.x;
    }

    public TitleBuilder moveTo(int target) {
        final int delta = target - this.x;
        if (delta != 0) {
            this.out.append(Component.text(FontChars.spaces(delta)).font(FontChars.UI));
            this.x = target;
        }
        return this;
    }

    /** A background glyph drawn with its left edge at {@code left}; advance is width + 1. */
    public TitleBuilder background(char glyph, int left, int width) {
        this.moveTo(left);
        this.out.append(Component.text(glyph).font(FontChars.UI).color(WHITE));
        this.x += width + 1;
        return this;
    }

    /**
     * One line of text whose glyph tops sit at {@code top}. Characters the vanilla bitmap fonts do
     * not have are replaced, since only those fonts can be shifted.
     */
    public TitleBuilder text(String text, int anchorX, int top, int color, Align align) {
        final String safe = Glyphs.sanitize(text);
        final int width = Glyphs.advanceSum(safe);
        final int left = switch (align) {
            case LEFT -> anchorX;
            case CENTER -> anchorX - width / 2;
            case RIGHT -> anchorX - width;
        };
        this.moveTo(left);
        this.out.append(Component.text(safe).font(FontChars.textFont(top)).color(TextColor.color(color)));
        this.x += width;
        return this;
    }

    /**
     * A row of square pixels of {@code size} px (1 or 2... drawn as 2-px dots, so size must be 2),
     * left edge at {@code left}, top at {@code top}. Equal neighbours share one component.
     */
    public TitleBuilder pixels(int[] rgb, int left, int top) {
        this.moveTo(left);
        final char dot = FontChars.pixel(top);
        final String step = String.valueOf(dot) + FontChars.spaces(-1);
        final TextComponent.Builder row = Component.text().font(FontChars.UI);
        int i = 0;
        while (i < rgb.length) {
            int j = i;
            while (j < rgb.length && rgb[j] == rgb[i]) {
                j++;
            }
            row.append(Component.text(step.repeat(j - i)).color(TextColor.color(rgb[i])));
            i = j;
        }
        this.out.append(row.build());
        this.x += rgb.length * 2;
        return this;
    }

    /** A horizontal bar of 2-px dots: {@code filled} of {@code total} dots in one colour, the rest another. */
    public TitleBuilder bar(int left, int top, int total, int filled, int on, int off) {
        final int[] colors = new int[total];
        for (int i = 0; i < total; i++) {
            colors[i] = i < filled ? on : off;
        }
        return this.pixels(colors, left, top);
    }

    public Component build() {
        return this.out.build();
    }
}
