package su.nuv.radio.platform;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * A native dialog: a title, a body text, inputs and buttons. Escape closes it without calling any
 * button. {@code columns} lays the buttons out in a grid; 0 makes it a yes/no confirmation, which
 * takes exactly two buttons.
 */
public record DialogSpec(Component title, Component body, int bodyWidth, List<Input> inputs, List<Button> buttons,
                         int columns) {

    public sealed interface Input permits TextInput, NumberInput {

        String key();
    }

    public record TextInput(String key, Component label, int width, int maxLength, String initial) implements Input {
    }

    /** A slider from {@code min} to {@code max}; {@code labelFormat} is a vanilla format like {@code "%s: %s%%"}. */
    public record NumberInput(String key, Component label, float min, float max, float step, float initial, int width,
                              String labelFormat) implements Input {
    }

    /** {@code onClick} runs on the server thread with what the player entered. */
    public record Button(Component label, int width, Consumer<Response> onClick) {
    }

    public interface Response {

        String text(String key);

        Float number(String key);
    }
}
