package cn.frkovo.rhythmcmaker.common.chart;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class Rhythmc3EffectCodec {
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
        if (source.has("properties") && source.get("properties").isJsonObject()) result.add("properties", source.getAsJsonObject("properties").deepCopy());
        return result;
    }
}
