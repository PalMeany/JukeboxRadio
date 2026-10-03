package su.nuv.radio.menu.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import su.nuv.radio.menu.MenuLayout;
import su.nuv.radio.menu.text.FontChars;
import su.nuv.radio.menu.text.Glyphs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates the radio's resource pack for Minecraft 26.3 (resource format 97): menu backgrounds as
 * font glyphs, text fonts shifted to every menu line, pixel glyphs, and the item models the menu
 * places in slots. Output is deterministic, so an unchanged plugin gives the same SHA-1 and
 * players do not download again.
 */
public final class PackBuilder {

    public static final int FORMAT_MIN = 97;
    public static final int FORMAT_MAX = 99;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Map<String, byte[]> files = new TreeMap<>();
    private final int spinTicks;

    /** @param spinTicks ticks per disc revolution; the 16-frame spin is played at spinTicks / 16 per frame */
    public PackBuilder(int spinTicks) {
        this.spinTicks = spinTicks;
    }

    public record Built(byte[] zip, String sha1) {
    }

    public Built build() {
        this.meta();
        this.fonts();
        this.textures();
        this.models();
        return this.zip();
    }

    // ------------------------------------------------------------------ pack.mcmeta

    private void meta() {
        final JsonObject pack = new JsonObject();
        pack.addProperty("description", "JukeboxRadio — меню радио");
        pack.addProperty("min_format", FORMAT_MIN);
        pack.addProperty("max_format", FORMAT_MAX);
        final JsonObject root = new JsonObject();
        root.add("pack", pack);
        this.json("pack.mcmeta", root);
    }

    // ------------------------------------------------------------------ fonts

    private void fonts() {
        final JsonArray ui = new JsonArray();
        final JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        final JsonObject advances = new JsonObject();
        for (int bit = 0; bit < FontChars.SPACE_BITS; bit++) {
            advances.addProperty(String.valueOf((char) (FontChars.NEG + bit)), -(1 << bit));
            advances.addProperty(String.valueOf((char) (FontChars.POS + bit)), 1 << bit);
        }
        space.add("advances", advances);
        ui.add(space);

        this.background(ui, FontChars.BG_MAIN_L, "main_l");
        this.background(ui, FontChars.BG_MAIN_R, "main_r");
        this.background(ui, FontChars.BG_LIST_L, "list_l");
        this.background(ui, FontChars.BG_LIST_R, "list_r");
        this.background(ui, FontChars.BG_ZOOM_L, "zoom_l");
        this.background(ui, FontChars.BG_ZOOM_R, "zoom_r");

        for (int y = 0; y < FontChars.PIXEL_ROWS; y++) {
            ui.add(bitmap(MenuLayout.NS + ":font/px.png", 32, FontChars.ascentFor(y, 7) , FontChars.pixel(y)));
        }
        this.font("ui", ui);

        final JsonArray vanilla = this.vanillaProviders();
        for (int top : FontChars.textTops()) {
            final JsonArray providers = new JsonArray();
            final JsonObject blank = new JsonObject();
            blank.addProperty("type", "space");
            final JsonObject blankAdvances = new JsonObject();
            blankAdvances.addProperty(" ", 4);
            blank.add("advances", blankAdvances);
            providers.add(blank);
            for (JsonElement element : vanilla) {
                final JsonObject provider = element.getAsJsonObject().deepCopy();
                final int ascent = provider.get("ascent").getAsInt();
                provider.addProperty("ascent", FontChars.ascentFor(top, ascent));
                providers.add(provider);
            }
            this.font("t" + top, providers);
        }
    }

    private void background(JsonArray ui, char glyph, String texture) {
        ui.add(bitmap(MenuLayout.NS + ":gui/" + texture + ".png", MenuLayout.CHEST_H, FontChars.ascentFor(0, 7),
                glyph));
    }

    private static JsonObject bitmap(String file, int height, int ascent, char glyph) {
        final JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", file);
        provider.addProperty("height", height);
        provider.addProperty("ascent", ascent);
        final JsonArray chars = new JsonArray();
        chars.add(String.valueOf(glyph));
        provider.add("chars", chars);
        return provider;
    }

