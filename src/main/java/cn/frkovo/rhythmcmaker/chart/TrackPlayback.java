package cn.frkovo.rhythmcmaker.chart;

import java.util.Comparator;
import java.util.List;

/** Runtime evaluation of RhythMC 3.0 track curves. */
public final class TrackPlayback {
    private TrackPlayback() {}

    public static Prepared prepare(ChartManifest.Track track, List<ChartManifest.SpeedEvent> legacySpeedEvents) {
        return new Prepared(track, legacySpeedEvents);
    }

    public record Pose(double distance, double x, double y, double z,
                       double rotationX, double rotationY, double rotationZ,
                       double scaleX, double scaleY, double scaleZ) {}

    public static final class Prepared {
        private final List<ChartManifest.NumEvent> speed;
        private final List<ChartManifest.NumEvent> xTransform;
        private final List<ChartManifest.NumEvent> yTransform;
        private final List<ChartManifest.NumEvent> zTransform;
        private final List<ChartManifest.NumEvent> xRotate;
        private final List<ChartManifest.NumEvent> yRotate;
        private final List<ChartManifest.NumEvent> zRotate;
        private final List<ChartManifest.NumEvent> xScale;
        private final List<ChartManifest.NumEvent> yScale;
        private final List<ChartManifest.NumEvent> zScale;
        private final double[] speedPrefix;

        private Prepared(ChartManifest.Track track, List<ChartManifest.SpeedEvent> legacySpeedEvents) {
            speed = events(track == null ? null : track.speedEvents, legacySpeedEvents);
            xTransform = events(track == null ? null : track.xTransformEvents, null);
            yTransform = events(track == null ? null : track.yTransformEvents, null);
            zTransform = events(track == null ? null : track.zTransformEvents, null);
            xRotate = events(track == null ? null : track.xRotateEvents, null);
            yRotate = events(track == null ? null : track.yRotateEvents, null);
            zRotate = events(track == null ? null : track.zRotateEvents, null);
            xScale = events(track == null ? null : track.xScaleEvents, null);
            yScale = events(track == null ? null : track.yScaleEvents, null);
            zScale = events(track == null ? null : track.zScaleEvents, null);
            speedPrefix = buildSpeedPrefix(speed);
        }

        public double distanceAt(double beat) {
            if (speed.isEmpty()) return beat;
            int index = upperBound(speed, beat) - 1;
            if (index < 0) return 0.0;
            ChartManifest.NumEvent event = speed.get(index);
            return speedPrefix[index] + eventDistance(event, beat);
        }

        public Pose poseAt(double beat) {
            return new Pose(distanceAt(beat),
                    valueAt(xTransform, beat, 0.0), valueAt(yTransform, beat, 0.0), valueAt(zTransform, beat, 0.0),
                    valueAt(xRotate, beat, 0.0), valueAt(yRotate, beat, 0.0), valueAt(zRotate, beat, 0.0),
                    valueAt(xScale, beat, 1.0), valueAt(yScale, beat, 1.0), valueAt(zScale, beat, 1.0));
        }
    }

    private static List<ChartManifest.NumEvent> events(List<ChartManifest.NumEvent> source, List<ChartManifest.SpeedEvent> legacy) {
        java.util.ArrayList<ChartManifest.NumEvent> result = new java.util.ArrayList<>();
        if (source != null) result.addAll(source.stream().filter(TrackPlayback::valid).toList());
        if (result.isEmpty() && legacy != null) for (ChartManifest.SpeedEvent event : legacy) {
            if (event != null && Double.isFinite(event.startBeat) && Double.isFinite(event.endBeat)
                    && Double.isFinite(event.startValue) && Double.isFinite(event.endValue)) {
                result.add(new ChartManifest.NumEvent(event.startBeat, event.endBeat, event.startValue, event.endValue, event.easing));
            }
        }
        result.sort(Comparator.comparingDouble(event -> event.startBeat));
        return List.copyOf(result);
    }

    private static boolean valid(ChartManifest.NumEvent event) {
        return event != null && Double.isFinite(event.startBeat) && Double.isFinite(event.endBeat)
                && Double.isFinite(event.startValue) && Double.isFinite(event.endValue);
    }

    private static double valueAt(List<ChartManifest.NumEvent> events, double beat, double fallback) {
        int index = upperBound(events, beat) - 1;
        if (index < 0) return fallback;
        ChartManifest.NumEvent event = events.get(index);
        if (event.endBeat <= event.startBeat || beat >= event.endBeat) return event.endValue;
        return interpolate(event, (beat - event.startBeat) / (event.endBeat - event.startBeat));
    }

