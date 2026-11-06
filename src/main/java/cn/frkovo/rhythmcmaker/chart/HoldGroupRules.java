package cn.frkovo.rhythmcmaker.chart;

import java.util.Comparator;
import java.util.List;

public final class HoldGroupRules {
    private HoldGroupRules() {}

    public static List<ChartManifest.Note> members(ChartManifest chart, int trackId, int holdGroup) {
        return chart.notes.stream()
                .filter(note -> note != null && note.type == 2 && note.trackId == trackId && note.holdGroup == holdGroup)
                .sorted(Comparator.comparingDouble(note -> note.beat))
                .toList();
    }

    public static boolean canAppend(ChartManifest.Note head, int trackId, double tailBeat) {
        return head != null && head.trackId == trackId && Double.isFinite(tailBeat) && tailBeat > head.beat;
    }

    public static boolean isEndpoint(ChartManifest chart, ChartManifest.Note note) {
        if (note == null || note.type != 2 || note.holdGroup < 0) return true;
        List<ChartManifest.Note> members = members(chart, note.trackId, note.holdGroup);
        return members.size() >= 2 && (members.getFirst().id.equals(note.id) || members.getLast().id.equals(note.id));
    }

    public static ChartManifest.Note otherEndpoint(ChartManifest chart, ChartManifest.Note note) {
        if (note == null || note.type != 2 || note.holdGroup < 0) return null;
        List<ChartManifest.Note> members = members(chart, note.trackId, note.holdGroup);
        if (members.size() != 2) return null;
        return members.getFirst().id.equals(note.id) ? members.getLast() : members.getFirst();
    }

    public static int nextGroupId(ChartManifest chart) {
        return chart.notes.stream().filter(note -> note != null).mapToInt(note -> note.holdGroup).max().orElse(-1) + 1;
    }
}
