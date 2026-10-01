package cn.frkovo.rhythmcv2.cv2.chart;

import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;

import java.math.BigDecimal;

/**
 * 草稿音符：稳定字符串 id；beat 用精确分数，十进制量用 BigDecimal 字符串语义保存。
 * noteType 对齐 Reborn NoteType：0=TAP 1=LOOK 2=HOLD 3=DODGE。
 */
public final class EditorNote {
    public static final int TAP = 0;
    public static final int LOOK = 1;
    public static final int HOLD = 2;
    public static final int DODGE = 3;

    public String id;
    public int noteType;
    public BeatFraction beat;
    /** 判定平面局部坐标，单位格；字符串十进制（BigDecimal 语义）。 */
    public BigDecimal[] pos = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    public BigDecimal[] scale = {BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE};
    public BigDecimal[] rotation = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    /** -1 = 不属于任何长押组。 */
    public int holdGroup = -1;

    public EditorNote(String id, int noteType, BeatFraction beat) {
        this.id = id;
        this.noteType = noteType;
        this.beat = beat;
    }

    public static EditorNote copyOf(EditorNote n) {
        EditorNote c = new EditorNote(n.id, n.noteType, n.beat);
        for (int i = 0; i < 3; i++) {
            c.pos[i] = n.pos[i];
            c.scale[i] = n.scale[i];
            c.rotation[i] = n.rotation[i];
        }
        c.holdGroup = n.holdGroup;
        return c;
    }
}
