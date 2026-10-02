package su.nuv.radio.menu.pack;

import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.resource.ResourcePackCallback;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Gets the radio pack to players: served by a tiny built-in HTTP server (or from an external URL,
 * or left to the owner's own pack) and pushed during the configuration phase as an extra pack, so
 * it stacks on top of any server pack instead of replacing it and loads before the player spawns.
 */
public final class PackDelivery implements Listener {

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

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("jukeboxradio:menu".getBytes(StandardCharsets.UTF_8));
    /** How long a joining player may sit on the loading screen before being let in anyway. */
    private static final long CONFIGURE_WAIT_SECONDS = 180;

    private final Plugin plugin;
    private final Settings settings;
    private final PackBuilder.Built pack;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private HttpServer server;
    private URI uri;

    public PackDelivery(Plugin plugin, Settings settings, PackBuilder.Built pack) {
        this.plugin = plugin;
        this.settings = settings;
        this.pack = pack;
    }

    public void start() {
        switch (this.settings.mode()) {
            case SELF_HOST -> this.startServer();
            case EXTERNAL -> {
                if (this.settings.externalUrl() == null || this.settings.externalUrl().isBlank()) {
                    this.plugin.getLogger().warning("pack.mode is EXTERNAL but pack.external-url is empty: "
                            + "players will not get the radio pack");
                } else {
                    this.uri = URI.create(this.settings.externalUrl());
                }
            }
            case NONE -> this.plugin.getLogger().info("pack.mode is NONE: merge plugins/JukeboxRadio/pack/"
                    + "jukeboxradio.zip into your server pack (SHA-1 " + this.pack.sha1() + ")");
        }
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.send(player);
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
                host = Bukkit.getIp().isBlank() ? "127.0.0.1" : Bukkit.getIp();
                if (host.equals("127.0.0.1")) {
                    this.plugin.getLogger().warning("pack.public-address is AUTO and server-ip is empty: players on "
                            + "other machines cannot download the radio pack. Set pack.public-address.");
                }
            }
            this.uri = URI.create("http://" + host + ":" + this.settings.port() + path);
            this.plugin.getLogger().info("Radio pack served at " + this.uri);
        } catch (IOException error) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not start the radio pack server on port "
                    + this.settings.port(), error);
        }
    }

    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
        }
    }

    public State state(Player player) {
        if (this.settings.mode() == Mode.NONE) {
            return State.LOADED;
        }
        return this.states.getOrDefault(player.getUniqueId(), State.UNKNOWN);
    }

    /** Sends the pack (again), e.g. when a player opens the menu without it. */
    public void send(Player player) {
        if (this.uri == null || this.settings.mode() == Mode.NONE) {
            return;
        }
        this.states.put(player.getUniqueId(), State.LOADING);
        player.sendResourcePacks(this.request(ResourcePackCallback.noOp()));
    }

    private ResourcePackRequest request(ResourcePackCallback callback) {
        return ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, this.uri, this.pack.sha1()))
                .replace(false)
                .required(this.settings.required())
                .prompt(Component.text(this.settings.prompt()))
                .callback(callback)
                .build();
    }

    /**
     * Sends the pack while the player is still in the configuration phase and holds the connection
     * there until the client has loaded it. A server pack reloads every client resource; done after
     * join, the player stood in the world frozen (and killable) behind the loading screen.
     */
    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (this.uri == null || this.settings.mode() == Mode.NONE) {
            return;
        }
        final UUID id = event.getConnection().getProfile().getId();
        if (id == null) {
            return;
        }
        final CompletableFuture<ResourcePackStatus> done = new CompletableFuture<>();
        this.states.put(id, State.LOADING);
        event.getConnection().getAudience().sendResourcePacks(this.request((packId, status, audience) -> {
            if (!status.intermediate()) {
                done.complete(status);
            }
        }));
        State state;
        try {
            state = stateOf(done.get(CONFIGURE_WAIT_SECONDS, TimeUnit.SECONDS));
        } catch (TimeoutException slow) {
            // let them in; the pack keeps loading and the status event reports it later
            state = State.LOADING;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            state = State.UNKNOWN;
        } catch (ExecutionException impossible) {
            state = State.FAILED;
        }
        this.states.put(id, state);
        if (state == State.FAILED) {
            this.plugin.getLogger().warning("Radio pack failed for " + event.getConnection().getProfile().getName()
                    + " (" + this.uri + ")");
        }
    }

    /** Fallback when the configuration phase did not deliver it, e.g. the event was skipped. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (this.states.getOrDefault(player.getUniqueId(), State.UNKNOWN) != State.UNKNOWN) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
                this.send(player);
            }
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        final State state = switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> State.LOADED;
            case DECLINED -> State.DECLINED;
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> State.FAILED;
            default -> State.LOADING;
        };
        this.states.put(event.getPlayer().getUniqueId(), state);
        if (state == State.FAILED) {
            this.plugin.getLogger().warning("Radio pack " + event.getStatus() + " for " + event.getPlayer().getName()
                    + " (" + this.uri + ")");
        }
    }

    private static State stateOf(ResourcePackStatus status) {
        return switch (status) {
            case SUCCESSFULLY_LOADED -> State.LOADED;
            case DECLINED -> State.DECLINED;
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> State.FAILED;
            default -> State.LOADING;
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.states.remove(event.getPlayer().getUniqueId());
    }
}