    private static int upperBound(List<ChartManifest.NumEvent> events, double beat) {
        int low = 0;
        int high = events.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (events.get(middle).startBeat <= beat) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static double[] buildSpeedPrefix(List<ChartManifest.NumEvent> events) {
        double[] prefix = new double[events.size()];
        double distance = 0.0;
        for (int index = 0; index < events.size(); index++) {
            prefix[index] = distance;
            ChartManifest.NumEvent event = events.get(index);
            distance += eventDistance(event, event.endBeat);
        }
        return prefix;
    }

    private static double eventDistance(ChartManifest.NumEvent event, double beat) {
        if (beat < event.startBeat) return 0.0;
        if (event.endBeat <= event.startBeat || beat > event.endBeat) {
            return 0.5 * (event.startValue + event.endValue) * Math.max(0.0, event.endBeat - event.startBeat);
        }
        double duration = beat - event.startBeat;
        double value = interpolate(event, duration / (event.endBeat - event.startBeat));
        return 0.5 * (event.startValue + value) * duration;
    }

    private static double interpolate(ChartManifest.NumEvent event, double progress) {
        return event.startValue + (event.endValue - event.startValue) * ease(progress, event.easingType);
    }

    private static double ease(double progress, int easingType) {
        double value = Math.max(0.0, Math.min(1.0, progress));
        return switch (Math.max(0, Math.min(33, easingType))) {
            case 1 -> 1 - Math.cos(value * Math.PI) / 2;
            case 2 -> Math.sin(value * Math.PI) / 2;
            case 3 -> -(Math.cos(Math.PI * value) - 1) / 2;
            case 4 -> value * value;
            case 5 -> 1 - Math.pow(1 - value, 2);
            case 6 -> value < 0.5 ? 2 * value * value : 1 - Math.pow(-2 * value + 2, 2) / 2;
            case 7 -> value * value * value;
            case 8 -> 1 - Math.pow(1 - value, 3);
            case 9 -> value < 0.5 ? 4 * value * value * value : 1 - Math.pow(-2 * value + 2, 3) / 2;
            case 10 -> Math.pow(value, 4);
            case 11 -> 1 - Math.pow(1 - value, 4);
            case 12 -> value < 0.5 ? 8 * Math.pow(value, 4) : 1 - Math.pow(-2 * value + 2, 4) / 2;
            case 13 -> Math.pow(value, 5);
            case 14 -> 1 - Math.pow(1 - value, 5);
            case 15 -> value < 0.5 ? 16 * Math.pow(value, 5) : 1 - Math.pow(-2 * value + 2, 5) / 2;
            case 16 -> value == 0 ? 0 : Math.pow(2, 10 * value - 10);
            case 17 -> value == 1 ? 1 : 1 - Math.pow(2, -10 * value);
            case 18 -> value == 0 ? 0 : value == 1 ? 1 : value < 0.5 ? Math.pow(2, 20 * value - 10) / 2 : (2 - Math.pow(2, -20 * value + 10)) / 2;
            case 19 -> 1 - Math.sqrt(1 - value * value);
            case 20 -> Math.sqrt(1 - Math.pow(value - 1, 2));
            case 21 -> value < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * value, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * value + 2, 2)) + 1) / 2;
            case 22 -> 2.70158 * value * value * value - 1.70158 * value * value;
            case 23 -> 1 + 2.70158 * Math.pow(value - 1, 3) + 1.70158 * Math.pow(value - 1, 2);
            case 24 -> value < 0.5 ? (Math.pow(2 * value, 2) * ((1.70158 * 1.525 + 1) * 2 * value - 1.70158 * 1.525)) / 2 : (Math.pow(2 * value - 2, 2) * ((1.70158 * 1.525 + 1) * (value * 2 - 2) + 1.70158 * 1.525) + 2) / 2;
            case 25 -> value == 0 || value == 1 ? value : -Math.pow(2, 10 * value - 10) * Math.sin((value * 10 - 10.75) * (2 * Math.PI / 3));
            case 26 -> value == 0 || value == 1 ? value : Math.pow(2, -10 * value) * Math.sin((value * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
            case 27 -> value == 0 || value == 1 ? value : value < 0.5 ? -(Math.pow(2, 20 * value - 10) * Math.sin((20 * value - 11.125) * (2 * Math.PI / 4.5))) / 2 : Math.pow(2, -20 * value + 10) * Math.sin((20 * value - 11.125) * (2 * Math.PI / 4.5)) / 2 + 1;
            case 28 -> 1 - bounce(1 - value);
            case 29 -> bounce(value);
            case 30 -> value < 0.5 ? (1 - bounce(1 - 2 * value)) / 2 : (1 + bounce(2 * value - 1)) / 2;
            case 31 -> 0;
            case 32 -> 1;
            case 33 -> value < 0.5 ? 0 : 1;
            default -> value;
        };
    }

    private static double bounce(double value) {
        if (value < 1 / 2.75) return 7.5625 * value * value;
        if (value < 2 / 2.75) return 7.5625 * Math.pow(value - 1.5 / 2.75, 2) + 0.75;
        if (value < 2.5 / 2.75) return 7.5625 * Math.pow(value - 2.25 / 2.75, 2) + 0.9375;
        return 7.5625 * Math.pow(value - 2.625 / 2.75, 2) + 0.984375;
    }
}
