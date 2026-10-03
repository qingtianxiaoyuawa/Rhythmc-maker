package cn.frkovo.rhythmcmaker.common.effect.animation;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Parsed animation portion of one raw RhythMC effect event. */
public final class EffectAnimation {
    private final String eventType;
    private final double eventBeat;
    private final JsonObject source;
    private final List<EffectAnimationChannel> channels;

    public EffectAnimation(String eventType, double eventBeat, JsonObject source,
                           List<EffectAnimationChannel> channels) {
        this.eventType = eventType == null ? "" : eventType;
        this.eventBeat = Double.isFinite(eventBeat) ? eventBeat : 0.0;
        this.source = source == null ? new JsonObject() : source.deepCopy();
        this.channels = channels == null ? List.of() : List.copyOf(channels);
    }

    public String eventType() {
        return eventType;
    }

    public double eventBeat() {
        return eventBeat;
    }

    /** Returns a copy so evaluation cannot mutate persisted event data. */
    public JsonObject source() {
        return source.deepCopy();
    }

    public List<EffectAnimationChannel> channels() {
        return channels;
    }

    public EffectAnimationSnapshot evaluateAt(double beat) {
        ArrayList<EffectAnimationSnapshot.ChannelValue> values = new ArrayList<>();
        for (EffectAnimationChannel channel : channels) {
            values.add(new EffectAnimationSnapshot.ChannelValue(channel.name(), channel.valueAt(beat)));
        }
        return new EffectAnimationSnapshot(beat, values);
    }
}
