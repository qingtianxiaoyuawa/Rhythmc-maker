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
        if (note != null && note.visualOnly) return false;
        if (note == null || note.type != 2 || note.holdGroup < 0) return true;
        return note.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.HEAD
                || note.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.TAIL;
    }

    public static ChartManifest.Note otherEndpoint(ChartManifest chart, ChartManifest.Note note) {
        if (note == null || note.type != 2 || note.holdGroup < 0) return null;
        List<ChartManifest.Note> members = members(chart, note.trackId, note.holdGroup);
        if (members.size() < 2) return null;
        ChartManifest.Note head = members.stream().filter(candidate -> candidate.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.HEAD).findFirst().orElse(members.getFirst());
        ChartManifest.Note tail = members.stream().filter(candidate -> candidate.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.TAIL).findFirst().orElse(members.getLast());
        return head.id.equals(note.id) ? tail : head;
    }

    public static boolean isValidGroup(ChartManifest chart, ChartManifest.Note note) {
        if (note.holdGroup < 0) return true;
        List<ChartManifest.Note> real = members(chart, note.trackId, note.holdGroup).stream().filter(member -> !member.visualOnly).toList();
        List<ChartManifest.Note> heads = real.stream().filter(member -> member.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.HEAD).toList();
        List<ChartManifest.Note> tails = real.stream().filter(member -> member.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.TAIL).toList();
        if (heads.size() != 1 || tails.size() != 1 || heads.getFirst().beat >= tails.getFirst().beat) return false;
        return real.stream().allMatch(member -> Double.isFinite(member.beat)
                && member.beat >= heads.getFirst().beat && member.beat <= tails.getFirst().beat);
    }

    public static boolean isHead(ChartManifest chart, ChartManifest.Note note) {
        if (note.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.HEAD) return true;
        if (note.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.TAIL) return false;
        return members(chart, note.trackId, note.holdGroup).getFirst().id.equals(note.id);
    }

    public static boolean canSetBeat(ChartManifest chart, ChartManifest.Note note, double beat) {
        if (!Double.isFinite(beat) || beat < 0.0 || beat > 1_000_000.0 || !isEndpoint(chart, note)) return false;
        if (note.holdGroup < 0) return true;
        if (!isValidGroup(chart, note)) return false;
        ChartManifest.Note other = otherEndpoint(chart, note);
        return isHead(chart, note) ? beat < other.beat : beat > other.beat;
    }

    public static int nextGroupId(ChartManifest chart) {
        return chart.notes.stream().filter(note -> note != null).mapToInt(note -> note.holdGroup).max().orElse(-1) + 1;
    }
}
