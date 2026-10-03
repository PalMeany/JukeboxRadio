package su.nuv.radio.menu.pack;

import su.nuv.radio.menu.MenuLayout;
import su.nuv.radio.menu.MenuTheme;
import su.nuv.radio.menu.art.Icons;

import java.awt.image.BufferedImage;

/**
 * Every texture of the radio's resource pack, drawn in code as pixel art at one texel per GUI
 * pixel, in the vanilla container grammar: 1-px black outline with rounded corners, 2-px white
 * light and #555555 shade, #C6C6C6 face, #8B8B8B slots.
 */
public final class PackArt {

    public static int PANEL = 0xC6C6C6;
    public static int OUTLINE = 0x000000;
    public static int LIGHT = 0xFFFFFF;
    public static int SHADE = 0x555555;
    public static int SLOT = 0x8B8B8B;
    public static int SLOT_DARK = 0x373737;
    public static int ROW = 0x2B2B2B;
    public static int ROW_EDGE_DARK = 0x151515;
    public static int ROW_EDGE_LIGHT = 0x4A4A4A;
    public static int TRACK = 0x2E2E2E;
    /** 0: the vanilla 2-px chamfer; otherwise the radius of round panel corners. */
    private static int cornerRadius;

    public static final int DISC_FRAMES = 16;
    public static final int DISC_SIZE = 64;

    private PackArt() {
    }

    /** Takes the panel and button colours of {@code theme}; call once at startup, before the pack is drawn. */
    public static void use(MenuTheme theme) {
        final MenuTheme.Panel panel = theme.panel();
        PANEL = panel.face();
        OUTLINE = panel.outline();
        LIGHT = panel.light();
        SHADE = panel.shade();
        SLOT = panel.slot();
        SLOT_DARK = panel.slotEdge();
        ROW = panel.row();
        ROW_EDGE_DARK = panel.rowEdgeDark();
        ROW_EDGE_LIGHT = panel.rowEdgeLight();
        TRACK = panel.track();
        cornerRadius = panel.cornerRadius();
        ButtonStyle.NORMAL.use(theme.buttons().normal());
        ButtonStyle.OFF.use(theme.buttons().off());
        ButtonStyle.ON.use(theme.buttons().on());
    }

    // ------------------------------------------------------------------ primitives

    static BufferedImage image(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    static void px(BufferedImage image, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
            image.setRGB(x, y, 0xFF000000 | rgb);
        }
    }

