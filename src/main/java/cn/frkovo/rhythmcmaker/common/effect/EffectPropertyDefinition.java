package cn.frkovo.rhythmcmaker.common.effect;

import java.util.Objects;

/** Declares one editable property exposed by an effect type. */
public record EffectPropertyDefinition(String name, String displayName,
                                       EffectPropertyValueKind valueKind, boolean animatable) {
    public EffectPropertyDefinition {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        displayName = displayName == null || displayName.isBlank() ? name : displayName;
        valueKind = Objects.requireNonNull(valueKind, "valueKind");
    }
}
