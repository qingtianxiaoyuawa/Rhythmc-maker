package cn.frkovo.rhythmcmaker.common.effect.parameter;

import cn.frkovo.rhythmcmaker.common.effect.EffectPropertyDefinition;
import cn.frkovo.rhythmcmaker.common.effect.EffectPropertyValueKind;
import java.util.List;

public record EffectParameterField(String name, String label, EffectParameterControl control,
                                   boolean optional, String defaultJson, String help,
                                   List<String> choices, List<String> branches, double minimum, double maximum) {
    public EffectParameterField {
        choices = List.copyOf(choices);
        branches = List.copyOf(branches);
    }

    public boolean active(String branch) {
        return branches.isEmpty() || branches.contains(branch);
    }

    public EffectPropertyDefinition property() {
        EffectPropertyValueKind kind = switch (control) {
            case TEXT, MULTILINE_TEXT -> EffectPropertyValueKind.TEXT;
            case INTEGER, LONG -> EffectPropertyValueKind.INTEGER;
            case DECIMAL -> EffectPropertyValueKind.DECIMAL;
            case BOOLEAN -> EffectPropertyValueKind.BOOLEAN;
            case ENUM -> EffectPropertyValueKind.ENUM;
            case VECTOR, RGBA -> EffectPropertyValueKind.VECTOR;
            case INTEGER_LIST -> EffectPropertyValueKind.INTEGER_ARRAY;
            case STRING_LIST, NOTE_TYPES, VECTOR_LIST, RGB_LIST -> EffectPropertyValueKind.JSON;
        };
        return new EffectPropertyDefinition(name, label, kind, control == EffectParameterControl.DECIMAL
                || control == EffectParameterControl.VECTOR || control == EffectParameterControl.RGBA);
    }
}
