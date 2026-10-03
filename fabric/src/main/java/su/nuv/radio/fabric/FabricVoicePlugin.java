package su.nuv.radio.fabric;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.fabricmc.loader.api.FabricLoader;
import su.nuv.radio.voice.VoiceService;


/**
 * Simple Voice Chat creates this from the {@code voicechat} entrypoint; it hands everything to the
 * one {@link VoiceService} the radio uses.
 */
public final class FabricVoicePlugin implements VoicechatPlugin {

    private static VoiceService voice;

    static synchronized VoiceService voice() {
        if (voice == null) {
            voice = new VoiceService(FabricPlatform.LOGGER, () -> {
                final var radio = RadioMod.radio();
                return radio == null || radio.config() == null ? "Радио" : radio.config().voiceCategoryName();
            });
        }
        return voice;
    }

    static boolean installed() {
        return FabricLoader.getInstance().isModLoaded("voicechat");
    }

    @Override
    public String getPluginId() {
        return voice().getPluginId();
    }

    @Override
    public void initialize(VoicechatApi api) {
        voice().initialize(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        voice().registerEvents(registration);
    }
}
