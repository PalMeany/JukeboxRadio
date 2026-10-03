package su.nuv.radio.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import su.nuv.radio.Radio;
import su.nuv.radio.catalog.model.ResolveResult;
import su.nuv.radio.catalog.model.TrackMeta;
import su.nuv.radio.config.Messages;
import su.nuv.radio.platform.BlockKind;
import su.nuv.radio.platform.CommandSource;
import su.nuv.radio.platform.RadioPlayer;
import su.nuv.radio.relay.BlockPos;
import su.nuv.radio.station.QueueEntry;
import su.nuv.radio.station.RadioStation;
import su.nuv.radio.station.StationKey;
import su.nuv.radio.util.Durations;
import su.nuv.radio.util.Http;
import su.nuv.radio.util.Links;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /radio}: the chat side of the radio, handy for pasting long links. Commands act on the
 * jukebox the player looks at, or the one they opened last.
 */
public final class RadioCommand {

    private static final List<String> SUBCOMMANDS = List.of("open", "play", "search", "skip", "pause", "stop",
            "volume", "status", "code", "stopall");

    private final Radio plugin;

    public RadioCommand(Radio plugin) {
        this.plugin = plugin;
    }

    /** Runs {@code /label args...}; always handles it (usage goes to chat). */
    public boolean execute(CommandSource original, String label, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("as") && original.hasPermission(Radio.ADMIN)) {
            // admin form: /radio as <player> <subcommand...> runs a subcommand for that player
            final Optional<RadioPlayer> target = this.plugin.platform().playerExact(args[1]);
            if (target.isEmpty()) {
                Messages.error(original, "Игрок не в сети.");
                return true;
            }
            return this.execute(target.get(), label, java.util.Arrays.copyOfRange(args, 2, args.length));
        }
        final CommandSource sender = original;
        if (args.length >= 2 && args[0].equalsIgnoreCase("click") && sender.player().isPresent()
                && sender.hasPermission(Radio.ADMIN)) {
            final RadioPlayer clicker = sender.player().get();
            // diagnostics: press a menu slot as if clicked (/radio as <player> click <slot> [right])
            try {
                this.plugin.menus().simulateClick(clicker, Integer.parseInt(args[1]),
                        args.length > 2 && args[2].equalsIgnoreCase("right"));
            } catch (NumberFormatException error) {
                Messages.error(sender, "Номер слота — число.");
            }
            return true;
        }
        final String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("stopall")) {
            if (!sender.hasPermission(Radio.ADMIN)) {
                Messages.error(sender, "Нет прав.");
                return true;
            }
            this.plugin.stations().all().forEach(RadioStation::stop);
            Messages.ok(sender, "Все радио остановлены.");
            return true;
        }
        if (sub.equals("open") && args.length >= 5 && sender.hasPermission(Radio.ADMIN)) {
            // admin/console form: /radio open <player> <x> <y> <z> opens that jukebox for the player
            final Optional<RadioPlayer> target = this.plugin.platform().playerExact(args[1]);
            if (target.isEmpty()) {
                Messages.error(sender, "Игрок не в сети.");
                return true;
            }
            try {
                final BlockPos pos = new BlockPos(target.get().world(), Integer.parseInt(args[2]),
                        Integer.parseInt(args[3]), Integer.parseInt(args[4]));
                if (this.plugin.platform().blockAt(pos) != BlockKind.JUKEBOX) {
                    Messages.error(sender, "Там нет проигрывателя.");
                    return true;
                }
                this.plugin.menus().open(target.get(), this.plugin.stations().getOrCreate(pos.stationKey()));
            } catch (NumberFormatException error) {
                Messages.error(sender, "Координаты — целые числа.");
            }
            return true;
        }
        if (sender.player().isEmpty()) {
            Messages.error(sender, "Команда только для игроков (кроме /radio stopall).");
            return true;
        }
        final RadioPlayer player = sender.player().get();
        if (!player.hasPermission(Radio.USE)) {
            Messages.error(player, "Нет прав пользоваться радио.");
            return true;
        }
        if (sub.equals("help")) {
            this.help(player, label);
            return true;
        }
        final Optional<RadioStation> station = this.target(player);
        if (station.isEmpty()) {
            Messages.error(player, "Посмотрите на проигрыватель или откройте его: Shift + ПКМ.");
            return true;
        }
        final RadioStation radio = station.get();
        final String rest = args.length > 1 ? String.join(" ", List.of(args).subList(1, args.length)) : "";
        switch (sub) {
            case "open" -> this.open(player, radio, null);
            case "search" -> this.open(player, radio, rest.isBlank() ? null : rest);
            case "play" -> this.play(player, radio, rest);
            case "skip" -> {
                radio.skip();
                Messages.ok(player, "Пропущено.");
            }
            case "pause" -> radio.togglePause();
            case "stop" -> {
                radio.stop();
                Messages.ok(player, "Остановлено.");
            }
            case "volume" -> {
                try {
                    radio.setVolume(Integer.parseInt(rest.strip()));
                    Messages.ok(player, "Громкость: " + radio.volume() + "%");
                } catch (NumberFormatException error) {
                    Messages.error(player, "Укажите число от 0 до 100.");
                }
            }
            case "status" -> this.status(player, radio);
            case "code" -> this.code(player, radio);
            default -> this.help(player, label);
        }
        return true;
    }

    private void help(RadioPlayer player, String label) {
        Messages.info(player, "Shift + ПКМ по проигрывателю — открыть радио.");
        Messages.info(player, "/" + label + " play <ссылка или запрос> — в очередь");
        Messages.info(player, "/" + label + " search <запрос> — открыть поиск");
        Messages.info(player, "/" + label + " skip | pause | stop | volume <0-100> | status | code");
    }

    private void open(RadioPlayer player, RadioStation radio, String query) {
        if (query == null) {
            this.plugin.menus().open(player, radio);
        } else {
            this.plugin.menus().openWithQuery(player, radio, query);
        }
    }

    private void play(RadioPlayer player, RadioStation radio, String text) {
        if (text.isBlank()) {
            Messages.error(player, "Укажите ссылку Spotify / Apple Music / YouTube или название трека.");
            return;
        }
        final String name = player.name();
        if (Links.looksLikeLink(text)) {
            Messages.info(player, "Открываю ссылку…");
            this.plugin.catalogs().resolve(Links.withScheme(Links.clean(text))).whenComplete((result, error) ->
                    this.plugin.sync(() -> {
                        if (error != null) {
                            Messages.error(player, Http.playerMessage(error, "Не удалось открыть ссылку."));
                            return;
                        }
                        final List<TrackMeta> tracks = result instanceof ResolveResult.Many many
                                ? many.collection().tracks() : List.of(((ResolveResult.Single) result).track());
                        final int added = radio.enqueue(tracks, player.uuid(), name, false);
                        this.reportAdded(player, tracks, added);
                    }));
            return;
        }
        Messages.info(player, "Ищу «" + text + "»…");
        this.plugin.catalogs().search(text, 0, 1).whenComplete((page, error) -> this.plugin.sync(() -> {
            if (error != null) {
                Messages.error(player, Http.playerMessage(error, "Поиск не удался."));
                return;
            }
            if (page.items().isEmpty()) {
                Messages.error(player, "Ничего не нашлось.");
                return;
            }
            final List<TrackMeta> tracks = List.of(page.items().getFirst());
            this.reportAdded(player, tracks, radio.enqueue(tracks, player.uuid(), name, false));
        }));
    }

    private void reportAdded(RadioPlayer player, List<TrackMeta> tracks, int added) {
        if (added == 0) {
            Messages.error(player, "Очередь заполнена.");
        } else if (tracks.size() == 1) {
            Messages.ok(player, "В очереди: " + tracks.getFirst().artistLine() + " — " + tracks.getFirst().title());
        } else {
            Messages.ok(player, "Добавлено " + Durations.tracks(added) + ".");
        }
    }

    private void code(RadioPlayer player, RadioStation radio) {
        final String code = radio.linkCode();
        if (code == null) {
            Messages.error(player, "Нотные блоки-повторители выключены в config.yml (relay.enabled).");
            return;
        }
        player.sendMessage(Component.text("[Радио] ", NamedTextColor.GOLD)
                .append(Component.text("Код связи проигрывателя: ", NamedTextColor.GRAY))
                .append(Component.text(code, NamedTextColor.GREEN)
                        .hoverEvent(HoverEvent.showText(Component.text("Скопировать")))
                        .clickEvent(ClickEvent.copyToClipboard(code))));
        Messages.info(player, "Shift + ПКМ по нотному блоку и введите код — он будет играть то же самое. "
                + "Сейчас повторителей: " + radio.relayPositions().size() + ".");
    }

    private void status(RadioPlayer player, RadioStation radio) {
        final QueueEntry current = radio.current();
        if (current == null) {
            Messages.info(player, "Ничего не играет. В очереди " + Durations.tracks(radio.queue().size()) + ".");
            return;
        }
        Messages.info(player, current.meta().artistLine() + " — " + current.meta().title() + " ("
                + Durations.clock(radio.positionMs()) + " / " + Durations.clock(radio.durationMs()) + ")");
    }

    private Optional<RadioStation> target(RadioPlayer player) {
        final Optional<BlockPos> looked = player.targetJukebox(8);
        if (looked.isPresent()) {
            return Optional.of(this.plugin.stations().getOrCreate(looked.get().stationKey()));
        }
        final Optional<StationKey> last = this.plugin.menus().lastStation(player);
        return last.flatMap(key -> this.plugin.stations().get(key))
                .filter(radio -> player.distanceTo(radio.block().world(), radio.block().centerX(),
                        radio.block().centerY(), radio.block().centerZ()) < 96);
    }

    /** Suggestions for the argument being typed. */
    public List<String> complete(CommandSource sender, String[] args) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
