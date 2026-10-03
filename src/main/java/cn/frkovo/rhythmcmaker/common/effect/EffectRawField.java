package cn.frkovo.rhythmcmaker.common.effect;

import java.util.Objects;

/** Keeps an unrecognised JSON field lossless across an editor round trip. */
public record EffectRawField(String name, String jsonValue) {
    public EffectRawField {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        jsonValue = Objects.requireNonNull(jsonValue, "jsonValue").trim();
        if (jsonValue.isEmpty()) throw new IllegalArgumentException("jsonValue must not be blank");
    }
}
