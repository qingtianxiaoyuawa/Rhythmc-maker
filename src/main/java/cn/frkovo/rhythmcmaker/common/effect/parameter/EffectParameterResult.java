package cn.frkovo.rhythmcmaker.common.effect.parameter;

import com.google.gson.JsonElement;

public record EffectParameterResult(JsonElement value, String error) {
    public boolean successful() {
        return error.isEmpty();
    }
}
