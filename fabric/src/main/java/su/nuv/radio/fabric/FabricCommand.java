package su.nuv.radio.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import su.nuv.radio.Radio;
import su.nuv.radio.platform.CommandSource;
import su.nuv.radio.platform.RadioPlayer;

import java.util.List;
import java.util.Optional;

/** {@code /radio} and {@code /jukeboxradio} as Brigadier commands taking the rest of the line. */
final class FabricCommand {

    private FabricCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String label : List.of("radio", "jukeboxradio")) {
            dispatcher.register(Commands.literal(label)
                    .executes(context -> run(context, label, ""))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .suggests(suggestions())
                            .executes(context -> run(context, label, StringArgumentType.getString(context, "args")))));
        }
    }

    private static int run(CommandContext<CommandSourceStack> context, String label, String line) {
        final Radio radio = RadioMod.radio();
        if (radio == null) {
            return 0;
        }
        final String[] args = line.isBlank() ? new String[0] : line.strip().split("\\s+");
        radio.command().execute(source(context.getSource()), label, args);
        return 1;
    }

    private static SuggestionProvider<CommandSourceStack> suggestions() {
        return (context, builder) -> {
            final Radio radio = RadioMod.radio();
            if (radio == null) {
                return builder.buildFuture();
            }
            final String typed = builder.getRemaining();
            final String[] args = typed.split("\\s+", -1);
            final int start = builder.getStart() + typed.lastIndexOf(' ') + 1;
            final var offset = builder.createOffset(start);
            radio.command().complete(source(context.getSource()), args).forEach(offset::suggest);
            return offset.buildFuture();
        };
    }

    private static CommandSource source(CommandSourceStack stack) {
        if (stack.getEntity() instanceof ServerPlayer player && RadioMod.platform() != null) {
            return RadioMod.platform().wrap(player);
        }
        return new CommandSource() {
            @Override
            public String name() {
                return stack.getTextName();
            }

            @Override
            public void sendMessage(Component message) {
                stack.sendSystemMessage(Texts.vanilla(message, stack.registryAccess()));
            }

            @Override
            public boolean hasPermission(String permission) {
                return Permissions.check(stack, permission);
            }

            @Override
            public Optional<RadioPlayer> player() {
                return Optional.empty();
            }
        };
    }
}
