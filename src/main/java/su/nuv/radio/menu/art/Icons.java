package su.nuv.radio.menu.art;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 16×16 pixel icons in one grammar: white 2-px strokes with the vanilla text drop shadow
 * (25 % grey, one pixel down-right). Tinting the element recolours the white, never the shadow.
 *
 * <p>Every mask is drawn on an 11×11 grid centred on pixel 5 and placed at (2, 2), so the ink
 * spans pixels 2..12 and the shadow reaches 13: centred on a button face (rows 1..13 between the
 * bevels) with the same margin on every side.
 */
public final class Icons {

    public static final String PLAY = "jr_i_play.png";
    public static final String PAUSE = "jr_i_pause.png";
    public static final String NEXT = "jr_i_next.png";
    public static final String PREV = "jr_i_prev.png";
    public static final String STOP = "jr_i_stop.png";
    public static final String REPEAT = "jr_i_repeat.png";
    public static final String REPEAT_ONE = "jr_i_repeat_one.png";
    public static final String SHUFFLE = "jr_i_shuffle.png";
    public static final String PLUS = "jr_i_plus.png";
    public static final String MINUS = "jr_i_minus.png";
    public static final String CLOSE = "jr_i_close.png";
    public static final String SEARCH = "jr_i_search.png";
    public static final String UP = "jr_i_up.png";
    public static final String QUEUE = "jr_i_queue.png";
    public static final String NOTE = "jr_i_note.png";
    public static final String LEFT = "jr_i_left.png";
    public static final String RIGHT = "jr_i_right.png";
    public static final String CHECK = "jr_i_check.png";
    public static final String ALERT = "jr_i_alert.png";
    public static final String CLOCK = "jr_i_clock.png";

    private static final Map<String, String[]> MASKS = new LinkedHashMap<>();

    static {
        MASKS.put(PLAY, new String[]{
                "..#........",
                "..##.......",
                "..####.....",
                "..#####....",
                "..#######..",
                "..########.",
                "..#######..",
                "..#####....",
                "..####.....",
                "..##.......",
                "..#........"});
        MASKS.put(PAUSE, new String[]{
                "...........",
                ".###...###.",
                ".###...###.",
                ".###...###.",
                ".###...###.",
                ".###...###.",
                ".###...###.",
                ".###...###.",
                ".###...###.",
                ".###...###.",
                "..........."});
        MASKS.put(NEXT, new String[]{
                ".#......##.",
                ".##.....##.",
                ".###....##.",
                ".####...##.",
                ".#####..##.",
                ".######.##.",
                ".#####..##.",
                ".####...##.",
                ".###....##.",
                ".##.....##.",
                ".#......##."});
        MASKS.put(PREV, mirror(MASKS.get(NEXT)));
        MASKS.put(STOP, new String[]{
                "...........",
                ".#########.",
                ".#########.",
                ".#########.",
                ".#########.",
                ".#########.",
                ".#########.",
                ".#########.",
                ".#########.",
                ".#########.",
                "..........."});
        MASKS.put(REPEAT, new String[]{
                "........#..",
                ".#########.",
                ".#########.",
                ".##.....#..",
                ".##........",
                ".##.....##.",
                "........##.",
                "..#.....##.",
                ".#########.",
                ".#########.",
                "..#........"});
        MASKS.put(REPEAT_ONE, new String[]{
                "........#..",
                ".#########.",
                ".#########.",
                ".##..#..#..",
                ".##.##.....",
                ".##..#..##.",
                ".....#..##.",
                "..#..#..##.",
                ".#########.",
                ".#########.",
                "..#........"});
        MASKS.put(SHUFFLE, new String[]{
                "........#..",
                "........##.",
                "##....#####",
                ".##..##.##.",
                "..####..#..",
                "...##......",
                "..####..#..",
                ".##..##.##.",
                "##....#####",
                "........##.",
                "........#.."});
        MASKS.put(PLUS, new String[]{
                "...........",
                "....###....",
                "....###....",
                "....###....",
                ".#########.",
                ".#########.",
                ".#########.",
                "....###....",
                "....###....",
                "....###....",
                "..........."});
        MASKS.put(MINUS, new String[]{
                "...........",
                "...........",
                "...........",
                "...........",
                ".#########.",
                ".#########.",
                ".#########.",
                "...........",
                "...........",
                "...........",
                "..........."});
        MASKS.put(CLOSE, new String[]{
                "...........",
                ".##.....##.",
                ".###...###.",
                "..###.###..",
                "...#####...",
                "....###....",
                "...#####...",
                "..###.###..",
                ".###...###.",
                ".##.....##.",
                "..........."});
        MASKS.put(SEARCH, new String[]{
                "..####.....",
                ".######....",
                "##....##...",
                "##....##...",
                "##....##...",
                "##....##...",
                ".######....",
                "..######...",
                "......###..",
                ".......###.",
                "........###"});
        MASKS.put(UP, new String[]{
                "...........",
                "...........",
                "...........",
                "....###....",
                "...#####...",
                "..###.###..",
                ".###...###.",
                "###.....###",
                "...........",
                "...........",
                "..........."});
        MASKS.put(LEFT, transpose(MASKS.get(UP)));
        MASKS.put(RIGHT, mirror(MASKS.get(LEFT)));
        MASKS.put(QUEUE, new String[]{
                "##.########",
                "##.########",
                "...........",
                "##.########",
                "##.########",
                "...........",
                "##.########",
                "##.########",
                "...........",
                "##.#####...",
                "##.#####..."});
        MASKS.put(NOTE, new String[]{
                "...########",
                "...########",
                "...##....##",
                "...##....##",
                "...##....##",
                "...##....##",
                "...##....##",
                ".####..####",
                "#####.#####",
                "#####.#####",
                ".###...###."});
        MASKS.put(CHECK, new String[]{
                "...........",
                "...........",
                "........###",
                ".......###.",
                "......###..",
                "###..###...",
                ".######....",
                "..####.....",
                "...##......",
                "...........",
                "..........."});
        MASKS.put(ALERT, new String[]{
                "...........",
                ".....#.....",
                "....###....",
                "....#.#....",
                "...##.##...",
                "...##.##...",
                "..###.###..",
                "..#######..",
                ".####.####.",
                "###########",
                "..........."});
        MASKS.put(CLOCK, new String[]{
                "...#####...",
                ".###...###.",
                ".##..#..##.",
                "##...#...##",
                "##...#...##",
                "##...###.##",
                "##.......##",
                "##.......##",
                ".##.....##.",
                ".###...###.",
                "...#####..."});
    }

