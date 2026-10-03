package cn.frkovo.rhythmcmaker.common.effect.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Strongly typed boundary value for an animation channel. Numeric values can
 * be interpolated; other values intentionally use step semantics.
 */
public sealed interface EffectAnimationValue permits EffectAnimationValue.NumberValue,
        EffectAnimationValue.BooleanValue, EffectAnimationValue.TextValue,
        EffectAnimationValue.JsonValue, EffectAnimationValue.NullValue {
    JsonElement toJson();

    default boolean isNumeric() {
        return this instanceof NumberValue;
    }

    default double numericValue() {
        if (this instanceof NumberValue number) return number.value();
        throw new IllegalStateException("动画值不是数值");
    }

    static EffectAnimationValue fromJson(JsonElement source) {
        if (source == null || source.isJsonNull()) return new NullValue();
        if (source.isJsonPrimitive()) {
            JsonPrimitive primitive = source.getAsJsonPrimitive();
            if (primitive.isBoolean()) return new BooleanValue(primitive.getAsBoolean());
            if (primitive.isNumber()) return new NumberValue(primitive.getAsDouble());
            return new TextValue(primitive.getAsString());
        }
        if (source.isJsonArray()) return new JsonValue(source.getAsJsonArray().deepCopy());
        return new JsonValue(source.getAsJsonObject().deepCopy());
    }

    record NumberValue(double value) implements EffectAnimationValue {
        public NumberValue {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("动画数值必须有限");
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }
    }

    record BooleanValue(boolean value) implements EffectAnimationValue {
        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }
    }

    record TextValue(String value) implements EffectAnimationValue {
        public TextValue {
            if (value == null) throw new NullPointerException("动画文本值不能为空");
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }
    }

    record JsonValue(JsonElement value) implements EffectAnimationValue {
        public JsonValue {
            if (value == null || value.isJsonNull() && !(value instanceof JsonNull)) {
                throw new NullPointerException("动画 JSON 值不能为空");
            }
            value = value.deepCopy();
        }

        @Override
        public JsonElement toJson() {
            return value.deepCopy();
        }
    }

    record NullValue() implements EffectAnimationValue {
        @Override
        public JsonElement toJson() {
            return JsonNull.INSTANCE;
        }
    }
}
