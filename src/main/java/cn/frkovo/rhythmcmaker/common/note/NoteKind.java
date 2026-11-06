package cn.frkovo.rhythmcmaker.common.note;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;

public enum NoteKind {
    TAP("TAP 音符"), LOOK("LOOK 音符"), HOLD("HOLD 音符"), DODGE("DODGE 音符"), HOLD_GROUP("HOLD组 音符");

    private final String label;

    NoteKind(String label) { this.label = label; }

    public String label() { return label; }

    public static NoteKind of(ChartManifest.Note note) {
        return note.type == 2 && note.holdGroup >= 0 ? HOLD_GROUP : ofType(note.type);
    }

    public static NoteKind ofType(int type) {
        return switch (type) { case 1 -> LOOK; case 2 -> HOLD; case 3 -> DODGE; default -> TAP; };
    }
}
