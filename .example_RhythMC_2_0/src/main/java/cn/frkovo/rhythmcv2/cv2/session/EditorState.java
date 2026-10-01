package cn.frkovo.rhythmcv2.cv2.session;

import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;

import java.util.LinkedHashSet;
import java.util.Set;

/** 会话编辑状态（进 draft.json editorState；不参与谱面语义，不进撤销栈）。 */
public final class EditorState {
    public BeatFraction cursorBeat = BeatFraction.ZERO;
    /** 每拍分割数；≤0 = off（量化关闭）。 */
    public int snap = 4;
    public String tool = "TAP";
    public final Set<String> selectedIds = new LinkedHashSet<>();
    /** A-B 循环（beat）；null = 未设。 */
    public BeatFraction loopA;
    public BeatFraction loopB;
    public String lastPos = "0,0,0";
    /** mod 音频匹配提示（_editor.yml audioHint）。 */
    public String audioHint = "";
    /** 正在编辑的 HOLD 组（-1 = 无；placeType=HOLD 时第一次放置分配）。 */
    public int holdGroupDraft = -1;

    public boolean loopActive() {
        return loopA != null && loopB != null;
    }

    public void clearLoop() {
        loopA = null;
        loopB = null;
    }
}
