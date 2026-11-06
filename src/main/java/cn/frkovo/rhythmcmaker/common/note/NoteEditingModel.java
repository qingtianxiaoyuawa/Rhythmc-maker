package cn.frkovo.rhythmcmaker.common.note;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartTiming;
import cn.frkovo.rhythmcmaker.chart.EditorTrackLayout;
import cn.frkovo.rhythmcmaker.chart.HoldGroupRules;
import cn.frkovo.rhythmcmaker.chart.NoteEditRules;
import cn.frkovo.rhythmcmaker.chart.PlaybackCoordinates;

public final class NoteEditingModel {
    private static final NoteEditingModel INSTANCE = new NoteEditingModel();

    private NoteEditingModel() {}

    public static NoteEditingModel getInstance() { return INSTANCE; }

    public boolean canEdit(ChartManifest chart, ChartManifest.Note note) {
        return note != null && HoldGroupRules.isEndpoint(chart, note) && HoldGroupRules.isValidGroup(chart, note);
    }

    public boolean setPosition(ChartManifest chart, ChartManifest.Note note, double x, double relativeY, double beat) {
        if (!canEdit(chart, note) || !Double.isFinite(x) || !Double.isFinite(relativeY)
                || !HoldGroupRules.canSetBeat(chart, note, beat)) return false;
        EditorTrackLayout layout = EditorTrackLayout.of(chart);
        double oldRelativeY = NoteEditRules.editorRelativeY(note.y, note.type);
        if (x != note.x && (!onStep(x, 0.05) || x < -layout.halfWidth() || x > layout.halfWidth())) return false;
        if (note.type == 2 ? Math.abs(relativeY) > 1.0e-6 && Math.abs(relativeY - oldRelativeY) > 1.0e-6
                : relativeY != oldRelativeY && (!onStep(relativeY, 0.05) || relativeY < 0.0 || relativeY > layout.maximumRelativeY())) return false;
        double previousHead = note.beat;
        double previousTail = note.beat;
        if (note.holdGroup >= 0) {
            ChartManifest.Note other = HoldGroupRules.otherEndpoint(chart, note);
            previousHead = HoldGroupRules.isHead(chart, note) ? note.beat : other.beat;
            previousTail = HoldGroupRules.isHead(chart, note) ? other.beat : note.beat;
        }
        note.x = x;
        if (note.type != 2) note.y = NoteEditRules.editorAbsoluteY(relativeY, note.type);
        if (Math.abs(note.beat - beat) > 1.0e-10) {
            note.beat = beat;
            note.time = ChartTiming.beatToSeconds(chart, beat);
            note.z = PlaybackCoordinates.editorWorldZAtBeat(chart, beat) - 0.5;
            note.editorBeat = null;
        }
        if (note.holdGroup >= 0) {
            HoldGroupModel.getInstance().moveInterior(chart, note, previousHead, previousTail);
            HoldGroupModel.getInstance().reconcileGroup(chart, note.trackId, note.holdGroup);
        }
        return true;
    }

    public boolean setBeat(ChartManifest chart, ChartManifest.Note note, EditorBeat beat) {
        if (!beat.isValid() || !setPosition(chart, note, note.x, NoteEditRules.editorRelativeY(note.y, note.type), beat.value())) return false;
        note.editorBeat = beat;
        return true;
    }

    public boolean setTransform(ChartManifest chart, ChartManifest.Note note, NoteTransformField field, double value) {
        if (!canEdit(chart, note) || !Double.isFinite(value) || value < field.minimum() || value > field.maximum()
                || !onStep(value, field.step())) return false;
        field.set(note, NoteGeometry.stepped(value, field.minimum(), field.maximum(), field.step()));
        if (note.holdGroup >= 0) HoldGroupModel.getInstance().reconcileGroup(chart, note.trackId, note.holdGroup);
        return true;
    }

    public boolean setType(ChartManifest chart, ChartManifest.Note note, int type) {
        if (!canEdit(chart, note) || note.holdGroup >= 0 || type < 0 || type > 3 || note.type == type) return false;
        double relativeY = NoteEditRules.editorRelativeY(note.y, note.type);
        note.type = type;
        note.y = NoteEditRules.editorAbsoluteY(type == 2 ? 0.0 : relativeY, type);
        return true;
    }

    private boolean onStep(double value, double step) {
        return Math.abs(value / step - Math.rint(value / step)) < 1.0e-7;
    }
}
