package su.nuv.radio.menu;

import net.kyori.adventure.text.Component;
import su.nuv.radio.catalog.model.ResolveResult;
import su.nuv.radio.catalog.model.SearchPage;
import su.nuv.radio.catalog.model.CollectionHit;
import su.nuv.radio.catalog.model.TrackCollection;
import su.nuv.radio.catalog.model.TrackMeta;
import su.nuv.radio.menu.art.CoverArt;
import su.nuv.radio.menu.art.Icons;
import su.nuv.radio.menu.pack.Models;
import su.nuv.radio.menu.pack.PackArt;
import su.nuv.radio.menu.pack.PackArt.ButtonStyle;
import su.nuv.radio.menu.text.FontChars;
import su.nuv.radio.menu.text.Glyphs;
import su.nuv.radio.menu.text.TitleBuilder;
import su.nuv.radio.platform.ClickKind;
import su.nuv.radio.platform.MenuItem;
import su.nuv.radio.platform.MenuView;
import su.nuv.radio.platform.RadioPlayer;
import su.nuv.radio.station.PlaybackState;
import su.nuv.radio.station.QueueEntry;
import su.nuv.radio.station.RadioStation;
import su.nuv.radio.station.RepeatMode;
import su.nuv.radio.station.StationListener;
import su.nuv.radio.util.Durations;
import su.nuv.radio.util.Http;
import su.nuv.radio.util.Links;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One player's radio menu: a six-row chest whose title draws the whole screen (background, texts,
 * progress bar, full-size cover) and whose slots hold the interactive parts (buttons, discs,
 * cover tiles). Screens: now playing, search results, queue and the full cover.
 */
public final class RadioMenu implements StationListener, MenuView.Listener {

    public enum Screen {
        PLAYING, SEARCH, QUEUE, ZOOM
    }

    private static final int PAGE = MenuLayout.LIST_ROWS;
    private static final int MAX_SEARCH_PAGES = 200;

    private final MenuManager manager;
    private final RadioPlayer player;
    private final RadioStation station;
    private final MenuView view;

    private Screen screen = Screen.PLAYING;
    private Component shownTitle;
    private int ticks;
    private boolean closed;
    private boolean suspended;
    private String notice;
    private boolean noticeError;
    private int noticeUntil;
    private int clearArmedUntil = -1;
    private int queuePage;

    // turntable animation
    private long shownSerial = -1;
    private int shownLabel = Tone.NEUTRAL_LABEL;
    private int transition = -1;
    private int transitionFly;
    private int oldLabel;
    private int armPose = Models.ARM_REST;
    private int armStepAt;

    // search
    private String query = "";
    private boolean loading;
    private String searchError;
    private String searchHeader;
    private List<TrackMeta> results = List.of();
    private TrackCollection collection;
    /** Search looks for albums and playlists instead of tracks. */
    private boolean collectionSearch;
    private List<CollectionHit> hits = List.of();
    /** Page of the album results the open collection came from, or -1: "back" returns there. */
    private int hitsPage = -1;
    private String hitsHeader;
    private int hitsPages;
    private boolean hitsHasNext;
    private int searchPage;
    private int searchPages;
    private boolean searchHasNext;
    private long searchToken;

    RadioMenu(MenuManager manager, RadioPlayer player, RadioStation station) {
        this.manager = manager;
        this.player = player;
        this.station = station;
        this.view = player.createMenu(this);
    }

    public RadioPlayer player() {
        return this.player;
    }

    public RadioStation station() {
        return this.station;
    }

    boolean isClosed() {
        return this.closed;
    }

    boolean isSuspended() {
        return this.suspended;
    }

    int age() {
        return this.ticks;
    }

    // ------------------------------------------------------------------ lifecycle

    void open() {
        final QueueEntry current = this.station.current();
        this.shownSerial = current == null ? -1 : current.serial();
        this.shownLabel = this.labelOf(current);
        this.armPose = this.armTarget();
        this.station.addListener(this);
        this.render();
        this.reopen();
    }

    /** Opens (or reopens) the chest with the current title and items. */
    private void reopen() {
        this.shownTitle = this.title();
        this.suspended = false;
        this.view.open(this.shownTitle);
    }

    private MenuView view() {
        return this.view;
    }

    /** True while the player has this menu's window open. */
    boolean isShown() {
        return this.view.isOpen();
    }

    /** The menu steps aside for a dialog; it comes back through {@link #resume}. */
    void suspend() {
        this.suspended = true;
        this.player.closeScreen();
    }

    @Override
    public void clicked(int slot, ClickKind click) {
        this.manager.clicked(this, slot, click);
    }

    @Override
    public void closed() {
        this.manager.closed(this);
    }

    void resume() {
        if (this.closed) {
            return;
        }
        this.render();
        this.reopen();
    }

