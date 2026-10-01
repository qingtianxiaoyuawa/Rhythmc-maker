package cn.frkovo.rhythmcmaker.chart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChartTiming {
    private ChartTiming() {}

    public static List<ChartManifest.BpmEvent> sorted(List<ChartManifest.BpmEvent> source, double fallbackBpm) {
        List<ChartManifest.BpmEvent> result = new ArrayList<>();
        if (source != null) for (ChartManifest.BpmEvent event : source) {
            if (event != null && Double.isFinite(event.beat) && Double.isFinite(event.bpm) && event.bpm > 0) {
                result.add(new ChartManifest.BpmEvent(event.beat, event.bpm));
            }
        }
        if (result.isEmpty()) result.add(new ChartManifest.BpmEvent(0, Math.max(1, fallbackBpm)));
        result.sort(Comparator.comparingDouble(event -> event.beat));
        List<ChartManifest.BpmEvent> deduplicated = new ArrayList<>();
        for (ChartManifest.BpmEvent event : result) {
            if (!deduplicated.isEmpty() && Math.abs(deduplicated.get(deduplicated.size() - 1).beat - event.beat) < 1.0e-9) {
                deduplicated.set(deduplicated.size() - 1, event);
            } else deduplicated.add(event);
        }
        if (deduplicated.get(0).beat > 0) deduplicated.add(0, new ChartManifest.BpmEvent(0, deduplicated.get(0).bpm));
        return deduplicated;
    }

    public static Prepared prepare(ChartManifest chart) {
        return prepare(chart.bpms, chart.bpm);
    }

    public static Prepared prepare(List<ChartManifest.BpmEvent> events, double fallbackBpm) {
        return new Prepared(sorted(events, fallbackBpm));
    }

    public static double beatToSeconds(List<ChartManifest.BpmEvent> events, double fallbackBpm, double beat) {
        return prepare(events, fallbackBpm).beatToSeconds(beat);
    }

    public static double secondsToBeat(List<ChartManifest.BpmEvent> events, double fallbackBpm, double seconds) {
        return prepare(events, fallbackBpm).secondsToBeat(seconds);
    }

    public static double beatToSeconds(ChartManifest chart, double beat) {
        return beatToSeconds(chart.bpms, chart.bpm, beat);
    }

    public static double secondsToBeat(ChartManifest chart, double seconds) {
        return secondsToBeat(chart.bpms, chart.bpm, seconds);
    }

    public static final class Prepared {
        private final List<ChartManifest.BpmEvent> timing;
        private final double[] starts;
        private final double[] cumulativeSeconds;

        private Prepared(List<ChartManifest.BpmEvent> timing) {
            this.timing = List.copyOf(timing);
            this.starts = new double[this.timing.size()];
            this.cumulativeSeconds = new double[this.timing.size()];
            for (int index = 0; index < this.timing.size(); index++) {
                ChartManifest.BpmEvent event = this.timing.get(index);
                starts[index] = event.beat;
                if (index > 0) {
                    ChartManifest.BpmEvent previous = this.timing.get(index - 1);
                    cumulativeSeconds[index] = cumulativeSeconds[index - 1]
                            + (event.beat - previous.beat) * 60.0 / previous.bpm;
                }
            }
        }

        public double quantizeBeat(double originalBeat) {
            if (!Double.isFinite(originalBeat)) return 0.0;
            double originalSeconds = beatToSeconds(originalBeat);
            double bestBeat = originalBeat;
            double bestError = Double.POSITIVE_INFINITY;
            int bestDenominator = Integer.MAX_VALUE;
            for (int denominator = 1; denominator <= 32; denominator++) {
                double scaled = originalBeat * denominator;
                double lower = Math.floor(scaled);
                double upper = Math.ceil(scaled);
                for (double numerator : new double[]{lower, upper}) {
                    double candidateBeat = numerator / denominator;
                    double error = Math.abs(beatToSeconds(candidateBeat) - originalSeconds);
                    boolean better = error < bestError - 1.0e-12;
                    boolean tied = Math.abs(error - bestError) <= 1.0e-12;
                    if (better || (tied && (denominator < bestDenominator
                            || (denominator == bestDenominator && candidateBeat < bestBeat)))) {
                        bestBeat = candidateBeat;
                        bestError = error;
                        bestDenominator = denominator;
                    }
                }
            }
            return bestBeat;
        }
        public double beatToSeconds(double beat) {
            if (beat <= starts[0]) return (beat - starts[0]) * 60.0 / timing.get(0).bpm;
            int index = Math.max(0, upperBound(starts, beat) - 1);
            ChartManifest.BpmEvent event = timing.get(index);
            return cumulativeSeconds[index] + (beat - event.beat) * 60.0 / event.bpm;
        }

        public double secondsToBeat(double seconds) {
            if (seconds <= 0) return starts[0] + seconds * timing.get(0).bpm / 60.0;
            int index = Math.max(0, upperBound(cumulativeSeconds, seconds) - 1);
            ChartManifest.BpmEvent event = timing.get(index);
            return event.beat + (seconds - cumulativeSeconds[index]) * event.bpm / 60.0;
        }

        private static int upperBound(double[] values, double value) {
            int low = 0;
            int high = values.length;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (values[middle] <= value) low = middle + 1;
                else high = middle;
            }
            return low;
        }
    }
}

