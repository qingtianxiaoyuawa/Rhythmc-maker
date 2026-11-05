package cn.frkovo.rhythmcmaker.chart;

public final class NoteEditRules {
    private static final double FRACTION_EPSILON = 0.000001;

    private NoteEditRules() {
    }

    public static boolean canEdit(ChartManifest.Note note) {
        return note != null && note.holdGroup < 0;
    }

    public static boolean requiresDisplayEntity(double x, double y, double z) {
        return hasFraction(x) || hasFraction(y) || hasFraction(z);
    }

    private static boolean hasFraction(double value) {
        return Math.abs(value - Math.rint(value)) > FRACTION_EPSILON;
    }
}
