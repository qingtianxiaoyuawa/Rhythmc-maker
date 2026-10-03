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
        register(new EffectTypeDefinition("GLOW_COLOR", "Glow Color", List.of(
            property("color", EffectPropertyValueKind.COLOR, false))));
        register(new EffectTypeDefinition("HIDE_NOTES", "Hide Notes", List.of(
            property("noteTypes", EffectPropertyValueKind.INTEGER_ARRAY, true),
            property("tracks", EffectPropertyValueKind.INTEGER_ARRAY, true),
            property("hidden", EffectPropertyValueKind.BOOLEAN, true))));
        register(new EffectTypeDefinition("TITLE", "Title", textProperties()));
        register(new EffectTypeDefinition("ACTIONBAR", "Actionbar", textProperties()));
        register(new EffectTypeDefinition("MESSAGE", "Message", textProperties()));
        register(new EffectTypeDefinition("TEXT_DISPLAY", "Text Display", displayProperties()));
        register(new EffectTypeDefinition("TEXT_DISPLAY_EFFECT", "Text Display Effect", displayProperties()));
        register(new EffectTypeDefinition("TEXT_DISPLAY_SYNC_TRACK", "Text Display Sync Track", displayProperties()));
        register(new EffectTypeDefinition("TEXT_DISPLAY_DESYNC_TRACK", "Text Display Desync Track", displayProperties()));
        register(new EffectTypeDefinition("TEXT_DISPLAY_REMOVE", "Remove Text Display", List.of()));
        register(new EffectTypeDefinition("HOLOGRAM", "Hologram", displayProperties()));
        register(new EffectTypeDefinition("REMOVE_HOLOGRAM", "Remove Hologram", List.of()));
        register(new EffectTypeDefinition("EFFECT", "Potion Effect", List.of(
            property("effect", EffectPropertyValueKind.ENUM, false),
            property("amplifier", EffectPropertyValueKind.INTEGER, true),
            property("duration", EffectPropertyValueKind.INTEGER, true))));
        register(new EffectTypeDefinition("CLEAR_EFFECT", "Clear Effects", List.of()));
        register(new EffectTypeDefinition("TIME", "Time", List.of(
            property("time", EffectPropertyValueKind.INTEGER, true))));
        register(new EffectTypeDefinition("WEATHER", "Weather", List.of(
            property("weather", EffectPropertyValueKind.ENUM, true))));
        register(new EffectTypeDefinition("ARENA", "Arena", List.of(
            property("arena", EffectPropertyValueKind.TEXT, false))));
        register(new EffectTypeDefinition("CHANGE_ARENA", "Change Arena", List.of(
            property("arena", EffectPropertyValueKind.TEXT, false))));
        register(new EffectTypeDefinition("FIREWORK", "Firework", List.of(
            property("x", EffectPropertyValueKind.DECIMAL, true),
            property("y", EffectPropertyValueKind.DECIMAL, true),
            property("z", EffectPropertyValueKind.DECIMAL, true),
            property("color", EffectPropertyValueKind.COLOR, true),
            property("count", EffectPropertyValueKind.INTEGER, true))));
    }

    private static List<EffectPropertyDefinition> textProperties() {
        return List.of(property("text", EffectPropertyValueKind.TEXT, true), property("color", EffectPropertyValueKind.COLOR, true));
    }

    private static List<EffectPropertyDefinition> displayProperties() {
        return List.of(
            property("text", EffectPropertyValueKind.TEXT, true),
            property("color", EffectPropertyValueKind.COLOR, true),
            property("x", EffectPropertyValueKind.DECIMAL, true),
            property("y", EffectPropertyValueKind.DECIMAL, true),
            property("z", EffectPropertyValueKind.DECIMAL, true),
            property("scale", EffectPropertyValueKind.DECIMAL, true),
            property("opacity", EffectPropertyValueKind.DECIMAL, true));
    }

    private static EffectPropertyDefinition property(String name, EffectPropertyValueKind kind, boolean animatable) {
        return new EffectPropertyDefinition(name, name, kind, animatable);
    }
}
