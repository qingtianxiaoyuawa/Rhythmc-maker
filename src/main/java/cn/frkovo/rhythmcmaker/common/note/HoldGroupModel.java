package cn.frkovo.rhythmcmaker.common.note;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartTiming;
import cn.frkovo.rhythmcmaker.chart.HoldFillPlanner;
import cn.frkovo.rhythmcmaker.chart.HoldGroupRules;
import cn.frkovo.rhythmcmaker.chart.PlaybackCoordinates;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class HoldGroupModel {
    private static final HoldGroupModel INSTANCE = new HoldGroupModel();

    private HoldGroupModel() {}

    public static HoldGroupModel getInstance() { return INSTANCE; }

    public List<ChartManifest.Note> runtimeNotes(ChartManifest chart) {
        return chart.notes == null ? List.of() : chart.notes.stream().filter(note -> note != null && !note.visualOnly).toList();
    }

    public void initializeRoles(ChartManifest chart) {
        if (chart.notes == null) return;
        for (GroupKey key : groups(chart)) {
            List<ChartManifest.Note> members = realMembers(chart, key);
            if (members.size() == 1) {
                if (members.getFirst().holdRole == HoldRole.SINGLE) members.getFirst().holdRole = HoldRole.HEAD;
                continue;
            }
            if (members.isEmpty() || members.stream().anyMatch(note -> note.holdRole == HoldRole.HEAD || note.holdRole == HoldRole.TAIL)) continue;
            for (ChartManifest.Note member : members) member.holdRole = HoldRole.MEMBER;
            members.getFirst().holdRole = HoldRole.HEAD;
            members.getLast().holdRole = HoldRole.TAIL;
        }
    }

    public void reconcile(ChartManifest chart) {
        initializeRoles(chart);
        for (GroupKey key : groups(chart)) reconcileGroup(chart, key.trackId(), key.groupId());
    }

    public void reconcileGroup(ChartManifest chart, int trackId, int groupId) {
        if (groupId < 0) return;
        GroupKey key = new GroupKey(trackId, groupId);
        List<ChartManifest.Note> members = realMembers(chart, key);
        List<ChartManifest.Note> generated = new ArrayList<>();
        ChartTiming.Prepared timing = ChartTiming.prepare(chart);
        for (int pair = 0; pair + 1 < members.size(); pair++) {
            ChartManifest.Note start = members.get(pair);
            ChartManifest.Note end = members.get(pair + 1);
            double span = Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2) + Math.pow(end.z - start.z, 2));
            int count = HoldFillPlanner.fillCount(span, start.scaleZ, end.scaleZ);
            for (int index = 1; index <= count; index++) {
                double progress = index / (double) (count + 1);
                ChartManifest.Note fill = new ChartManifest.Note();
                fill.id = UUID.nameUUIDFromBytes((start.id + ":fill:" + end.id + ":" + index).getBytes(StandardCharsets.UTF_8)).toString();
                fill.type = 2;
                fill.trackId = trackId;
                fill.holdGroup = groupId;
                fill.holdRole = HoldRole.MEMBER;
                fill.visualOnly = true;
                interpolate(fill, start, end, progress);
                fill.time = timing.beatToSeconds(fill.beat);
                generated.add(fill);
            }
        }
        chart.notes = new ArrayList<>(chart.notes.stream().filter(note -> note == null || !note.visualOnly
                || note.trackId != trackId || note.holdGroup != groupId).toList());
        chart.notes.addAll(generated);
    }

    public void moveInterior(ChartManifest chart, ChartManifest.Note endpoint, double previousHeadBeat, double previousTailBeat) {
        if (endpoint.holdGroup < 0 || previousTailBeat <= previousHeadBeat) return;
        List<ChartManifest.Note> members = realMembers(chart, new GroupKey(endpoint.trackId, endpoint.holdGroup));
        ChartManifest.Note head = members.stream().filter(note -> note.holdRole == HoldRole.HEAD).findFirst().orElseThrow();
        ChartManifest.Note tail = members.stream().filter(note -> note.holdRole == HoldRole.TAIL).findFirst().orElseThrow();
        ChartTiming.Prepared timing = ChartTiming.prepare(chart);
        for (ChartManifest.Note member : members) {
            if (member.holdRole != HoldRole.MEMBER) continue;
            double progress = Math.max(0.0, Math.min(1.0, (member.beat - previousHeadBeat) / (previousTailBeat - previousHeadBeat)));
            double beat = HoldFillPlanner.interpolate(head.beat, tail.beat, progress);
            if (Math.abs(beat - member.beat) > 1.0e-10) {
                member.beat = beat;
                member.editorBeat = null;
            }
            member.z = PlaybackCoordinates.editorWorldZAtBeat(chart, member.beat) - 0.5;
            member.time = timing.beatToSeconds(member.beat);
        }
    }

    private void interpolate(ChartManifest.Note target, ChartManifest.Note start, ChartManifest.Note end, double progress) {
        target.x = HoldFillPlanner.interpolate(start.x, end.x, progress);
        target.y = HoldFillPlanner.interpolate(start.y, end.y, progress);
        target.z = HoldFillPlanner.interpolate(start.z, end.z, progress);
        target.beat = HoldFillPlanner.interpolate(start.beat, end.beat, progress);
        target.scaleX = HoldFillPlanner.interpolate(start.scaleX, end.scaleX, progress);
        target.scaleY = HoldFillPlanner.interpolate(start.scaleY, end.scaleY, progress);
        target.scaleZ = HoldFillPlanner.interpolate(start.scaleZ, end.scaleZ, progress);
        target.rotationX = HoldFillPlanner.interpolate(start.rotationX, end.rotationX, progress);
        target.rotationY = HoldFillPlanner.interpolate(start.rotationY, end.rotationY, progress);
        target.rotationZ = HoldFillPlanner.interpolate(start.rotationZ, end.rotationZ, progress);
    }

    private List<ChartManifest.Note> realMembers(ChartManifest chart, GroupKey key) {
        return HoldGroupRules.members(chart, key.trackId(), key.groupId()).stream().filter(note -> !note.visualOnly).toList();
    }

    private List<GroupKey> groups(ChartManifest chart) {
        return chart.notes.stream().filter(note -> note != null && note.type == 2 && note.holdGroup >= 0)
                .map(note -> new GroupKey(note.trackId, note.holdGroup)).distinct().toList();
    }

    private record GroupKey(int trackId, int groupId) {}
}
