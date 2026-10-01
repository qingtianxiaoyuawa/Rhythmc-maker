package cn.frkovo.rhythmcv2.cv2.session;

import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import cn.frkovo.rhythmcv2.cv2.ops.Operation;
import cn.frkovo.rhythmcv2.cv2.render.ChartMath;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 放置 = 空间（准星 × 判定平面）× 时间（游标 snap 量化）解耦（§9.1）。
 * MVP：静态轨道假设（轨道变换事件的放置反解为 M1+）。
 */
public final class PlacementService {

    /** 准星射线 × 判定平面（chart z=0 平面，世界 z = judgeZ）→ chart 局部坐标 [x,y,0]；null = 平行或背向。 */
    public double[] aimChartPos(CharterSession session) {
        Location eye = session.player().getEyeLocation();
        Vector dir = eye.getDirection();
        if (Math.abs(dir.getZ()) < 1e-6) {
            return null;
        }
        double t = (ChartMath.judgeZ() - eye.getZ()) / dir.getZ();
        if (t <= 0) {
            return null; // 背向判定平面
        }
        double hx = eye.getX() + dir.getX() * t;
        double hy = eye.getY() + dir.getY() * t;
        double x = hx - ChartMath.gameCenterX(0);
        double y = hy - ChartMath.originY();
        if (Math.abs(x) > 32 || Math.abs(y) > 32) {
            return null;
        }
        return new double[]{round2(x), round2(y), 0};
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** 当前工具 + ghost 位置 → NOTE_CREATE 操作（holdGroup 由调用方维护）。 */
    public Operation placeNote(CharterSession session, double[] chartPos) {
        String tool = session.state().tool;
        int type = cn.frkovo.rhythmcv2.cv2.render.PreviewRenderer.noteTypeOf(tool);
        EditorChartAccess access = new EditorChartAccess(session);
        String trackId = access.activeTrackId();
        BeatFraction beat = session.chart().clock()
                .quantize(session.state().cursorBeat, session.state().snap);
        EditorNote note = new EditorNote(session.chart().nextNoteId(), type, beat);
        note.pos[0] = BigDecimal.valueOf(chartPos[0]);
        note.pos[1] = BigDecimal.valueOf(chartPos[1]);
        note.pos[2] = BigDecimal.ZERO;
        if (type == EditorNote.DODGE) {
            note.scale[0] = note.scale[1] = note.scale[2] = new BigDecimal("1.5");
        }
        return new Operation.NoteCreate(session.chart().activeLevel, trackId,
                cn.frkovo.rhythmcv2.cv2.ops.OperationApplier.noteSnapshot(note));
    }

    /** 准星指向的最近音符（渲染位置到视线距离 ≤ 1.5）。 */
    public EditorNote targetedNote(CharterSession session) {
        Location eye = session.player().getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        EditorNote best = null;
        double bestDist = 1.5;
        double speed = session.plugin().config().playerSpeed();
        for (cn.frkovo.rhythmcv2.cv2.chart.EditorTrack track : session.chart().active().tracks.values()) {
            cn.frkovo.rhythmcv2.cv2.render.ChartMath.TrackState ts =
                    cn.frkovo.rhythmcv2.cv2.render.ChartMath.evaluateState(track,
                            session.state().cursorBeat.toDouble(), speed);
            for (EditorNote n : track.notes.values()) {
                double noteDis = cn.frkovo.rhythmcv2.cv2.render.ChartMath.noteDistance(track, n.beat, speed);
                double[] w = cn.frkovo.rhythmcv2.cv2.render.ChartMath.composeWorld(
                        ts, noteDis, n.pos[0], n.pos[1], n.pos[2]);
                Vector p = new Vector(
                        ChartMath.gameCenterX(0) + w[0],
                        ChartMath.originY() + w[1],
                        ChartMath.judgeZ() - w[2]).subtract(eye.toVector());
                double along = p.dot(dir);
                if (along < 0) {
                    continue;
                }
                double perp = p.subtract(dir.clone().multiply(along)).length();
                if (perp < bestDist) {
                    bestDist = perp;
                    best = n;
                }
            }
        }
        return best;
    }

    /** HOLD 组追加：在游标处为最近同类型 HOLD 组追加节点；无组则开新组。 */
    public Operation holdAppend(CharterSession session, double[] chartPos) {
        EditorChartAccess access = new EditorChartAccess(session);
        String trackId = access.activeTrackId();
        BeatFraction beat = session.chart().clock()
                .quantize(session.state().cursorBeat, session.state().snap);
        EditorNote note = new EditorNote(session.chart().nextNoteId(), EditorNote.HOLD, beat);
        note.pos[0] = BigDecimal.valueOf(chartPos[0]);
        note.pos[1] = BigDecimal.valueOf(chartPos[1]);
        note.pos[2] = BigDecimal.ZERO;
        note.holdGroup = session.state().holdGroupDraft >= 0
                ? session.state().holdGroupDraft
                : (session.state().holdGroupDraft = session.chart().nextHoldGroupId());
        return new Operation.NoteCreate(session.chart().activeLevel, trackId,
                cn.frkovo.rhythmcv2.cv2.ops.OperationApplier.noteSnapshot(note));
    }

    public Operation holdFinish(CharterSession session) {
        session.state().holdGroupDraft = -1;
        return null; // 状态操作，不产生领域操作
    }
}
