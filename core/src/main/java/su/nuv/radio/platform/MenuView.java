package su.nuv.radio.platform;

import net.kyori.adventure.text.Component;

/**
 * A six-row chest window the radio draws into. Items never move: every click is reported to the
 * listener and otherwise ignored.
 */
public interface MenuView {

    int SIZE = 54;

    interface Listener {

        void clicked(int slot, ClickKind click);

        /** The player closed the window (not a title change or a reopen by the radio itself). */
        void closed();
    }

    void setItem(int slot, MenuItem item);

    MenuItem getItem(int slot);

    void setContents(MenuItem[] items);

    /** Opens the window with {@code title}, or reopens it when the platform cannot change titles in place. */
    void open(Component title);

    /** Changes the title of the open window without moving the cursor; false when it could not. */
    boolean updateTitle(Component title);

    /** True while this window is the one the player has open. */
    boolean isOpen();
}
