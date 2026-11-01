package cn.frkovo.rhythmcmaker.chart;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record HoldInterval(String id, int trackId, int holdGroup, double startBeat, double endBeat,
                           List<ChartManifest.Note> members) {
    public HoldInterval {
        Objects.requireNonNull(id);
        if (!Double.isFinite(startBeat) || !Double.isFinite(endBeat) || endBeat < startBeat) {
            throw new IllegalArgumentException("Hold interval must have a finite non-negative duration");
        }
        members = members.stream().sorted(Comparator.comparingDouble(note -> note.beat)).toList();
    }

    public double durationBeats() { return endBeat - startBeat; }

    public boolean hasEndJudgementBoundary() { return members.size() > 2; }

    public static List<HoldInterval> fromChart(ChartManifest chart) {
        java.util.Map<GroupKey, java.util.List<ChartManifest.Note>> grouped = new java.util.HashMap<>();
        for (ChartManifest.Note note : chart.notes) {
            if (note != null && note.type == 2 && note.holdGroup >= 0 && Double.isFinite(note.beat)) {
                GroupKey key = new GroupKey(note.trackId, note.holdGroup);
                grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(note);
            }
        }
        return grouped.entrySet().stream().map(entry -> create(entry.getKey(), entry.getValue())).filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(HoldInterval::startBeat)).toList();
    }

    private static HoldInterval create(GroupKey key, List<ChartManifest.Note> members) {
        if (members.size() < 2) return null;
        List<ChartManifest.Note> ordered = members.stream().sorted(Comparator.comparingDouble(note -> note.beat)).toList();
        ChartManifest.Note first = ordered.get(0);
        ChartManifest.Note last = ordered.get(ordered.size() - 1);
        return new HoldInterval(key.trackId + ":" + key.holdGroup, key.trackId, key.holdGroup, first.beat, last.beat, ordered);
    }

    private record GroupKey(int trackId, int holdGroup) {}
}
