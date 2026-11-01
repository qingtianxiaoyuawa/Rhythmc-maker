package cn.frkovo.rhythmcmaker.chart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class HoldNoteGroupMerger {
    private static final double MAX_DISTANCE_SQUARED = 1.0000001;

    private HoldNoteGroupMerger() {}

    public static List<ChartManifest.Note> connectedNotes(List<ChartManifest.Note> notes, ChartManifest.Note selected) {
        if (notes == null || selected == null || selected.type != 2) return List.of();
        Set<ChartManifest.Note> connected = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<ChartManifest.Note> pending = new ArrayDeque<>();
        pending.add(selected);
        while (!pending.isEmpty()) {
            ChartManifest.Note current = pending.removeFirst();
            if (!connected.add(current)) continue;
            if (current.holdGroup >= 0) for (ChartManifest.Note member : notes) {
                if (isSameTrackHold(member, selected) && member.holdGroup == current.holdGroup) pending.addLast(member);
            }
            for (ChartManifest.Note candidate : notes) {
                if (!isSameTrackHold(candidate, selected) || connected.contains(candidate)) continue;
                double dx = candidate.x - current.x;
                double dy = candidate.y - current.y;
                double dz = candidate.z - current.z;
                if (dx * dx + dy * dy + dz * dz <= MAX_DISTANCE_SQUARED) pending.addLast(candidate);
            }
        }
        return new ArrayList<>(connected);
    }

    public static int nextGroupId(List<ChartManifest.Note> notes) {
        return notes.stream().filter(java.util.Objects::nonNull).mapToInt(note -> note.holdGroup).max().orElse(-1) + 1;
    }

    private static boolean isSameTrackHold(ChartManifest.Note candidate, ChartManifest.Note selected) {
        return candidate != null && candidate.type == 2 && candidate.trackId == selected.trackId;
    }
}
