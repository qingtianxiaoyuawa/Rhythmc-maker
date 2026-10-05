package cn.frkovo.rhythmcmaker.common.chart;

import com.google.gson.JsonObject;

import java.util.Set;

public final class Rhythmc3EffectCodec {
    private static final Set<String> LOCAL_PREVIEW_KEYS = Set.of(
            "viewRange", "previewRange", "displayRange", "rhythmcPreview", "previewTag", "entityTags");

    private Rhythmc3EffectCodec() {}

    public static JsonObject importEffect(JsonObject source) {
        JsonObject result = new JsonObject();
        String type = source.has("effectType") ? source.get("effectType").getAsString() : source.has("eventType") ? source.get("eventType").getAsString() : source.has("type") ? source.get("type").getAsString() : "";
        result.addProperty("eventType", type);
        if (source.has("beat")) result.add("beat", source.get("beat").deepCopy());
        if (source.has("properties") && source.get("properties").isJsonObject()) result.add("properties", source.getAsJsonObject("properties").deepCopy());
        return result;
    }

    public static JsonObject exportEffect(JsonObject source) {
        JsonObject result = new JsonObject();
        String type = source.has("eventType") ? source.get("eventType").getAsString() : source.has("effectType") ? source.get("effectType").getAsString() : source.has("type") ? source.get("type").getAsString() : "";
        result.addProperty("effectType", type);
        if (source.has("beat")) result.add("beat", source.get("beat").deepCopy());
        if (source.has("properties") && source.get("properties").isJsonObject()) result.add("properties", exportProperties(source.getAsJsonObject("properties")));
        return result;
    }

    public static boolean containsLocalPreviewProperties(JsonObject source) {
        if (source == null || !source.has("properties") || !source.get("properties").isJsonObject()) return false;
        for (String key : LOCAL_PREVIEW_KEYS) if (source.getAsJsonObject("properties").has(key)) return true;
        return false;
    }

    private static JsonObject exportProperties(JsonObject source) {
        JsonObject result = source.deepCopy();
        for (String key : LOCAL_PREVIEW_KEYS) result.remove(key);
        return result;
    }
}