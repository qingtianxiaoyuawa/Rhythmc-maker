package cn.frkovo.rhythmcmaker.chart;

public final class TimeFadeRegression {
    public static void main(String[] args) {
        require(TimeFade.wrap(24000) == 0, "day wraps at 24000 ticks");
        require(TimeFade.distance(23000, 1000, TimeFade.Direction.CLOCKWISE) == 2000,
                "clockwise transition crosses midnight");
        require(TimeFade.distance(1000, 23000, TimeFade.Direction.COUNTERCLOCKWISE) == 2000,
                "counterclockwise transition crosses midnight");
        require(TimeFade.at(23000, 1000, TimeFade.Direction.CLOCKWISE, 0.5) == 0,
                "midpoint interpolation crosses midnight");
        require(TimeFade.at(1000, 23000, TimeFade.Direction.COUNTERCLOCKWISE, 1.0) == 23000,
                "completed fade reaches exact endpoint");
        require(TimeFade.at(1000, 23000, TimeFade.Direction.CLOCKWISE, Double.NaN) == 1000,
                "invalid progress stays at start");
        System.out.println("Time fade regression passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
