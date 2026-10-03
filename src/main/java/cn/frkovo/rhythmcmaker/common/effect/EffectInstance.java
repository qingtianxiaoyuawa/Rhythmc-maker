package cn.frkovo.rhythmcmaker.common.effect;

import java.util.List;
import java.util.Objects;

/** Strongly typed effect event while retaining fields unknown to this version of the editor. */
public final class EffectInstance {
    private final String eventType;
    private final String typeFieldName;
    private final double beat;
    private final List<EffectPropertyValue> properties;
    private final boolean propertiesPresent;
    private final cn.frkovo.rhythmcmaker.common.effect.animation.EffectAnimation animation;
    private final List<EffectRawField> rawFields;

    public EffectInstance(String eventType, double beat, List<EffectPropertyValue> properties,
                          cn.frkovo.rhythmcmaker.common.effect.animation.EffectAnimation animation,
                          List<EffectRawField> rawFields) {
        this(eventType, "eventType", beat, properties, animation, rawFields);
    }

    public EffectInstance(String eventType, String typeFieldName, double beat, List<EffectPropertyValue> properties,
                          cn.frkovo.rhythmcmaker.common.effect.animation.EffectAnimation animation,
                          List<EffectRawField> rawFields) {
        this(eventType, typeFieldName, beat, properties, true, animation, rawFields);
    }

    public EffectInstance(String eventType, String typeFieldName, double beat, List<EffectPropertyValue> properties,
                          boolean propertiesPresent,
                          cn.frkovo.rhythmcmaker.common.effect.animation.EffectAnimation animation,
                          List<EffectRawField> rawFields) {
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType must not be blank");
        if (!Double.isFinite(beat) || beat < 0.0) throw new IllegalArgumentException("beat must be finite and non-negative");
        if (!"eventType".equals(typeFieldName) && !"type".equals(typeFieldName)) throw new IllegalArgumentException("typeFieldName must be eventType or type");
        this.eventType = eventType;
        this.typeFieldName = typeFieldName;
        this.beat = beat;
        this.properties = List.copyOf(Objects.requireNonNull(properties, "properties"));
        this.propertiesPresent = propertiesPresent;
        this.animation = animation;
        this.rawFields = List.copyOf(Objects.requireNonNull(rawFields, "rawFields"));
    }

    public String eventType() {
        return eventType;
    }

    public String typeFieldName() {
        return typeFieldName;
    }

    public double beat() {
        return beat;
    }

    public List<EffectPropertyValue> properties() {
        return properties;
    }

    public boolean propertiesPresent() {
        return propertiesPresent;
    }

    public cn.frkovo.rhythmcmaker.common.effect.animation.EffectAnimation animation() {
        return animation;
    }

    public List<EffectRawField> rawFields() {
        return rawFields;
    }

    public boolean hasAnimation() {
        return animation != null && !animation.channels().isEmpty();
    }
}
