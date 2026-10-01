package cn.frkovo.rhythmcv2.cv2.render;

import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent;
import cn.frkovo.rhythmcv2.cv2.chart.EditorTrack;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;

import java.math.BigDecimal;
import java.util.List;

/**
 * 与 RhythMC-Reborn 运行时同构的轨道/音符姿态数学（方案 A：复制公式 + 固定样例一致性测试）。
 * 对照源：ChartUtils.getTransformation / getDistance、TrackObject.evaluateState、
 * NoteObject.composeRenderState。改动必须同步跑 ChartMathTest 样例。
 */
public final class ChartMath {

    /** Reborn PlayerOptionsConfig：DEFAULT 1.0 / MIN 0.1 / MAX 5.0。 */
    public static final double DEFAULT_PLAYER_SPEED = 1.0;

    private ChartMath() {
    }

    /** 变换通道取值：最后一个 startBeat ≤ beat 的事件生效；区间内 easing 插值，区间外取端点值。 */
    public static double getTransformation(List<EditorNumEvent> events, double beat, double defaultValue) {
        double value = defaultValue;
        for (EditorNumEvent ev : events) {
            if (ev.startBeat.toDouble() > beat) {
                break;
            }
            double st = ev.startBeat.toDouble();
            double et = ev.endBeat.toDouble();
            if (beat >= et) {
                value = ev.endValue.doubleValue();
                continue;
            }
            double t = (beat - st) / (et - st);
            value = Easings.lerp(ev.startValue.doubleValue(), ev.endValue.doubleValue(), t, ev.easing);
            break;
        }
        return value;
    }

    /** speed 通道积分（Reborn ChartUtils.getDistance）；空洞段不产生距离（≈速度 0）。 */
    public static double getDistance(List<EditorNumEvent> speedEvents, double beat) {
        if (!speedEvents.isEmpty() && beat < speedEvents.getFirst().startBeat.toDouble()) {
            EditorNumEvent first = speedEvents.getFirst();
            return first.startValue.doubleValue() * (beat - first.startBeat.toDouble());
        }
        double distance = 0;
        for (EditorNumEvent ev : speedEvents) {
            if (ev.startBeat.toDouble() > beat) {
                break;
            }
            distance += singleEventDistance(ev, beat);
        }
        return distance;
    }

    public static double getDistance(List<EditorNumEvent> speedEvents, double beat, double playerSpeed) {
        return getDistance(speedEvents, beat) * playerSpeed;
    }

    private static double singleEventDistance(EditorNumEvent ev, double beat) {
        double st = ev.startBeat.toDouble();
        double et = ev.endBeat.toDouble();
        if (st == et) {
            return 0;
        }
        if (beat < st) {
            return 0;
        }
        if (beat > et) {
            double fullArea = ev.startValue.doubleValue()
                    + (ev.endValue.doubleValue() - ev.startValue.doubleValue())
                    * Easings.integral(1, ev.easing);
            return fullArea * (et - st);
        }
        double t = (beat - st) / (et - st);
        double normalizedArea = ev.startValue.doubleValue() * t
                + (ev.endValue.doubleValue() - ev.startValue.doubleValue())
                * Easings.integral(t, ev.easing);
        return normalizedArea * (et - st);
    }

