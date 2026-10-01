package cn.frkovo.rhythmcv2.cv2.session;

import cn.frkovo.rhythmcv2.cv2.chart.EditorLevel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorTrack;

/** 会话内当前难度的工作轨道解析：固定使用该难度 0 号轨道（MVP 单轨道工作流）。 */
public final class EditorChartAccess {

    private final CharterSession session;

    public EditorChartAccess(CharterSession session) {
        this.session = session;
    }

    /** 当前难度的第一个轨道（草稿必有一轨）。 */
    public String activeTrackId() {
        EditorLevel level = session.chart().active();
        EditorTrack first = level.tracks.values().iterator().next();
        return first.id;
    }
}
