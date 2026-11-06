package cn.frkovo.rhythmcmaker.chart;

public final class TimeFade {
    public static final long DAY_TICKS = 24000L;

    public enum Direction {
        CLOCKWISE,
        COUNTERCLOCKWISE;

        public static Direction parse(String name) {
            return COUNTERCLOCKWISE.name().equals(name) ? COUNTERCLOCKWISE : CLOCKWISE;
        }
    }

    private TimeFade() {}

    public static long wrap(long tick) {
        return Math.floorMod(tick, DAY_TICKS);
    }

    public static long distance(long from, long to, Direction direction) {
        return direction == Direction.CLOCKWISE ? wrap(to - from) : wrap(from - to);
    }

    public static long at(long from, long to, Direction direction, double progress) {
        double bounded = Double.isNaN(progress) ? 0.0 : Math.max(0.0, Math.min(1.0, progress));
        if (bounded >= 1.0) return wrap(to);
        long moved = Math.round(distance(from, to, direction) * bounded);
        return wrap(direction == Direction.CLOCKWISE ? from + moved : from - moved);
    }
}
