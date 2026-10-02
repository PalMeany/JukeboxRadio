package su.nuv.radio.relay;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * links.yml: each jukebox's link code and each relay note block's code and volume. Touched only on
 * the server thread; {@link #snapshot()} hands the text to whoever writes it to disk.
 */
public final class RelayStore {

    /** A note block linked to a jukebox code, with its own volume, 0..100. */
    public record Relay(String code, int volume) {
    }

    private final Path file;
    private final Logger logger;
    private final Map<String, BlockPos> jukeboxByCode = new HashMap<>();
    private final Map<BlockPos, String> codeByJukebox = new HashMap<>();
    private final Map<BlockPos, Relay> relays = new HashMap<>();

    public RelayStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load() {
        if (!Files.exists(this.file)) {
            return;
        }
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(this.file));
        } catch (IOException | InvalidConfigurationException error) {
            this.logger.log(Level.SEVERE, "Could not read " + this.file + "; relays start empty", error);
            return;
        }
        final ConfigurationSection codes = yaml.getConfigurationSection("jukeboxes");
        if (codes != null) {
            for (String code : codes.getKeys(false)) {
                BlockPos.parse(codes.getString(code)).ifPresent(pos -> this.putJukebox(code, pos));
            }
        }
        final ConfigurationSection linked = yaml.getConfigurationSection("relays");
        if (linked != null) {
            for (String key : linked.getKeys(false)) {
                final Optional<BlockPos> pos = BlockPos.parse(key);
                final String code = linked.getString(key + ".code");
                if (pos.isPresent() && code != null) {
                    this.relays.put(pos.get(), new Relay(code, clampVolume(linked.getInt(key + ".volume", 100))));
                }
            }
        }
    }

    /** The file's text for the current state; build it on the server thread, write it anywhere. */
    public String snapshot() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "JukeboxRadio: link codes of jukeboxes and the note blocks relaying them.",
                "Written by the plugin; edit only while the server is stopped."));
        this.jukeboxByCode.forEach((code, pos) -> yaml.set("jukeboxes." + code, pos.serialize()));
        this.relays.forEach((pos, relay) -> {
            yaml.set("relays." + pos.serialize() + ".code", relay.code());
            yaml.set("relays." + pos.serialize() + ".volume", relay.volume());
        });
        return yaml.saveToString();
    }

    /** Atomically replaces the file with {@code text}. */
    public void write(String text) {
        try {
            Files.createDirectories(this.file.getParent());
            final Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            Files.writeString(temp, text);
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            this.logger.log(Level.WARNING, "Could not save " + this.file, error);
        }
    }

    // ------------------------------------------------------------------ jukeboxes

    public Optional<String> codeOf(BlockPos jukebox) {
        return Optional.ofNullable(this.codeByJukebox.get(jukebox));
    }

    public Optional<BlockPos> jukeboxOf(String code) {
        return Optional.ofNullable(this.jukeboxByCode.get(code));
    }

    public boolean codeTaken(String code) {
        return this.jukeboxByCode.containsKey(code);
    }

    public void putJukebox(String code, BlockPos pos) {
        this.jukeboxByCode.put(code, pos);
        this.codeByJukebox.put(pos, code);
    }

    /** Forgets a jukebox's code. Its relays keep it, silent, in case the owner wants them back. */
    public Optional<String> removeJukebox(BlockPos pos) {
        final String code = this.codeByJukebox.remove(pos);
        if (code != null) {
            this.jukeboxByCode.remove(code);
        }
        return Optional.ofNullable(code);
    }

    // ------------------------------------------------------------------ relays

    public Optional<Relay> relay(BlockPos pos) {
        return Optional.ofNullable(this.relays.get(pos));
    }

    public void putRelay(BlockPos pos, String code, int volume) {
        this.relays.put(pos, new Relay(code, clampVolume(volume)));
    }

    public Optional<Relay> removeRelay(BlockPos pos) {
        return Optional.ofNullable(this.relays.remove(pos));
    }

    /** Every note block linked to {@code code}, reachable or not. */
    public Map<BlockPos, Relay> relaysOf(String code) {
        final Map<BlockPos, Relay> out = new HashMap<>();
        this.relays.forEach((pos, relay) -> {
            if (relay.code().equals(code)) {
                out.put(pos, relay);
            }
        });
        return out;
    }

    /** Moves the relays at {@code from} by one step, all at once (a piston pushes them together). */
    public List<String> shift(List<BlockPos> from, int dx, int dy, int dz) {
        final Map<BlockPos, Relay> moving = new HashMap<>();
        for (BlockPos pos : from) {
            final Relay relay = this.relays.remove(pos);
            if (relay != null) {
                moving.put(pos, relay);
            }
        }
        final List<String> codes = new ArrayList<>();
        moving.forEach((pos, relay) -> {
            this.relays.put(pos.offset(dx, dy, dz), relay);
            codes.add(relay.code());
        });
        return codes;
    }

    static int clampVolume(int volume) {
        return Math.max(0, Math.min(100, volume));
    }
}
