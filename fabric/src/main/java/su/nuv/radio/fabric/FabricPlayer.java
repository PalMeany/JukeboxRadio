package su.nuv.radio.fabric;

import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import su.nuv.radio.platform.DialogSpec;
import su.nuv.radio.platform.MenuView;
import su.nuv.radio.platform.PackOffer;
import su.nuv.radio.platform.RadioPlayer;
import su.nuv.radio.relay.BlockPos;

import java.util.Optional;
import java.util.UUID;

/** A server player as the radio sees them. */
final class FabricPlayer implements RadioPlayer {

    private final FabricPlatform platform;
    private final ServerPlayer player;

    FabricPlayer(FabricPlatform platform, ServerPlayer player) {
        this.platform = platform;
        this.player = player;
    }

    ServerPlayer vanilla() {
        return this.player;
    }

    @Override
    public UUID uuid() {
        return this.player.getUUID();
    }

    @Override
    public String name() {
        return this.player.getGameProfile().name();
    }

    @Override
    public boolean isOnline() {
        return !this.player.hasDisconnected() && this.platform.server().getPlayerList().getPlayer(this.uuid()) == this.player;
    }

    @Override
    public void sendMessage(Component message) {
        this.player.sendSystemMessage(Texts.vanilla(message, this.player.registryAccess()));
    }

    @Override
    public boolean hasPermission(String permission) {
        return Permissions.check(this.player, permission);
    }

    @Override
    public UUID world() {
        return FabricPlatform.worldId(this.player.level());
    }

    @Override
    public double x() {
        return this.player.getX();
    }

    @Override
    public double y() {
        return this.player.getY();
    }

    @Override
    public double z() {
        return this.player.getZ();
    }

    @Override
    public Optional<BlockPos> targetJukebox(int reach) {
        final Vec3 eye = this.player.getEyePosition();
        final Vec3 end = eye.add(this.player.getLookAngle().scale(reach));
        final BlockHitResult hit = this.player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, this.player));
        if (hit.getType() != HitResult.Type.BLOCK || !this.player.level().getBlockState(hit.getBlockPos()).is(Blocks.JUKEBOX)) {
            return Optional.empty();
        }
        return Optional.of(this.platform.pos(this.player.level(), hit.getBlockPos()));
    }

    @Override
    public MenuView createMenu(MenuView.Listener listener) {
        return new FabricMenuView(this.player, listener);
    }

    @Override
    public void closeScreen() {
        this.player.closeContainer();
    }

    @Override
    public void showDialog(DialogSpec dialog) {
        this.platform.dialogs().show(this.platform, this.player, dialog);
    }

    @Override
    public void sendPack(PackOffer offer) {
        this.player.connection.send(new ClientboundResourcePackPushPacket(offer.id(), offer.uri().toString(), offer.sha1(),
                offer.required(), Optional.of(Texts.vanilla(offer.prompt(), this.player.registryAccess()))));
    }
}
