package cn.frkovo.rhythmcv2.cv2.chart;

import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;

import java.math.BigDecimal;

/** 草稿 NumEvent：与 Reborn NumEvent(startBeat,endBeat,startValue,endValue,easingType) 对齐，beat 为精确分数。 */
public final class EditorNumEvent {
    public String id;
    public BeatFraction startBeat;
    public BeatFraction endBeat;
    public BigDecimal startValue;
    public BigDecimal endValue;
    public int easing;

    public EditorNumEvent(String id, BeatFraction startBeat, BeatFraction endBeat,
                          BigDecimal startValue, BigDecimal endValue, int easing) {
        this.id = id;
        this.startBeat = startBeat;
        this.endBeat = endBeat;
        this.startValue = startValue;
        this.endValue = endValue;
        this.easing = easing;
    }

    public static EditorNumEvent copyOf(EditorNumEvent e) {
        return new EditorNumEvent(e.id, e.startBeat, e.endBeat, e.startValue, e.endValue, e.easing);
    }
}
