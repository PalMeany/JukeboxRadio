package su.nuv.radio.config;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only view of a parsed YAML document with dotted paths, as config.yml and links.yml use. */
public final class YamlTree {

    private final Map<?, ?> root;

    private YamlTree(Map<?, ?> root) {
        this.root = root == null ? Map.of() : root;
    }

    public static YamlTree parse(String text) {
        final Object loaded = new Yaml().load(text == null ? "" : text);
        return new YamlTree(loaded instanceof Map<?, ?> map ? map : Map.of());
    }

    public static YamlTree of(Map<?, ?> map) {
        return new YamlTree(map);
    }

    private Object get(String path) {
        Object node = this.root;
        for (String part : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> map)) {
                return null;
            }
            node = map.get(part);
        }
        return node;
    }

    public String getString(String path, String fallback) {
        final Object value = this.get(path);
        return value == null || value instanceof Map<?, ?> || value instanceof List<?> ? fallback : String.valueOf(value);
    }

    public int getInt(String path, int fallback) {
        final Object value = this.get(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    public double getDouble(String path, double fallback) {
        final Object value = this.get(path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value).strip());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    public boolean getBoolean(String path, boolean fallback) {
        final Object value = this.get(path);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value).strip());
    }

    public List<String> getStringList(String path) {
        final Object value = this.get(path);
        final List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
        }
        return out;
    }

    /** The section at {@code path}, or an empty one. */
    public YamlTree section(String path) {
        final Object value = this.get(path);
        return new YamlTree(value instanceof Map<?, ?> map ? map : Map.of());
    }

    /** Keys of this section, in file order. */
    public List<String> keys() {
        final List<String> keys = new ArrayList<>();
        this.root.keySet().forEach(key -> keys.add(String.valueOf(key)));
        return keys;
    }
}
