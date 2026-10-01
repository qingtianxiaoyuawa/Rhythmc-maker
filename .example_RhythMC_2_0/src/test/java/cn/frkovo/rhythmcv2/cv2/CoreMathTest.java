package cn.frkovo.rhythmcv2.cv2;

import cn.frkovo.rhythmcv2.cv2.chart.Channel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent;
import cn.frkovo.rhythmcv2.cv2.chart.EditorTrack;
import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import cn.frkovo.rhythmcv2.cv2.render.ChartMath;
import cn.frkovo.rhythmcv2.cv2.render.Easings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreMathTest {

    // ---- BeatFraction ----

    @Test
    void fractionArithmeticAndNormalization() {
        assertEquals("1/3", BeatFraction.of(2, 6).toString());
        assertEquals("12", BeatFraction.parse("12").toString());
        assertEquals("7/16", BeatFraction.parse("7/16").toString());
        assertEquals("5/6", BeatFraction.parse("1/2").add(BeatFraction.parse("1/3")).toString());
        assertEquals("1/6", BeatFraction.parse("1/2").subtract(BeatFraction.parse("1/3")).toString());
        assertEquals("-1/3", BeatFraction.parse("1/3").negate().toString());
        // 交叉相乘精确比较：333333333333333333/10^18（截断）< 1/3（循环）< 1/2
        assertTrue(BeatFraction.parse("333333333333333333/1000000000000000000").compareTo(
                BeatFraction.parse("1/3")) < 0);
        assertTrue(BeatFraction.parse("1/3").compareTo(BeatFraction.parse("1/2")) < 0);
    }

    @Test
    void floorNegative() {
        assertEquals(-1L, BeatFraction.parse("-1/3").floor().longValue());
        assertEquals(-2L, BeatFraction.parse("-5/3").floor().longValue());
        assertEquals(2L, BeatFraction.parse("8/3").floor().longValue());
        assertEquals(3L, BeatFraction.parse("8/3").ceil().longValue());
    }

    // ---- BeatClock：与 Reborn TimingManager 公式对齐的样例 ----

    @Test
    void constantBpmRoundTrip() {
        BeatClock clock = BeatClock.constant(new BigDecimal("120"), 0);
        // 120 BPM → 500ms/拍
        assertEquals(500, clock.msLong(BeatFraction.of(1)));
        assertEquals(4750, clock.msLong(BeatFraction.parse("19/2")));
        assertEquals(BeatFraction.parse("19/2"), clock.beat(4750));
    }

    @Test
    void constantBpmWithOffset() {
        BeatClock clock = BeatClock.constant(new BigDecimal("120"), 2000);
        assertEquals(2500, clock.msLong(BeatFraction.of(1)));
        assertEquals(BeatFraction.of(2), clock.beat(3000));
        // beat=0 → offset
        assertEquals(2000, clock.msLong(BeatFraction.ZERO));
    }

    @Test
    void multiBpmSegmentation() {
        // beat0=120, beat4=60：段1 4拍×500ms=2000ms；段2 1000ms/拍
        BeatClock clock = new BeatClock(List.of(
                new BeatClock.BpmEvent(BeatFraction.ZERO, new BigDecimal("120")),
                new BeatClock.BpmEvent(BeatFraction.of(4), new BigDecimal("60"))), 0);
        assertEquals(2000, clock.msLong(BeatFraction.of(4)));
        assertEquals(3000, clock.msLong(BeatFraction.of(5)));
        assertEquals(BeatFraction.of(6), clock.beat(4000));
        // 段内非整数拍：2500ms = 段2 内 500ms = 0.5 拍 → beat 4.5
        BeatFraction b = clock.beat(2500);
        assertEquals("9/2", b.toString());
    }

    @Test
    void quantize() {
        BeatClock clock = BeatClock.constant(new BigDecimal("120"), 0);
        assertEquals("1/3", clock.quantize(BeatFraction.parse("7/20"), 3).toString());
        assertEquals("1/2", clock.quantize(BeatFraction.parse("7/20"), 2).toString());
        assertEquals(BeatFraction.parse("7/20"), clock.quantize(BeatFraction.parse("7/20"), 0));
    }

    // ---- Easings：样例值与 Reborn EasingUtils 一致 ----

    @Test
    void easingSampleValues() {
        assertEquals(0.5, Easings.ease(0.5, Easings.LINEAR), 1e-12);
        // IN_QUAD at 0.5 = 0.25
        assertEquals(0.25, Easings.ease(0.5, 4), 1e-12);
        // OUT_QUAD at 0.5 = 0.75
        assertEquals(0.75, Easings.ease(0.5, 5), 1e-12);
        // IN_OUT_QUAD at 0.5 = 0.5
        assertEquals(0.5, Easings.ease(0.5, 6), 1e-12);
        // IN_SINE at 0.5 = 1-cos(π/4) ≈ 0.29289
        assertEquals(1 - Math.cos(Math.PI / 4), Easings.ease(0.5, 1), 1e-12);
        assertEquals(0, Easings.ease(0, 16), 1e-12); // IN_EXPO x==0
        assertEquals(1, Easings.ease(1, 17), 1e-12); // OUT_EXPO x==1
        // lerp 端点 clamp
        assertEquals(2.0, Easings.lerp(1, 2, 1.5, Easings.LINEAR), 1e-12);
        assertEquals(1.0, Easings.lerp(1, 2, -0.5, Easings.LINEAR), 1e-12);
    }

    @Test
    void easingIntegralShape() {
        for (int id = 0; id < Easings.COUNT; id++) {
            double f0 = Easings.integral(0, id);
            assertEquals(0, f0, 1e-9, "id=" + id);
            // IN_SQUARE 零面积（Reborn 特判）；IN_OUT_SQUARE 全程 = 0.5
            double expected1 = id == 31 ? 0 : id == 32 ? 1 : id == 33 ? 0.5 : 1;
            assertEquals(expected1, Easings.integral(1, id), 1e-9, "id=" + id);
        }
        // 单调不减仅对无负瓣的函数成立（back/elastic 系有负瓣，积分合法地先减后增）
        java.util.Set<Integer> negativeLobe = java.util.Set.of(22, 24, 25, 26, 27);
        for (int id = 0; id < Easings.COUNT; id++) {
            if (negativeLobe.contains(id)) {
                continue;
            }
            double prev = 0;
            for (int i = 1; i <= 20; i++) {
                double v = Easings.integral(i / 20.0, id);
                assertTrue(v >= prev - 1e-12, "id=" + id + " t=" + i);
                prev = v;
            }
        }
        // IN_SQUARE 特判：0 面积
        assertEquals(0, Easings.integral(0.75, 31), 1e-12);
        assertEquals(0.75, Easings.integral(0.75, 32), 1e-12);
    }

    // ---- ChartMath：Reborn 同构样例 ----

    @Test
    void transformationInterpolation() {
        EditorTrack track = new EditorTrack("track-0");
        track.events.get(Channel.X_TRANSFORM).add(
                new EditorNumEvent("e1", BeatFraction.ZERO, BeatFraction.of(4),
                        BigDecimal.ZERO, BigDecimal.valueOf(2), 5)); // OUT_QUAD
        // beat 2: t=0.5, ease=0.75 → 1.5
        assertEquals(1.5, ChartMath.getTransformation(track.events.get(Channel.X_TRANSFORM), 2, 0), 1e-12);
        // 区间后取端点
        assertEquals(2.0, ChartMath.getTransformation(track.events.get(Channel.X_TRANSFORM), 10, 0), 1e-12);
        // 无事件取默认
        assertEquals(1.0, ChartMath.getTransformation(track.events.get(Channel.X_SCALE), 0, 1), 1e-12);
    }

    @Test
    void distanceIntegralAndHoles() {
        EditorTrack track = new EditorTrack("track-0");
        List<EditorNumEvent> speed = track.events.get(Channel.SPEED);
        speed.add(new EditorNumEvent("s1", BeatFraction.ZERO, BeatFraction.of(4),
                BigDecimal.ONE, BigDecimal.ONE, 0));
        speed.add(new EditorNumEvent("s2", BeatFraction.of(8), BeatFraction.of(12),
                BigDecimal.ONE, BigDecimal.ONE, 0));
        // 恒速 1 × playerSpeed 1：beat 4 → 距离 4
        assertEquals(4, ChartMath.getDistance(speed, 4, 1.0), 1e-12);
        // 空洞段不产生距离（速度 0 语义）
        assertEquals(4, ChartMath.getDistance(speed, 6, 1.0), 1e-12);
        assertEquals(5, ChartMath.getDistance(speed, 9, 1.0), 1e-12);
        // 首事件前的负外推（Reborn 同语义）
        EditorTrack t2 = new EditorTrack("track-1");
        t2.events.get(Channel.SPEED).add(new EditorNumEvent("s", BeatFraction.of(4),
                BeatFraction.of(8), BigDecimal.ONE, BigDecimal.ONE, 0));
        assertEquals(-2, ChartMath.getDistance(t2.events.get(Channel.SPEED), 2, 1.0), 1e-12);
    }

    @Test
    void noteComposeStaticTrack() {
        EditorTrack track = new EditorTrack("track-0");
        // 恒速 1：Reborn 语义下无 speedEvents 距离恒 0，需有事件才有位移
        track.events.get(Channel.SPEED).add(new EditorNumEvent("s",
                BeatFraction.ZERO, BeatFraction.of(16), BigDecimal.ONE, BigDecimal.ONE, 0));
        ChartMath.TrackState ts = ChartMath.evaluateState(track, 4, 1.0);
        // 音符 beat 8, pos (0,0,0)，游标 beat 4 → dis = 8-4 = 4
        EditorNote n = new EditorNote("n1", EditorNote.TAP, BeatFraction.of(8));
        double noteDis = ChartMath.noteDistance(track, n.beat, 1.0);
        double[] w = ChartMath.composeWorld(ts, noteDis, n.pos[0], n.pos[1], n.pos[2]);
        assertEquals(4, w[3], 1e-12);
        assertEquals(0, w[0], 1e-12);
        assertEquals(0, w[1], 1e-12);
        // worldZ = originZ − dis：dis>0 → 世界北移（更负）
        double worldZ = ChartMath.judgeZ() - w[2];
        assertTrue(worldZ < ChartMath.judgeZ());
    }

    @Test
    void noteComposeZRotation() {
        EditorTrack track = new EditorTrack("track-0");
        track.events.get(Channel.Z_ROTATE).add(new EditorNumEvent("r",
                BeatFraction.ZERO, BeatFraction.of(16), BigDecimal.ZERO, BigDecimal.valueOf(90), 0));
        ChartMath.TrackState ts = ChartMath.evaluateState(track, 16, 1.0);
        assertEquals(90, ts.zRot(), 1e-9);
        // 旋转 Z 90°：(1,0) → (0,1)
        double noteDis = ChartMath.noteDistance(track, BeatFraction.of(16), 1.0);
        double[] w = ChartMath.composeWorld(ts, noteDis,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(0, w[0], 1e-9);
        assertEquals(1, w[1], 1e-9);
    }

    @Test
    void slotLayoutMatchesReborn() {
        assertEquals(0.5, ChartMath.gameCenterX(0), 1e-12);
        assertEquals(-1.5, ChartMath.gameCenterZ(), 1e-12);
        assertEquals(-3.0, ChartMath.judgeZ(), 1e-12);
        assertEquals(100.5, ChartMath.originY(), 1e-12);
    }

    @Test
    void visualScaleMask() {
        assertEquals(1.0, ChartMath.visualScaleMask(EditorNote.TAP, 25), 1e-12);
        assertEquals(0.9, ChartMath.visualScaleMask(EditorNote.TAP, 0), 1e-12);
        assertEquals(1.0, ChartMath.visualScaleMask(EditorNote.DODGE, 0), 1e-12);
    }
}
