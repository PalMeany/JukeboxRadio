package su.nuv.radio.fabric;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Adventure text (what the radio core builds) to vanilla components, through their shared JSON form. */
final class Texts {

    private Texts() {
    }

    static Component vanilla(net.kyori.adventure.text.Component text, HolderLookup.Provider registries) {
        if (text == null) {
            return Component.empty();
        }
        final JsonElement json = GsonComponentSerializer.gson().serializeToTree(text);
        return ComponentSerialization.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json)
                .result()
                .or(() -> ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).result())
                .orElse(Component.empty());
    }
}
