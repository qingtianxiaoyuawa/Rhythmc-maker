package cn.frkovo.rhythmcmaker.common.effect.animation;

import java.util.List;
import java.util.Optional;

/** Immutable evaluated state that can be handed to a renderer or sync layer. */
public final class EffectAnimationSnapshot {
    private final double beat;
    private final List<ChannelValue> values;

    public EffectAnimationSnapshot(double beat, List<ChannelValue> values) {
        this.beat = Double.isFinite(beat) ? beat : 0.0;
        this.values = values == null ? List.of() : List.copyOf(values);
    }

    public double beat() {
        return beat;
    }

    public List<ChannelValue> values() {
        return values;
    }

    public Optional<EffectAnimationValue> value(String channelName) {
        if (channelName == null) return Optional.empty();
        for (ChannelValue value : values) {
            if (channelName.equals(value.channelName())) return Optional.of(value.value());
        }
        return Optional.empty();
    }

    public record ChannelValue(String channelName, EffectAnimationValue value) {
        public ChannelValue {
            if (channelName == null || channelName.isBlank()) throw new IllegalArgumentException("动画通道名称不能为空");
            if (value == null) throw new NullPointerException("动画值不能为空");
        }
    }
}