    /**
     * The vanilla bitmap providers the text fonts re-anchor, cut down to {@link Glyphs#inMenuCharset}:
     * other cells become U+0000, which the client skips, and sheets left empty are dropped.
     */
    private JsonArray vanillaProviders() {
        final JsonArray out = new JsonArray();
        for (JsonElement element : allVanillaProviders()) {
            final JsonObject provider = element.getAsJsonObject().deepCopy();
            final JsonArray rows = new JsonArray();
            boolean any = false;
            for (JsonElement row : provider.getAsJsonArray("chars")) {
                final StringBuilder kept = new StringBuilder();
                for (int cp : row.getAsString().codePoints().toArray()) {
                    if (cp != 0 && Glyphs.inMenuCharset(cp)) {
                        kept.appendCodePoint(cp);
                        any = true;
                    } else {
                        kept.append('\u0000');
                    }
                }
                rows.add(kept.toString());
            }
            if (any) {
                provider.add("chars", rows);
                out.add(provider);
            }
        }
        return out;
    }

    private JsonArray allVanillaProviders() {
        try (InputStream in = PackBuilder.class.getClassLoader().getResourceAsStream("vanilla-font-providers.json")) {
            if (in == null) {
                throw new IllegalStateException("vanilla-font-providers.json missing from the jar");
            }
            final JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            final JsonArray out = new JsonArray();
            for (JsonElement provider : root.getAsJsonArray("providers")) {
                if ("bitmap".equals(provider.getAsJsonObject().get("type").getAsString())) {
                    out.add(provider);
                }
            }
            return out;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private void font(String name, JsonArray providers) {
        final JsonObject root = new JsonObject();
        root.add("providers", providers);
        this.json("assets/" + MenuLayout.NS + "/font/" + name + ".json", root);
    }

    // ------------------------------------------------------------------ textures

    private void textures() {
        this.split("main", PackArt.mainBackground());
        this.split("list", PackArt.listBackground());
        this.split("zoom", PackArt.zoomBackground());
        this.png("font/px", PackArt.pixelGlyph());

        this.png("item/disc_spin", PackArt.discStrip(false));
        this.png("item/label_spin", PackArt.discStrip(true));
        final int frametime = Math.max(1, Math.round(this.spinTicks / (float) PackArt.DISC_FRAMES));
        final JsonObject animation = new JsonObject();
        final JsonObject anim = new JsonObject();
        anim.addProperty("frametime", frametime);
        animation.add("animation", anim);
        this.json(texturePath("item/disc_spin") + ".mcmeta", animation);
        this.json(texturePath("item/label_spin") + ".mcmeta", animation);

        this.png("item/disc_still", PackArt.discFrame(0, false, 1, PackArt.DISC_SIZE));
        this.png("item/label_still", PackArt.discFrame(0, true, 1, PackArt.DISC_SIZE));
        for (int f = 0; f < Models.EJECT_FRAMES; f++) {
            final double alpha = 1 - (f + 1) / (double) (Models.EJECT_FRAMES + 1);
            this.png("item/disc_fade_" + f, PackArt.discFrame(f * 12, false, alpha, PackArt.DISC_SIZE));
            this.png("item/label_fade_" + f, PackArt.discFrame(f * 12, true, alpha, PackArt.DISC_SIZE));
        }
        this.png("item/mini_disc", PackArt.discFrame(0, false, 1, 16));
        this.png("item/mini_label", PackArt.discFrame(0, true, 1, 16));
        for (int pose = 0; pose < Models.ARM_ANGLES.size(); pose++) {
            this.png("item/arm_" + pose, PackArt.tonearm(Models.ARM_ANGLES.get(pose)));
        }
        for (String icon : Models.BUTTON_ICONS) {
            for (PackArt.ButtonStyle style : PackArt.ButtonStyle.values()) {
                this.png("item/" + Models.button(icon, style).value(), PackArt.button(icon, style));
            }
        }
        for (int level = 0; level <= 10; level++) {
            this.png("item/volume_" + level, PackArt.volume(level));
        }
        this.png("item/clear", PackArt.clear());
        this.png("item/white", PackArt.white(16, 16));
    }

    /** Cuts a full-width background into the chest part and the side-panel part. */
    private void split(String name, BufferedImage full) {
        this.png("gui/" + name + "_l", full.getSubimage(0, 0, MenuLayout.CHEST_W, MenuLayout.CHEST_H));
        this.png("gui/" + name + "_r", full.getSubimage(MenuLayout.CHEST_W, 0, MenuLayout.EXT_W, MenuLayout.CHEST_H));
    }

    private static String texturePath(String name) {
        return "assets/" + MenuLayout.NS + "/textures/" + name + ".png";
    }

    // ------------------------------------------------------------------ models and item definitions

    private void models() {
        final int discSlotCx = MenuLayout.slotCx(MenuLayout.SLOT_DISC);
        final int discSlotCy = MenuLayout.slotCy(MenuLayout.SLOT_DISC);
        final double centreX = MenuLayout.DISC_CX - discSlotCx;
        final double centreY = -(MenuLayout.DISC_CY - discSlotCy);

        this.disc("disc_spin", "item/disc_spin", "item/label_spin", centreX, centreY, 4, 0);
        this.disc("disc_still", "item/disc_still", "item/label_still", centreX, centreY, 4, 0);

        // the next disc flies from the first up-next slot into the platter, growing and turning
        final double fromX = MenuLayout.slotCx(MenuLayout.SLOT_NEXT) - discSlotCx;
        final double fromY = -(MenuLayout.slotCy(MenuLayout.SLOT_NEXT) - discSlotCy);
        for (int f = 0; f < Models.FLY_FRAMES; f++) {
            final double t = (f + 1) / (double) (Models.FLY_FRAMES + 1);
            final double ease = 1 - Math.pow(1 - t, 2);
            final double x = fromX + (centreX - fromX) * ease;
            final double y = fromY + (centreY - fromY) * ease + Math.sin(t * Math.PI) * 10;
            this.disc("disc_fly_" + f, "item/disc_still", "item/label_still", x, y, 1 + 3 * ease, 150 * (1 - ease));
        }
        // the old one pops up out of the jukebox and fades
        for (int f = 0; f < Models.EJECT_FRAMES; f++) {
            final double t = (f + 1) / (double) Models.EJECT_FRAMES;
            this.disc("disc_eject_" + f, "item/disc_fade_" + f, "item/label_fade_" + f, centreX, centreY + 22 * t,
                    4 - 0.4 * t, 0);
        }

        this.disc("mini_disc", "item/mini_disc", "item/mini_label", 0, 0, 1, 0);
        for (int f = 1; f <= Models.SHIFT_FRAMES; f++) {
            this.disc("mini_shift_" + f, "item/mini_disc", "item/mini_label", -18.0 * f / (Models.SHIFT_FRAMES + 1),
                    0, 1, 0);
        }

        // tonearm pivot sits at the turntable's top-right corner; texture pivot is (52, 10) of 64
        final int armSlotCx = MenuLayout.slotCx(MenuLayout.SLOT_ARM);
        final int armSlotCy = MenuLayout.slotCy(MenuLayout.SLOT_ARM);
        final double armX = (74 - 20) - armSlotCx;
        final double armY = -((22 + 22) - armSlotCy);
        for (int pose = 0; pose < Models.ARM_ANGLES.size(); pose++) {
            this.flat("arm_" + pose, "item/arm_" + pose, armX, armY, 4, true);
        }

        for (String icon : Models.BUTTON_ICONS) {
            for (PackArt.ButtonStyle style : PackArt.ButtonStyle.values()) {
                final String name = Models.button(icon, style).value();
                this.flat(name, "item/" + name, 0, 0, 1, false);
            }
        }
        final JsonArray volumeEntries = new JsonArray();
        for (int level = 0; level <= 10; level++) {
            this.model("volume_" + level, generated(new String[]{"item/volume_" + level}, 0, 0, 1, 0));
            final JsonObject entry = new JsonObject();
            entry.addProperty("threshold", level - 0.5);
            entry.add("model", reference("volume_" + level, 0));
            volumeEntries.add(entry);
        }
        final JsonObject volume = new JsonObject();
        volume.addProperty("type", "minecraft:range_dispatch");
        volume.addProperty("property", "minecraft:custom_model_data");
        volume.addProperty("index", 0);
        volume.add("entries", volumeEntries);
        volume.add("fallback", reference("volume_0", 0));
        this.item("volume", volume, false);

        this.flat("clear", "item/clear", 0, 0, 1, false);
        this.patch("patch_tile", 1.125, true);
        this.patch("patch_slot", 1.0, false);
    }

    private void disc(String name, String vinyl, String label, double x, double y, double scale, double spin) {
        this.model(name, generated(new String[]{vinyl, label}, x, y, scale, spin));
        final JsonObject definition = reference(name, 2);
        this.item(name, definition, true);
    }

    private void flat(String name, String texture, double x, double y, double scale, boolean oversized) {
        this.model(name, generated(new String[]{texture}, x, y, scale, 0));
        this.item(name, reference(name, 0), oversized);
    }

    /** 8×8 cells, each face tinted by its own custom_model_data colour: one tile of a cover. */
    private void patch(String name, double scale, boolean oversized) {
        final JsonObject model = new JsonObject();
        final JsonObject textures = new JsonObject();
        textures.addProperty("p", MenuLayout.NS + ":item/white");
        textures.addProperty("particle", MenuLayout.NS + ":item/white");
        model.add("textures", textures);
        model.addProperty("gui_light", "front");
        final JsonArray elements = new JsonArray();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                final JsonObject element = new JsonObject();
                element.add("from", vector(x * 2, 14 - y * 2, 8));
                element.add("to", vector(x * 2 + 2, 16 - y * 2, 8));
                element.addProperty("shade", false);
                final JsonObject face = new JsonObject();
                face.add("uv", vector4(0, 0, 16, 16));
                face.addProperty("texture", "#p");
                face.addProperty("tintindex", y * 8 + x);
                final JsonObject faces = new JsonObject();
                faces.add("south", face);
                element.add("faces", faces);
                elements.add(element);
            }
        }
        model.add("elements", elements);
        model.add("display", display(0, 0, scale, 0));
        this.model(name, model);
        final JsonObject definition = new JsonObject();
        definition.addProperty("type", "minecraft:model");
        definition.addProperty("model", MenuLayout.NS + ":item/" + name);
        final JsonArray tints = new JsonArray();
        for (int i = 0; i < 64; i++) {
            final JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:custom_model_data");
            tint.addProperty("index", i);
            tint.addProperty("default", 0xFFE4D6B1);
            tints.add(tint);
        }
        definition.add("tints", tints);
        this.item(name, definition, oversized);
    }

