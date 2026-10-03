package su.nuv.radio.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import su.nuv.radio.platform.CommandSource;

/** Chat lines. The menu carries most feedback; chat is for commands and failures. */
public final class Messages {

    private static final Component PREFIX = Component.text("[Радио] ", NamedTextColor.GOLD);

    private Messages() {
    }

    public static void info(CommandSource to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GRAY)));
    }

    public static void ok(CommandSource to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GREEN)));
    }

    public static void error(CommandSource to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.RED)));
    }
}
