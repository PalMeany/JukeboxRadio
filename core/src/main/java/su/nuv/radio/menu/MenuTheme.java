package su.nuv.radio.menu;

/**
 * The menu's palette. {@code VANILLA} is the container grammar of DESIGN.md; {@code DARK} keeps
 * the same layout on near-black panels with flat slots, dark buttons and light text, for servers
 * whose own interface is dark. Picked with {@code menu.theme} and applied once at startup, before
 * the pack is drawn.
 */
public enum MenuTheme {

    VANILLA(new Panel(0xC6C6C6, 0x000000, 0xFFFFFF, 0x555555, 0x8B8B8B, 0x373737, 0x2B2B2B, 0x151515, 0x4A4A4A, 0x2E2E2E, 0),
            new Text(0x404040, 0x1E1E1E, 0x4C4C4C, 0xAA0000, 0xFFFFFF, 0xA8A8A8, 0x8C8CB4),
            new Buttons(new int[]{0x6E6E6E, 0x9A9A9A, 0x4E4E4E, 0xFFFFFF}, new int[]{0x3A3A3A, 0x4A4A4A, 0x2A2A2A, 0x8A8A8A},
                    new int[]{0x7D7D7D, 0xB8B8B8, 0x555555, 0xFFFFFF})),
    DARK(new Panel(0x161616, 0x2E2E2E, 0x161616, 0x161616, 0x262626, 0x262626, 0x202020, 0x202020, 0x202020, 0x2A2A2A, 4),
            new Text(0xD6D6D6, 0xF2F2F2, 0x9A9A9A, 0xFF6B6B, 0xFFFFFF, 0xA8A8A8, 0x9C9CC8),
            new Buttons(new int[]{0x2C2C2C, 0x3A3A3A, 0x1F1F1F, 0xF0F0F0}, new int[]{0x1E1E1E, 0x262626, 0x161616, 0x7A7A7A},
                    new int[]{0x4A4A4A, 0x5E5E5E, 0x333333, 0xFFFFFF}));

    /**
     * @param cornerRadius 0 keeps the vanilla 2-px chamfer; otherwise a rounded corner of that radius
     */
    public record Panel(int face, int outline, int light, int shade, int slot, int slotEdge, int row, int rowEdgeDark,
                        int rowEdgeLight, int track, int cornerRadius) {
    }

    public record Text(int normal, int strong, int muted, int error, int row, int rowMuted, int hint) {
    }

    /** face, top bevel, bottom bevel, icon colour for each button style. */
    public record Buttons(int[] normal, int[] off, int[] on) {
    }

    private final Panel panel;
    private final Text text;
    private final Buttons buttons;

    MenuTheme(Panel panel, Text text, Buttons buttons) {
        this.panel = panel;
        this.text = text;
        this.buttons = buttons;
    }

    public Panel panel() {
        return this.panel;
    }

    public Text text() {
        return this.text;
    }

    public Buttons buttons() {
        return this.buttons;
    }

    public static MenuTheme parse(String value) {
        return value != null && value.strip().equalsIgnoreCase("dark") ? DARK : VANILLA;
    }
}
