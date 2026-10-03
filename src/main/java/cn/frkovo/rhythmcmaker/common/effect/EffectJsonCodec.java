package cn.frkovo.rhythmcmaker.common.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import cn.frkovo.rhythmcmaker.common.effect.animation.EffectAnimationJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Persistence boundary for the compatible eventType/beat/properties effect JSON shape. */
public final class EffectJsonCodec {
    private EffectJsonCodec() {
    }

    public static EffectInstance read(JsonObject source) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        String typeFieldName = source.has("eventType") ? "eventType" : "type";
        if (!source.has(typeFieldName) || !source.get(typeFieldName).isJsonPrimitive()) throw new IllegalArgumentException("effect eventType is missing");
        String eventType = source.get(typeFieldName).getAsString();
        double beat = source.has("beat") && source.get("beat").isJsonPrimitive() ? source.get("beat").getAsDouble() : 0.0;
        List<EffectPropertyValue> properties = readProperties(source.get("properties"));
        cn.frkovo.rhythmcmaker.common.effect.animation.EffectAnimation animation = EffectAnimationJson.read(source).orElse(null);
        List<EffectRawField> rawFields = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (entry.getKey().equals(typeFieldName)
                    || entry.getKey().equals("beat") || entry.getKey().equals("properties")) continue;
            rawFields.add(new EffectRawField(entry.getKey(), entry.getValue().toString()));
        }
        return new EffectInstance(eventType, typeFieldName, beat, properties,
                source.has("properties") && source.get("properties").isJsonObject(), animation, rawFields);
    }

    public static JsonObject write(EffectInstance effect) {
        if (effect == null) throw new IllegalArgumentException("effect must not be null");
        JsonObject result = new JsonObject();
        result.addProperty(effect.typeFieldName(), effect.eventType());
        result.addProperty("beat", effect.beat());
        if (effect.propertiesPresent() || !effect.properties().isEmpty()) {
            JsonObject properties = new JsonObject();
            for (EffectPropertyValue property : effect.properties()) properties.add(property.name(), toJson(property.value()));
            result.add("properties", properties);
        }
        if (effect.hasAnimation()) {
            JsonObject serialized = EffectAnimationJson.write(result, effect.animation().channels());
            result.add("animation", serialized.get("animation").deepCopy());
        }
        for (EffectRawField field : effect.rawFields()) {
            if (result.has(field.name())) continue;
            result.add(field.name(), parseRaw(field.jsonValue()));
        }
        return result;
    }

    private static List<EffectPropertyValue> readProperties(JsonElement element) {
        if (element == null || !element.isJsonObject()) return List.of();
        List<EffectPropertyValue> properties = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) properties.add(new EffectPropertyValue(entry.getKey(), fromJson(entry.getValue())));
        return properties;
    }

    private static EffectValue fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return EffectValue.rawJson(JsonNull.INSTANCE.toString());
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<Double> vector = new ArrayList<>();
            boolean numeric = !array.isEmpty();
            for (JsonElement value : array) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) { numeric = false; break; }
                vector.add(value.getAsDouble());
            }
            return numeric ? EffectValue.vector(vector) : EffectValue.rawJson(element.toString());
        }
        if (!element.isJsonPrimitive()) return EffectValue.rawJson(element.toString());
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return EffectValue.bool(primitive.getAsBoolean());
        if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            return Math.rint(value) == value ? EffectValue.integer((long) value) : EffectValue.decimal(value);
        }
        return EffectValue.text(primitive.getAsString());
    }

    private static JsonElement toJson(EffectValue value) {
        return switch (value.kind()) {
            case TEXT, ENUM -> new JsonPrimitive(value.textValue());
            case INTEGER -> new JsonPrimitive((long) value.numericValue());
            case DECIMAL, COLOR -> new JsonPrimitive(value.numericValue());
            case BOOLEAN -> new JsonPrimitive(value.booleanValue());
            case INTEGER_ARRAY -> {
                JsonArray array = new JsonArray();
                for (long item : value.integerArrayValue()) array.add(item);
                yield array;
            }
            case JSON -> parseRaw(value.rawJson());
            case VECTOR -> {
                JsonArray array = new JsonArray();
                for (double component : value.vectorValue()) array.add(component);
                yield array;
            }
        };
    }

    private static JsonElement parseRaw(String value) {
        return JsonParser.parseString(value);
    }
}