    private Icons() {
    }

    public static final int GRID = 11;
    private static final int OFFSET = 2;

    /** Left-right flip on the grid, so a centred icon stays centred. */
    private static String[] mirror(String[] rows) {
        final String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) {
            out[i] = new StringBuilder(rows[i]).reverse().toString();
        }
        return out;
    }

    /** Swaps rows and columns: an up chevron becomes a left one. */
    private static String[] transpose(String[] rows) {
        final String[] out = new String[GRID];
        for (int x = 0; x < GRID; x++) {
            final StringBuilder column = new StringBuilder();
            for (int y = 0; y < GRID; y++) {
                column.append(rows[y].charAt(x));
            }
            out[x] = column.toString();
        }
        return out;
    }

    /** The raw masks, for tests. */
    public static Map<String, String[]> masks() {
        return java.util.Collections.unmodifiableMap(MASKS);
    }

    /** One icon by name, 16×16. */
    public static BufferedImage image(String name) {
        final String[] rows = MASKS.get(name);
        if (rows == null) {
            throw new IllegalArgumentException("No icon " + name);
        }
        return render(rows);
    }

    static Map<String, BufferedImage> all() {
        final Map<String, BufferedImage> out = new LinkedHashMap<>();
        MASKS.forEach((name, rows) -> out.put(name, render(rows)));
        return out;
    }

    private static BufferedImage render(String[] rows) {
        final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                if (ink(rows, x, y)) {
                    image.setRGB(x + OFFSET, y + OFFSET, 0xFFFFFFFF);
                    if (!ink(rows, x + 1, y + 1)) {
                        image.setRGB(x + OFFSET + 1, y + OFFSET + 1, 0xFF3F3F3F);
                    }
                }
            }
        }
        return image;
    }

    private static boolean ink(String[] rows, int x, int y) {
        return y < GRID && x < GRID && rows[y].charAt(x) == '#';
    }
}
