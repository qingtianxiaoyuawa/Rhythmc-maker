package cn.frkovo.rhythmcmaker.common.effect;

import java.util.Objects;

/** Associates a property name with its typed value. */
public record EffectPropertyValue(String name, EffectValue value) {
    public EffectPropertyValue {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        Objects.requireNonNull(value, "value");
    }
}
