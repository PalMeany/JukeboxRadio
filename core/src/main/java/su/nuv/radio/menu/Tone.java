package su.nuv.radio.menu;

/**
 * The menu palette, vanilla by default (see {@link MenuTheme}). Everything is achromatic on purpose:
 * the only saturated colour on screen is the one taken from the current cover (the disc label, the
 * bars).
 */
public final class Tone {

    public static final int OUTLINE = 0x000000;
    public static final int PANEL = 0xC6C6C6;
    public static final int BEVEL_LIGHT = 0xFFFFFF;
    public static final int BEVEL_DARK = 0x555555;
    public static final int SLOT = 0x8B8B8B;
    public static final int SLOT_EDGE = 0x373737;
    public static final int TAB_IDLE = 0xB4B4B4;

    public static int TEXT = 0x404040;
    public static int TEXT_STRONG = 0x1E1E1E;
    public static int TEXT_MUTED = 0x4C4C4C;
    public static int TEXT_ERROR = 0xAA0000;

    public static final int ROW = 0x2B2B2B;
    public static final int ROW_EDGE_DARK = 0x151515;
    public static final int ROW_EDGE_LIGHT = 0x4A4A4A;
    public static int ROW_TEXT = 0xFFFFFF;
    public static int ROW_TEXT_MUTED = 0xA8A8A8;

    public static final int FIELD = 0x000000;
    public static final int FIELD_EDGE = 0xA0A0A0;
    public static final int FIELD_TEXT = 0xE0E0E0;
    public static final int FIELD_HINT = 0x8A8A8A;

    public static final int BUTTON_FACE = 0x6E6E6E;
    public static final int BUTTON_TOP = 0x9A9A9A;
    public static final int BUTTON_BOTTOM = 0x4E4E4E;
    public static final int BUTTON_FACE_OFF = 0x3A3A3A;
    public static final int BUTTON_TOP_OFF = 0x4A4A4A;
    public static final int BUTTON_BOTTOM_OFF = 0x2A2A2A;
    public static final int BUTTON_TEXT = 0xFFFFFF;
    public static final int BUTTON_TEXT_OFF = 0x8A8A8A;
    public static final int HOVER = 0xFFFFFF;
    public static final int DANGER_FACE = 0x7A1E1E;
    public static final int DANGER_TOP = 0xA33333;
    public static final int DANGER_BOTTOM = 0x4E1010;

    public static final int TRACK = 0x2E2E2E;

    public static final int TOOLTIP = 0x100010;
    public static final int TOOLTIP_EDGE = 0x3A0E86;

    public static final int NEUTRAL_LABEL = 0xE8E4DA;

    /** Text colour of the hint under the full cover. */
    public static int HINT = 0x8C8CB4;

    /** Takes the text colours of {@code theme}; call once at startup, before any menu opens. */
    public static void use(MenuTheme theme) {
        final MenuTheme.Text text = theme.text();
        TEXT = text.normal();
        TEXT_STRONG = text.strong();
        TEXT_MUTED = text.muted();
        TEXT_ERROR = text.error();
        ROW_TEXT = text.row();
        ROW_TEXT_MUTED = text.rowMuted();
        HINT = text.hint();
    }

    /** Vanilla text shadow: the colour at a quarter brightness. */
    public static int shadowOf(int color) {
        return (color & 0xFCFCFC) >> 2;
    }

    private Tone() {
    }
}