    static void pxa(BufferedImage image, int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
            image.setRGB(x, y, argb);
        }
    }

    static void fill(BufferedImage image, int x, int y, int w, int h, int rgb) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                px(image, xx, yy, rgb);
            }
        }
    }

    /** A vanilla slot whose outer box starts at (x, y): 18×18 with the dark top-left edge. */
    static void slot(BufferedImage image, int x, int y) {
        fill(image, x, y, 18, 18, SLOT);
        fill(image, x, y, 17, 1, SLOT_DARK);
        fill(image, x, y, 1, 17, SLOT_DARK);
        fill(image, x + 1, y + 17, 17, 1, LIGHT);
        fill(image, x + 17, y + 1, 1, 17, LIGHT);
    }

    /** A slot for the item of a given container slot index. */
    static void slotAt(BufferedImage image, int slot) {
        slot(image, MenuLayout.slotX(slot) - 1, MenuLayout.slotY(slot) - 1);
    }

    /** A dark list row, like a vanilla text field, spanning the chest and its extension. */
    static void darkRow(BufferedImage image, int x, int y, int w, int h) {
        fill(image, x, y, w, h, ROW);
        fill(image, x, y, w - 1, 1, ROW_EDGE_DARK);
        fill(image, x, y, 1, h - 1, ROW_EDGE_DARK);
        fill(image, x + 1, y + h - 1, w - 1, 1, ROW_EDGE_LIGHT);
        fill(image, x + w - 1, y + 1, 1, h - 1, ROW_EDGE_LIGHT);
    }

    /**
     * The menu's outer shape: the chest plus the extension beside its six rows, as one L-shaped
     * vanilla panel. {@code face} is the fill colour.
     */
    static BufferedImage panel(int face) {
        final int w = MenuLayout.FULL_W;
        final int h = MenuLayout.CHEST_H;
        final int extH = MenuLayout.EXT_H;
        final BufferedImage image = image(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (inside(x, y, w, h, extH)) {
                    px(image, x, y, face);
                }
            }
        }
        // outline and bevels follow the shape: a pixel is edge when a neighbour is outside
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!inside(x, y, w, h, extH)) {
                    continue;
                }
                if (!inside(x - 1, y, w, h, extH) || !inside(x + 1, y, w, h, extH)
                        || !inside(x, y - 1, w, h, extH) || !inside(x, y + 1, w, h, extH)) {
                    px(image, x, y, OUTLINE);
                } else if (!inside(x - 3, y, w, h, extH) || !inside(x, y - 3, w, h, extH)
                        || !inside(x - 2, y - 2, w, h, extH)) {
                    px(image, x, y, LIGHT);
                } else if (!inside(x + 3, y, w, h, extH) || !inside(x, y + 3, w, h, extH)
                        || !inside(x + 2, y + 2, w, h, extH)) {
                    px(image, x, y, SHADE);
                }
            }
        }
        return image;
    }

    private static boolean inside(int x, int y, int w, int h, int extH) {
        if (x < 0 || y < 0 || y >= h) {
            return false;
        }
        final int right = y < extH ? w : MenuLayout.CHEST_W;
        if (x >= right) {
            return false;
        }
        // rounded 2-px corners at every convex corner
        return !corner(x, y, 0, 0, 1, 1) && !corner(x, y, right - 1, 0, -1, 1)
                && !corner(x, y, 0, h - 1, 1, -1) && !corner(x, y, MenuLayout.CHEST_W - 1, h - 1, -1, -1)
                && !corner(x, y, w - 1, extH - 1, -1, -1);
    }

    private static boolean corner(int x, int y, int cx, int cy, int dx, int dy) {
        final int ox = (x - cx) * dx;
        final int oy = (y - cy) * dy;
        if (cornerRadius <= 0) {
            return ox >= 0 && oy >= 0 && ox + oy < 2;
        }
        if (ox < 0 || oy < 0 || ox >= cornerRadius || oy >= cornerRadius) {
            return false;
        }
        final double rx = cornerRadius - ox - 0.5;
        final double ry = cornerRadius - oy - 0.5;
        return rx * rx + ry * ry > cornerRadius * cornerRadius;
    }

    /** The player inventory slots vanilla draws under a six-row chest. */
    static void playerInventory(BufferedImage image) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                slot(image, 7 + 18 * c, 139 + 18 * r);
            }
        }
        for (int c = 0; c < 9; c++) {
            slot(image, 7 + 18 * c, 197);
        }
    }

    // ------------------------------------------------------------------ backgrounds

    /** "Now playing": turntable, tab column, cover frame, up-next hotbar, controls, side panel. */
    public static BufferedImage mainBackground() {
        final BufferedImage image = panel(PANEL);
        turntable(image, 7, 17);
        for (int slot : new int[]{MenuLayout.SLOT_TAB_PLAYING, MenuLayout.SLOT_TAB_SEARCH,
                MenuLayout.SLOT_TAB_QUEUE, MenuLayout.SLOT_TAB_CLOSE}) {
            slotAt(image, slot);
        }
        coverFrame(image, 95, 15, 76);
        for (int i = 0; i < 9; i++) {
            slotAt(image, MenuLayout.SLOT_NEXT + i);
            slotAt(image, MenuLayout.SLOT_PREV + i);
        }
        playerInventory(image);
        // side panel: progress groove and a divider above the up-next caption
        fill(image, MenuLayout.BAR_X - 1, MenuLayout.BAR_Y - 1, MenuLayout.BAR_W + 2, 4, OUTLINE);
        fill(image, MenuLayout.BAR_X, MenuLayout.BAR_Y, MenuLayout.BAR_W, 2, TRACK);
        fill(image, 182, 102, 108, 1, SHADE);
        fill(image, 182, 103, 108, 1, LIGHT);
        return image;
    }

    /** Search results and queue: five dark rows with an action slot and a cover slot each. */
    public static BufferedImage listBackground() {
        final BufferedImage image = panel(PANEL);
        for (int row = 0; row < MenuLayout.LIST_ROWS; row++) {
            final int top = MenuLayout.rowTop(row) - 1;
            darkRow(image, 7, top, 286, 18);
            slot(image, 7, top);
            fill(image, 26, top + 1, 16, 16, 0x1E1E1E);
        }
        for (int i = 0; i < 9; i++) {
            slotAt(image, MenuLayout.SLOT_PAGE_PREV + i);
        }
        playerInventory(image);
        return image;
    }

    /** The full-cover view: dark plate and an item-frame border around the 128×128 picture. */
    public static BufferedImage zoomBackground() {
        final BufferedImage image = panel(0x1B1B1B);
        final int x = MenuLayout.ZOOM_X - 4;
        final int y = MenuLayout.ZOOM_Y - 4;
        final int size = MenuLayout.ZOOM_CELLS * MenuLayout.ZOOM_PX + 8;
        for (int yy = 0; yy < size; yy++) {
            for (int xx = 0; xx < size; xx++) {
                final int edge = Math.min(Math.min(xx, yy), Math.min(size - 1 - xx, size - 1 - yy));
                final int color = switch (edge) {
                    case 0 -> 0x3E2914;
                    case 1, 2 -> ((xx + yy) % 4 < 2) ? 0x9C6B3D : 0x8A5C33;
                    case 3 -> 0x6B4626;
                    default -> 0xE4D6B1;
                };
                px(image, x + xx, y + yy, color);
            }
        }
        playerInventory(image);
        return image;
    }

    /** The jukebox top as a turntable: oak frame, felt mat, metal platter ring, rivets. */
    static void turntable(BufferedImage image, int x, int y) {
        final int size = 74;
        final int[] wood = {0x7B5230, 0x8C5F37, 0x6E4829, 0x875A33};
        for (int yy = 0; yy < size; yy++) {
            for (int xx = 0; xx < size; xx++) {
                int color = wood[((xx / 6) + (yy * 3 / 8)) % wood.length];
                if ((xx * 7 + yy * 3) % 13 == 0) {
                    color = 0x5F3E22;
                }
                final int edge = Math.min(Math.min(xx, yy), Math.min(size - 1 - xx, size - 1 - yy));
                if (edge == 0) {
                    color = 0x3B2716;
                } else if (edge == 1 && (xx == 1 || yy == 1)) {
                    color = 0xA0703F;
                } else if (edge == 1) {
                    color = 0x4F341D;
                }
                px(image, x + xx, y + yy, color);
            }
        }
        for (int yy = 5; yy < size - 5; yy++) {
            for (int xx = 5; xx < size - 5; xx++) {
                int color = ((xx + yy) % 2 == 0) ? 0x2A2A2C : 0x262628;
                if (xx == 5 || yy == 5) {
                    color = 0x1A1A1B;
                } else if (xx == size - 6 || yy == size - 6) {
                    color = 0x3B3B3E;
                }
                px(image, x + xx, y + yy, color);
            }
        }
        final double c = (size - 1) / 2.0;
        for (int yy = 0; yy < size; yy++) {
            for (int xx = 0; xx < size; xx++) {
                final double r = Math.hypot(xx - c, yy - c);
                if (r <= 33.5 && r > 32.2) {
                    px(image, x + xx, y + yy, 0x8A8A8E);
                } else if (r <= 32.2 && r > 31) {
                    px(image, x + xx, y + yy, 0x5C5C60);
                } else if (r <= 31) {
                    px(image, x + xx, y + yy, 0x333336);
                }
            }
        }
        for (int[] rivet : new int[][]{{8, 8}, {size - 9, 8}, {8, size - 9}, {size - 9, size - 9}}) {
            px(image, x + rivet[0], y + rivet[1], 0xB5B5B8);
            px(image, x + rivet[0] + 1, y + rivet[1] + 1, 0x55555A);
        }
    }

    /** An oak item frame holding a blank map; the cover items tile the parchment. */
    static void coverFrame(BufferedImage image, int x, int y, int size) {
        for (int yy = 0; yy < size; yy++) {
            for (int xx = 0; xx < size; xx++) {
                final int edge = Math.min(Math.min(xx, yy), Math.min(size - 1 - xx, size - 1 - yy));
                int color;
                if (edge == 0) {
                    color = 0x3E2914;
                } else if (edge == 1) {
                    color = ((xx + yy) % 4 < 2) ? 0x9C6B3D : 0x8A5C33;
                } else if ((xx - 2) % 18 == 0 || (yy - 2) % 18 == 0) {
                    color = 0xD2C299;
                } else {
                    color = ((xx * 3 + yy * 5) % 7 == 0) ? 0xDCCDA6 : 0xE4D6B1;
                }
                px(image, x + xx, y + yy, color);
            }
        }
    }

    // ------------------------------------------------------------------ disc and arm

    /**
     * One frame of the record at {@code angle}, 64×64, pre-rotated on the pixel grid (so the art
     * stays crisp while it spins). {@code label} draws the centre label (white, tinted in game)
     * instead of the vinyl. {@code alpha} fades it for the eject animation.
     */
    public static BufferedImage discFrame(double angle, boolean label, double alpha, int size) {
        final BufferedImage image = image(size, size);
        final double c = (size - 1) / 2.0;
        final double k = size / 32.0;
        final int a = (int) Math.round(alpha * 255) << 24;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                final double dx = x - c;
                final double dy = y - c;
                final double r = Math.sqrt(dx * dx + dy * dy) / k;
                final double theta = Math.toDegrees(Math.atan2(dy, dx)) - angle;
                final Integer color = label ? labelColor(r, theta) : vinylColor(r, theta);
                if (color != null) {
                    pxa(image, x, y, a | color);
                }
            }
        }
        return image;
    }

    private static Integer vinylColor(double r, double theta) {
        if (r > 15.9) {
            return null;
        }
        if (r > 14.9) {
            return 0x0B0B0B;
        }
        if (r > 7.2) {
            final boolean even = ((int) Math.floor(r * 1.25)) % 2 == 0;
            final double sheenA = Math.abs(delta(theta, -135));
            final double sheenB = Math.abs(delta(theta, 45));
            if (sheenA < 16 || sheenB < 10) {
                return even ? 0x3A3A3F : 0x44444A;
            }
            if (sheenA < 26 || sheenB < 18) {
                return even ? 0x2A2A2E : 0x303034;
            }
            return even ? 0x1B1B1D : 0x232326;
        }
        return r > 6.6 ? 0x101012 : null;
    }

    private static Integer labelColor(double r, double theta) {
        if (r > 6.7) {
            return null;
        }
        if (r > 5.9) {
            return 0xD9D9D9;
        }
        if (r < 1.3) {
            return 0x141414;
        }
        if (r < 2.2) {
            return 0xEDEDED;
        }
        final double t = norm(theta);
        if (r > 3.6 && r < 4.8 && t > 200 && t < 340) {
            return ((int) ((t - 200) / 11)) % 2 == 0 ? 0xC4C4C4 : 0xE2E2E2;
        }
        if (r > 3.3 && r < 4.3 && t > 50 && t < 130) {
            return 0xCFCFCF;
        }
        return 0xFFFFFF;
    }

    private static double norm(double angle) {
        double a = angle % 360;
        return a < 0 ? a + 360 : a;
    }

    private static double delta(double a, double b) {
        double d = (a - b) % 360;
        if (d > 180) {
            d -= 360;
        }
        if (d < -180) {
            d += 360;
        }
        return d;
    }

    /** A vertical strip of {@link #DISC_FRAMES} frames, one full clockwise turn, for texture animation. */
    public static BufferedImage discStrip(boolean label) {
        final BufferedImage strip = image(DISC_SIZE, DISC_SIZE * DISC_FRAMES);
        for (int f = 0; f < DISC_FRAMES; f++) {
            final BufferedImage frame = discFrame(f * 360.0 / DISC_FRAMES, label, 1, DISC_SIZE);
            strip.getGraphics().drawImage(frame, 0, f * DISC_SIZE, null);
        }
        return strip;
    }

    /**
     * The tonearm, 64×64 with its pivot at (52, 10). {@code degrees} is the swing from straight
     * down, positive towards the record's centre.
     */
    public static BufferedImage tonearm(double degrees) {
        final BufferedImage image = image(64, 64);
        final double pivotX = 52;
        final double pivotY = 10;
        final double angle = Math.toRadians(degrees);
        final double length = 38;
        // counterweight behind the pivot
        stroke(image, pivotX, pivotY, pivotX + Math.sin(angle) * 7, pivotY - Math.cos(angle) * 7, 0x6A6A70, 2);
        // shaft
        final double tipX = pivotX - Math.sin(angle) * length;
        final double tipY = pivotY + Math.cos(angle) * length;
        stroke(image, pivotX + 1, pivotY + 1, tipX + 1, tipY + 1, 0x3A3A3E, 1);
        stroke(image, pivotX, pivotY, tipX, tipY, 0xE2E2E5, 1);
        stroke(image, pivotX + 1, pivotY, tipX + 1, tipY, 0xA6A6AB, 1);
        // headshell: a light block with a dark cartridge so it reads on the black record
        for (int yy = -2; yy <= 3; yy++) {
            for (int xx = -2; xx <= 2; xx++) {
                final int color = yy == 3 ? 0x141416 : xx == -2 || yy == -2 ? 0xC8C8CC : 0x7E7E84;
                px(image, (int) Math.round(tipX) + xx, (int) Math.round(tipY) + yy, color);
            }
        }
        // pivot base
        for (int yy = -3; yy <= 3; yy++) {
            for (int xx = -3; xx <= 3; xx++) {
                if (xx * xx + yy * yy <= 10) {
                    px(image, (int) pivotX + xx, (int) pivotY + yy, (xx + yy) < 0 ? 0xE0E0E3 : 0x8E8E93);
                }
            }
        }
        return image;
    }

    private static void stroke(BufferedImage image, double x0, double y0, double x1, double y1, int rgb, int width) {
        final int steps = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))) * 2 + 1;
        for (int i = 0; i <= steps; i++) {
            final double t = i / (double) steps;
            final int x = (int) Math.round(x0 + (x1 - x0) * t);
            final int y = (int) Math.round(y0 + (y1 - y0) * t);
            for (int w = 0; w < width; w++) {
                px(image, x + w, y, rgb);
            }
        }
    }

    // ------------------------------------------------------------------ buttons and small items

    public enum ButtonStyle {
        NORMAL(0x6E6E6E, 0x9A9A9A, 0x4E4E4E, 0xFFFFFF),
        OFF(0x3A3A3A, 0x4A4A4A, 0x2A2A2A, 0x8A8A8A),
        ON(0x7D7D7D, 0xB8B8B8, 0x555555, 0xFFFFFF),
        DANGER(0x7A1E1E, 0xA33333, 0x4E1010, 0xFFFFFF);

        int face;
        int top;
        int bottom;
        int ink;

        ButtonStyle(int face, int top, int bottom, int ink) {
            this.face = face;
            this.top = top;
            this.bottom = bottom;
            this.ink = ink;
        }

        void use(int[] colors) {
            this.face = colors[0];
            this.top = colors[1];
            this.bottom = colors[2];
            this.ink = colors[3];
        }
    }

    /** A 16×16 stone button with a pixel icon, optionally with an accent-coloured icon (tinted later). */
    public static BufferedImage button(String icon, ButtonStyle style) {
        final BufferedImage image = image(16, 16);
        fill(image, 0, 0, 16, 16, style.face);
        fill(image, 0, 0, 16, 1, style.top);
        fill(image, 0, 14, 16, 2, style.bottom);
        if (icon != null) {
            final BufferedImage mask = Icons.image(icon);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    final int p = mask.getRGB(x, y);
                    if ((p >>> 24) == 0) {
                        continue;
                    }
                    final boolean shadow = (p & 0xFFFFFF) != 0xFFFFFF;
                    px(image, x, y, shadow ? mix(style.face, 0x000000, 0.55) : style.ink);
                }
            }
        }
        return image;
    }

    /** Just the icon (white with shadow), for items tinted in game. */
    public static BufferedImage icon(String icon) {
        return Icons.image(icon);
    }

    /**
     * The volume meter: a speaker and five rising bars; {@code level} is 0..10. It keeps the icons'
     * margins: pixels 2..13 across, rows 3..12 on the face between the bevels.
     */
    public static BufferedImage volume(int level) {
        final BufferedImage image = button(null, ButtonStyle.NORMAL);
        fill(image, 2, 6, 1, 4, 0xFFFFFF);
        fill(image, 3, 5, 1, 6, 0xFFFFFF);
        fill(image, 4, 4, 1, 8, 0xFFFFFF);
        for (int i = 0; i < 5; i++) {
            final int height = 2 + i * 2;
            final int color = level >= (i + 1) * 2 ? 0xFFFFFF : level == (i + 1) * 2 - 1 ? 0xA8A8A8 : 0x3A3A3A;
            fill(image, 6 + i * 2, 13 - height, 1, height, color);
        }
        return image;
    }

    /** A transparent 16×16 texture for items that only carry a tooltip over drawn text. */
    public static BufferedImage clear() {
        return image(16, 16);
    }

    /** A white square used by the tinted cover-patch items and by text-pixel glyphs. */
    public static BufferedImage white(int w, int h) {
        final BufferedImage image = image(w, h);
        fill(image, 0, 0, w, h, 0xFFFFFF);
        return image;
    }

    /** 2 px wide, 32 px tall, only the top 2 rows opaque: a pixel glyph that can sit anywhere. */
    public static BufferedImage pixelGlyph() {
        final BufferedImage image = image(2, 32);
        fill(image, 0, 0, 2, 2, 0xFFFFFF);
        return image;
    }

    static int mix(int a, int b, double t) {
        final int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        final int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        final int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }
}
