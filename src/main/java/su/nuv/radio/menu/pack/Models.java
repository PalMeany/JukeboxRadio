package su.nuv.radio.menu.pack;

import net.kyori.adventure.key.Key;
import su.nuv.radio.menu.MenuLayout;
import su.nuv.radio.menu.art.Icons;

import java.util.List;

/** Item model ids the menu puts on its items; the pack builder defines each of them. */
public final class Models {

    public static final Key DISC_SPIN = key("disc_spin");
    public static final Key DISC_STILL = key("disc_still");
    public static final int FLY_FRAMES = 6;
    public static final int EJECT_FRAMES = 4;
    public static final Key MINI_DISC = key("mini_disc");
    public static final int SHIFT_FRAMES = 3;
    public static final Key PATCH_TILE = key("patch_tile");
    public static final Key PATCH_SLOT = key("patch_slot");
    public static final Key CLEAR = key("clear");
    public static final Key VOLUME = key("volume");

    /** Tonearm poses: rest, two swing steps, then four positions creeping towards the label. */
    public static final List<Double> ARM_ANGLES = List.of(-12.0, -4.0, 4.0, 12.0, 17.0, 22.0, 27.0);
    public static final int ARM_REST = 0;
    public static final int ARM_PLAY_FIRST = 3;

    /** Every icon a button can show, with the styles the menu uses. */
    public static final List<String> BUTTON_ICONS = List.of(Icons.PREV, Icons.PLAY, Icons.PAUSE, Icons.NEXT,
            Icons.STOP, Icons.REPEAT, Icons.REPEAT_ONE, Icons.SHUFFLE, Icons.MINUS, Icons.PLUS, Icons.SEARCH,
            Icons.QUEUE, Icons.NOTE, Icons.CLOSE, Icons.LEFT, Icons.RIGHT, Icons.UP, Icons.CHECK, Icons.ALERT);

    private Models() {
    }

    static Key key(String path) {
        return Key.key(MenuLayout.NS, path);
    }

    public static Key discFly(int frame) {
        return key("disc_fly_" + frame);
    }

    public static Key discEject(int frame) {
        return key("disc_eject_" + frame);
    }

    public static Key miniShift(int frame) {
        return key("mini_shift_" + frame);
    }

    public static Key arm(int pose) {
        return key("arm_" + pose);
    }

    /** A button, e.g. {@code button(Icons.PLAY, ButtonStyle.NORMAL)}. */
    public static Key button(String icon, PackArt.ButtonStyle style) {
        return key("b_" + iconName(icon) + "_" + style.name().toLowerCase(java.util.Locale.ROOT));
    }

    static String iconName(String iconFile) {
        return iconFile.replace("jr_i_", "").replace(".png", "");
    }
}
