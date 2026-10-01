package cn.frkovo.rhythmcv2.cv2.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * 规范化有理数 beat 值。编辑态、撤销栈、自动保存与 .rmcd 工作文件的唯一权威 beat 类型。
 * 不变量：denominator > 0，gcd(|numerator|, denominator) == 1。
 */
public record BeatFraction(BigInteger numerator, BigInteger denominator) implements Comparable<BeatFraction> {

    public static final BeatFraction ZERO = new BeatFraction(BigInteger.ZERO, BigInteger.ONE);
    private static final BigInteger TWO = BigInteger.valueOf(2);

    public BeatFraction {
        if (denominator.signum() == 0) {
            throw new ArithmeticException("beat denominator is zero");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger g = numerator.gcd(denominator);
        if (g.signum() != 0 && g.compareTo(BigInteger.ONE) != 0) {
            numerator = numerator.divide(g);
            denominator = denominator.divide(g);
        }
    }

    public static BeatFraction of(long numerator) {
        return new BeatFraction(BigInteger.valueOf(numerator), BigInteger.ONE);
    }

    public static BeatFraction of(long numerator, long denominator) {
        return new BeatFraction(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    /** 解析 "12"、"n/d" 形式的精确字符串。 */
    public static BeatFraction parse(String text) {
        String s = text.trim();
        int slash = s.indexOf('/');
        if (slash < 0) {
            return new BeatFraction(new BigInteger(s), BigInteger.ONE);
        }
        return new BeatFraction(new BigInteger(s.substring(0, slash)), new BigInteger(s.substring(slash + 1)));
    }

    /** 从十进制（导入边界专用）构造精确分数；输入必须已经过 BigDecimal 而非 double 路径。 */
    public static BeatFraction fromDecimal(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        int scale = stripped.scale();
        if (scale >= 0) {
            return new BeatFraction(stripped.unscaledValue(), BigInteger.TEN.pow(scale));
        }
        return new BeatFraction(stripped.unscaledValue().multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
    }

    public boolean isZero() {
        return numerator.signum() == 0;
    }

    public boolean isNegative() {
        return numerator.signum() < 0;
    }

    public BeatFraction add(BeatFraction other) {
        return new BeatFraction(
                numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public BeatFraction subtract(BeatFraction other) {
        return new BeatFraction(
                numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public BeatFraction multiply(long factor) {
        return new BeatFraction(numerator.multiply(BigInteger.valueOf(factor)), denominator);
    }

    public BeatFraction divide(long divisor) {
        return new BeatFraction(numerator, denominator.multiply(BigInteger.valueOf(divisor)));
    }

    public BeatFraction negate() {
        return new BeatFraction(numerator.negate(), denominator);
    }

    @Override
    public int compareTo(BeatFraction other) {
        return numerator.multiply(other.denominator)
                .compareTo(other.numerator.multiply(denominator));
    }

    public BigInteger floor() {
        BigInteger[] div = numerator.divideAndRemainder(denominator);
        if (div[1].signum() == 0 || numerator.signum() >= 0) {
            return div[0];
        }
        return div[0].subtract(BigInteger.ONE);
    }

    public BigInteger ceil() {
        return isZero() ? BigInteger.ZERO : floor().add(isExact() ? BigInteger.ZERO : BigInteger.ONE);
    }

    public boolean isExact() {
        return denominator.compareTo(BigInteger.ONE) == 0;
    }

    /** HALF_UP 取整到整数拍。 */
    public BigInteger roundHalfUp() {
        if (numerator.signum() >= 0) {
            return TWO.multiply(numerator).add(denominator).divide(TWO.multiply(denominator));
        }
        return TWO.multiply(numerator).subtract(denominator).divide(TWO.multiply(denominator));
    }

    /** 定点小数（默认 12 位 HALF_EVEN，与 BeatClock 除法口径一致）。 */
    public BigDecimal toBigDecimal() {
        return toBigDecimal(12);
    }

    public BigDecimal toBigDecimal(int scale) {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), scale, RoundingMode.HALF_EVEN);
    }

    /** 渲染/插值边界专用（渲染数学与 Reborn 一样运行在 double 域）。 */
    public double toDouble() {
        return toBigDecimal(17).doubleValue();
    }

    /** 序列化："n/d"；整数简写为 "n"。 */
    @Override
    public String toString() {
        return isExact() ? numerator.toString() : numerator + "/" + denominator;
    }
}
