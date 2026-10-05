package cn.frkovo.rhythmcmaker.common.effect.parameter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;

public final class EffectParameterMigration {
    private static final EffectParameterMigration INSTANCE = new EffectParameterMigration();

    private EffectParameterMigration() {
    }

    public static EffectParameterMigration getInstance() {
        return INSTANCE;
    }

    public JsonObject convert(JsonObject event) {
        EffectParameterCodec codec = EffectParameterCodec.getInstance();
        String type = codec.type(event);
        JsonObject result = event.has("properties") && event.get("properties").isJsonObject()
                ? event.getAsJsonObject("properties").deepCopy() : new JsonObject();
        EffectParameterType schema = EffectParameterSchema.getInstance().find(type);
        if (schema == null) return result;
        for (EffectParameterField field : schema.fields()) {
            JsonElement value = codec.read(event, field.name());
            if (value != null && !result.has(field.name())) result.add(field.name(), value.deepCopy());
        }
        if (type.equals("TITLE") && !result.has("title") && string(result.get("text"))) {
            result.add("title", result.get("text").deepCopy());
            result.remove("text");
        }
        if ((type.equals("MESSAGE") || type.equals("HOLOGRAM")) && !result.has("contents") && string(result.get("text"))) {
            JsonArray contents = new JsonArray();
            contents.add(result.get("text").getAsString());
            result.add("contents", contents);
            result.remove("text");
        }
        if (type.equals("HOLOGRAM")) coordinates(result, "location", false);
        if (type.equals("TEXT_DISPLAY") || type.equals("TEXT_DISPLAY_EFFECT")) {
            coordinates(result, "position", false);
            if (number(result.get("scale"))) {
                JsonArray scale = new JsonArray();
                for (int component = 0; component < 3; component++) scale.add(result.get("scale").deepCopy());
                result.add("scale", scale);
            }
            if (!result.has("targetOpacity") && number(result.get("opacity"))) {
                result.add("targetOpacity", result.get("opacity").deepCopy());
                result.remove("opacity");
            }
        }
        if (type.equals("FIREWORK")) {
            coordinates(result, "locations", true);
            if (!result.has("colors") && number(result.get("color"))) {
                int packed = result.get("color").getAsInt();
                JsonArray color = new JsonArray();
                color.add((packed >>> 16) & 255);
                color.add((packed >>> 8) & 255);
                color.add(packed & 255);
                JsonArray colors = new JsonArray();
                colors.add(color);
                result.add("colors", colors);
                result.remove("color");
            }
        }
        if (type.equals("EFFECT") && !result.has("effectId") && number(result.get("effect"))) {
            result.add("effectId", result.get("effect").deepCopy());
            result.remove("effect");
        }
        if (type.equals("HIDE_NOTES") && result.has("noteTypes") && result.get("noteTypes").isJsonArray()) {
            JsonArray values = result.getAsJsonArray("noteTypes");
            if (values.asList().stream().allMatch(value -> number(value) && value.getAsDouble() == value.getAsInt()
                    && value.getAsInt() >= 0 && value.getAsInt() <= 3)) {
                JsonArray names = new JsonArray();
                List<String> noteTypes = List.of("TAP", "LOOK", "HOLD", "DODGE");
                for (JsonElement value : values) names.add(noteTypes.get(value.getAsInt()));
                result.add("noteTypes", names);
            }
        }
        return result;
    }

    private void coordinates(JsonObject properties, String target, boolean list) {
        if (properties.has(target) || !number(properties.get("x")) || !number(properties.get("y")) || !number(properties.get("z"))) return;
        JsonArray vector = new JsonArray();
        for (String axis : List.of("x", "y", "z")) {
            vector.add(properties.get(axis).deepCopy());
            properties.remove(axis);
        }
        if (list) {
            JsonArray vectors = new JsonArray();
            vectors.add(vector);
            properties.add(target, vectors);
        } else properties.add(target, vector);
    }

    private boolean string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private boolean number(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && Double.isFinite(value.getAsDouble());
    }
}
