package su.nuv.radio.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Null-safe readers for loosely shaped provider JSON. */
public final class Json {

    private Json() {
    }

    public static JsonObject obj(JsonElement parent, String key) {
        if (parent instanceof JsonObject object && object.get(key) instanceof JsonObject child) {
            return child;
        }
        return null;
    }

    public static JsonArray arr(JsonElement parent, String key) {
        if (parent instanceof JsonObject object && object.get(key) instanceof JsonArray child) {
            return child;
        }
        return new JsonArray();
    }

    public static String str(JsonElement parent, String key) {
        if (parent instanceof JsonObject object) {
            final JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return null;
    }

    public static long num(JsonElement parent, String key, long fallback) {
        if (parent instanceof JsonObject object) {
            final JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                return value.getAsLong();
            }
        }
        return fallback;
    }

    /** Walks nested object keys, returning {@code null} at the first missing step. */
    public static JsonElement path(JsonElement root, String... keys) {
        JsonElement current = root;
        for (String key : keys) {
            if (!(current instanceof JsonObject object)) {
                return null;
            }
            current = object.get(key);
        }
        return current;
    }

    public static List<JsonObject> objects(JsonArray array) {
        final List<JsonObject> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (element instanceof JsonObject object) {
                out.add(object);
            }
        }
        return out;
    }
}
