package su.nuv.radio.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import su.nuv.radio.Radio;
import su.nuv.radio.platform.BlockKind;

/**
 * The Fabric entry point: builds the platform when the server starts and forwards Fabric events to
 * {@link Radio}. Server side only; players need Simple Voice Chat to hear it and get the menu pack
 * from the server.
 */
public final class RadioMod implements ModInitializer {

    private static FabricPlatform platform;
    private static Radio radio;

    static Radio radio() {
        return radio;
    }

    static FabricPlatform platform() {
        return platform;
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(RadioMod::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (platform != null) {
                platform.tick();
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                FabricCommand.register(dispatcher));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (radio == null || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)
                    || !(level instanceof ServerLevel serverLevel) || !player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            final BlockKind kind = FabricPlatform.kind(level.getBlockState(hit.getBlockPos()));
            if (kind == BlockKind.OTHER) {
                return InteractionResult.PASS;
            }
            final boolean handled = radio.use(platform.wrap(serverPlayer), platform.pos(serverLevel, hit.getBlockPos()),
                    kind, true, player.getMainHandItem().isEmpty());
            return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            final BlockKind kind = FabricPlatform.kind(state);
            if (radio != null && kind != BlockKind.OTHER && level instanceof ServerLevel serverLevel) {
                radio.blockGone(platform.pos(serverLevel, pos), kind);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (radio != null) {
                radio.joined(platform.wrap(handler.player));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (radio != null) {
                radio.quit(handler.player.getUUID());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (radio != null && entity instanceof ServerPlayer player) {
                radio.died(player.getUUID());
            }
        });
    }

    private static void start(MinecraftServer server) {
        platform = new FabricPlatform(server);
        radio = new Radio(platform, FabricVoicePlugin.voice());
        radio.loadConfig();
        radio.enable(FabricVoicePlugin.installed());
        platform.watch(radio);
    }

    private static void stop() {
        if (radio != null) {
            radio.disable();
        }
        if (platform != null) {
            platform.disable();
        }
        radio = null;
        platform = null;
    }
}
