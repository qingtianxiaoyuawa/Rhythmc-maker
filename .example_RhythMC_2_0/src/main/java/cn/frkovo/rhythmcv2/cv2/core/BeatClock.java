package cn.frkovo.rhythmcv2.cv2.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * beat ↔ ms 双向换算（BPM 事件表分段积分）、offset、snap 量化与小节计算。
 * 除法一律 scale=12、HALF_EVEN（附录 A）；beat 比较一律精确比较。
 */
public final class BeatClock {

    public static final int DIV_SCALE = 12;
    private static final BigDecimal MS_PER_MINUTE = BigDecimal.valueOf(60000);

    /** bpms 事件表条目：beat 严格递增，首项 beat = 0，bpm > 0。 */
    public record BpmEvent(BeatFraction beat, BigDecimal bpm) {
    }

    private final List<BpmEvent> bpms;
    private final long offsetMs;
    /** 每段累计起点毫秒（精确 BigDecimal，不提前取整）。 */
    private final List<BigDecimal> segmentStartMs;

    public BeatClock(List<BpmEvent> bpms, long offsetMs) {
        if (bpms == null || bpms.isEmpty()) {
            throw new IllegalArgumentException("bpms must not be empty");
        }
        if (!bpms.getFirst().beat().isZero()) {
            throw new IllegalArgumentException("first bpm event beat must be 0");
        }
        for (int i = 0; i < bpms.size(); i++) {
            BpmEvent e = bpms.get(i);
            if (e.bpm().signum() <= 0) {
                throw new IllegalArgumentException("bpm must be positive at index " + i);
            }
            if (i > 0 && bpms.get(i - 1).beat().compareTo(e.beat()) >= 0) {
                throw new IllegalArgumentException("bpms beats must be strictly increasing");
            }
        }
        this.bpms = List.copyOf(bpms);
        this.offsetMs = offsetMs;
        this.segmentStartMs = new ArrayList<>(bpms.size());
        BigDecimal t = BigDecimal.ZERO;
        for (int i = 0; i < bpms.size(); i++) {
            segmentStartMs.add(t);
            if (i + 1 < bpms.size()) {
                BeatFraction span = bpms.get(i + 1).beat().subtract(bpms.get(i).beat());
                t = t.add(segmentMs(span, bpms.get(i).bpm()));
            }
        }
    }

    private static BigDecimal segmentMs(BeatFraction beats, BigDecimal bpm) {
        return beats.toBigDecimal(DIV_SCALE).multiply(MS_PER_MINUTE)
                .divide(bpm, DIV_SCALE, RoundingMode.HALF_EVEN);
    }

    /** ms(beat)：分段积分，精确 BigDecimal（scale 12）。offset 只参与 ms 域。 */
    public BigDecimal msExact(BeatFraction beat) {
        BigDecimal t = BigDecimal.valueOf(offsetMs);
        for (int i = 0; i < bpms.size(); i++) {
            BpmEvent seg = bpms.get(i);
            if (beat.compareTo(seg.beat()) <= 0) {
                break;
            }
            BeatFraction clamped = beat;
            if (i + 1 < bpms.size() && beat.compareTo(bpms.get(i + 1).beat()) > 0) {
                clamped = bpms.get(i + 1).beat();
            }
            t = t.add(segmentMs(clamped.subtract(seg.beat()), seg.bpm()));
        }
        return t;
    }

    /** 边界换算：整数毫秒（tick / 音频协议）。 */
    public long msLong(BeatFraction beat) {
        return msExact(beat).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** beat(ms)：先扣 offset，再逐段累减，余数按当前段 bpm 换算成分数。 */
    public BeatFraction beat(long ms) {
        BigDecimal r = BigDecimal.valueOf(ms).subtract(BigDecimal.valueOf(offsetMs));
        for (int i = 0; i < bpms.size(); i++) {
            BpmEvent seg = bpms.get(i);
            BigDecimal segMs;
            if (i + 1 < bpms.size()) {
                segMs = segmentMs(bpms.get(i + 1).beat().subtract(seg.beat()), seg.bpm());
                if (r.compareTo(segMs) >= 0) {
                    r = r.subtract(segMs);
                    continue;
                }
            } else {
                segMs = null;
            }
            BigDecimal frac = r.multiply(seg.bpm()).divide(MS_PER_MINUTE, DIV_SCALE, RoundingMode.HALF_EVEN);
            return seg.beat().add(BeatFraction.fromDecimal(frac));
        }
        return bpms.getLast().beat();
    }

    /** snap 量化：q(beat) = round(beat × snap) / snap。snap ≤ 0 表示关闭量化。 */
    public BeatFraction quantize(BeatFraction beat, int snap) {
        if (snap <= 0) {
            return beat;
        }
        return new BeatFraction(beat.multiply((long) snap).roundHalfUp(), BigInteger.valueOf(snap));
    }

    /** 小节号（固定 4/4）。 */
    public long bar(BeatFraction beat) {
        return beat.floor().divide(BigInteger.valueOf(4)).longValueExact();
    }

    /** 拍内位置（0..4）。 */
    public BeatFraction inBar(BeatFraction beat) {
        return beat.subtract(BeatFraction.of(bar(beat) * 4L));
    }

    /** ≤ beat 的当前 BPM（编辑器显示用）。 */
    public BigDecimal bpmAt(BeatFraction beat) {
        BigDecimal bpm = bpms.getFirst().bpm();
        for (BpmEvent e : bpms) {
            if (e.beat().compareTo(beat) <= 0) {
                bpm = e.bpm();
            } else {
                break;
            }
        }
        return bpm;
    }

    public List<BpmEvent> events() {
        return bpms;
    }

    public long offsetMs() {
        return offsetMs;
    }

    /** 供测试：单段恒速表的便捷构造。 */
    public static BeatClock constant(BigDecimal bpm, long offsetMs) {
        return new BeatClock(List.of(new BpmEvent(BeatFraction.ZERO, bpm)), offsetMs);
    }
}
