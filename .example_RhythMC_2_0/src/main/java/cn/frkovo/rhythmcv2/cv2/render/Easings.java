package cn.frkovo.rhythmcv2.cv2.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * easing id 0–33 对照表，与 RhythMC-Reborn animation.md / EasingUtils 同构（方案 A：复制公式 + 样例测试）。
 * 积分使用 2048 步梯形预计算表，特判 IN_SQUARE / OUT_SQUARE / IN_OUT_SQUARE。
 */
public final class Easings {

    public static final int LINEAR = 0;
    public static final int COUNT = 34;
    private static final int INTEGRAL_STEPS = 2048;

    private static final DoubleUnaryOperator[] FUNCTIONS = {
            x -> x,
            x -> 1 - Math.cos(x * Math.PI / 2),
            x -> Math.sin(x * Math.PI / 2),
            x -> -(Math.cos(Math.PI * x) - 1) / 2,
            x -> x * x,
            x -> 1 - (1 - x) * (1 - x),
            x -> x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2,
            x -> x * x * x,
            x -> 1 - Math.pow(1 - x, 3),
            x -> x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2,
            x -> x * x * x * x,
            x -> 1 - Math.pow(1 - x, 4),
            x -> x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2,
            x -> Math.pow(x, 5),
            x -> 1 - Math.pow(1 - x, 5),
            x -> x < 0.5 ? 16 * x * x * x * x * x : 1 - Math.pow(-2 * x + 2, 5) / 2,
            x -> x == 0 ? 0 : Math.pow(2, 10 * x - 10),
            x -> x == 1 ? 1 : 1 - Math.pow(2, -10 * x),
            x -> x == 0 ? 0 : x == 1 ? 1 : x < 0.5 ? Math.pow(2, 20 * x - 10) / 2 : (2 - Math.pow(2, -20 * x + 10)) / 2,
            x -> 1 - Math.sqrt(1 - Math.pow(x, 2)),
            x -> Math.sqrt(1 - Math.pow(x - 1, 2)),
            x -> x < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * x, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * x + 2, 2)) + 1) / 2,
            x -> 2.70158 * x * x * x - 1.70158 * x * x,
            x -> 1 + 2.70158 * Math.pow(x - 1, 3) + 1.70158 * Math.pow(x - 1, 2),
            x -> x < 0.5
                    ? (Math.pow(2 * x, 2) * ((1.70158 * 1.525 + 1) * 2 * x - 1.70158 * 1.525)) / 2
                    : (Math.pow(2 * x - 2, 2) * ((1.70158 * 1.525 + 1) * (x * 2 - 2) + 1.70158 * 1.525) + 2) / 2,
            x -> x == 0 ? 0 : x == 1 ? 1 : -Math.pow(2, 10 * x - 10) * Math.sin((x * 10 - 10.75) * ((2 * Math.PI) / 3)),
            x -> x == 0 ? 0 : x == 1 ? 1 : Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * ((2 * Math.PI) / 3)) + 1,
            x -> x == 0 ? 0 : x == 1 ? 1
                    : x < 0.5 ? -(Math.pow(2, 20 * x - 10) * Math.sin((20 * x - 11.125) * ((2 * Math.PI) / 4.5))) / 2
                    : (Math.pow(2, -20 * x + 10) * Math.sin((20 * x - 11.125) * ((2 * Math.PI) / 4.5))) / 2 + 1,
            Easings::inBounce,
            Easings::outBounce,
            x -> x < 0.5 ? (1 - outBounce(1 - 2 * x)) / 2 : (1 + outBounce(2 * x - 1)) / 2,
            x -> x < 1 ? 0 : 1,
            x -> x <= 0 ? 0 : 1,
            x -> x < 0.5 ? 0 : 1,
    };

    /** 每种 easing 的归一化积分表 F(t)/F(1)（索引 = easing id）。 */
    private static final List<double[]> INTEGRALS = new ArrayList<>(COUNT);

    static {
        for (int id = 0; id < COUNT; id++) {
            INTEGRALS.add(buildIntegral(FUNCTIONS[id]));
        }
    }

    private Easings() {
    }

    public static int clampId(int id) {
        return (id < 0 || id >= COUNT) ? LINEAR : id;
    }

    public static String name(int id) {
        return switch (clampId(id)) {
            case 0 -> "LINEAR"; case 1 -> "IN_SINE"; case 2 -> "OUT_SINE"; case 3 -> "IN_OUT_SINE";
            case 4 -> "IN_QUAD"; case 5 -> "OUT_QUAD"; case 6 -> "IN_OUT_QUAD"; case 7 -> "IN_CUBIC";
            case 8 -> "OUT_CUBIC"; case 9 -> "IN_OUT_CUBIC"; case 10 -> "IN_QUART"; case 11 -> "OUT_QUART";
            case 12 -> "IN_OUT_QUART"; case 13 -> "IN_QUINT"; case 14 -> "OUT_QUINT"; case 15 -> "IN_OUT_QUINT";
            case 16 -> "IN_EXPO"; case 17 -> "OUT_EXPO"; case 18 -> "IN_OUT_EXPO"; case 19 -> "IN_CIRC";
            case 20 -> "OUT_CIRC"; case 21 -> "IN_OUT_CIRC"; case 22 -> "IN_BACK"; case 23 -> "OUT_BACK";
            case 24 -> "IN_OUT_BACK"; case 25 -> "IN_ELASTIC"; case 26 -> "OUT_ELASTIC"; case 27 -> "IN_OUT_ELASTIC";
            case 28 -> "IN_BOUNCE"; case 29 -> "OUT_BOUNCE"; case 30 -> "IN_OUT_BOUNCE"; case 31 -> "IN_SQUARE";
            case 32 -> "OUT_SQUARE"; default -> "IN_OUT_SQUARE";
        };
    }

    /** getEase(t)：clamp 到 [0,1] 后套函数。 */
    public static double ease(double t, int easing) {
        if (t < 0) return 0;
        if (t > 1) return 1;
        return FUNCTIONS[clampId(easing)].applyAsDouble(t);
    }

    /** getEase(a, b, t)：线性插值包一层 easing。t clamp 后越界直接取端点（与 Reborn 一致）。 */
    public static double lerp(double a, double b, double t, int easing) {
        if (t < 0) return a;
        if (t > 1) return b;
        return a + (b - a) * ease(t, easing);
    }

    /** 归一化积分 ∫₀ᵗ f / ∫₀¹ f；零面积函数特判与 Reborn 一致。 */
    public static double integral(double t, int easing) {
        int id = clampId(easing);
        if (t <= 0) return 0;
        if (t > 1) t = 1;
        if (id == 31) return 0;                 // IN_SQUARE
        if (id == 32) return Math.min(t, 1);    // OUT_SQUARE
        if (id == 33) return Math.max(0, Math.min(t, 1) - 0.5); // IN_OUT_SQUARE
        double[] table = INTEGRALS.get(id);
        double x = t * (INTEGRAL_STEPS - 1);
        int i = (int) x;
        double frac = x - i;
        int j = Math.min(i + 1, INTEGRAL_STEPS - 1);
        return table[i] * (1 - frac) + table[j] * frac;
    }

    private static double[] buildIntegral(DoubleUnaryOperator f) {
        double[] table = new double[INTEGRAL_STEPS];
        double total = 0;
        double prev = f.applyAsDouble(0);
        for (int i = 1; i < INTEGRAL_STEPS; i++) {
            double x = (double) i / (INTEGRAL_STEPS - 1);
            double cur = f.applyAsDouble(x);
            total += (prev + cur) / 2 / (INTEGRAL_STEPS - 1);
            prev = cur;
            table[i] = total;
        }
        if (total <= 1e-12) {
            for (int i = 0; i < INTEGRAL_STEPS; i++) table[i] = 0;
        } else {
            for (int i = 0; i < INTEGRAL_STEPS; i++) table[i] /= total;
        }
        return table;
    }

    private static double outBounce(double x) {
        double n1 = 7.5625, d1 = 2.75;
        if (x < 1 / d1) return n1 * x * x;
        if (x < 2 / d1) return n1 * (x -= 1.5 / d1) * x + 0.75;
        if (x < 2.5 / d1) return n1 * (x -= 2.25 / d1) * x + 0.9375;
        return n1 * (x -= 2.625 / d1) * x + 0.984375;
    }

    private static double inBounce(double x) {
        return 1 - outBounce(1 - x);
    }
}
