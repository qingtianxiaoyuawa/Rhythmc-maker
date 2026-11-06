package cn.frkovo.rhythmcmaker.common.note;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public final class NoteEditorDraft {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NoteEditorDraft() {}

    public static ChartManifest snapshot(ChartManifest chart) {
        ChartManifest snapshot = GSON.fromJson(GSON.toJson(chart), ChartManifest.class);
        apply(serialize(chart), snapshot);
        return snapshot;
    }

    public static String serialize(ChartManifest chart) {
        List<Entry> entries = chart.notes.stream().filter(note -> note != null && note.editorBeat != null
                && note.editorBeat.represents(note.beat)).map(note -> new Entry(note.id, note.beat, note.editorBeat)).toList();
        return GSON.toJson(new Draft(entries));
    }

    public static void apply(String json, ChartManifest chart) {
        Draft draft = GSON.fromJson(json, Draft.class);
        if (draft == null || draft.notes() == null) throw new IllegalArgumentException("Invalid note editor draft");
        java.util.Map<String, ChartManifest.Note> notesById = new java.util.HashMap<>();
        for (ChartManifest.Note note : chart.notes) if (note != null) notesById.put(note.id, note);
        for (Entry entry : draft.notes()) {
            if (entry == null || entry.fraction() == null || !entry.fraction().represents(entry.beat())) continue;
            ChartManifest.Note note = notesById.get(entry.noteId());
            if (note != null && Math.abs(note.beat - entry.beat()) < 1.0e-9) note.editorBeat = entry.fraction();
        }
    }

    private record Draft(List<Entry> notes) {}
    private record Entry(String noteId, double beat, EditorBeat fraction) {}
}
