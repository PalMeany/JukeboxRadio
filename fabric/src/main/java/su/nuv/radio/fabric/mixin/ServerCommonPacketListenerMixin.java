package su.nuv.radio.fabric.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.nuv.radio.fabric.RadioHooks;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerCommonPacketListenerMixin {
    @Shadow
    protected abstract GameProfile playerProfile();

    @Inject(method = "handleCustomClickAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"),
            cancellable = true)
    private void jukeboxradio$dialogButton(ServerboundCustomClickActionPacket packet, CallbackInfo callback) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game
                && RadioHooks.dialogButton(game.player, packet.id(), packet.payload())) {
            callback.cancel();
        }
    }

    @Inject(method = "handleResourcePackResponse", at = @At("TAIL"))
    private void jukeboxradio$packStatus(ServerboundResourcePackPacket packet, CallbackInfo callback) {
        final GameProfile profile = this.playerProfile();
        RadioHooks.packStatus(profile.id(), profile.name(), packet.id(), packet.action());
    }
}
