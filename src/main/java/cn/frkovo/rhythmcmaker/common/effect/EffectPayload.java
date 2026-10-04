package cn.frkovo.rhythmcmaker.common.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class EffectPayload {
    private static final int VECTOR_SIZE = 3;

    private EffectPayload() {
    }

    public static JsonElement value(JsonObject effect, String key) {
        if (effect == null) return null;
        if (effect.has(key)) return effect.get(key);
        JsonObject properties = properties(effect);
        return properties == null ? null : properties.get(key);
    }

    public static JsonObject properties(JsonObject effect) {
        if (effect == null || !effect.has("properties") || !effect.get("properties").isJsonObject()) return null;
        return effect.getAsJsonObject("properties");
    }

    public static String string(JsonObject effect, String key, String fallback) {
        try {
            JsonElement value = value(effect, key);
            return value == null ? fallback : value.getAsString();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static double doubleValue(JsonObject effect, String key, double fallback) {
        try {
            JsonElement value = value(effect, key);
            return value == null ? fallback : value.getAsDouble();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static int integer(JsonObject effect, String key, int fallback) {
        try {
            JsonElement value = value(effect, key);
            return value == null ? fallback : value.getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static float floatValue(JsonObject effect, String key, float fallback) {
        try {
            JsonElement value = value(effect, key);
            return value == null ? fallback : value.getAsFloat();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static boolean booleanValue(JsonObject effect, String key, boolean fallback) {
        try {
            JsonElement value = value(effect, key);
            return value == null ? fallback : value.getAsBoolean();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static double[] vector(JsonObject effect, String key, double[] fallback) {
        try {
            JsonElement value = value(effect, key);
            if (!(value instanceof JsonArray array) || array.size() < VECTOR_SIZE) return fallback;
            return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
        } catch (RuntimeException exception) {
            return fallback;
        }
    }
}