    /** 音符预计算初始距离：Reborn NoteObject 构造期 track.getDistance(note.beat)。 */
    public static double noteDistance(EditorTrack track, BeatFraction beat, double playerSpeed) {
        return getDistance(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.SPEED),
                beat.toDouble(), playerSpeed);
    }

    /** 轨道在某 beat 的完整姿态。 */
    public record TrackState(double distance, double xTransform, double yTransform, double zTransform,
                             double xRot, double yRot, double zRot,
                             double xScale, double yScale, double zScale) {

        public double sinX() {
            return Math.sin(Math.toRadians(xRot));
        }

        public double cosX() {
            return Math.cos(Math.toRadians(xRot));
        }

        public double sinY() {
            return Math.sin(Math.toRadians(yRot));
        }

        public double cosY() {
            return Math.cos(Math.toRadians(yRot));
        }

        public double sinZ() {
            return Math.sin(Math.toRadians(zRot));
        }

        public double cosZ() {
            return Math.cos(Math.toRadians(zRot));
        }
    }

    public static TrackState evaluateState(EditorTrack track, double beat, double playerSpeed) {
        double xRot = getTransformation(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.X_ROTATE), beat, 0);
        double yRot = getTransformation(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.Y_ROTATE), beat, 0);
        double zRot = getTransformation(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.Z_ROTATE), beat, 0);
        return new TrackState(
                getDistance(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.SPEED), beat, playerSpeed),
                getTransformation(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.X_TRANSFORM), beat, 0),
                getTransformation(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.Y_TRANSFORM), beat, 0),
                getTransformation(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.Z_TRANSFORM), beat, 0),
                xRot, yRot, zRot,
                getTransformation(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.X_SCALE), beat, 1),
                getTransformation(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.Y_SCALE), beat, 1),
                getTransformation(track.events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.Z_SCALE), beat, 1));
    }

    /** 音符世界渲染坐标：dis = noteDistance − trackDistance + posZ·trackZScale；旋转 Z→Y→X；worldZ = originZ − z。 */
    public static double[] composeWorld(TrackState ts, double noteDistance,
                                        BigDecimal posX, BigDecimal posY, BigDecimal posZ) {
        double dis = noteDistance - ts.distance() + posZ.doubleValue() * ts.zScale();
        double x = posX.doubleValue() * ts.xScale();
        double y = posY.doubleValue() * ts.yScale();
        double z = dis;

        double x1 = x * ts.cosZ() - y * ts.sinZ();
        double y1 = x * ts.sinZ() + y * ts.cosZ();
        double z1 = z;

        double x2 = x1 * ts.cosY() + z1 * ts.sinY();
        double y2 = y1;
        double z2 = -x1 * ts.sinY() + z1 * ts.cosY();

        double x3 = x2;
        double y3 = y2 * ts.cosX() - z2 * ts.sinX();
        double z3 = y2 * ts.sinX() + z2 * ts.cosX();

        return new double[]{x3 + ts.xTransform(), y3 + ts.yTransform(), z3 + ts.zTransform(), dis};
    }

    /** Reborn zNear/zFar（按 noteType）：TAP/LOOK (0,25)，HOLD (1,25)，DODGE (−2,25)。 */
    public static double zNear(int noteType) {
        return switch (noteType) {
            case EditorNote.HOLD -> 1;
            case EditorNote.DODGE -> -2;
            default -> 0;
        };
    }

    public static double zFar(int noteType) {
        return 25;
    }

    /** TAP/LOOK 视觉缩放遮罩：zFar 处 1.0 → zNear 处 0.9（HOLD/DODGE 恒 1.0）。 */
    public static double visualScaleMask(int noteType, double dis) {
        if (noteType != EditorNote.TAP && noteType != EditorNote.LOOK) {
            return 1.0;
        }
        double near = zNear(noteType);
        double far = zFar(noteType);
        double t = (far - dis) / (far - near);
        t = Math.max(0, Math.min(1, t));
        return 1.0 + (0.9 - 1.0) * t;
    }

    /** Reborn 槽位布局：gameCenter = (pos·200+0.5, 100, −1.5)（yaw 180）；判定点 = gameCenter Z − 1.5。 */
    public static double gameCenterX(int slot) {
        return slot * 200 + 0.5;
    }

    public static double gameCenterZ() {
        return -1.5;
    }

    public static double gameCenterY() {
        return 100;
    }

    public static double judgeZ() {
        return gameCenterZ() - 1.5;
    }

    /** 音符渲染原点 = 判定点 + (0, 0.5, 0)。 */
    public static double originY() {
        return gameCenterY() + 0.5;
    }
}
