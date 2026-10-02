package su.nuv.radio.menu.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cover reduced to what the radio can draw: square RGB grids (64, 32, 16 and 8 cells) and the
 * colour the disc label takes.
 */
public final class CoverArt {

    public static final int SOURCE = 64;

    private final int[] source;
    private final int labelColor;
    private final Map<Integer, int[]> grids = new ConcurrentHashMap<>();

    private CoverArt(int[] source, int labelColor) {
        this.source = source;
        this.labelColor = labelColor;
    }

    /** Builds from any picture: dark letterbox bars are trimmed, then the centre square is used. */
    public static CoverArt from(BufferedImage picture) {
        final BufferedImage square = centreSquare(trimBars(picture));
        final int[] pixels = downscale(square, SOURCE);
        return new CoverArt(pixels, labelColor(downscaleGrid(pixels, SOURCE, 32), 32));
    }

    /** For previews and tests. */
    public static CoverArt fromPixels(int[] rgb64) {
        return new CoverArt(rgb64.clone(), labelColor(downscaleGrid(rgb64, SOURCE, 32), 32));
    }

    public int labelColor() {
        return this.labelColor;
    }

    /** The bar/accent colour: the label colour lifted enough to read on a dark track. */
    public int accentColor() {
        return accentOf(this.labelColor);
    }

    public static int accentOf(int rgb) {
        final float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        return Color.HSBtoRGB(hsb[0], Math.min(hsb[1], 0.85f), Math.max(hsb[2], 0.7f)) & 0xFFFFFF;
    }

    /** Plain RGB values for a {@code size}×{@code size} grid, row-major. */
    public int[] grid(int size) {
        return this.grids.computeIfAbsent(size, s -> SOURCE % s == 0 ? downscaleGrid(this.source, SOURCE, s)
                : resample(this.source, SOURCE, s));
    }

    /** Area-weighted resample for sizes that do not divide the source. */
    static int[] resample(int[] pixels, int side, int size) {
        final int[] out = new int[size * size];
        final double f = side / (double) size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double r = 0;
                double g = 0;
                double b = 0;
                double weight = 0;
                final double x0 = x * f;
                final double y0 = y * f;
                for (int yy = (int) y0; yy < Math.min(side, Math.ceil(y0 + f)); yy++) {
                    final double wy = Math.min(yy + 1, y0 + f) - Math.max(yy, y0);
                    for (int xx = (int) x0; xx < Math.min(side, Math.ceil(x0 + f)); xx++) {
                        final double w = wy * (Math.min(xx + 1, x0 + f) - Math.max(xx, x0));
                        final int p = pixels[yy * side + xx];
                        r += ((p >> 16) & 0xFF) * w;
                        g += ((p >> 8) & 0xFF) * w;
                        b += (p & 0xFF) * w;
                        weight += w;
                    }
                }
                out[y * size + x] = (int) Math.round(r / weight) << 16 | (int) Math.round(g / weight) << 8
                        | (int) Math.round(b / weight);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ image maths

    static BufferedImage trimBars(BufferedImage image) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        int top = 0;
        while (top < h / 3 && darkRow(image, top)) {
            top++;
        }
        int bottom = h - 1;
        while (bottom > h * 2 / 3 && darkRow(image, bottom)) {
            bottom--;
        }
        if (top == 0 && bottom == h - 1) {
            return image;
        }
        return image.getSubimage(0, top, w, bottom - top + 1);
    }

    private static boolean darkRow(BufferedImage image, int y) {
        final int step = Math.max(1, image.getWidth() / 24);
        for (int x = 0; x < image.getWidth(); x += step) {
            final int rgb = image.getRGB(x, y);
            if (((rgb >> 16) & 0xFF) > 18 || ((rgb >> 8) & 0xFF) > 18 || (rgb & 0xFF) > 18) {
                return false;
            }
        }
        return true;
    }

    static BufferedImage centreSquare(BufferedImage image) {
        final int side = Math.min(image.getWidth(), image.getHeight());
        final int x = (image.getWidth() - side) / 2;
        final int y = (image.getHeight() - side) / 2;
        return image.getSubimage(x, y, side, side);
    }

