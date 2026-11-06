package cn.frkovo.rhythmcmaker.chart;

public final class EditorNoteLayout {
    private EditorNoteLayout() {
    }

    public static void repositionNotes(ChartManifest chart) {
        if (chart == null || chart.notes == null) return;
        ChartTiming.Prepared timing = ChartTiming.prepare(chart);
        for (ChartManifest.Note note : chart.notes) {
            if (note == null) continue;
            double beat = Double.isFinite(note.beat) ? Math.max(0.0, note.beat) : timing.secondsToBeat(note.time);
            note.beat = beat;
            note.time = timing.beatToSeconds(beat);
            note.z = PlaybackCoordinates.editorWorldZAtBeat(chart, beat) - 0.5;
        }
    }
}
