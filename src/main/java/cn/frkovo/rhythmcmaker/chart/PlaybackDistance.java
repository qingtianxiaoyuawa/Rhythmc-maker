package cn.frkovo.rhythmcmaker.chart;

import java.util.Comparator;
import java.util.List;

public final class PlaybackDistance {
    private PlaybackDistance() {}

    public static List<ChartManifest.SpeedEvent> sortedEvents(List<ChartManifest.SpeedEvent> speedEvents) {
        if (speedEvents == null || speedEvents.isEmpty()) return List.of();
        return speedEvents.stream()
                .filter(event -> event != null && Double.isFinite(event.startBeat) && Double.isFinite(event.endBeat)
                        && Double.isFinite(event.startValue) && Double.isFinite(event.endValue))
                .sorted(Comparator.comparingDouble(event -> event.startBeat)).toList();
    }

    public static double atBeat(List<ChartManifest.SpeedEvent> speedEvents, double beat) {
        if (speedEvents == null || speedEvents.isEmpty()) return beat;
        double distance = 0.0;
        for (ChartManifest.SpeedEvent event : speedEvents) {
            if (event.startBeat > beat) break;
            if (event.endBeat <= event.startBeat || beat <= event.startBeat) continue;
            double length = event.endBeat - event.startBeat;
            if (beat > event.endBeat) {
                distance += 0.5 * (event.startValue + event.endValue) * length;
            } else {
                double progress = (beat - event.startBeat) / length;
                double currentValue = event.startValue + (event.endValue - event.startValue) * ease(progress, event.easing);
                distance += 0.5 * (event.startValue + currentValue) * (beat - event.startBeat);
            }
        }
        return distance;
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
            case 16 -> Math.pow(2, 10 * t - 10);
            case 17 -> 1 - Math.pow(2, -10 * t);
            case 18 -> t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
            case 19 -> 1 - Math.sqrt(1 - t * t);
            case 20 -> Math.sqrt(1 - Math.pow(t - 1, 2));
            case 21 -> t < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
            case 22 -> 2.70158 * t * t * t - 1.70158 * t * t;
            case 23 -> 1 + 2.70158 * Math.pow(t - 1, 3) + 1.70158 * Math.pow(t - 1, 2);
            case 24 -> t < 0.5 ? Math.pow(2 * t, 2) * ((1.70158 * 1.525 + 1) * 2 * t - 1.70158 * 1.525) / 2
                    : (Math.pow(2 * t - 2, 2) * ((1.70158 * 1.525 + 1) * (2 * t - 2) + 1.70158 * 1.525) + 2) / 2;
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