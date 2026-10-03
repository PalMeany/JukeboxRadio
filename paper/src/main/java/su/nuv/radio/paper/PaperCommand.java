package su.nuv.radio.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import su.nuv.radio.Radio;

import java.util.List;

/** {@code /radio} through Bukkit's command map. */
final class PaperCommand implements TabExecutor {

    private final Radio radio;
    private final PaperPlatform platform;

    PaperCommand(Radio radio, PaperPlatform platform) {
        this.radio = radio;
        this.platform = platform;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return this.radio.command().execute(this.platform.source(sender), label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return this.radio.command().complete(this.platform.source(sender), args);
    }
}