    void release() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.station.removeListener(this);
    }

    void tick() {
        if (this.closed || this.suspended) {
            return;
        }
        this.ticks++;
        if (this.notice != null && this.ticks > this.noticeUntil) {
            this.notice = null;
            this.refreshTitle();
        }
        if (this.clearArmedUntil > 0 && this.ticks > this.clearArmedUntil) {
            this.clearArmedUntil = -1;
            this.render();
        }
        if (this.screen == Screen.PLAYING) {
            this.stepTransition();
            this.stepArm();
            if (this.ticks % 20 == 0) {
                this.refreshTitle();
            }
        }
    }

    // ------------------------------------------------------------------ station events

    @Override
    public void stationChanged(RadioStation changed, Change change) {
        if (this.closed) {
            return;
        }
        if (change == Change.TRACK) {
            final QueueEntry current = this.station.current();
            final long serial = current == null ? -1 : current.serial();
            if (serial != this.shownSerial) {
                final boolean hadDisc = this.shownSerial != -1;
                this.oldLabel = this.shownLabel;
                this.shownSerial = serial;
                this.shownLabel = this.labelOf(current);
                if (this.screen == Screen.ZOOM) {
                    this.screen = Screen.PLAYING;
                }
                if (this.screen == Screen.PLAYING && !this.suspended) {
                    this.startTransition(hadDisc, serial != -1);
                    return;
                }
            }
        }
        if (change == Change.META) {
            final int label = this.labelOf(this.station.current());
            if (label != this.shownLabel && this.transition < 0) {
                this.shownLabel = label;
            }
        }
        if (!this.suspended) {
            this.render();
        }
    }

    // ------------------------------------------------------------------ turntable motion

    private void startTransition(boolean hadDisc, boolean hasDisc) {
        this.transition = 0;
        this.transitionFly = hadDisc ? Models.EJECT_FRAMES : 0;
        if (!hasDisc) {
            this.transitionFly = Integer.MAX_VALUE;
        }
        this.armPose = Math.min(this.armPose, Models.ARM_PLAY_FIRST - 1);
        this.render();
        this.refreshTitle();
    }

    private void stepTransition() {
        if (this.transition < 0) {
            return;
        }
        final int t = this.transition++;
        final MenuView view = this.view();
        if (t < this.transitionFly && t < Models.EJECT_FRAMES) {
            view.setItem(MenuLayout.SLOT_DISC, MenuItems.disc(Models.discEject(t), this.oldLabel));
            if (t == Models.EJECT_FRAMES - 1 && this.transitionFly == Integer.MAX_VALUE) {
                this.endTransition();
            }
            return;
        }
        if (this.transitionFly == Integer.MAX_VALUE) {
            this.endTransition();
            return;
        }
        final int f = t - this.transitionFly;
        if (f == 0) {
            view.setItem(MenuLayout.SLOT_NEXT, null);
            this.clearCover(view);
        }
        if (f < Models.FLY_FRAMES) {
            view.setItem(MenuLayout.SLOT_DISC, MenuItems.disc(Models.discFly(f), this.shownLabel));
        }
        // the rest of the hotbar slides one slot left while the disc flies
        if (f >= 0 && f < Models.SHIFT_FRAMES) {
            for (int i = 1; i < 9; i++) {
                final MenuItem item = view.getItem(MenuLayout.SLOT_NEXT + i);
                if (item != null) {
                    view.setItem(MenuLayout.SLOT_NEXT + i, item.withModel(Models.miniShift(f + 1)));
                }
            }
        } else if (f == Models.SHIFT_FRAMES) {
            this.renderHotbar(view);
        }
        // the cover develops top to bottom, like a map being filled in
        if (f >= 1 && f <= 4) {
            this.renderCoverRow(view, f - 1);
        }
        if (f >= Models.FLY_FRAMES) {
            this.endTransition();
        }
    }

    private void endTransition() {
        this.transition = -1;
        this.render();
    }

    private int armTarget() {
        if (this.station.current() == null || this.station.state() != PlaybackState.PLAYING || this.transition >= 0) {
            return Models.ARM_REST;
        }
        final long length = this.station.durationMs();
        final double progress = length > 0 ? this.station.positionMs() / (double) length : 0;
        final int span = Models.ARM_ANGLES.size() - Models.ARM_PLAY_FIRST;
        return Models.ARM_PLAY_FIRST + Math.min(span - 1, (int) (progress * span));
    }

    private void stepArm() {
        final int target = this.armTarget();
        if (target == this.armPose || this.ticks < this.armStepAt) {
            return;
        }
        this.armPose += Integer.signum(target - this.armPose);
        this.armStepAt = this.ticks + 2;
        this.view().setItem(MenuLayout.SLOT_ARM, MenuItems.arm(this.armPose));
    }

    // ------------------------------------------------------------------ rendering

    void render() {
        if (this.closed) {
            return;
        }
        final MenuView view = this.view();
        final MenuItem[] contents = new MenuItem[MenuView.SIZE];
        switch (this.screen) {
            case PLAYING -> this.playingItems(contents);
            case SEARCH -> this.searchItems(contents);
            case QUEUE -> this.queueItems(contents);
            case ZOOM -> {
            }
        }
        if (this.transition >= 0) {
            // keep the animated slots as the animation left them
            for (int slot : this.animatedSlots()) {
                contents[slot] = view.getItem(slot);
            }
        }
        view.setContents(contents);
        this.refreshTitle();
    }

    private List<Integer> animatedSlots() {
        final List<Integer> slots = new ArrayList<>();
        slots.add(MenuLayout.SLOT_DISC);
        for (int i = 0; i < 9; i++) {
            slots.add(MenuLayout.SLOT_NEXT + i);
        }
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                slots.add(MenuLayout.SLOT_COVER + r * 9 + c);
            }
        }
        return slots;
    }

    private void refreshTitle() {
        if (this.closed || this.suspended) {
            return;
        }
        final Component title = this.title();
        if (title.equals(this.shownTitle)) {
            return;
        }
        this.shownTitle = title;
        if (!this.view.updateTitle(title)) {
            this.reopen();
        }
    }

    private Component title() {
        return switch (this.screen) {
            case PLAYING -> this.playingTitle();
            case SEARCH -> this.listTitle(true);
            case QUEUE -> this.listTitle(false);
            case ZOOM -> this.zoomTitle();
        };
    }

    // ------------------------------------------------------------------ now playing

    private void playingItems(MenuItem[] slots) {
        final QueueEntry current = this.station.current();
        if (this.transition < 0) {
            // the cover may have arrived since the disc landed
            this.shownLabel = this.labelOf(current);
        }
        final boolean playing = this.station.state() == PlaybackState.PLAYING;
        if (current != null) {
            slots[MenuLayout.SLOT_DISC] = MenuItems.disc(playing ? Models.DISC_SPIN : Models.DISC_STILL, this.shownLabel);
        }
        slots[MenuLayout.SLOT_ARM] = MenuItems.arm(this.armPose);
        final String code = this.station.linkCode();
        slots[MenuLayout.SLOT_TAB_PLAYING] = code == null
                ? MenuItems.button(Icons.NOTE, ButtonStyle.ON, "Проигрыватель", "Вы здесь")
                : MenuItems.button(Icons.NOTE, ButtonStyle.ON, "Проигрыватель", "Вы здесь", "",
                "Код связи: " + code,
                "Нотных блоков-повторителей: " + this.station.relayPositions().size(),
                "Shift + ПКМ по нотному блоку — ввести код");
        slots[MenuLayout.SLOT_TAB_SEARCH] = MenuItems.button(Icons.SEARCH, ButtonStyle.NORMAL, "Поиск и ссылки",
                "Название, исполнитель или ссылка", "Spotify / Apple Music / YouTube");
        slots[MenuLayout.SLOT_TAB_QUEUE] = MenuItems.button(Icons.QUEUE, ButtonStyle.NORMAL, "Очередь",
                Durations.tracks(this.station.queue().size()));
        slots[MenuLayout.SLOT_TAB_CLOSE] = MenuItems.button(Icons.CLOSE, ButtonStyle.NORMAL, "Закрыть",
                "Музыка продолжит играть");

        final CoverArt cover = current == null ? null : this.manager.cover(current.meta().coverUrl(), this::requestRender);
        if (cover != null) {
            final int[] grid = cover.grid(32);
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    slots[MenuLayout.SLOT_COVER + r * 9 + c] = this.coverTile(grid, r, c);
                }
            }
        }
        this.hotbar(slots);

        final boolean present = current != null;
        slots[MenuLayout.SLOT_PREV] = MenuItems.button(Icons.PREV, present ? ButtonStyle.NORMAL : ButtonStyle.OFF,
                "Назад", "С начала или предыдущий трек");
        final boolean running = playing || this.station.state() == PlaybackState.LOADING;
        slots[MenuLayout.SLOT_PLAY] = MenuItems.button(running ? Icons.PAUSE : Icons.PLAY,
                present || !this.station.queue().isEmpty() ? ButtonStyle.NORMAL : ButtonStyle.OFF,
                running ? "Пауза" : "Играть");
        slots[MenuLayout.SLOT_SKIP] = MenuItems.button(Icons.NEXT, present ? ButtonStyle.NORMAL : ButtonStyle.OFF,
                "Дальше");
        slots[MenuLayout.SLOT_STOP] = MenuItems.button(Icons.STOP, present ? ButtonStyle.NORMAL : ButtonStyle.OFF,
                "Стоп", "Очередь останется");
        final RepeatMode repeat = this.station.repeat();
        slots[MenuLayout.SLOT_REPEAT] = MenuItems.button(repeat == RepeatMode.ONE ? Icons.REPEAT_ONE : Icons.REPEAT,
                repeat == RepeatMode.OFF ? ButtonStyle.NORMAL : ButtonStyle.ON, repeat.label(),
                "Щелчок — переключить");
        slots[MenuLayout.SLOT_SHUFFLE] = MenuItems.button(Icons.SHUFFLE,
                this.station.queue().size() > 1 ? ButtonStyle.NORMAL : ButtonStyle.OFF, "Перемешать очередь");
        slots[MenuLayout.SLOT_VOL_DOWN] = MenuItems.button(Icons.MINUS, ButtonStyle.NORMAL, "Тише", "−10%");
        slots[MenuLayout.SLOT_VOL] = MenuItems.volume(this.station.volume());
        slots[MenuLayout.SLOT_VOL_UP] = MenuItems.button(Icons.PLUS, ButtonStyle.NORMAL, "Громче", "+10%");
    }

    private MenuItem coverTile(int[] grid, int r, int c) {
        final int[] tile = new int[64];
        for (int y = 0; y < 8; y++) {
            System.arraycopy(grid, (r * 8 + y) * 32 + c * 8, tile, y * 8, 8);
        }
        return MenuItems.patch(true, tile, "Обложка целиком", List.of("Щёлкните, чтобы открыть"));
    }

    private void clearCover(MenuView view) {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                view.setItem(MenuLayout.SLOT_COVER + r * 9 + c, null);
            }
        }
    }

    private void renderCoverRow(MenuView view, int row) {
        final QueueEntry current = this.station.current();
        final CoverArt cover = current == null ? null : this.manager.cover(current.meta().coverUrl(), this::requestRender);
        if (cover == null) {
            return;
        }
        final int[] grid = cover.grid(32);
        for (int c = 0; c < 4; c++) {
            view.setItem(MenuLayout.SLOT_COVER + row * 9 + c, this.coverTile(grid, row, c));
        }
    }

    private void hotbar(MenuItem[] slots) {
        final List<QueueEntry> queue = this.station.queue();
        for (int i = 0; i < Math.min(9, queue.size()); i++) {
            final QueueEntry entry = queue.get(i);
            final TrackMeta meta = entry.meta();
            final List<String> lore = new ArrayList<>();
            if (!meta.artistLine().isEmpty()) {
                lore.add(meta.artistLine());
            }
            lore.add(Durations.clock(meta.durationMs()).replace("--:--", "длительность неизвестна")
                    + (entry.requesterName() == null ? "" : " · поставил " + entry.requesterName()));
            lore.add("ЛКМ — играть сейчас · ПКМ — убрать");
            slots[MenuLayout.SLOT_NEXT + i] = MenuItems.miniDisc(Models.MINI_DISC, this.labelOf(entry), meta.title(), lore);
        }
    }

    private void renderHotbar(MenuView view) {
        final MenuItem[] slots = new MenuItem[MenuView.SIZE];
        this.hotbar(slots);
        for (int i = 0; i < 9; i++) {
            view.setItem(MenuLayout.SLOT_NEXT + i, slots[MenuLayout.SLOT_NEXT + i]);
        }
    }

    private Component playingTitle() {
        final TitleBuilder t = new TitleBuilder();
        t.background(FontChars.BG_MAIN_L, 0, MenuLayout.CHEST_W);
        t.background(FontChars.BG_MAIN_R, MenuLayout.CHEST_W, MenuLayout.EXT_W);
        t.text("Проигрыватель", MenuLayout.TITLE_X, 6, Tone.TEXT, TitleBuilder.Align.LEFT);

        final int x = MenuLayout.PANEL_X;
        final int w = MenuLayout.PANEL_W;
        final QueueEntry current = this.station.current();
        if (this.notice != null) {
            t.text(Glyphs.fitPx(this.notice, w), x, 8, this.noticeError ? Tone.TEXT_ERROR : Tone.TEXT,
                    TitleBuilder.Align.LEFT);
        }
        if (current == null) {
            if (this.notice == null) {
                t.text("Ничего не играет", x, 8, Tone.TEXT_MUTED, TitleBuilder.Align.LEFT);
            }
            t.text("Тишина", x, 22, Tone.TEXT_STRONG, TitleBuilder.Align.LEFT);
            final List<String> hint = Glyphs.wrapPx(this.station.queue().isEmpty()
                    ? "Нажмите поиск слева и найдите трек или вставьте ссылку."
                    : "В очереди есть треки: нажмите «Играть».", w, 4);
            for (int i = 0; i < hint.size(); i++) {
                t.text(hint.get(i), x, 36 + i * 10, Tone.TEXT, TitleBuilder.Align.LEFT);
            }
        } else {
            final TrackMeta meta = current.meta();
            if (this.notice == null) {
                final String state = switch (this.station.state()) {
                    case PLAYING -> "Играет";
                    case PAUSED -> "Пауза";
                    case LOADING -> "Загрузка…";
                    case FAILED -> "Не удалось воспроизвести";
                    default -> "Остановлено";
                };
                final String source = this.manager.catalogs().get(meta.provider()).map(c -> c.displayName())
                        .orElse(meta.provider());
                t.text(Glyphs.fitPx(state + " · " + source, w), x, 8,
                        this.station.state() == PlaybackState.FAILED ? Tone.TEXT_ERROR : Tone.TEXT_MUTED,
                        TitleBuilder.Align.LEFT);
            }
            final List<String> title = Glyphs.wrapPx(meta.title(), w, 3);
            int y = 21;
            for (String line : title) {
                t.text(line, x, y, Tone.TEXT_STRONG, TitleBuilder.Align.LEFT);
                y += 10;
            }
            y += 2;
            t.text(Glyphs.fitPx(meta.artistLine(), w), x, y, Tone.TEXT, TitleBuilder.Align.LEFT);
            y += 10;
            if (meta.album() != null && !meta.album().isBlank() && y <= 64) {
                t.text(Glyphs.fitPx(meta.album(), w), x, y, Tone.TEXT_MUTED, TitleBuilder.Align.LEFT);
                y += 10;
            }
            final String error = this.station.lastError();
            if (error != null && y <= 76) {
                t.text(Glyphs.fitPx(error, w), x, y, Tone.TEXT_ERROR, TitleBuilder.Align.LEFT);
            } else if (current.requesterName() != null && y <= 76) {
                t.text(Glyphs.fitPx("поставил " + current.requesterName(), w), x, y, Tone.TEXT_MUTED,
                        TitleBuilder.Align.LEFT);
            }
            final long length = this.station.durationMs();
            final long position = this.station.positionMs();
            final int dots = MenuLayout.BAR_W / 2;
            final int filled = length > 0 ? (int) Math.round(dots * Math.min(1.0, position / (double) length)) : 0;
            t.bar(MenuLayout.BAR_X, MenuLayout.BAR_Y, dots, filled, CoverArt.accentOf(this.shownLabel), PackArt.TRACK);
            t.text(Durations.clock(position).replace("--:--", "0:00"), x, 95, Tone.TEXT, TitleBuilder.Align.LEFT);
            t.text(length > 0 ? Durations.clock(length) : "--:--", x + w, 95, Tone.TEXT, TitleBuilder.Align.RIGHT);
        }
        final List<QueueEntry> queue = this.station.queue();
        if (queue.isEmpty()) {
            t.text("Очередь пуста", x, 106, Tone.TEXT_MUTED, TitleBuilder.Align.LEFT);
        } else {
            t.text(Glyphs.fitPx("Далее: " + queue.getFirst().meta().title(), w), x, 106, Tone.TEXT,
                    TitleBuilder.Align.LEFT);
            t.text(Glyphs.fitPx(Durations.tracks(queue.size()) + " · "
                    + Durations.clock(this.station.queueDurationMs()).replace("--:--", "—"), w), x, 116,
                    Tone.TEXT_MUTED, TitleBuilder.Align.LEFT);
        }
        return t.build();
    }

    // ------------------------------------------------------------------ lists

    private void searchItems(MenuItem[] slots) {
        final List<TrackMeta> rows = this.showingHits() ? List.of() : this.visibleResults();
        if (this.showingHits()) {
            for (int r = 0; r < this.hits.size(); r++) {
                final CollectionHit hit = this.hits.get(r);
                slots[r * 9] = MenuItems.button(Icons.PLUS, ButtonStyle.NORMAL, "Всё в очередь", hit.title());
                slots[r * 9 + 1] = this.rowCover(hit.coverUrl(), hit.title());
                final MenuItem row = MenuItems.hit(hit.title(), List.of(hit.kind().label(), hit.subtitle(),
                        "ЛКМ — открыть · ПКМ — всё в очередь"));
                for (int c = 2; c < 9; c++) {
                    slots[r * 9 + c] = row;
                }
            }
        }
        for (int r = 0; r < rows.size(); r++) {
            final TrackMeta track = rows.get(r);
            slots[r * 9] = MenuItems.button(Icons.PLAY, ButtonStyle.NORMAL, "Играть сейчас", track.title());
            slots[r * 9 + 1] = this.rowCover(track, r);
            final MenuItem hit = MenuItems.hit(track.title(), this.trackLore(track, "ЛКМ — в очередь · ПКМ — играть сейчас"));
            for (int c = 2; c < 9; c++) {
                slots[r * 9 + c] = hit;
            }
        }
        final boolean isCollection = this.collection != null;
        final int pages = this.searchPageCount();
        final boolean canPrev = this.searchPage > 0;
        final boolean canNext = isCollection ? this.searchPage + 1 < pages : this.searchHasNext;
        slots[MenuLayout.SLOT_PAGE_PREV] = MenuItems.button(Icons.LEFT, canPrev ? ButtonStyle.NORMAL : ButtonStyle.OFF,
                "Предыдущая страница");
        slots[MenuLayout.SLOT_PAGE_NEXT] = MenuItems.button(Icons.RIGHT, canNext ? ButtonStyle.NORMAL : ButtonStyle.OFF,
                "Следующая страница");
        slots[MenuLayout.SLOT_LIST_ACTION_A] = isCollection && this.hitsPage >= 0
                ? MenuItems.button(Icons.LEFT, ButtonStyle.NORMAL, "Назад к результатам", "«" + this.query + "»")
                : MenuItems.button(Icons.SEARCH, ButtonStyle.NORMAL, "Новый поиск",
                this.collectionSearch ? "Название альбома или плейлиста" : "Название, исполнитель или ссылка");
        if (isCollection) {
            slots[MenuLayout.SLOT_LIST_ACTION_B] = MenuItems.button(Icons.PLUS, ButtonStyle.NORMAL, "Добавить все",
                    Durations.tracks(this.collection.tracks().size()) + " в конец очереди");
        } else {
            slots[MenuLayout.SLOT_LIST_ACTION_B] = this.collectionSearch
                    ? MenuItems.button(Icons.NOTE, ButtonStyle.NORMAL, "Искать треки", "Сейчас: альбомы и плейлисты")
                    : MenuItems.button(Icons.QUEUE, ButtonStyle.NORMAL, "Искать альбомы и плейлисты",
                    "Сейчас: треки");
        }
        this.listNavigation(slots);
    }

    private void queueItems(MenuItem[] slots) {
        final List<QueueEntry> queue = this.station.queue();
        final int pages = Math.max(1, (queue.size() + PAGE - 1) / PAGE);
        this.queuePage = Math.max(0, Math.min(this.queuePage, pages - 1));
        final int from = this.queuePage * PAGE;
        for (int r = 0; r < PAGE && from + r < queue.size(); r++) {
            final QueueEntry entry = queue.get(from + r);
            this.station.enrich(entry);
            final TrackMeta meta = entry.meta();
            slots[r * 9] = MenuItems.button(Icons.UP, from + r > 0 ? ButtonStyle.NORMAL : ButtonStyle.OFF,
                    "Поднять выше", meta.title());
            slots[r * 9 + 1] = this.rowCover(meta, r);
            final MenuItem hit = MenuItems.hit(meta.title(), this.trackLore(meta, "ЛКМ — играть сейчас · ПКМ — убрать"));
            for (int c = 2; c < 9; c++) {
                slots[r * 9 + c] = hit;
            }
        }
        slots[MenuLayout.SLOT_PAGE_PREV] = MenuItems.button(Icons.LEFT,
                this.queuePage > 0 ? ButtonStyle.NORMAL : ButtonStyle.OFF, "Предыдущая страница");
        slots[MenuLayout.SLOT_PAGE_NEXT] = MenuItems.button(Icons.RIGHT,
                this.queuePage + 1 < pages ? ButtonStyle.NORMAL : ButtonStyle.OFF, "Следующая страница");
        slots[MenuLayout.SLOT_LIST_ACTION_A] = MenuItems.button(Icons.SHUFFLE,
                queue.size() > 1 ? ButtonStyle.NORMAL : ButtonStyle.OFF, "Перемешать");
        final boolean armed = this.ticks < this.clearArmedUntil;
        slots[MenuLayout.SLOT_LIST_ACTION_B] = armed
                ? MenuItems.button(Icons.CHECK, ButtonStyle.DANGER, "Точно очистить?", "Щёлкните ещё раз")
                : MenuItems.button(Icons.CLOSE, queue.isEmpty() ? ButtonStyle.OFF : ButtonStyle.NORMAL,
                "Очистить очередь", "Нужно подтвердить");
        this.listNavigation(slots);
    }

    private void listNavigation(MenuItem[] slots) {
        slots[MenuLayout.SLOT_TO_PLAYER] = MenuItems.button(Icons.NOTE, ButtonStyle.NORMAL, "К проигрывателю");
        slots[MenuLayout.SLOT_CLOSE] = MenuItems.button(Icons.CLOSE, ButtonStyle.NORMAL, "Закрыть",
                "Музыка продолжит играть");
    }

    private MenuItem rowCover(TrackMeta track, int row) {
        return this.rowCover(track.coverUrl(), track.title());
    }

    private MenuItem rowCover(String coverUrl, String title) {
        final CoverArt cover = this.manager.cover(coverUrl, this::requestRender);
        if (cover == null) {
            return MenuItems.miniDisc(Models.MINI_DISC, Tone.NEUTRAL_LABEL, title, List.of());
        }
        return MenuItems.patch(false, cover.grid(8), null, null);
    }

    private List<String> trackLore(TrackMeta track, String hint) {
        final List<String> lore = new ArrayList<>();
        if (!track.artistLine().isEmpty()) {
            lore.add(track.artistLine());
        }
        if (track.album() != null && !track.album().isBlank()) {
            lore.add(track.album());
        }
        lore.add(Durations.clock(track.durationMs()).replace("--:--", "длительность неизвестна"));
        lore.add(hint);
        return lore;
    }

    private Component listTitle(boolean search) {
        final TitleBuilder t = new TitleBuilder();
        t.background(FontChars.BG_LIST_L, 0, MenuLayout.CHEST_W);
        t.background(FontChars.BG_LIST_R, MenuLayout.CHEST_W, MenuLayout.EXT_W);
        final int right = MenuLayout.FULL_W - 8;
        String header;
        String page = null;
        int headerColor = Tone.TEXT;
        final List<String[]> lines = new ArrayList<>();
        if (search) {
            if (this.loading) {
                header = "Ищу «" + this.query + "»…";
            } else if (this.searchError != null) {
                header = this.searchError;
                headerColor = Tone.TEXT_ERROR;
            } else {
                header = this.searchHeader == null ? "Поиск" : this.searchHeader;
            }
            final int pages = this.searchPageCount();
            if (!this.loading && this.searchError == null && pages > 1) {
                page = (this.searchPage + 1) + " / " + pages;
            }
            if (this.showingHits()) {
                for (CollectionHit hit : this.hits) {
                    lines.add(new String[]{hit.title(), hit.subtitle(), hit.kind().label()});
                }
            } else {
                for (TrackMeta track : this.visibleResults()) {
                    lines.add(new String[]{track.title(), this.subtitle(track), Durations.clock(track.durationMs())});
                }
            }
        } else {
            final List<QueueEntry> queue = this.station.queue();
            header = queue.isEmpty() ? "Очередь пуста" : "Очередь · " + Durations.tracks(queue.size()) + " · "
                    + Durations.clock(this.station.queueDurationMs()).replace("--:--", "—");
            final int pages = Math.max(1, (queue.size() + PAGE - 1) / PAGE);
            if (pages > 1) {
                page = (this.queuePage + 1) + " / " + pages;
            }
            final int from = this.queuePage * PAGE;
            for (int r = 0; r < PAGE && from + r < queue.size(); r++) {
                final QueueEntry entry = queue.get(from + r);
                final String who = entry.requesterName() == null ? "" : " · " + entry.requesterName();
                lines.add(new String[]{(from + r + 1) + ". " + entry.meta().title(), entry.meta().artistLine() + who,
                        Durations.clock(entry.meta().durationMs())});
            }
        }
        final int pageWidth = page == null ? 0 : Glyphs.advanceSum(page) + 8;
        t.text(Glyphs.fitPx(this.notice != null ? this.notice : header, right - 8 - pageWidth), 8, 6,
                this.notice != null ? (this.noticeError ? Tone.TEXT_ERROR : Tone.TEXT) : headerColor,
                TitleBuilder.Align.LEFT);
        if (page != null) {
            t.text(page, right, 6, Tone.TEXT_MUTED, TitleBuilder.Align.RIGHT);
        }
        final int textLeft = 46;
        for (int r = 0; r < lines.size(); r++) {
            final int top = MenuLayout.rowTop(r);
            final String[] line = lines.get(r);
            final int durationWidth = Glyphs.advanceSum(line[2]);
            t.text(Glyphs.fitPx(line[0], right - textLeft - durationWidth - 6), textLeft, top, Tone.ROW_TEXT,
                    TitleBuilder.Align.LEFT);
            t.text(line[2], right, top, Tone.ROW_TEXT_MUTED, TitleBuilder.Align.RIGHT);
            t.text(Glyphs.fitPx(line[1], right - textLeft), textLeft, top + 8, Tone.ROW_TEXT_MUTED,
                    TitleBuilder.Align.LEFT);
        }
        if (lines.isEmpty()) {
            final String empty = search
                    ? (this.loading ? "Подождите…" : this.query.isEmpty() ? "Нажмите «Новый поиск» внизу" : "Ничего не нашлось")
                    : "Добавьте треки через поиск";
            t.text(empty, (textLeft + right) / 2, MenuLayout.rowTop(2) + 5, Tone.ROW_TEXT_MUTED,
                    TitleBuilder.Align.CENTER);
        }
        return t.build();
    }

    private String subtitle(TrackMeta track) {
        final String artists = track.artistLine();
        if (track.album() == null || track.album().isBlank()) {
            return artists;
        }
        return artists.isEmpty() ? track.album() : artists + " · " + track.album();
    }

    // ------------------------------------------------------------------ zoom

    private Component zoomTitle() {
        final TitleBuilder t = new TitleBuilder();
        t.background(FontChars.BG_ZOOM_L, 0, MenuLayout.CHEST_W);
        t.background(FontChars.BG_ZOOM_R, MenuLayout.CHEST_W, MenuLayout.EXT_W);
        final QueueEntry current = this.station.current();
        final CoverArt cover = current == null ? null : this.manager.cover(current.meta().coverUrl(), this::requestRender);
        if (cover != null) {
            final int[] grid = cover.grid(MenuLayout.ZOOM_CELLS);
            final int[] row = new int[MenuLayout.ZOOM_CELLS];
            for (int y = 0; y < MenuLayout.ZOOM_CELLS; y++) {
                System.arraycopy(grid, y * MenuLayout.ZOOM_CELLS, row, 0, MenuLayout.ZOOM_CELLS);
                t.pixels(row, MenuLayout.ZOOM_X, MenuLayout.ZOOM_Y + y * MenuLayout.ZOOM_PX);
            }
        }
        if (current != null) {
            final int x = MenuLayout.PANEL_X;
            int y = 12;
            for (String line : Glyphs.wrapPx(current.meta().title(), MenuLayout.PANEL_W, 4)) {
                t.text(line, x, y, Tone.ROW_TEXT, TitleBuilder.Align.LEFT);
                y += 10;
            }
            y += 4;
            for (String line : Glyphs.wrapPx(current.meta().artistLine(), MenuLayout.PANEL_W, 2)) {
                t.text(line, x, y, Tone.ROW_TEXT_MUTED, TitleBuilder.Align.LEFT);
                y += 10;
            }
            t.text("Щёлкните, чтобы", x, 104, Tone.HINT, TitleBuilder.Align.LEFT);
            t.text("вернуться", x, 114, Tone.HINT, TitleBuilder.Align.LEFT);
        }
        return t.build();
    }

    // ------------------------------------------------------------------ clicks

    void onClick(int slot, ClickKind click) {
        if (this.closed || slot < 0 || slot >= 54) {
            return;
        }
        final boolean right = click.isRight();
        switch (this.screen) {
            case PLAYING -> this.clickPlaying(slot, right);
            case SEARCH -> this.clickSearch(slot, right);
            case QUEUE -> this.clickQueue(slot, right);
            case ZOOM -> this.show(Screen.PLAYING);
        }
    }

    private void clickPlaying(int slot, boolean right) {
        final int coverCol = slot % 9;
        final int coverRow = slot / 9;
        if (coverRow < 4 && coverCol >= 5 && this.station.current() != null
                && this.view().getItem(slot) != null) {
            this.show(Screen.ZOOM);
            return;
        }
        if (slot >= MenuLayout.SLOT_NEXT && slot < MenuLayout.SLOT_NEXT + 9) {
            final List<QueueEntry> queue = this.station.queue();
            final int index = slot - MenuLayout.SLOT_NEXT;
            if (index < queue.size()) {
                final QueueEntry entry = queue.get(index);
                if (right) {
                    this.station.remove(entry.serial());
                    this.notice("Убрано: " + entry.meta().title(), false);
                } else {
                    this.station.jumpTo(entry.serial());
                }
            }
            return;
        }
        switch (slot) {
            case MenuLayout.SLOT_TAB_SEARCH -> this.manager.askQuery(this, this.query);
            case MenuLayout.SLOT_TAB_QUEUE -> this.show(Screen.QUEUE);
            case MenuLayout.SLOT_TAB_CLOSE -> this.player.closeScreen();
            case MenuLayout.SLOT_PREV -> this.station.previous();
            case MenuLayout.SLOT_PLAY -> this.station.togglePause();
            case MenuLayout.SLOT_SKIP -> this.station.skip();
            case MenuLayout.SLOT_STOP -> this.station.stop();
            case MenuLayout.SLOT_REPEAT -> {
                this.station.cycleRepeat();
                this.notice(this.station.repeat().label(), false);
            }
            case MenuLayout.SLOT_SHUFFLE -> {
                if (this.station.queue().size() > 1) {
                    this.station.shuffle();
                    this.notice("Очередь перемешана", false);
                }
            }
            case MenuLayout.SLOT_VOL_DOWN -> this.station.setVolume(this.station.volume() - 10);
            case MenuLayout.SLOT_VOL_UP -> this.station.setVolume(this.station.volume() + 10);
            case MenuLayout.SLOT_VOL -> this.station.setVolume(this.station.volume() + (right ? 10 : -10));
            default -> {
            }
        }
    }

    private void clickSearch(int slot, boolean right) {
        final int row = slot / 9;
        final int col = slot % 9;
        if (row < PAGE && this.showingHits()) {
            if (row < this.hits.size()) {
                final CollectionHit hit = this.hits.get(row);
                if (col == 0 || (col >= 2 && right)) {
                    this.enqueueHit(hit);
                } else if (col >= 1) {
                    this.openHit(hit);
                }
            }
            return;
        }
        if (row < PAGE) {
            final List<TrackMeta> rows = this.visibleResults();
            if (row >= rows.size()) {
                return;
            }
            final TrackMeta track = rows.get(row);
            if (col == 0 || (col >= 2 && right)) {
                this.station.playNow(track, this.player.uuid(), this.player.name());
                this.show(Screen.PLAYING);
            } else if (col >= 1) {
                this.enqueue(List.of(track));
            }
            return;
        }
        switch (slot) {
            case MenuLayout.SLOT_PAGE_PREV -> this.pageSearch(-1);
            case MenuLayout.SLOT_PAGE_NEXT -> this.pageSearch(1);
            case MenuLayout.SLOT_LIST_ACTION_A -> {
                if (this.collection != null && this.hitsPage >= 0) {
                    this.backToHits();
                } else {
                    this.manager.askQuery(this, this.query);
                }
            }
            case MenuLayout.SLOT_LIST_ACTION_B -> {
                if (this.collection != null) {
                    this.enqueue(this.collection.tracks());
                } else {
                    this.toggleSearchKind();
                }
            }
            case MenuLayout.SLOT_TO_PLAYER -> this.show(Screen.PLAYING);
            case MenuLayout.SLOT_CLOSE -> this.player.closeScreen();
            default -> {
            }
        }
    }

    private void clickQueue(int slot, boolean right) {
        final int row = slot / 9;
        final int col = slot % 9;
        final List<QueueEntry> queue = this.station.queue();
        if (row < PAGE) {
            final int index = this.queuePage * PAGE + row;
            if (index >= queue.size()) {
                return;
            }
            final QueueEntry entry = queue.get(index);
            if (col == 0) {
                this.station.moveUp(entry.serial());
            } else if (right) {
                this.station.remove(entry.serial());
                this.notice("Убрано: " + entry.meta().title(), false);
            } else {
                this.station.jumpTo(entry.serial());
                this.show(Screen.PLAYING);
            }
            return;
        }
        switch (slot) {
            case MenuLayout.SLOT_PAGE_PREV -> {
                this.queuePage = Math.max(0, this.queuePage - 1);
                this.render();
            }
            case MenuLayout.SLOT_PAGE_NEXT -> {
                this.queuePage++;
                this.render();
            }
            case MenuLayout.SLOT_LIST_ACTION_A -> {
                if (queue.size() > 1) {
                    this.station.shuffle();
                    this.notice("Очередь перемешана", false);
                }
            }
            case MenuLayout.SLOT_LIST_ACTION_B -> {
                if (queue.isEmpty()) {
                    return;
                }
                if (this.ticks < this.clearArmedUntil) {
                    this.clearArmedUntil = -1;
                    this.station.clearQueue();
                    this.notice("Очередь очищена", false);
                } else {
                    this.clearArmedUntil = this.ticks + 60;
                    this.render();
                }
            }
            case MenuLayout.SLOT_TO_PLAYER -> this.show(Screen.PLAYING);
            case MenuLayout.SLOT_CLOSE -> this.player.closeScreen();
            default -> {
            }
        }
    }

    void show(Screen next) {
        this.screen = next;
        this.transition = -1;
        this.render();
    }

    private void notice(String text, boolean error) {
        this.notice = text;
        this.noticeError = error;
        this.noticeUntil = this.ticks + 70;
        this.refreshTitle();
    }

    private void requestRender() {
        this.manager.later(1, () -> {
            if (!this.closed && !this.suspended && this.transition < 0) {
                this.render();
            }
        });
    }

    // ------------------------------------------------------------------ search and links

    /** A query or link typed into the search dialog. */
    void submit(String text) {
        final String value = text == null ? "" : text.strip();
        this.screen = Screen.SEARCH;
        if (value.isEmpty()) {
            this.render();
            return;
        }
        this.query = value;
        this.searchPage = 0;
        this.collection = null;
        this.results = List.of();
        this.hits = List.of();
        this.hitsPage = -1;
        this.searchError = null;
        if (Links.looksLikeLink(value)) {
            this.resolveLink(Links.withScheme(Links.clean(value)));
        } else {
            this.runSearch();
        }
    }

    private void resolveLink(String link) {
        final long token = ++this.searchToken;
        this.loading = true;
        this.searchHeader = null;
        this.render();
        this.manager.catalogs().resolve(link).whenComplete((result, error) -> this.manager.sync(() -> {
            if (token != this.searchToken || this.closed) {
                return;
            }
            this.loading = false;
            if (error != null) {
                this.searchError = Http.playerMessage(error, "Не удалось открыть ссылку.");
                if (this.hitsPage >= 0) {
                    this.searchPage = this.hitsPage;
                    this.hitsPage = -1;
                }
            } else if (result instanceof ResolveResult.Single single) {
                this.results = List.of(single.track());
                this.searchHeader = "Трек · " + this.sourceName(single.track());
                this.searchPages = 1;
                this.searchHasNext = false;
            } else if (result instanceof ResolveResult.Many many) {
                final TrackCollection c = many.collection();
                this.collection = c;
                this.searchHeader = c.kind().label() + " · " + c.title() + " · " + Durations.tracks(c.tracks().size());
            }
            this.render();
        }));
    }

    private void runSearch() {
        if (this.collectionSearch) {
            this.runCollectionSearch();
            return;
        }
        final long token = ++this.searchToken;
        this.loading = true;
        this.searchHeader = null;
        this.render();
        final int page = this.searchPage;
        this.manager.catalogs().search(this.query, page * PAGE, PAGE).whenComplete((result, error) -> this.manager.sync(() -> {
            if (token != this.searchToken || this.closed) {
                return;
            }
            this.loading = false;
            if (error != null) {
                this.searchError = Http.playerMessage(error, "Поиск не удался.");
                this.results = List.of();
            } else {
                this.applySearch(result);
            }
            this.render();
        }));
    }

    private void applySearch(SearchPage page) {
        this.results = page.items();
        this.searchHasNext = page.hasNext() && this.searchPage + 1 < MAX_SEARCH_PAGES;
        if (page.total() >= 0) {
            this.searchPages = Math.min(MAX_SEARCH_PAGES, Math.max(1, (page.total() + PAGE - 1) / PAGE));
        } else {
            this.searchPages = this.searchPage + (this.searchHasNext ? 2 : 1);
        }
        final String source = this.manager.catalogs().searchCatalog().map(c -> c.displayName()).orElse("");
        this.searchHeader = page.items().isEmpty() ? "Ничего не нашлось: «" + this.query + "»"
                : "«" + this.query + "»" + (source.isEmpty() ? "" : " · " + source);
    }

    private boolean showingHits() {
        return this.collectionSearch && this.collection == null;
    }

    private void runCollectionSearch() {
        final long token = ++this.searchToken;
        this.loading = true;
        this.searchHeader = null;
        this.render();
        final int page = this.searchPage;
        this.manager.catalogs().searchCollections(this.query, page * PAGE, PAGE).whenComplete((result, error) ->
                this.manager.sync(() -> {
                    if (token != this.searchToken || this.closed) {
                        return;
                    }
                    this.loading = false;
                    if (error != null) {
                        this.searchError = Http.playerMessage(error, "Поиск альбомов не удался.");
                        this.hits = List.of();
                    } else {
                        this.hits = result.items();
                        this.searchHasNext = result.hasNext() && this.searchPage + 1 < MAX_SEARCH_PAGES;
                        this.searchPages = result.total() >= 0
                                ? Math.min(MAX_SEARCH_PAGES, Math.max(1, (result.total() + PAGE - 1) / PAGE))
                                : this.searchPage + (this.searchHasNext ? 2 : 1);
                        this.searchHeader = result.items().isEmpty()
                                ? "Альбомов не нашлось: «" + this.query + "»"
                                : "«" + this.query + "» · альбомы и плейлисты";
                    }
                    this.render();
                }));
    }

    /** Switches search between tracks and albums/playlists and repeats the current query. */
    private void toggleSearchKind() {
        this.collectionSearch = !this.collectionSearch;
        this.searchPage = 0;
        this.results = List.of();
        this.hits = List.of();
        this.hitsPage = -1;
        this.searchError = null;
        if (this.query.isBlank()) {
            this.searchHeader = null;
            this.render();
        } else {
            this.runSearch();
        }
    }

    /** Opens a found album as a track list; "back" returns to the results. */
    private void openHit(CollectionHit hit) {
        this.hitsPage = this.searchPage;
        this.hitsHeader = this.searchHeader;
        this.hitsPages = this.searchPages;
        this.hitsHasNext = this.searchHasNext;
        this.searchPage = 0;
        this.searchError = null;
        this.resolveLink(hit.link());
    }

    private void backToHits() {
        this.collection = null;
        this.searchPage = this.hitsPage;
        this.searchHeader = this.hitsHeader;
        this.searchPages = this.hitsPages;
        this.searchHasNext = this.hitsHasNext;
        this.searchError = null;
        this.hitsPage = -1;
        this.render();
    }

    /** Loads a found album and queues all of it, without leaving the results. */
    private void enqueueHit(CollectionHit hit) {
        this.notice("Загружаю «" + hit.title() + "»…", false);
        this.manager.catalogs().resolve(hit.link()).whenComplete((result, error) -> this.manager.sync(() -> {
            if (this.closed) {
                return;
            }
            if (error != null) {
                this.notice(Http.playerMessage(error, "Не удалось загрузить «" + hit.title() + "»"), true);
            } else if (result instanceof ResolveResult.Many many) {
                this.enqueue(many.collection().tracks());
            } else if (result instanceof ResolveResult.Single single) {
                this.enqueue(List.of(single.track()));
            }
        }));
    }

    private int searchPageCount() {
        if (this.collection != null) {
            return Math.max(1, (this.collection.tracks().size() + PAGE - 1) / PAGE);
        }
        return this.searchPages;
    }

    private void pageSearch(int delta) {
        final int next = this.searchPage + delta;
        if (next < 0) {
            return;
        }
        if (this.collection != null) {
            if (next < this.searchPageCount()) {
                this.searchPage = next;
                this.render();
            }
            return;
        }
        if (delta > 0 && !this.searchHasNext) {
            return;
        }
        this.searchPage = next;
        this.runSearch();
    }

    private List<TrackMeta> visibleResults() {
        if (this.collection != null) {
            final List<TrackMeta> all = this.collection.tracks();
            final int from = Math.min(all.size(), this.searchPage * PAGE);
            final List<TrackMeta> page = all.subList(from, Math.min(all.size(), from + PAGE));
            this.enrichVisible(page);
            return page;
        }
        return this.results;
    }

    /** Collection listings from embed pages carry no covers; fetch them for the rows on screen. */
    private void enrichVisible(List<TrackMeta> page) {
        for (TrackMeta track : page) {
            if (track.coverUrl() == null) {
                final TrackCollection owner = this.collection;
                this.manager.catalogs().enrich(track).thenAccept(full -> this.manager.sync(() -> {
                    if (full.coverUrl() != null && owner == this.collection) {
                        final List<TrackMeta> tracks = new ArrayList<>(owner.tracks());
                        final int index = tracks.indexOf(track);
                        if (index >= 0) {
                            tracks.set(index, full);
                            this.collection = new TrackCollection(owner.provider(), owner.id(), owner.kind(),
                                    owner.title(), owner.owner(), owner.coverUrl(), owner.url(), tracks, owner.total());
                            this.requestRender();
                        }
                    }
                }));
            }
        }
    }

    private void enqueue(List<TrackMeta> tracks) {
        final int accepted = this.station.enqueue(tracks, this.player.uuid(), this.player.name(), false);
        if (accepted == 0) {
            this.notice("Очередь заполнена", true);
        } else if (tracks.size() == 1) {
            this.notice("В очереди: " + tracks.getFirst().title(), false);
        } else {
            this.notice("Добавлено " + Durations.tracks(accepted).toLowerCase(Locale.ROOT), false);
        }
    }

    private String sourceName(TrackMeta meta) {
        return this.manager.catalogs().get(meta.provider()).map(c -> c.displayName()).orElse(meta.provider());
    }

    private int labelOf(QueueEntry entry) {
        if (entry == null) {
            return Tone.NEUTRAL_LABEL;
        }
        final CoverArt cover = this.manager.cover(entry.meta().coverUrl(), this::requestRender);
        return cover == null ? Tone.NEUTRAL_LABEL : cover.labelColor();
    }
}
