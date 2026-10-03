package su.nuv.radio.relay;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Link codes: five characters from an alphabet without look-alikes (no 0/O, 1/I/L), so a code read
 * off the screen is typed right. Input may be in any case, with stray spaces or dashes.
 */
public final class LinkCodes {

    public static final int LENGTH = 5;
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkCodes() {
    }

    /** A fresh code that {@code taken} does not already hold. */
    public static String generate(Predicate<String> taken) {
        while (true) {
            final StringBuilder code = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            if (!taken.test(code.toString())) {
                return code.toString();
            }
        }
    }

    /** The canonical form of what a player typed, or empty when it cannot be a code. */
    public static Optional<String> normalize(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        final String code = typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != LENGTH) {
            return Optional.empty();
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(code);
    }
}