    /** Area-averaged downscale of a square image to {@code size}² RGB values. */
    static int[] downscale(BufferedImage image, int size) {
        BufferedImage current = toRgb(image);
        // halve with bilinear until close, then box-average the rest
        while (current.getWidth() >= size * 4) {
            final int next = current.getWidth() / 2;
            final BufferedImage half = new BufferedImage(next, next, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, next, next, null);
            g.dispose();
            current = half;
        }
        final int src = current.getWidth();
        final int[] in = current.getRGB(0, 0, src, src, null, 0, src);
        final int[] out = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                final int x0 = x * src / size;
                final int x1 = Math.max(x0 + 1, (x + 1) * src / size);
                final int y0 = y * src / size;
                final int y1 = Math.max(y0 + 1, (y + 1) * src / size);
                long r = 0;
                long gg = 0;
                long b = 0;
                int n = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        final int p = in[yy * src + xx];
                        r += (p >> 16) & 0xFF;
                        gg += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                out[y * size + x] = (int) ((r / n) << 16 | (gg / n) << 8 | (b / n));
            }
        }
        return out;
    }

    private static BufferedImage toRgb(BufferedImage image) {
        final BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = rgb.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /** Box downscale of an RGB grid whose side is a multiple of {@code size}. */
    static int[] downscaleGrid(int[] pixels, int side, int size) {
        if (side == size) {
            return pixels.clone();
        }
        final int f = side / size;
        final int[] out = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int yy = 0; yy < f; yy++) {
                    for (int xx = 0; xx < f; xx++) {
                        final int p = pixels[(y * f + yy) * side + x * f + xx];
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                    }
                }
                final int n = f * f;
                out[y * size + x] = (r / n) << 16 | (g / n) << 8 | (b / n);
            }
        }
        return out;
    }

    /**
     * The label colour: the strongest saturated hue family weighted by area and vividness, pushed
     * into a range that reads on a black record. Near-monochrome covers get a paper white label.
     */
    static int labelColor(int[] pixels, int size) {
        final int bins = 24;
        final double[] weight = new double[bins];
        final double[][] sum = new double[bins][3];
        double saturatedTotal = 0;
        long lightSum = 0;
        for (int p : pixels) {
            final int r = (p >> 16) & 0xFF;
            final int g = (p >> 8) & 0xFF;
            final int b = p & 0xFF;
            lightSum += (r + g + b) / 3;
            final float[] hsb = Color.RGBtoHSB(r, g, b, null);
            final double vivid = hsb[1] * hsb[1] * (hsb[2] < 0.15 ? 0 : Math.min(1.0, hsb[2] * 1.4));
            if (vivid <= 0.02) {
                continue;
            }
            final int bin = Math.min(bins - 1, (int) (hsb[0] * bins));
            weight[bin] += vivid;
            sum[bin][0] += r * vivid;
            sum[bin][1] += g * vivid;
            sum[bin][2] += b * vivid;
            saturatedTotal += vivid;
        }
        if (saturatedTotal < pixels.length * 0.02) {
            final int average = (int) (lightSum / pixels.length);
            return average > 150 ? 0xF0EDE6 : 0xD8D4CC;
        }
        int best = 0;
        double bestWeight = -1;
        for (int i = 0; i < bins; i++) {
            // neighbouring bins count half, so a hue split across a bin edge still wins
            final double w = weight[i] + 0.5 * (weight[(i + 1) % bins] + weight[(i + bins - 1) % bins]);
            if (w > bestWeight) {
                bestWeight = w;
                best = i;
            }
        }
        final int r = (int) (sum[best][0] / weight[best]);
        final int g = (int) (sum[best][1] / weight[best]);
        final int b = (int) (sum[best][2] / weight[best]);
        final float[] hsb = Color.RGBtoHSB(r, g, b, null);
        final float saturation = Math.max(0.35f, Math.min(0.92f, hsb[1]));
        final float brightness = Math.max(0.62f, Math.min(0.96f, hsb[2]));
        return Color.HSBtoRGB(hsb[0], saturation, brightness) & 0xFFFFFF;
    }
}
