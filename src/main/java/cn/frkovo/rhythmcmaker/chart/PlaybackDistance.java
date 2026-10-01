package cn.frkovo.rhythmcmaker.chart;

import java.util.Comparator;
import java.util.List;

public final class PlaybackDistance {
    private static final int INTEGRAL_STEPS = 4097;
    private static final double[][] INTEGRALS = buildIntegrals();
    private PlaybackDistance() {}

    public static List<ChartManifest.SpeedEvent> sortedEvents(List<ChartManifest.SpeedEvent> speedEvents) {
        if (speedEvents == null || speedEvents.isEmpty()) return List.of();
        return speedEvents.stream()
                .filter(event -> event != null && Double.isFinite(event.startBeat) && Double.isFinite(event.endBeat)
                        && Double.isFinite(event.startValue) && Double.isFinite(event.endValue))
                .sorted(Comparator.comparingDouble(event -> event.startBeat)).toList();
    }

    public static Prepared prepare(List<ChartManifest.SpeedEvent> speedEvents) {
        return new Prepared(sortedEvents(speedEvents));
    }

    public static double atBeat(List<ChartManifest.SpeedEvent> speedEvents, double beat) {
        return prepare(speedEvents).atBeat(beat);
    }

    public static double eventDistance(ChartManifest.SpeedEvent event, double beat) {
        double start = event.startBeat;
        double end = event.endBeat;
        if (!(end > start) || beat <= start) return 0.0;
        double length = end - start;
        double progress = Math.min(1.0, (beat - start) / length);
        double normalizedArea = event.startValue * progress
                + (event.endValue - event.startValue) * integral(progress, event.easing);
        return normalizedArea * length;
    }

    public static final class Prepared {
        private final List<ChartManifest.SpeedEvent> events;
        private final double[] starts;

        private Prepared(List<ChartManifest.SpeedEvent> events) {
            this.events = events;
            this.starts = new double[events.size()];
            for (int index = 0; index < events.size(); index++) starts[index] = events.get(index).startBeat;
        }

        public double atBeat(double beat) {
            if (events.isEmpty()) return beat;
            if (beat < starts[0]) {
                ChartManifest.SpeedEvent first = events.get(0);
                return first.startValue * (beat - first.startBeat);
            }
            double distance = 0.0;
            for (int index = 0; index < events.size(); index++) {
                if (starts[index] > beat) break;
                distance += eventDistance(events.get(index), beat);
            }
            return distance;
        }
    }

    private static double integral(double progress, int easing) {
        if (progress <= 0.0) return 0.0;
        int id = Math.max(0, Math.min(33, easing));
        if (id == 31) return 0.0;
        if (id == 32) return Math.min(progress, 1.0);
        if (id == 33) return Math.max(0.0, Math.min(progress, 1.0) - 0.5);
        if (progress >= 1.0) return 1.0;
        double x = progress * (INTEGRAL_STEPS - 1);
        int lower = (int) x;
        int upper = Math.min(lower + 1, INTEGRAL_STEPS - 1);
        double fraction = x - lower;
        return INTEGRALS[id][lower] * (1.0 - fraction) + INTEGRALS[id][upper] * fraction;
    }

    private static double[][] buildIntegrals() {
        double[][] tables = new double[34][INTEGRAL_STEPS];
        for (int id = 0; id < tables.length; id++) {
            double total = 0.0;
            double previous = ease(0.0, id);
            for (int index = 1; index < INTEGRAL_STEPS; index++) {
                double current = ease((double) index / (INTEGRAL_STEPS - 1), id);
                total += (previous + current) * 0.5 / (INTEGRAL_STEPS - 1);
                tables[id][index] = total;
                previous = current;
            }
            if (total > 1.0E-12) {
                for (int index = 0; index < INTEGRAL_STEPS; index++) tables[id][index] /= total;
            }
        }
        return tables;
    }

    private static double ease(double t, int id) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return switch (id) {
            case 1 -> 1 - Math.cos(t * Math.PI) / 2;
            case 2 -> Math.sin(t * Math.PI) / 2;
            case 3 -> -(Math.cos(Math.PI * t) - 1) / 2;
            case 4 -> t * t;
            case 5 -> 1 - Math.pow(1 - t, 2);
            case 6 -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case 7 -> t * t * t;
            case 8 -> 1 - Math.pow(1 - t, 3);
            case 9 -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case 10 -> Math.pow(t, 4);
            case 11 -> 1 - Math.pow(1 - t, 4);
            case 12 -> t < 0.5 ? 8 * Math.pow(t, 4) : 1 - Math.pow(-2 * t + 2, 4) / 2;
            case 13 -> Math.pow(t, 5);
            case 14 -> 1 - Math.pow(1 - t, 5);
            case 15 -> t < 0.5 ? 16 * Math.pow(t, 5) : 1 - Math.pow(-2 * t + 2, 5) / 2;
            case 16 -> t == 0 ? 0 : 1 - Math.pow(2, -10 * t);
            case 17 -> t == 1 ? 1 : Math.pow(2, 10 * t - 10);
            case 18 -> t < 0.5 ? (1 - Math.pow(2, 20 * t - 10)) / 2 : (Math.pow(2, -20 * t + 10) + 1) / 2;
            case 19 -> 1 - Math.sqrt(1 - Math.pow(t, 2));
            case 20 -> Math.sqrt(1 - Math.pow(t - 1, 2));
            case 21 -> t < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
            case 22 -> 2.70158 * t * t * t - 1.70158 * t * t;
            case 23 -> 1 + 2.70158 * Math.pow(t - 1, 3) + 1.70158 * Math.pow(t - 1, 2);
            case 24 -> t < 0.5 ? (Math.pow(2 * t, 2) * ((1.70158 + 1) * 2 * t - 1.70158)) / 2 : (Math.pow(2 * t - 2, 2) * ((1.70158 + 1) * (t * 2 - 2) + 1.70158) + 2) / 2;
            case 25 -> -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * Math.PI / 3));
            case 26 -> Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
            case 27 -> t < 0.5 ? -Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * (2 * Math.PI / 4.5)) / 2
                    : Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * (2 * Math.PI / 4.5)) / 2 + 1;
            case 28 -> 1 - outBounce(1 - t);
            case 29 -> outBounce(t);
            case 30 -> t < 0.5 ? (1 - outBounce(1 - 2 * t)) / 2 : (1 + outBounce(2 * t - 1)) / 2;
            case 31 -> 0;
            case 32 -> 1;
            case 33 -> t < 0.5 ? 0 : 1;
            default -> t;
        };
    }

    private static double outBounce(double t) {
        double n = 7.5625;
        double d = 2.75;
        if (t < 1 / d) return n * t * t;
        if (t < 2 / d) return n * (t -= 1.5 / d) * t + 0.75;
        if (t < 2.5 / d) return n * (t -= 2.25 / d) * t + 0.9375;
        return n * (t -= 2.625 / d) * t + 0.984375;
    }
}
