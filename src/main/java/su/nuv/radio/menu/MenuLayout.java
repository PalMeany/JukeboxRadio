package su.nuv.radio.menu;

/**
 * Geometry of the radio menu in container pixels: (0,0) is the top-left of the vanilla 176×222
 * six-row chest, x grows right, y grows down. The menu also draws a 120-px panel to the right of
 * the chest (only its top 126 px, beside the chest rows); at every automatic GUI scale the window
 * is at least 416 px wide, so the extension always fits.
 */
public final class MenuLayout {

    public static final String NS = "jukeboxradio";

    public static final int CHEST_W = 176;
    public static final int CHEST_H = 222;
    public static final int EXT_W = 120;
    public static final int FULL_W = CHEST_W + EXT_W;
    /** The extension panel's height: the six chest rows plus their frame. */
    public static final int EXT_H = 130;

    /** Where vanilla starts drawing the container title. */
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 6;

    // ---------------------------------------------------------------- main screen slots

    public static final int SLOT_DISC = 10;
    /** After the disc slot on purpose: items draw in slot order, and the arm lies on the record. */
    public static final int SLOT_ARM = 30;
    public static final int SLOT_TAB_PLAYING = 4;
    public static final int SLOT_TAB_SEARCH = 13;
    public static final int SLOT_TAB_QUEUE = 22;
    public static final int SLOT_TAB_CLOSE = 31;
    /** Top-left slot of the 4×4 cover block (rows 0-3, columns 5-8). */
    public static final int SLOT_COVER = 5;
    public static final int SLOT_NEXT = 36;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_PLAY = 46;
    public static final int SLOT_SKIP = 47;
    public static final int SLOT_STOP = 48;
    public static final int SLOT_REPEAT = 49;
    public static final int SLOT_SHUFFLE = 50;
    public static final int SLOT_VOL_DOWN = 51;
    public static final int SLOT_VOL = 52;
    public static final int SLOT_VOL_UP = 53;

    // ---------------------------------------------------------------- list screens

    public static final int LIST_ROWS = 5;
    public static final int SLOT_PAGE_PREV = 45;
    public static final int SLOT_PAGE_NEXT = 46;
    public static final int SLOT_LIST_ACTION_A = 47;
    public static final int SLOT_LIST_ACTION_B = 48;
    public static final int SLOT_TO_PLAYER = 52;
    public static final int SLOT_CLOSE = 53;

    // ---------------------------------------------------------------- right panel (main screen)

    public static final int PANEL_X = 184;
    public static final int PANEL_W = 104;
    public static final int BAR_X = 184;
    public static final int BAR_Y = 90;
    public static final int BAR_W = 104;

    // ---------------------------------------------------------------- zoom

    /** 56 cells of 2 px: the largest square that clears the "Inventory" label at y 128. */
    public static final int ZOOM_X = 32;
    public static final int ZOOM_Y = 10;
    public static final int ZOOM_CELLS = 56;
    public static final int ZOOM_PX = 2;

    // ---------------------------------------------------------------- disc animation geometry

    /** Centre of the turntable, where the spinning disc sits. */
    public static final int DISC_CX = 44;
    public static final int DISC_CY = 54;

    private MenuLayout() {
    }

    public static int slotX(int slot) {
        return 8 + 18 * (slot % 9);
    }

    public static int slotY(int slot) {
        return 18 + 18 * (slot / 9);
    }

    /** Centre of a slot's 16×16 item area. */
    public static int slotCx(int slot) {
        return slotX(slot) + 8;
    }

    public static int slotCy(int slot) {
        return slotY(slot) + 8;
    }

    public static int rowTop(int row) {
        return 18 + 18 * row;
    }
}
