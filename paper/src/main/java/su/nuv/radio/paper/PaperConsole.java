package su.nuv.radio.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import su.nuv.radio.platform.CommandSource;
import su.nuv.radio.platform.RadioPlayer;

import java.util.Optional;

/** The console or a command block running {@code /radio}. */
record PaperConsole(CommandSender sender) implements CommandSource {

    @Override
    public String name() {
        return this.sender.getName();
    }

    @Override
    public void sendMessage(Component message) {
        this.sender.sendMessage(message);
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.sender.hasPermission(permission);
    }

    @Override
    public Optional<RadioPlayer> player() {
        return Optional.empty();
    }
}
