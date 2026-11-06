package cn.frkovo.rhythmcmaker.chart;

import cn.frkovo.rhythmcmaker.client.playback.HoldPlaybackStateMachine;

import java.util.List;

public final class HoldPlaybackRegression {
    public static void main(String[] args) {
        require(cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterSchema.getInstance().find("JUDGE_FRAME_COLOR") != null,
                "reference judge frame color effect is registered");
        require(cn.frkovo.rhythmcmaker.client.playback.PersistentEffectStateFold.latestEventIndexes(
                List.of("JUDGE_FRAME_COLOR"), List.of(1.0), 1.0).equals(List.of(0)),
                "judge frame color persists through seek rebuild");
        ChartManifest.Note editableNote = note("editable", 1.0, -1);
        ChartManifest.Note longHold = note("long-hold-head", 1.0, 2);
        ChartManifest.Note longHoldTail = note("long-hold-tail", 4.0, 2);
        longHoldTail.x = 2.0;
        require(NoteEditRules.canEdit(longHold), "long Hold notes remain editable");
        require(!NoteEditRules.usesDisplayEntity(longHold), "integer Hold endpoints retain block representation");
        require(NoteEditRules.hasBlockRepresentationAt(editableNote, editableNote.type, editableNote.x, editableNote.y, editableNote.z), "exact note block break identity matches");
        require(!NoteEditRules.hasBlockRepresentationAt(editableNote, editableNote.type + 1, editableNote.x, editableNote.y, editableNote.z), "breaking another block type cannot delete a note");
        ChartManifest layoutChart = new ChartManifest();
        layoutChart.bpm = 120.0;
        layoutChart.divisionsPerBeat = 2;
        layoutChart.notes.add(longHold);
        layoutChart.notes.add(longHoldTail);
        EditorTrackLayout.of(5, 8).applyTo(layoutChart);
        EditorNoteLayout.repositionNotes(layoutChart);
        require(longHold.z == PlaybackCoordinates.editorWorldZAtBeat(layoutChart, longHold.beat) - 0.5, "layout changes reposition Hold representative by beat");
        require(longHoldTail.z == PlaybackCoordinates.editorWorldZAtBeat(layoutChart, longHoldTail.beat) - 0.5,
                "layout repositions each Hold endpoint by its own beat");
        require(HoldGroupRules.canAppend(longHold, longHoldTail.trackId, longHoldTail.beat),
                "Hold endpoints may use different X coordinates on the same track");
        require(!HoldGroupRules.canAppend(longHoldTail, longHold.trackId, longHold.beat),
                "Hold tails cannot precede their heads");
        require(!HoldGroupRules.canAppend(longHold, longHoldTail.trackId + 1, longHoldTail.beat),
                "Hold tails cannot cross tracks");
        require(!HoldGroupRules.canAppend(longHold, longHold.trackId, Double.NaN),
                "Hold tails reject non-finite beats");
        require(HoldFillPlanner.fillCount(1.0, 1.0, 1.0) == 0, "touching Hold blocks need no visual fill");
        require(HoldFillPlanner.fillCount(3.0, 1.0, 1.0) == 2, "wide Hold gaps use evenly spaced visual fills");
        require(NoteEditRules.requiresDisplayEntity(1.25, 66.0, -4.5),
                "fractional note coordinates require a display entity");
        require(!NoteEditRules.requiresDisplayEntity(1.0, 66.0, -4.0),
                "integer note coordinates remain block represented");
        require(NoteEditRules.editorRelativeY(66.75, 0) == 0.75, "editor Y is displayed relative to the note platform");
        require(NoteEditRules.editorAbsoluteY(0.75, 0) == 66.75, "relative editor Y restores the absolute chart position");
        require(NoteEditRules.editorAbsoluteY(NoteEditRules.editorRelativeY(65.0, 2), 2) == 65.0,
                "editing coordinates does not shift Hold endpoints");
        ChartManifest.Note first = note("first", 1.0, 8);
        ChartManifest.Note middle = note("middle", 2.0, 8);
        ChartManifest.Note last = note("last", 4.0, 8);
        ChartManifest.Note otherTrack = note("other", 2.0, 8);
        otherTrack.trackId = 1;
        ChartManifest chart = new ChartManifest();
        chart.notes = List.of(first, middle, last, otherTrack);
        require(HoldGroupRules.isEndpoint(chart, first) && HoldGroupRules.isEndpoint(chart, last),
                "only the first and last Hold members are editable endpoints");
        require(!HoldGroupRules.isEndpoint(chart, middle), "middle Hold members cannot open the editor");
        List<HoldInterval> intervals = HoldInterval.fromChart(chart);
        require(intervals.size() == 2, "track-isolated grouping");
        HoldInterval interval = intervals.stream().filter(candidate -> candidate.trackId() == 0).findFirst().orElseThrow();
        require(interval.startBeat() == 1.0 && interval.endBeat() == 4.0, "hold endpoints");
        require(interval.durationBeats() == 3.0 && interval.members().size() == 3, "hold member preservation");
        ChartManifest.Note simultaneousFirst = note("simultaneous-first", 2.0, 9);
        ChartManifest.Note simultaneousLast = note("simultaneous-last", 2.0, 9);
        HoldInterval simultaneous = HoldInterval.fromChart(chartWith(List.of(simultaneousFirst, simultaneousLast))).getFirst();
        require(simultaneous.startBeat() == simultaneous.endBeat() && simultaneous.durationBeats() == 0.0,
                "same-beat hold group remains loadable");
        require(interval.hasEndJudgementBoundary(), "three-member runtime groups have an end boundary");
        ChartManifest.Note representative = note("representative-head", 1.0, 12);
        ChartManifest.Note representativeTail = note("representative-tail", 8.0, 12);
        HoldInterval representativeInterval = HoldInterval.fromChart(chartWith(List.of(representative, representativeTail))).getFirst();
        require(representativeInterval.startBeat() == 1.0 && representativeInterval.endBeat() == 8.0,
                "Hold group interval comes from its endpoint notes");

        HoldPlaybackStateMachine machine = new HoldPlaybackStateMachine();
        machine.reset(intervals, 0.0);
        require(has(machine.update(1.0), HoldPlaybackStateMachine.TransitionType.STARTED), "start transition");
        require(has(machine.update(2.0), HoldPlaybackStateMachine.TransitionType.SUSTAINED), "sustain transition");
        require(has(machine.seek(3.0), HoldPlaybackStateMachine.TransitionType.CANCELLED), "seek cancels active hold");
        require(machine.state(interval.id()) == HoldPlaybackStateMachine.State.ACTIVE, "seek restores interval state");
        require(has(machine.update(4.0), HoldPlaybackStateMachine.TransitionType.ENDED), "end transition");
        machine.seek(2.0);
        require(has(machine.cancelAll(), HoldPlaybackStateMachine.TransitionType.CANCELLED), "cleanup cancellation");
        require(!has(machine.update(2.1), HoldPlaybackStateMachine.TransitionType.STARTED), "cancelled hold remains cancelled");
        machine.seek(2.0);
        require(machine.cancel(interval.id()) != null, "hidden hold cancellation");
        require(machine.state(interval.id()) == HoldPlaybackStateMachine.State.CANCELLED, "hidden hold is no longer active");
        machine.seek(2.0);
        require(machine.state(interval.id()) == HoldPlaybackStateMachine.State.ACTIVE, "visible interval resumes after filter rebuild");
        HoldPlaybackStateMachine skipped = new HoldPlaybackStateMachine();
        skipped.reset(intervals, 0.0);
        List<HoldPlaybackStateMachine.Transition> crossed = skipped.update(5.0);
        require(has(crossed, HoldPlaybackStateMachine.TransitionType.STARTED)
                && has(crossed, HoldPlaybackStateMachine.TransitionType.ENDED), "frame crossing both endpoints");
        System.out.println("Hold playback regression passed");
    }

    private static ChartManifest.Note note(String id, double beat, int group) {
        ChartManifest.Note note = new ChartManifest.Note();
        note.id = id;
        note.type = 2;
        note.trackId = 0;
        note.beat = beat;
        note.holdGroup = group;
        return note;
    }

    private static boolean has(List<HoldPlaybackStateMachine.Transition> transitions,
                               HoldPlaybackStateMachine.TransitionType type) {
        return transitions.stream().anyMatch(transition -> transition.type() == type);
    }

    private static ChartManifest chartWith(List<ChartManifest.Note> notes) {
        ChartManifest chart = new ChartManifest();
        chart.notes = notes;
        return chart;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
