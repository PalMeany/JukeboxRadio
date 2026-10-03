package su.nuv.radio.platform;

/** How a menu slot was clicked. */
public enum ClickKind {
    LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, OTHER;

    public boolean isRight() {
        return this == RIGHT || this == SHIFT_RIGHT;
    }
}
