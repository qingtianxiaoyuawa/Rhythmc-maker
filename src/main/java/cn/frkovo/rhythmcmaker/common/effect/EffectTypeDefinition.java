package cn.frkovo.rhythmcmaker.common.effect;

import java.util.List;
import java.util.Objects;

/** Describes an effect type and the fields an editor may expose for it. */
public final class EffectTypeDefinition {
    private final String eventType;
    private final String displayName;
    private final List<EffectPropertyDefinition> properties;

    public EffectTypeDefinition(String eventType, String displayName, List<EffectPropertyDefinition> properties) {
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType must not be blank");
        displayName = displayName == null || displayName.isBlank() ? eventType : displayName;
        Objects.requireNonNull(properties, "properties");
        this.eventType = eventType.trim().toUpperCase(java.util.Locale.ROOT);
        this.displayName = displayName;
        this.properties = List.copyOf(properties);
    }

    public String eventType() {
        return eventType;
    }

    public String displayName() {
        return displayName;
    }

    public List<EffectPropertyDefinition> properties() {
        return properties;
    }

    public EffectPropertyDefinition property(String name) {
        if (name == null) return null;
        for (EffectPropertyDefinition property : properties) if (property.name().equals(name)) return property;
        return null;
    }
}
