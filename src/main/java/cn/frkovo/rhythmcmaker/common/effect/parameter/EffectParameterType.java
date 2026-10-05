package cn.frkovo.rhythmcmaker.common.effect.parameter;

import cn.frkovo.rhythmcmaker.common.effect.EffectTypeDefinition;
import java.util.List;

public record EffectParameterType(String eventType, String displayName, boolean official,
                                  List<EffectParameterField> fields) {
    public EffectParameterType {
        fields = List.copyOf(fields);
    }

    public EffectTypeDefinition definition() {
        return new EffectTypeDefinition(eventType, displayName, fields.stream().map(EffectParameterField::property).toList());
    }
}
