package cn.frkovo.rhythmcmaker.common.effect.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Reads and writes only the optional animation extension of a raw effect JSON event. */
public final class EffectAnimationJson {
    private EffectAnimationJson() {
    }

    public static Optional<EffectAnimation> read(JsonObject event) {
        if (event == null || !event.has("animation") || !event.get("animation").isJsonObject()) return Optional.empty();
        JsonObject animation = event.getAsJsonObject("animation");
        if (!animation.has("channels") || !animation.get("channels").isJsonArray()) return Optional.empty();
        ArrayList<EffectAnimationChannel> channels = new ArrayList<>();
        for (JsonElement element : animation.getAsJsonArray("channels")) {
            if (!element.isJsonObject()) continue;
            readChannel(element.getAsJsonObject()).ifPresent(channels::add);
        }
        String eventType = string(event, "eventType", string(event, "type", ""));
        return Optional.of(new EffectAnimation(eventType, number(event, "beat", 0.0), event, channels));
    }

    /** Returns a deep copy of the event with the animation extension replaced. */
    public static JsonObject write(JsonObject event, List<EffectAnimationChannel> channels) {
        JsonObject result = event == null ? new JsonObject() : event.deepCopy();
        JsonArray serialized = new JsonArray();
        if (channels != null) for (EffectAnimationChannel channel : channels) {
            if (channel != null) serialized.add(writeChannel(channel));
        }
        JsonObject animation = new JsonObject();
        animation.add("channels", serialized);
        result.add("animation", animation);
        return result;
    }

    private static Optional<EffectAnimationChannel> readChannel(JsonObject source) {
        String name = string(source, "name", "");
        if (name.isBlank() || !source.has("keyframes") || !source.get("keyframes").isJsonArray()) return Optional.empty();
        ArrayList<EffectAnimationKeyframe> keyframes = new ArrayList<>();
        for (JsonElement element : source.getAsJsonArray("keyframes")) {
            if (!element.isJsonObject()) continue;
            JsonObject keyframe = element.getAsJsonObject();
            double beat = number(keyframe, "beat", Double.NaN);
            if (!Double.isFinite(beat) || !keyframe.has("value")) continue;
            try {
                keyframes.add(new EffectAnimationKeyframe(beat,
                        EffectAnimationValue.fromJson(keyframe.get("value")),
                        EffectAnimationEasing.normalizeId((int) number(keyframe, "easing", 0))));
            } catch (IllegalArgumentException ignored) {
                // Malformed keyframes do not invalidate the other channels or the raw event.
            }
        }
        EffectAnimationInterpolation interpolation = interpolation(source, keyframes);
        return Optional.of(new EffectAnimationChannel(name, interpolation, keyframes));
    }

    private static EffectAnimationInterpolation interpolation(JsonObject source, List<EffectAnimationKeyframe> keyframes) {
        String mode = string(source, "interpolation", "").toUpperCase(Locale.ROOT);
        if (mode.equals("STEP") || mode.equals("DISCRETE")) return EffectAnimationInterpolation.STEP;
        if (mode.equals("CONTINUOUS") || mode.equals("LINEAR")) return EffectAnimationInterpolation.CONTINUOUS;
        for (EffectAnimationKeyframe keyframe : keyframes) if (!keyframe.value().isNumeric()) return EffectAnimationInterpolation.STEP;
        return EffectAnimationInterpolation.CONTINUOUS;
    }

    private static JsonObject writeChannel(EffectAnimationChannel channel) {
        JsonObject result = new JsonObject();
        result.addProperty("name", channel.name());
        result.addProperty("interpolation", channel.interpolation().name());
        JsonArray keyframes = new JsonArray();
        for (EffectAnimationKeyframe keyframe : channel.keyframes()) {
            JsonObject value = new JsonObject();
            value.addProperty("beat", keyframe.beat());
            value.add("value", keyframe.value().toJson());
            value.addProperty("easing", EffectAnimationEasing.normalizeId(keyframe.easingId()));
            keyframes.add(value);
        }
        result.add("keyframes", keyframes);
        return result;
    }

    private static String string(JsonObject source, String key, String fallback) {
        try {
            return source.has(key) && source.get(key).isJsonPrimitive() ? source.get(key).getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double number(JsonObject source, String key, double fallback) {
        try {
            return source.has(key) && source.get(key).isJsonPrimitive() ? source.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
