package su.nuv.radio;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.Bukkit;
import su.nuv.radio.voice.VoiceService;


/**
 * Touches the optional plugins' classes. Kept apart so a missing plugin fails here with a
 * {@link NoClassDefFoundError} the caller catches, not while loading the main class.
 */
final class Integrations {

    private Integrations() {
    }

    static boolean registerVoice(VoiceService voice) {
        final BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            return false;
        }
        service.registerPlugin(voice);
        return true;
    }
}
