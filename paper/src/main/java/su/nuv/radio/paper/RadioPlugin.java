package su.nuv.radio.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import su.nuv.radio.Radio;
import su.nuv.radio.voice.VoiceService;

/** The Paper entry point: builds the platform, hands it to {@link Radio} and forwards Bukkit events. */
public final class RadioPlugin extends JavaPlugin {

    private Radio radio;

    @Override
    public void onEnable() {
        final PaperPlatform platform = new PaperPlatform(this);
        final VoiceService voice = new VoiceService(this.getLogger(),
                () -> this.radio == null ? "Радио" : this.radio.config().voiceCategoryName());
        this.radio = new Radio(platform, voice);
        this.radio.loadConfig();
        boolean voiceOk;
        try {
            voiceOk = Integrations.registerVoice(voice);
        } catch (NoClassDefFoundError missing) {
            voiceOk = false;
        }
        this.radio.enable(voiceOk);

        Bukkit.getPluginManager().registerEvents(new PaperListener(this.radio, platform), this);
        final PluginCommand command = this.getCommand("radio");
        if (command != null) {
            final PaperCommand executor = new PaperCommand(this.radio, platform);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        Bukkit.getOnlinePlayers().forEach(player -> this.radio.pack().send(platform.wrap(player)));
    }

    @Override
    public void onDisable() {
        if (this.radio != null) {
            this.radio.disable();
        }
    }
}
