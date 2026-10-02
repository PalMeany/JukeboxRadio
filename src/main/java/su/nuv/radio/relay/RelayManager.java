package su.nuv.radio.relay;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import su.nuv.radio.config.Messages;
import su.nuv.radio.config.RadioConfig;
import su.nuv.radio.station.PlaybackState;
import su.nuv.radio.station.RadioStation;
import su.nuv.radio.station.StationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Note blocks as relays of a jukebox. Each jukebox gets a link code; a note block given that code
 * plays the same audio, at its own volume, as long as it is within {@code range} of the jukebox or
 * of another active relay of the same jukebox. Runs on the server thread.
 */
public final class RelayManager {

    private final Plugin plugin;
    private final RelayStore store;
    private final StationManager stations;
    private final RadioConfig.RelaySettings settings;
    private final float hearDistance;
    /** One writer thread keeps saves in order. */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("jukeboxradio-links").daemon().factory());

    public RelayManager(Plugin plugin, RelayStore store, StationManager stations, RadioConfig.RelaySettings settings,
                        float hearDistance) {
        this.plugin = plugin;
        this.store = store;
        this.stations = stations;
        this.settings = settings;
        this.hearDistance = hearDistance;
    }

    public void enable() {
        this.store.load();
        this.stations.onCreate(this::attach);
        // the notes show which blocks are playing; every two seconds is enough to see it
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::sparkle, 40L, 40L);
    }

    public void shutdown() {
        this.writer.shutdown();
        try {
            this.writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        this.store.write(this.store.snapshot());
    }

    public boolean enabled() {
        return this.settings.enabled();
    }

    // ------------------------------------------------------------------ jukeboxes

    /** The jukebox's code, created on first use. */
    public String codeOf(BlockPos jukebox) {
        final Optional<String> known = this.store.codeOf(jukebox);
        if (known.isPresent()) {
            return known.get();
        }
        final String code = LinkCodes.generate(this.store::codeTaken);
        this.store.putJukebox(code, jukebox);
        this.save();
        return code;
    }

    private void attach(RadioStation station) {
        if (!this.settings.enabled()) {
            return;
        }
        final String code = this.codeOf(BlockPos.of(station.key()));
        station.setLinkCode(code);
        this.refresh(code);
    }

    /** A jukebox was broken: its code goes, its relays fall silent but keep the code. */
    public void jukeboxGone(BlockPos jukebox) {
        if (this.store.removeJukebox(jukebox).isPresent()) {
            this.save();
        }
    }

    // ------------------------------------------------------------------ network

    /** The relays of {@code code} in reach of its jukebox, with their volumes. */
    private Map<BlockPos, Integer> activeRelays(String code) {
        final Optional<BlockPos> jukebox = this.store.jukeboxOf(code);
        if (jukebox.isEmpty()) {
            return Map.of();
        }
        final Map<BlockPos, RelayStore.Relay> linked = this.store.relaysOf(code);
        final Set<BlockPos> active = RelayNetwork.active(jukebox.get(), linked.keySet(), this.settings.range());
        final Map<BlockPos, Integer> out = new HashMap<>();
        for (BlockPos pos : active) {
            out.put(pos, linked.get(pos).volume());
        }
        return out;
    }

    /** Re-applies {@code code}'s relays to its station, if the station is loaded. */
    private void refresh(String code) {
        this.store.jukeboxOf(code)
                .flatMap(jukebox -> this.stations.get(jukebox.stationKey()))
                .ifPresent(station -> station.setRelays(this.activeRelays(code), this.hearDistance));
    }

    public void relayGone(BlockPos pos) {
        this.store.removeRelay(pos).ifPresent(relay -> {
            this.refresh(relay.code());
            this.save();
        });
    }

    /** A piston moved these blocks one step; linked note blocks keep their link at the new spot. */
    public void relaysMoved(List<BlockPos> moved, int dx, int dy, int dz) {
        final List<String> codes = this.store.shift(moved, dx, dy, dz);
        if (!codes.isEmpty()) {
            codes.stream().distinct().forEach(this::refresh);
            this.save();
        }
    }

    public boolean isRelay(BlockPos pos) {
        return this.store.relay(pos).isPresent();
    }

    // ------------------------------------------------------------------ note block dialog

    /** Shift + use on a note block: shows its link, volume and reach, and lets the player change them. */
    public void openDialog(Player player, Block block) {
        final BlockPos pos = BlockPos.of(block);
        final Optional<RelayStore.Relay> relay = this.store.relay(pos);
        final ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10))
                .build();
        final List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(Component.text(relay.isPresent() ? "Сохранить" : "Подключить",
                        NamedTextColor.GREEN))
                .width(100)
                .action(DialogAction.customClick((response, audience) -> {
                    final String code = response.getText("code");
                    final Float volume = response.getFloat("volume");
                    this.sync(() -> this.link(player, pos, code, volume == null ? 100 : Math.round(volume)));
                }, once))
                .build());
        if (relay.isPresent()) {
            buttons.add(ActionButton.builder(Component.text("Отключить", NamedTextColor.RED))
                    .width(100)
                    .action(DialogAction.customClick((response, audience) -> this.sync(() -> this.unlink(player, pos)),
                            once))
                    .build());
        }
        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Нотный блок-повторитель"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(Component.text(this.status(pos)), 300)))
                        .inputs(List.of(
                                DialogInput.text("code", Component.text("Код связи проигрывателя"))
                                        .width(300)
                                        .maxLength(12)
                                        .initial(relay.map(RelayStore.Relay::code).orElse(""))
                                        .build(),
                                DialogInput.numberRange("volume", Component.text("Громкость"), 0f, 100f)
                                        .step(5f)
                                        .initial((float) relay.map(RelayStore.Relay::volume).orElse(100))
                                        .width(300)
                                        .labelFormat("%s: %s%%")
                                        .build()))
                        .build())
                .type(DialogType.multiAction(buttons).columns(2).build()));
        player.showDialog(dialog);
    }

    /** What the dialog says about this note block right now. */
    private String status(BlockPos pos) {
        final Optional<RelayStore.Relay> relay = this.store.relay(pos);
        if (relay.isEmpty()) {
            return "Не подключён. Введите код связи проигрывателя: он в меню проигрывателя, в подсказке вкладки "
                    + "«Проигрыватель», или по команде /radio code. Нотный блок будет играть то же, что проигрыватель.";
        }
        final String code = relay.get().code();
        final Optional<BlockPos> jukebox = this.store.jukeboxOf(code);
        if (jukebox.isEmpty()) {
            return "Код " + code + ": проигрыватель не найден — его сломали. Введите код другого проигрывателя "
                    + "или отключите блок.";
        }
        final Map<BlockPos, Integer> active = this.activeRelays(code);
        if (active.containsKey(pos)) {
            final String playing = this.stations.get(jukebox.get().stationKey())
                    .filter(station -> station.state() == PlaybackState.PLAYING && station.current() != null)
                    .map(station -> "Сейчас играет: " + station.current().meta().artistLine() + " — "
                            + station.current().meta().title() + ".")
                    .orElse("Проигрыватель сейчас молчит.");
            return "Подключён к проигрывателю " + code + " (" + jukebox.get().coordinates() + "). В сети "
                    + active.size() + " нотн. блок(ов). " + playing;
        }
        final double gap = RelayNetwork.gap(pos, jukebox.get(), active.keySet());
        if (Double.isInfinite(gap)) {
            return "Код " + code + ": проигрыватель в другом мире, отсюда не достать.";
        }
        return "Код " + code + ": вне зоны. До проигрывателя или ближайшего работающего повторителя "
                + Math.round(gap) + " бл., а нужно не дальше " + Math.round(this.settings.range())
                + ". Поставьте между ними ещё один нотный блок с этим кодом.";
    }

    private void link(Player player, BlockPos pos, String typed, int volume) {
        if (!player.isOnline() || !this.stillNoteBlock(pos)) {
            return;
        }
        final Optional<String> code = LinkCodes.normalize(typed);
        if (code.isEmpty()) {
            Messages.error(player, "Код связи — " + LinkCodes.LENGTH + " букв и цифр, например K7Q2P.");
            return;
        }
        final Optional<BlockPos> jukebox = this.store.jukeboxOf(code.get());
        if (jukebox.isEmpty() || !this.stillJukebox(jukebox.get())) {
            Messages.error(player, "Проигрыватель с кодом " + code.get() + " не найден.");
            return;
        }
        final Optional<RelayStore.Relay> before = this.store.relay(pos);
        final boolean sameCode = before.isPresent() && before.get().code().equals(code.get());
        if (!sameCode) {
            final String problem = this.whyNot(pos, code.get(), jukebox.get());
            if (problem != null) {
                Messages.error(player, problem);
                return;
            }
        }
        this.store.putRelay(pos, code.get(), volume);
        this.refresh(code.get());
        before.filter(old -> !old.code().equals(code.get())).ifPresent(old -> this.refresh(old.code()));
        this.save();
        if (sameCode) {
            Messages.ok(player, "Громкость нотного блока: " + RelayStore.clampVolume(volume) + "%.");
        } else {
            Messages.ok(player, "Нотный блок подключён к проигрывателю " + code.get() + " ("
                    + jukebox.get().coordinates() + ").");
        }
        pos.center().ifPresent(center -> center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f));
    }

    /** Why the note block cannot join {@code code}'s network, or null when it can. */
    private String whyNot(BlockPos pos, String code, BlockPos jukebox) {
        if (!pos.world().equals(jukebox.world())) {
            return "Проигрыватель в другом мире.";
        }
        final Map<BlockPos, RelayStore.Relay> linked = this.store.relaysOf(code);
        linked.remove(pos);
        if (linked.size() >= this.settings.maxPerJukebox()) {
            return "К этому проигрывателю уже подключено " + linked.size() + " нотных блоков — это максимум.";
        }
        final Set<BlockPos> active = RelayNetwork.active(jukebox, linked.keySet(), this.settings.range());
        final double gap = RelayNetwork.gap(pos, jukebox, active);
        if (gap > this.settings.range()) {
            return "Слишком далеко: до проигрывателя или ближайшего работающего повторителя " + Math.round(gap)
                    + " бл., а можно не дальше " + Math.round(this.settings.range())
                    + ". Поставьте повторитель поближе, он продлит зону.";
        }
        return null;
    }

    private void unlink(Player player, BlockPos pos) {
        if (!player.isOnline()) {
            return;
        }
        this.relayGone(pos);
        Messages.ok(player, "Нотный блок отключён от радио.");
    }

    private boolean stillNoteBlock(BlockPos pos) {
        return pos.block().map(block -> block.getType() == Material.NOTE_BLOCK).orElse(false);
    }

    private boolean stillJukebox(BlockPos pos) {
        return pos.block().map(block -> block.getType() == Material.JUKEBOX).orElse(false);
    }

    // ------------------------------------------------------------------ plumbing

    /** Floating notes over the relays of every playing station. */
    private void sparkle() {
        for (RadioStation station : this.stations.all()) {
            if (station.state() != PlaybackState.PLAYING) {
                continue;
            }
            for (BlockPos pos : station.relayPositions()) {
                pos.center().ifPresent(center -> {
                    if (center.getWorld().isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                        final Location above = center.add(0, 0.7, 0);
                        // for NOTE, a count of 0 turns offsetX into the note colour
                        center.getWorld().spawnParticle(Particle.NOTE, above, 0,
                                ThreadLocalRandom.current().nextInt(25) / 24.0, 0, 0, 1);
                    }
                });
            }
        }
    }

    private void save() {
        final String text = this.store.snapshot();
        if (!this.writer.isShutdown()) {
            this.writer.execute(() -> this.store.write(text));
        }
    }

    private void sync(Runnable task) {
        if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(this.plugin, task);
        }
    }
}
