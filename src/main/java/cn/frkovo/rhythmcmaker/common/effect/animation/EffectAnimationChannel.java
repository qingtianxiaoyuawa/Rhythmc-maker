package cn.frkovo.rhythmcmaker.common.effect.animation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** A named effect parameter with absolute-Beat keyframes. */
public final class EffectAnimationChannel {
    private final String name;
    private final EffectAnimationInterpolation interpolation;
    private final List<EffectAnimationKeyframe> keyframes;

    public EffectAnimationChannel(String name, EffectAnimationInterpolation interpolation,
                                  List<EffectAnimationKeyframe> keyframes) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("动画通道名称不能为空");
        this.name = name;
        this.interpolation = Objects.requireNonNull(interpolation, "动画插值模式不能为空");
        this.keyframes = canonicalize(keyframes);
    }

    public String name() {
        return name;
    }

    public EffectAnimationInterpolation interpolation() {
        return interpolation;
    }

    public List<EffectAnimationKeyframe> keyframes() {
        return keyframes;
    }

    public EffectAnimationValue valueAt(double beat) {
        if (keyframes.isEmpty()) return new EffectAnimationValue.NullValue();
        if (!Double.isFinite(beat) || beat <= keyframes.get(0).beat()) return keyframes.get(0).value();
        for (int index = 1; index < keyframes.size(); index++) {
            EffectAnimationKeyframe next = keyframes.get(index);
            if (beat < next.beat()) {
                EffectAnimationKeyframe previous = keyframes.get(index - 1);
                if (interpolation == EffectAnimationInterpolation.STEP
                        || !previous.value().isNumeric() || !next.value().isNumeric()) {
                    return previous.value();
                }
                double duration = next.beat() - previous.beat();
                double progress = (beat - previous.beat()) / duration;
                double eased = EffectAnimationEasing.apply(progress, previous.easingId());
                double start = previous.value().numericValue();
                double end = next.value().numericValue();
                return new EffectAnimationValue.NumberValue(start + (end - start) * eased);
            }
        }
        return keyframes.get(keyframes.size() - 1).value();
    }

    private static List<EffectAnimationKeyframe> canonicalize(List<EffectAnimationKeyframe> source) {
        ArrayList<EffectAnimationKeyframe> sorted = new ArrayList<>();
        if (source != null) {
            for (EffectAnimationKeyframe keyframe : source) {
                if (keyframe != null) sorted.add(keyframe);
            }
        }
        sorted.sort(Comparator.comparingDouble(EffectAnimationKeyframe::beat));
        ArrayList<EffectAnimationKeyframe> deduplicated = new ArrayList<>();
        for (EffectAnimationKeyframe keyframe : sorted) {
            if (!deduplicated.isEmpty()
                    && Double.compare(deduplicated.get(deduplicated.size() - 1).beat(), keyframe.beat()) == 0) {
                deduplicated.set(deduplicated.size() - 1, keyframe);
            } else {
                deduplicated.add(keyframe);
            }
        }
        return List.copyOf(deduplicated);
    }
}
