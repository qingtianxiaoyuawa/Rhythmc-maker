package cn.frkovo.rhythmcmaker.common.note;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.HoldGroupRules;

public final class NoteSnapModel {
    private static final NoteSnapModel INSTANCE = new NoteSnapModel();

    private NoteSnapModel() {}

    public static NoteSnapModel getInstance() { return INSTANCE; }

    public double targetZ(ChartManifest chart, ChartManifest.Note note) {
        double rounded = Math.ceil(note.z - 0.5);
        if (note.holdGroup < 0) return rounded;
        if (!HoldGroupRules.isValidGroup(chart, note)) return note.z;
        var members = HoldGroupRules.members(chart, note.trackId, note.holdGroup).stream().filter(member -> !member.visualOnly).toList();
        ChartManifest.Note head = members.stream().filter(member -> member.holdRole == HoldRole.HEAD).findFirst().orElseThrow();
        ChartManifest.Note tail = members.stream().filter(member -> member.holdRole == HoldRole.TAIL).findFirst().orElseThrow();
        if (head.beat >= tail.beat) return note.z;
        double headZ = Math.ceil(head.z - 0.5);
        double tailZ = Math.ceil(tail.z - 0.5);
        if (headZ <= tailZ) {
            headZ = Math.min(-3.0, Math.ceil(head.z));
            tailZ = Math.min(Math.floor(tail.z), headZ - 1.0);
        }
        if (note.holdRole == HoldRole.HEAD) return headZ;
        if (note.holdRole == HoldRole.TAIL) return tailZ;
        return Math.max(tailZ, Math.min(headZ, rounded));
    }
}
