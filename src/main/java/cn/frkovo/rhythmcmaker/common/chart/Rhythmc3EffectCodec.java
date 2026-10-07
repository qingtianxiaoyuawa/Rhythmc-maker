package cn.frkovo.rhythmcmaker.common.chart;

import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterCodec;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterField;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterMigration;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterSchema;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Set;

public final class Rhythmc3EffectCodec {
    private static final Set<String> LOCAL_PREVIEW_KEYS = Set.of(
            "viewRange", "previewRange", "displayRange", "rhythmcPreview", "previewTag", "entityTags", "arenaPath");

    private Rhythmc3EffectCodec() {}

    public static JsonObject importEffect(JsonObject source) {
        JsonObject result = new JsonObject();
        String type = source.has("effectType") ? source.get("effectType").getAsString() : source.has("eventType") ? source.get("eventType").getAsString() : source.has("type") ? source.get("type").getAsString() : "";
        if (type.equalsIgnoreCase("CHANGE_ARENA")) type = "ARENA";
        result.addProperty("eventType", type.toUpperCase(java.util.Locale.ROOT));
        if (source.has("beat")) result.add("beat", source.get("beat").deepCopy());
        JsonObject properties = source.has("properties") && source.get("properties").isJsonObject()
                ? source.getAsJsonObject("properties").deepCopy() : new JsonObject();
        for (var entry : source.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("effectType") && !key.equals("eventType") && !key.equals("type") && !key.equals("beat") && !key.equals("properties")) properties.add(key, entry.getValue().deepCopy());
        }
        result.add("properties", properties);
        result.add("properties", EffectParameterMigration.getInstance().convert(result));
        return result;
    }

    public static JsonObject exportEffect(JsonObject source) {
        JsonObject normalized = importEffect(source);
        JsonObject result = new JsonObject();
        String type = normalized.get("eventType").getAsString();
        if (type.equals("ACTIONBAR")) throw new IllegalArgumentException("ACTIONBAR 是 Maker 扩展，不是官方 3.0 特效；请改用 TITLE 或 MESSAGE 后导出");
        List<String> errors = EffectParameterCodec.getInstance().validateEvent(normalized);
        if (!errors.isEmpty()) throw new IllegalArgumentException(type + " 参数错误：" + String.join("；", errors));
        result.addProperty("effectType", type);
        if (normalized.has("beat")) result.add("beat", normalized.get("beat").deepCopy());
        JsonObject properties = exportProperties(normalized.getAsJsonObject("properties"));
        EffectParameterType definition = EffectParameterSchema.getInstance().find(type);
        if (definition != null) {
            for (EffectParameterField field : definition.fields()) {
                JsonElement value = EffectParameterCodec.getInstance().read(normalized, field.name());
                if (value != null) properties.add(field.name(), value.deepCopy());
            }
        }
        result.add("properties", properties);
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
