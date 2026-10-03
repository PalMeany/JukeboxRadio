package su.nuv.radio.paper;

import net.kyori.adventure.resource.ResourcePackCallback;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import su.nuv.radio.platform.DialogSpec;
import su.nuv.radio.platform.MenuView;
import su.nuv.radio.platform.PackOffer;
import su.nuv.radio.platform.RadioPlayer;
import su.nuv.radio.relay.BlockPos;

import java.util.Optional;
import java.util.UUID;

/** A Bukkit player as the radio sees them. */
final class PaperPlayer implements RadioPlayer {

    private final PaperPlatform platform;
    private final Player player;

    PaperPlayer(PaperPlatform platform, Player player) {
        this.platform = platform;
        this.player = player;
    }

    Player bukkit() {
        return this.player;
    }

    static ResourcePackRequest request(PackOffer offer, ResourcePackCallback callback) {
        return ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(offer.id(), offer.uri(), offer.sha1()))
                .replace(false)
                .required(offer.required())
                .prompt(offer.prompt())
                .callback(callback)
                .build();
    }

    @Override
    public UUID uuid() {
        return this.player.getUniqueId();
    }

    @Override
    public String name() {
        return this.player.getName();
    }

    @Override
    public boolean isOnline() {
        return this.player.isOnline();
    }

    @Override
    public void sendMessage(Component message) {
        this.player.sendMessage(message);
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.player.hasPermission(permission);
    }

    @Override
    public UUID world() {
        return this.player.getWorld().getUID();
    }

    @Override
    public double x() {
        return this.player.getLocation().getX();
    }

    @Override
    public double y() {
        return this.player.getLocation().getY();
    }

    @Override
    public double z() {
        return this.player.getLocation().getZ();
    }

    @Override
    public Optional<BlockPos> targetJukebox(int reach) {
        final Block looked = this.player.getTargetBlockExact(reach);
        if (looked == null || looked.getType() != Material.JUKEBOX) {
            return Optional.empty();
        }
        return Optional.of(PaperPlatform.pos(looked));
    }

    @Override
    public MenuView createMenu(MenuView.Listener listener) {
        return new PaperMenuView(this.platform, this.player, listener);
    }

    @Override
    public void closeScreen() {
        this.player.closeInventory();
    }

    @Override
    public void showDialog(DialogSpec dialog) {
        PaperDialogs.show(this.platform, this.player, dialog);
    }

    @Override
    public void sendPack(PackOffer offer) {
        this.player.sendResourcePacks(request(offer, ResourcePackCallback.noOp()));
    }
}