    private static JsonObject generated(String[] layers, double x, double y, double scale, double spin) {
        final JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        final JsonObject textures = new JsonObject();
        for (int i = 0; i < layers.length; i++) {
            textures.addProperty("layer" + i, MenuLayout.NS + ":" + layers[i]);
        }
        model.add("textures", textures);
        model.add("display", display(x, y, scale, spin));
        return model;
    }

    private static JsonObject display(double x, double y, double scale, double spin) {
        final JsonObject gui = new JsonObject();
        gui.add("rotation", vector(0, 0, round(spin)));
        gui.add("translation", vector(round(x), round(y), 0));
        gui.add("scale", vector(round(scale), round(scale), round(scale)));
        final JsonObject display = new JsonObject();
        display.add("gui", gui);
        return display;
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    /** An item-model reference; {@code tintLayers} &gt; 0 tints layer 1 from custom_model_data colour 0. */
    private static JsonObject reference(String name, int tintLayers) {
        final JsonObject definition = new JsonObject();
        definition.addProperty("type", "minecraft:model");
        definition.addProperty("model", MenuLayout.NS + ":item/" + name);
        if (tintLayers > 0) {
            final JsonArray tints = new JsonArray();
            final JsonObject white = new JsonObject();
            white.addProperty("type", "minecraft:constant");
            white.addProperty("value", -1);
            tints.add(white);
            final JsonObject label = new JsonObject();
            label.addProperty("type", "minecraft:custom_model_data");
            label.addProperty("index", 0);
            label.addProperty("default", 0xFFE8E4DA);
            tints.add(label);
            definition.add("tints", tints);
        }
        return definition;
    }

    private static JsonArray vector(double a, double b, double c) {
        final JsonArray array = new JsonArray();
        array.add(a);
        array.add(b);
        array.add(c);
        return array;
    }

    private static JsonArray vector4(double a, double b, double c, double d) {
        final JsonArray array = vector(a, b, c);
        array.add(d);
        return array;
    }

    private void model(String name, JsonObject model) {
        this.json("assets/" + MenuLayout.NS + "/models/item/" + name + ".json", model);
    }

    private void item(String name, JsonObject model, boolean oversized) {
        final JsonObject root = new JsonObject();
        root.add("model", model);
        if (oversized) {
            root.addProperty("oversized_in_gui", true);
        }
        this.json("assets/" + MenuLayout.NS + "/items/" + name + ".json", root);
    }

    // ------------------------------------------------------------------ output

    private void png(String name, BufferedImage image) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        this.files.put(texturePath(name), out.toByteArray());
    }

    private void json(String path, JsonElement element) {
        this.files.put(path, GSON.toJson(element).getBytes(StandardCharsets.UTF_8));
    }

    private Built zip() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : this.files.entrySet()) {
                final ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(315532800000L); // fixed timestamp: identical input, identical zip
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        final byte[] data = bytes.toByteArray();
        return new Built(data, sha1(data));
    }

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Writes the pack next to the plugin so owners can merge it into their own server pack. */
    public static void write(Built built, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, built.zip());
    }
}
