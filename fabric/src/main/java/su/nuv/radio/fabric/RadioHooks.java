package su.nuv.radio.fabric;

import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import su.nuv.radio.platform.PackStatus;

import java.util.Optional;
import java.util.UUID;

/** Entry points for the mixins, which run before Fabric API has an event for these packets. */
public final class RadioHooks {

    private RadioHooks() {
    }

    public static boolean dialogButton(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        final FabricPlatform platform = RadioMod.platform();
        return platform != null && platform.dialogs().handle(player, id, payload);
    }

    public static void packStatus(UUID player, String name, UUID packId, ServerboundResourcePackPacket.Action action) {
        if (RadioMod.radio() == null) {
            return;
        }
        final PackStatus status = switch (action) {
            case SUCCESSFULLY_LOADED -> PackStatus.LOADED;
            case DECLINED -> PackStatus.DECLINED;
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> PackStatus.FAILED;
            default -> PackStatus.LOADING;
        };
        RadioMod.radio().packStatus(player, name, packId, status);
    }
}
