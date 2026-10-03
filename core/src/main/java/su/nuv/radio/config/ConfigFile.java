package su.nuv.radio.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** config.yml in the data folder, written from the bundled default on first start. */
public final class ConfigFile {

    private ConfigFile() {
    }

    public static YamlTree load(Path dataFolder, Logger logger) {
        final Path file = dataFolder.resolve("config.yml");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(dataFolder);
                try (InputStream bundled = ConfigFile.class.getResourceAsStream("/config.yml")) {
                    if (bundled != null) {
                        Files.copy(bundled, file);
                    }
                }
            }
            return YamlTree.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            logger.log(Level.SEVERE, "Could not read " + file + "; using defaults", error);
            return YamlTree.parse("");
        }
    }
}
