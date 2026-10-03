package su.nuv.radio.menu.pack;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import su.nuv.radio.platform.PackOffer;
import su.nuv.radio.platform.PackStatus;
import su.nuv.radio.platform.RadioPlatform;
import su.nuv.radio.platform.RadioPlayer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gets the radio pack to players: served by a tiny built-in HTTP server (or from an external URL,
 * or left to the owner's own pack) and pushed as an extra pack, so it stacks on top of any server
 * pack instead of replacing it. When to push it (the configuration phase where the platform has
 * one, or right after joining) is the platform's call.
 */
public final class PackDelivery {

    public enum Mode {
        /** Built-in HTTP server on {@code port}. */
        SELF_HOST,
        /** The owner uploads the zip and gives its URL. */
        EXTERNAL,
        /** The owner merges the zip into their own pack; nothing is sent. */
        NONE
    }

    public enum State {
        UNKNOWN, LOADING, LOADED, DECLINED, FAILED
    }

    public record Settings(Mode mode, String bindIp, int port, String publicAddress, String externalUrl,
                           boolean required, String prompt) {
    }

    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("jukeboxradio:menu".getBytes(StandardCharsets.UTF_8));

    private final RadioPlatform platform;
    private final Settings settings;
    private final PackBuilder.Built pack;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private HttpServer server;
    private URI uri;

    public PackDelivery(RadioPlatform platform, Settings settings, PackBuilder.Built pack) {
        this.platform = platform;
        this.settings = settings;
        this.pack = pack;
    }

    public void start() {
        switch (this.settings.mode()) {
            case SELF_HOST -> this.startServer();
            case EXTERNAL -> {
                if (this.settings.externalUrl() == null || this.settings.externalUrl().isBlank()) {
                    this.platform.logger().warning("pack.mode is EXTERNAL but pack.external-url is empty: "
                            + "players will not get the radio pack");
                } else {
                    this.uri = URI.create(this.settings.externalUrl());
                }
            }
            case NONE -> this.platform.logger().info("pack.mode is NONE: merge " + this.platform.dataFolder()
                    .resolve("pack").resolve("jukeboxradio.zip") + " into your server pack (SHA-1 " + this.pack.sha1() + ")");
        }
    }

    private void startServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress(this.settings.bindIp(), this.settings.port()), 0);
            final String path = "/jukeboxradio-" + this.pack.sha1().substring(0, 12) + ".zip";
            this.server.createContext(path, exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, this.pack.zip().length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(this.pack.zip());
                }
            });
            this.server.setExecutor(null);
            this.server.start();
            String host = this.settings.publicAddress();
            if (host == null || host.isBlank() || host.equalsIgnoreCase("auto")) {
                final String ip = this.platform.serverIp();
                host = ip == null || ip.isBlank() ? "127.0.0.1" : ip;
                if (host.equals("127.0.0.1")) {
                    this.platform.logger().warning("pack.public-address is AUTO and server-ip is empty: players on "
                            + "other machines cannot download the radio pack. Set pack.public-address.");
                }
            }
            this.uri = URI.create("http://" + host + ":" + this.settings.port() + path);
            this.platform.logger().info("Radio pack served at " + this.uri);
        } catch (IOException error) {
            this.platform.logger().log(Level.SEVERE, "Could not start the radio pack server on port "
                    + this.settings.port(), error);
        }
    }

    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
        }
    }

    public State state(UUID player) {
        if (this.settings.mode() == Mode.NONE) {
            return State.LOADED;
        }
        return this.states.getOrDefault(player, State.UNKNOWN);
    }

    /** The pack to push, or empty when nothing is sent (mode NONE, or no URL). */
    public Optional<PackOffer> offer() {
        if (this.uri == null || this.settings.mode() == Mode.NONE) {
            return Optional.empty();
        }
        return Optional.of(new PackOffer(PACK_ID, this.uri, this.pack.sha1(), this.settings.required(),
                Component.text(this.settings.prompt())));
    }

    /** Sends the pack (again), e.g. when a player opens the menu without it. */
    public void send(RadioPlayer player) {
        this.offer().ifPresent(offer -> {
            this.states.put(player.uuid(), State.LOADING);
            player.sendPack(offer);
        });
    }

    /** A player joined: push the pack unless the configuration phase already did. */
    public void joined(RadioPlayer player) {
        if (this.states.getOrDefault(player.uuid(), State.UNKNOWN) != State.UNKNOWN) {
            return;
        }
        this.platform.later(20L, () -> {
            if (player.isOnline()) {
                this.send(player);
            }
        });
    }

    /** The platform pushed the pack itself (e.g. in the configuration phase) for this player. */
    public void sending(UUID player) {
        this.states.put(player, State.LOADING);
    }

    public void status(UUID player, String name, UUID packId, PackStatus status) {
        if (!PACK_ID.equals(packId)) {
            return;
        }
        final State state = switch (status) {
            case LOADED -> State.LOADED;
            case DECLINED -> State.DECLINED;
            case FAILED -> State.FAILED;
            case LOADING -> State.LOADING;
        };
        this.states.put(player, state);
        if (state == State.FAILED) {
            this.platform.logger().warning("Radio pack failed for " + name + " (" + this.uri + ")");
        }
    }

    public void quit(UUID player) {
        this.states.remove(player);
    }
}
