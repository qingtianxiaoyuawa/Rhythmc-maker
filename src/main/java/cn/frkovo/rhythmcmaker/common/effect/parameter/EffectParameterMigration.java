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
        if (type.equals("EFFECT")) migrateEffect(result);
        if (type.equals("CLEAR_EFFECT")) migrateClearedEffects(result);
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

    private void migrateEffect(JsonObject properties) {
        JsonElement effectId = properties.get("effectId");
        if (effectId == null || !number(effectId) && !string(effectId)) {
            effectId = number(properties.get("effect"))
                    ? properties.get("effect")
                    : firstString(properties, "effect-id", "type", "effect", "potion-effect", "potion");
        }
        if (effectId != null && string(effectId)) {
            properties.addProperty("effectId", toEffectId(effectId.getAsString()));
        } else if (effectId != null && number(effectId)) {
            properties.add("effectId", effectId.deepCopy());
        }
        properties.remove("effect-id");
        properties.remove("type");
        properties.remove("effect");
        properties.remove("potion-effect");
        properties.remove("potion");

        JsonElement durationTicks = properties.get("durationTicks");
        if (!properties.has("duration") && number(durationTicks)) {
            properties.addProperty("duration", Math.max(0L, Math.round(durationTicks.getAsDouble() * 50.0)));
            properties.remove("durationTicks");
        }
    }

    private void migrateClearedEffects(JsonObject properties) {
        JsonElement selectedEffects = properties.get("effects");
        if (selectedEffects != null && selectedEffects.isJsonArray()) {
            JsonArray normalizedEffects = new JsonArray();
            for (JsonElement effect : selectedEffects.getAsJsonArray()) {
                if (string(effect)) normalizedEffects.add(new JsonPrimitive(toEffectId(effect.getAsString())));
                else normalizedEffects.add(effect.deepCopy());
            }
            properties.add("effects", normalizedEffects);
        } else {
            JsonElement effect = firstString(properties, "effect-id", "effect");
            if (effect != null) {
                JsonArray effects = new JsonArray();
                effects.add(toEffectId(effect.getAsString()));
                properties.add("effects", effects);
            }
        }
        properties.remove("effect-id");
        properties.remove("effect");
    }

    public int toEffectId(String value) {
        if (value == null || value.isBlank()) return 1;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            int numeric = Integer.parseInt(normalized);
            if (numeric >= 1 && numeric <= 33) return numeric;
        } catch (NumberFormatException ignored) {
        }
        if (normalized.contains(":")) normalized = normalized.substring(normalized.indexOf(':') + 1);
        return switch (normalized) {
            case "speed" -> 1; case "slow", "slowness" -> 2; case "haste" -> 3; case "mining_fatigue", "miningfatigue" -> 4;
            case "strength" -> 5; case "instant_health", "instanthealth" -> 6; case "instant_damage", "instantdamage" -> 7;
            case "jump", "jump_boost", "jumpboost" -> 8; case "nausea", "confusion" -> 9; case "regeneration", "regen" -> 10;
            case "resistance" -> 11; case "fire_resistance", "fireresistance" -> 12; case "water_breathing", "waterbreathing" -> 13;
            case "invis", "invisible", "invisibility" -> 14; case "blind", "blindness" -> 15; case "nightvision", "night_vision" -> 16;
            case "hunger" -> 17; case "weakness" -> 18; case "poison" -> 19; case "wither" -> 20; case "health_boost", "healthboost" -> 21;
            case "absorption" -> 22; case "saturation" -> 23; case "glowing" -> 24; case "levitate", "levitation" -> 25;
            case "luck" -> 26; case "unluck", "bad_luck" -> 27; case "slow_falling", "slowfalling" -> 28;
            case "conduit_power", "conduitpower" -> 29; case "dolphins_grace", "dolphinsgrace" -> 30; case "bad_omen", "badomen" -> 31;
            case "hero_of_the_village", "hero_of_village", "heroofthevillage" -> 32; case "dark", "darkness" -> 33; default -> 1;
        };
    }

    private JsonElement firstString(JsonObject properties, String... keys) {
        for (String key : keys) {
            JsonElement value = properties.get(key);
            if (string(value) && !value.getAsString().isBlank()) return value;
        }
        return null;
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
