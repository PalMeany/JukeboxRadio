package su.nuv.radio.fabric;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import su.nuv.radio.platform.DialogSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DialogSpec} as a vanilla dialog. Each button sends a custom click action with a one-time
 * id; {@link #handle} finds the button again and gives it the inputs from the payload.
 */
final class FabricDialogs {

    private static final String NAMESPACE = "jukeboxradio";
    private static final long LIFETIME_MILLIS = 10 * 60 * 1000L;

    private record Pending(UUID player, DialogSpec.Button button, List<String> siblings, long expires) {
    }

    private final Map<String, Pending> pending = new HashMap<>();

    void show(FabricPlatform platform, ServerPlayer player, DialogSpec spec) {
        final long now = System.currentTimeMillis();
        this.pending.values().removeIf(entry -> entry.expires() < now);
        final var registries = player.registryAccess();
        final List<Input> inputs = new ArrayList<>();
        for (DialogSpec.Input input : spec.inputs()) {
            switch (input) {
                case DialogSpec.TextInput text -> inputs.add(new Input(text.key(), new TextInput(text.width(),
                        Texts.vanilla(text.label(), registries), true, text.initial() == null ? "" : text.initial(),
                        text.maxLength(), Optional.empty())));
                case DialogSpec.NumberInput number -> inputs.add(new Input(number.key(), new NumberRangeInput(number.width(),
                        Texts.vanilla(number.label(), registries), number.labelFormat(),
                        new NumberRangeInput.RangeInfo(number.min(), number.max(), Optional.of(number.initial()),
                                Optional.of(number.step())))));
            }
        }
        final List<String> tokens = new ArrayList<>();
        final List<ActionButton> buttons = new ArrayList<>();
        for (DialogSpec.Button button : spec.buttons()) {
            final String token = UUID.randomUUID().toString();
            tokens.add(token);
            final Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, "dialog/" + token);
            buttons.add(new ActionButton(new CommonButtonData(Texts.vanilla(button.label(), registries), Optional.empty(),
                    button.width()), Optional.of(new CustomAll(id, Optional.empty()))));
        }
        for (int i = 0; i < tokens.size(); i++) {
            this.pending.put(tokens.get(i), new Pending(player.getUUID(), spec.buttons().get(i), tokens, now + LIFETIME_MILLIS));
        }
        final CommonDialogData common = new CommonDialogData(Texts.vanilla(spec.title(), registries), Optional.empty(),
                true, false, DialogAction.CLOSE,
                List.of(new PlainMessage(Texts.vanilla(spec.body(), registries), spec.bodyWidth())), inputs);
        final Dialog dialog = spec.columns() == 0 && buttons.size() == 2
                ? new ConfirmationDialog(common, buttons.get(0), buttons.get(1))
                : new MultiActionDialog(common, buttons, Optional.empty(), Math.max(1, spec.columns()));
        player.openDialog(Holder.direct(dialog));
    }

    /** Server thread: a custom click action arrived. True when it was one of ours. */
    boolean handle(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!NAMESPACE.equals(id.getNamespace()) || !id.getPath().startsWith("dialog/")) {
            return false;
        }
        final Pending entry = this.pending.get(id.getPath().substring("dialog/".length()));
        if (entry == null || !entry.player().equals(player.getUUID()) || entry.expires() < System.currentTimeMillis()) {
            return true;
        }
        entry.siblings().forEach(this.pending::remove);
        final CompoundTag values = payload.filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast)
                .orElseGet(CompoundTag::new);
        entry.button().onClick().accept(new DialogSpec.Response() {
            @Override
            public String text(String key) {
                return values.getString(key).orElse(null);
            }

            @Override
            public Float number(String key) {
                return values.getFloat(key).orElse(null);
            }
        });
        return true;
    }
}
