package su.nuv.radio.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Changes an open container's title in place. Bukkit only offers a legacy-string title update,
 * which drops fonts (and the whole menu is drawn with fonts), and reopening a container moves the
 * player's mouse to the screen centre. So this sends the vanilla "open screen" packet for the same
 * container id, which the client applies without closing the screen, then re-syncs the contents.
 *
 * <p>Reflection over Paper's Mojang-mapped server classes; when anything is missing it reports
 * {@code false} and the caller reopens the menu instead.
 */
public final class TitleUpdater {

    private final Logger logger;
    private boolean ready;
    private Method getHandle;
    private Field containerMenuField;
    private Field containerIdField;
    private Method getMenuType;
    private Constructor<?> packetConstructor;
    private Method asVanilla;
    private Field connectionField;
    private Method send;
    private Method sendAllData;

    public TitleUpdater(Logger logger) {
        this.logger = logger;
        try {
            final ClassLoader loader = org.bukkit.Bukkit.getServer().getClass().getClassLoader();
            final Class<?> craftPlayer = Class.forName(org.bukkit.Bukkit.getServer().getClass().getPackageName()
                    + ".entity.CraftPlayer", true, loader);
            this.getHandle = craftPlayer.getMethod("getHandle");
            final Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer", true, loader);
            final Class<?> player = Class.forName("net.minecraft.world.entity.player.Player", true, loader);
            final Class<?> menu = Class.forName("net.minecraft.world.inventory.AbstractContainerMenu", true, loader);
            final Class<?> menuType = Class.forName("net.minecraft.world.inventory.MenuType", true, loader);
            final Class<?> vanillaComponent = Class.forName("net.minecraft.network.chat.Component", true, loader);
            final Class<?> packet = Class.forName("net.minecraft.network.protocol.game.ClientboundOpenScreenPacket",
                    true, loader);
            final Class<?> adventure = Class.forName("io.papermc.paper.adventure.PaperAdventure", true, loader);
            this.containerMenuField = player.getField("containerMenu");
            this.containerIdField = menu.getField("containerId");
            this.getMenuType = menu.getMethod("getType");
            this.packetConstructor = packet.getConstructor(int.class, menuType, vanillaComponent);
            this.asVanilla = adventure.getMethod("asVanilla", Component.class);
            this.connectionField = serverPlayer.getField("connection");
            final Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet", true, loader);
            this.send = this.connectionField.getType().getMethod("send", packetInterface);
            this.sendAllData = menu.getMethod("sendAllDataToRemote");
            this.ready = true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            logger.log(Level.WARNING, "In-place title updates unavailable; menus will reopen instead", error);
        }
    }

    /** Sets the title of the player's open container. {@code false} when it could not. */
    public boolean update(Player player, Component title) {
        if (!this.ready) {
            return false;
        }
        try {
            final Object handle = this.getHandle.invoke(player);
            final Object menu = this.containerMenuField.get(handle);
            final int id = this.containerIdField.getInt(menu);
            if (id == 0) {
                return false;
            }
            final Object type = this.getMenuType.invoke(menu);
            final Object packet = this.packetConstructor.newInstance(id, type, this.asVanilla.invoke(null, title));
            this.send.invoke(this.connectionField.get(handle), packet);
            this.sendAllData.invoke(menu);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            this.ready = false;
            this.logger.log(Level.WARNING, "In-place title update failed; menus will reopen instead", error);
            return false;
        }
    }
}
