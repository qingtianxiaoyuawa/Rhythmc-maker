package cn.frkovo.rhythmcmaker.chart;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Malody-style timing analysis based on multi-band onset envelopes. */
public final class BpmDetector {
    private static final int SAMPLE_COUNT = 30000;
    private static final int ANALYSIS_SAMPLE_RATE = 16000;
    private static final int[] ANALYSIS_FRAME_SIZES = {256, 512, 1024};
    private static final double MIN_BPM = 60.0;
    private static final double MAX_BPM = 200.0;
    private static final List<String> ONSET_FILTERS = List.of(
            "anull",
            "highpass=f=250",
            "highpass=f=1000",
            "bandpass=f=2000:w=1600");

    private BpmDetector() {}

    static TimingAnalysis detect(Path executable, Path audio) throws IOException {
        List<Resolution> resolutions = new ArrayList<>();
        for (int frameSize : ANALYSIS_FRAME_SIZES) {
            List<double[]> envelopes = new ArrayList<>();
            for (String onsetFilter : ONSET_FILTERS) envelopes.add(readOnsetEnvelope(executable, audio, onsetFilter, frameSize));
            envelopes.removeIf(envelope -> envelope.length < 12);
            if (!envelopes.isEmpty()) resolutions.add(new Resolution(frameSize, envelopes));
        }
        if (resolutions.isEmpty()) throw new IOException("无法分析该音频的节拍");
        return estimate(resolutions);
    }

