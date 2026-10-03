package su.nuv.radio.platform;

import net.kyori.adventure.text.Component;

import java.util.Optional;

/** Whoever ran {@code /radio}: a player or the console. */
public interface CommandSource {

    String name();

    void sendMessage(Component message);

    boolean hasPermission(String permission);

    /** The player behind this source, if it is one. */
    Optional<RadioPlayer> player();
}
