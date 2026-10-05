package cn.frkovo.rhythmcmaker.common.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Registry for built-in and project-defined effect types. */
public final class EffectTypeRegistry {
    private static final EffectTypeRegistry INSTANCE = new EffectTypeRegistry();
    private final List<EffectTypeDefinition> definitions = new ArrayList<>();

    private EffectTypeRegistry() {
        registerBuiltIns();
    }

    public static EffectTypeRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void register(EffectTypeDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (find(definition.eventType()) != null) throw new IllegalArgumentException("Effect type already registered: " + definition.eventType());
        definitions.add(definition);
    }

    public synchronized EffectTypeDefinition find(String eventType) {
        if (eventType == null) return null;
        String normalized = eventType.trim().toUpperCase(Locale.ROOT);
        for (EffectTypeDefinition definition : definitions) if (definition.eventType().equals(normalized)) return definition;
        return null;
    }

    public synchronized List<EffectTypeDefinition> definitions() {
        return List.copyOf(definitions);
    }

    private void registerBuiltIns() {
        for (cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterType type
                : cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterSchema.getInstance().types()) register(type.definition());
    }
}
