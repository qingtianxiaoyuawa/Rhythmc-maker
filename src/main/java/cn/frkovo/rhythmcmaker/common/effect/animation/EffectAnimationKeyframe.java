package cn.frkovo.rhythmcmaker.common.effect.animation;

import java.util.Objects;

/** One absolute-Beat sample in an effect animation channel. */
public record EffectAnimationKeyframe(double beat, EffectAnimationValue value, int easingId) {
    public EffectAnimationKeyframe {
        if (!Double.isFinite(beat)) throw new IllegalArgumentException("关键帧 Beat 必须有限");
        value = Objects.requireNonNull(value, "关键帧值不能为空");
    }
}