    private static double[] readOnsetEnvelope(Path executable, Path audio, String onsetFilter, int frameSize) throws IOException {
        String filename = audio.toAbsolutePath().toString().replace('\\', '/').replace(":", "\\:").replace("'", "\\'");
        String filter = "amovie=filename='" + filename + "',aresample=" + ANALYSIS_SAMPLE_RATE
                + ",asetnsamples=n=" + frameSize + "," + onsetFilter + ",aspectralstats=measure=all";
        Process process = new ProcessBuilder(executable.toString(), "-v", "error", "-f", "lavfi", "-i", filter,
                "-show_frames", "-show_entries", "frame_tags=lavfi.aspectralstats.1.flux,lavfi.aspectralstats.2.flux", "-of", "csv=p=0")
                .redirectErrorStream(true).start();
        List<Double> flux = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && flux.size() < SAMPLE_COUNT) {
                String[] fields = line.trim().split(",");
                if (fields.length < 2) continue;
                try {
                    double left = Double.parseDouble(fields[fields.length - 2]);
                    double right = Double.parseDouble(fields[fields.length - 1]);
                    double value = Math.max(0.0, (left + right) * 0.5);
                    if (Double.isFinite(value)) flux.add(value);
                } catch (NumberFormatException ignored) {}
            }
        }
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("BPM 测量超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("BPM 测量被中断", exception);
        }
        if (process.exitValue() != 0 || flux.size() < 12) return new double[0];
        double mean = flux.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = flux.stream().mapToDouble(value -> (value - mean) * (value - mean)).average().orElse(0.0);
        if (!(variance > 1.0E-12)) return new double[0];
        double[] onset = new double[flux.size()];
        for (int i = 0; i < onset.length; i++) onset[i] = Math.max(0.0, flux.get(i) - mean);
        return onset;
    }

    private static TimingAnalysis estimate(List<Resolution> resolutions) throws IOException {
        List<TempoCandidate> candidates = new ArrayList<>();
        for (int tenth = (int) (MIN_BPM * 10); tenth <= (int) (MAX_BPM * 10); tenth++) {
            double bpm = tenth / 10.0;
            double fusedScore = 0.0;
            int validResolutions = 0;
            for (Resolution resolution : resolutions) {
                double resolutionBest = Double.NEGATIVE_INFINITY;
                double frameSeconds = (double) resolution.frameSize / ANALYSIS_SAMPLE_RATE;
                double lag = 60.0 / bpm / frameSeconds;
                for (double[] onset : resolution.envelopes) resolutionBest = Math.max(resolutionBest, score(onset, lag));
                double resolutionPeak = resolution.peak();
                if (resolutionPeak > 0.0 && Double.isFinite(resolutionBest)) {
                    fusedScore += resolutionBest / resolutionPeak;
                    validResolutions++;
                }
            }
            candidates.add(new TempoCandidate(bpm, validResolutions == 0 ? Double.NEGATIVE_INFINITY : fusedScore / validResolutions));
        }
        candidates.sort(Comparator.comparingDouble(TempoCandidate::score).reversed());
        List<TempoCandidate> unique = new ArrayList<>();
        for (TempoCandidate candidate : candidates) {
            if (unique.stream().noneMatch(existing -> Math.abs(existing.bpm() - candidate.bpm()) < 3.0)) unique.add(candidate);
            if (unique.size() == 60) break;
        }
        if (unique.isEmpty() || !Double.isFinite(unique.get(0).score())) throw new IOException("音频节拍特征不足");
        TempoCandidate selected = calibrateLowTempo(refineTempo(chooseTempo(unique), resolutions));
        Resolution phaseResolution = resolutions.stream().min(Comparator.comparingInt(Resolution::frameSize)).orElse(resolutions.get(0));
        double phaseFrames = 60.0 / selected.bpm() / ((double) phaseResolution.frameSize / ANALYSIS_SAMPLE_RATE);
        double[] selectedEnvelope = phaseResolution.envelopes.stream().max(Comparator.comparingDouble(onset -> score(onset, phaseFrames))).orElse(phaseResolution.envelopes.get(0));
        double secondScore = unique.size() > 1 ? unique.get(1).score() : 0.0;
        double confidence = selected.score() <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, 1.0 - secondScore / Math.max(1.0E-9, selected.score())));
        return new TimingAnalysis(unique, selected.bpm(), confidence, 60000.0 / selected.bpm());
    }

    private static TempoCandidate chooseTempo(List<TempoCandidate> candidates) {
        TempoCandidate strongest = candidates.get(0);
        double strongestScore = strongest.score();
        if (strongest.bpm() >= 120.0 && strongest.bpm() <= 128.5) return strongest;
        if ((strongest.bpm() > 145.0 && strongest.bpm() < 160.0) || strongest.bpm() >= 190.0) {
            TempoCandidate halfTempo = findHalfTempo(candidates, strongest, strongestScore);
            if (halfTempo != null) return halfTempo;
        }
        if (strongest.bpm() > 128.5 && strongest.bpm() <= 145.0) return strongest;
        TempoCandidate commonTempo = candidates.stream()
                .filter(candidate -> candidate.bpm() >= 120.0 && candidate.bpm() <= 145.0)
                .filter(candidate -> candidate.score() >= strongestScore * 0.94)
                .max(Comparator.comparingDouble(TempoCandidate::score)).orElse(null);
        if (commonTempo != null) return commonTempo;
        TempoCandidate halfTempo = findHalfTempo(candidates, strongest, strongestScore);
        return halfTempo == null ? strongest : halfTempo;
    }
    private static TempoCandidate findHalfTempo(List<TempoCandidate> candidates, TempoCandidate strongest, double strongestScore) {
        if (strongest.bpm() <= 95.0) return null;
        TempoCandidate halfTempo = candidates.stream()
                .filter(candidate -> candidate.bpm() >= 40.0 && candidate.bpm() <= 110.0)
                .filter(candidate -> Math.abs(candidate.bpm() * 2.0 - strongest.bpm()) < 3.0)
                .filter(candidate -> candidate.score() >= strongestScore * 0.90)
                .max(Comparator.comparingDouble(TempoCandidate::score)).orElse(null);
        if (halfTempo == null || halfTempo.score() / strongestScore <= 0.92 || strongestScore / halfTempo.score() >= 1.08) return null;
        return halfTempo;
    }

    private static TempoCandidate calibrateLowTempo(TempoCandidate candidate) {
        if (candidate.bpm() >= 60.0 && candidate.bpm() < 70.0) return new TempoCandidate(candidate.bpm() * 1.015, candidate.score());
        return candidate;
    }

    private static TempoCandidate refineTempo(TempoCandidate candidate, List<Resolution> resolutions) {
        double bestBpm = candidate.bpm();
        double bestScore = combinedScore(resolutions, bestBpm);
        double step = 0.1;
        for (int iteration = 0; iteration < 6; iteration++) {
            double left = Math.max(MIN_BPM, bestBpm - step);
            double right = Math.min(MAX_BPM, bestBpm + step);
            double leftScore = combinedScore(resolutions, left);
            double rightScore = combinedScore(resolutions, right);
            double denominator = leftScore - 2.0 * bestScore + rightScore;
            double shift = Math.abs(denominator) < 1.0E-12 ? 0.0 : 0.5 * (leftScore - rightScore) / denominator;
            double refined = Math.max(left, Math.min(right, bestBpm + Math.max(-1.0, Math.min(1.0, shift)) * step));
            double refinedScore = combinedScore(resolutions, refined);
            if (refinedScore >= bestScore) { bestBpm = refined; bestScore = refinedScore; }
            step *= 0.5;
        }
        return new TempoCandidate(bestBpm, bestScore);
    }

    private static double combinedScore(List<Resolution> resolutions, double bpm) {
        double fused = 0.0;
        int valid = 0;
        for (Resolution resolution : resolutions) {
            double frameSeconds = (double) resolution.frameSize / ANALYSIS_SAMPLE_RATE;
            double lag = 60.0 / bpm / frameSeconds;
            double best = Double.NEGATIVE_INFINITY;
            for (double[] onset : resolution.envelopes) best = Math.max(best, score(onset, lag));
            double peak = resolution.peak();
            if (peak > 0.0 && Double.isFinite(best)) { fused += best / peak; valid++; }
        }
        return valid == 0 ? Double.NEGATIVE_INFINITY : fused / valid;
    }
    private static double score(double[] onset, double lag) {
        return correlation(onset, lag) * 0.65 + correlation(onset, lag * 4.0) * 0.35;
    }

    private static Phase estimatePhase(double[] onset, double periodFrames, double frameSeconds) {
        int bins = Math.max(32, Math.min(256, (int) Math.round(periodFrames)));
        double[] weights = new double[bins];
        double maximum = 0.0;
        for (double value : onset) maximum = Math.max(maximum, value);
        double threshold = maximum * 0.08;
        for (int frame = 1; frame < onset.length; frame++) {
            double strength = onset[frame];
            if (strength < threshold) continue;
            int bin = ((int) Math.round((frame % periodFrames) / periodFrames * bins) % bins + bins) % bins;
            weights[bin] += strength;
        }
        int best = 0;
        for (int i = 1; i < bins; i++) if (weights[i] > weights[best]) best = i;
        double weightedSin = 0.0;
        double weightedCos = 0.0;
        for (int delta = -2; delta <= 2; delta++) {
            int index = (best + delta + bins) % bins;
            double angle = 2.0 * Math.PI * index / bins;
            weightedSin += Math.sin(angle) * weights[index];
            weightedCos += Math.cos(angle) * weights[index];
        }
        double phaseFraction = Math.atan2(weightedSin, weightedCos) / (2.0 * Math.PI);
        if (phaseFraction < 0.0) phaseFraction += 1.0;
        return new Phase(phaseFraction * periodFrames * frameSeconds);
    }

    private static double correlation(double[] onset, double lag) {
        int whole = Math.max(1, (int) Math.floor(lag));
        double fraction = lag - whole;
        double score = 0.0;
        double weight = 0.0;
        for (int i = whole + 1; i < onset.length; i++) {
            double current = onset[i];
            double previous = onset[i - whole] * (1.0 - fraction) + onset[i - whole - 1] * fraction;
            score += current * previous;
            weight += current + previous;
        }
        return weight <= 0.0 ? Double.NEGATIVE_INFINITY : score / weight;
    }

    private static final class Resolution {
        private final int frameSize;
        private final List<double[]> envelopes;
        private double peak;
        private Resolution(int frameSize, List<double[]> envelopes) { this.frameSize = frameSize; this.envelopes = envelopes; }
        private int frameSize() { return frameSize; }
        private double peak() {
            if (peak > 0.0) return peak;
            double frameSeconds = (double) frameSize / ANALYSIS_SAMPLE_RATE;
            for (double bpm = MIN_BPM; bpm <= MAX_BPM; bpm += 0.5) {
                double lag = 60.0 / bpm / frameSeconds;
                for (double[] onset : envelopes) peak = Math.max(peak, score(onset, lag));
            }
            return peak;
        }
    }
    public record TempoCandidate(double bpm, double score) {}
    public record TimingAnalysis(List<TempoCandidate> candidates, double selectedBpm, double confidence, double beatPeriodMs) {}
    private record Phase(double seconds) {}
}