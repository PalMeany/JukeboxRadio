package su.nuv.radio.fabric;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import su.nuv.radio.Radio;

import java.lang.reflect.Method;

/**
 * Permission nodes through fabric-permissions-api when a permissions mod (LuckPerms) provides it;
 * otherwise everyone may use the radio and operators may administer it.
 */
final class Permissions {

    private static final Method ENTITY_CHECK;
    private static final Method SOURCE_CHECK;

    static {
        Method entity = null;
        Method source = null;
        try {
            final Class<?> api = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            entity = api.getMethod("check", Entity.class, String.class, boolean.class);
            source = api.getMethod("check", net.minecraft.commands.SharedSuggestionProvider.class, String.class, boolean.class);
        } catch (ReflectiveOperationException | LinkageError missing) {
            // no permissions mod: defaults below
        }
        ENTITY_CHECK = entity;
        SOURCE_CHECK = source;
    }

    private Permissions() {
    }

    private static boolean fallback(String permission, boolean operator) {
        return !Radio.ADMIN.equals(permission) || operator;
    }

    static boolean check(Entity entity, String permission) {
        final boolean operator = entity.createCommandSourceStackForNameResolution(
                (net.minecraft.server.level.ServerLevel) entity.level()).permissions()
                .hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        if (ENTITY_CHECK != null) {
            try {
                return (boolean) ENTITY_CHECK.invoke(null, entity, permission, fallback(permission, operator));
            } catch (ReflectiveOperationException | RuntimeException error) {
                return fallback(permission, operator);
            }
        }
        return fallback(permission, operator);
    }

    static boolean check(CommandSourceStack source, String permission) {
        final boolean operator = source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        if (SOURCE_CHECK != null) {
            try {
                return (boolean) SOURCE_CHECK.invoke(null, source, permission, fallback(permission, operator));
            } catch (ReflectiveOperationException | RuntimeException error) {
                return fallback(permission, operator);
            }
        }
        return fallback(permission, operator);
    }
}
