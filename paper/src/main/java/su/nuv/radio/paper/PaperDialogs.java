package su.nuv.radio.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import su.nuv.radio.platform.DialogSpec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** {@link DialogSpec} as a Paper dialog with click callbacks. */
final class PaperDialogs {

    private PaperDialogs() {
    }

    static void show(PaperPlatform platform, Player player, DialogSpec spec) {
        final ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        final List<DialogInput> inputs = new ArrayList<>();
        for (DialogSpec.Input input : spec.inputs()) {
            switch (input) {
                case DialogSpec.TextInput text -> inputs.add(DialogInput.text(text.key(), text.label())
                        .width(text.width())
                        .maxLength(text.maxLength())
                        .initial(text.initial() == null ? "" : text.initial())
                        .build());
                case DialogSpec.NumberInput number -> inputs.add(DialogInput.numberRange(number.key(), number.label(),
                                number.min(), number.max())
                        .step(number.step())
                        .initial(number.initial())
                        .width(number.width())
                        .labelFormat(number.labelFormat())
                        .build());
            }
        }
        final List<ActionButton> buttons = new ArrayList<>();
        for (DialogSpec.Button button : spec.buttons()) {
            buttons.add(ActionButton.builder(button.label())
                    .width(button.width())
                    .action(DialogAction.customClick((response, audience) -> {
                        final DialogSpec.Response answer = new DialogSpec.Response() {
                            @Override
                            public String text(String key) {
                                return response.getText(key);
                            }

                            @Override
                            public Float number(String key) {
                                return response.getFloat(key);
                            }
                        };
                        platform.sync(() -> button.onClick().accept(answer));
                    }, once))
                    .build());
        }
        final DialogBase base = DialogBase.builder(spec.title())
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(spec.body(), spec.bodyWidth())))
                .inputs(inputs)
                .build();
        final DialogType type = spec.columns() == 0 && buttons.size() == 2
                ? DialogType.confirmation(buttons.get(0), buttons.get(1))
                : DialogType.multiAction(buttons).columns(spec.columns()).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }
}
