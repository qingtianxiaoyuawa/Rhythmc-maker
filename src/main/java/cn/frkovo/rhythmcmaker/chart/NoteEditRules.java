package cn.frkovo.rhythmcmaker.chart;

public final class NoteEditRules {
    private static final double FRACTION_EPSILON = 0.000001;

    private NoteEditRules() {
    }

    public static boolean canOpenEditor(ChartManifest.Note note) {
        return note != null;
    }

    public static boolean canEdit(ChartManifest.Note note) {
        return canOpenEditor(note);
    }

    public static boolean requiresDisplayEntity(double x, double y, double z) {
        return hasFraction(x) || hasFraction(y) || hasFraction(z);
    }

    public static double editorRelativeY(double absoluteY, int type) {
        return absoluteY - editorHeightBase(type);
    }

    public static double editorAbsoluteY(double relativeY, int type) {
        return relativeY + editorHeightBase(type);
    }

    public static double editorHeightBase(int type) {
        return type == 2 ? 65.0 : 66.0;
    }

    public static boolean usesDisplayEntity(ChartManifest.Note note) {
        return note != null && (note.visualOnly || requiresDisplayEntity(note.x, note.y, note.z)
                || cn.frkovo.rhythmcmaker.common.note.NoteGeometry.hasTransform(note));
    }

    public static boolean hasBlockRepresentationAt(ChartManifest.Note note, int type, double x, double y, double z) {
        return note != null && note.type == type
                && !usesDisplayEntity(note)
                && Math.abs(note.x - x) < FRACTION_EPSILON
                && Math.abs(note.y - y) < FRACTION_EPSILON
                && Math.abs(note.z - z) < FRACTION_EPSILON;
    }

    public static double editorDisplayCoordinate(double coordinate) {
        return coordinate + 0.5;
    }

    public static double editorNoteCoordinate(double displayCoordinate) {
        return displayCoordinate - 0.5;
    }

    public static double editorDisplayCenter(double start, double end) {
        return (editorDisplayCoordinate(start) + editorDisplayCoordinate(end)) * 0.5;
    }

    public static double editorDisplaySpan(double start, double end) {
        return Math.max(1.0, Math.abs(editorDisplayCoordinate(end) - editorDisplayCoordinate(start)) + 1.0);
    }

    private static boolean hasFraction(double value) {
        return Math.abs(value - Math.rint(value)) > FRACTION_EPSILON;
    }
}
