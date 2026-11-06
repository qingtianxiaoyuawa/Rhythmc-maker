package cn.frkovo.rhythmcmaker.chart;

public final class HoldFillPlanner {
    public static final double MIN_GAP = 0.1;
    public static final int MAX_PER_PAIR = 256;

    private HoldFillPlanner() {}

    public static int fillCount(double span, double scaleStart, double scaleEnd) {
        double blockLength = (Math.abs(scaleStart) + Math.abs(scaleEnd)) * 0.5;
        if (!(blockLength > 0.0) || !(span - blockLength >= MIN_GAP)) return 0;
        int count = (int) Math.ceil(span / blockLength - 1.0E-9) - 1;
        return Math.max(0, Math.min(MAX_PER_PAIR, count));
    }

    public static double interpolate(double start, double end, double progress) {
        return start + (end - start) * progress;
    }
}
