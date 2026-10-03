package cn.frkovo.rhythmcmaker.common.effect.animation;

import com.google.gson.JsonObject;

import java.util.Optional;

/** Entry point for runtime consumers evaluating an effect at a Beat. */
public final class EffectAnimationEvaluator {
    private EffectAnimationEvaluator() {
    }

    public static EffectAnimationSnapshot evaluate(EffectAnimation animation, double beat) {
        if (animation == null) return new EffectAnimationSnapshot(beat, java.util.List.of());
        return animation.evaluateAt(beat);
    }

    public static Optional<EffectAnimationSnapshot> evaluate(JsonObject event, double beat) {
        return EffectAnimationJson.read(event).map(animation -> animation.evaluateAt(beat));
    }
}
