package cn.frkovo.rhythmcmaker.common.note;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;

public enum NoteTransformField {
    SCALE_X, SCALE_Y, SCALE_Z, ROTATION_X, ROTATION_Y, ROTATION_Z;

    public double minimum() { return ordinal() < 3 ? 1.0 : -180.0; }
    public double maximum() { return ordinal() < 3 ? 3.0 : 180.0; }
    public double step() { return ordinal() < 3 ? 0.1 : 0.5; }

    public double get(ChartManifest.Note note) {
        return switch (this) { case SCALE_X -> note.scaleX; case SCALE_Y -> note.scaleY; case SCALE_Z -> note.scaleZ;
            case ROTATION_X -> note.rotationX; case ROTATION_Y -> note.rotationY; case ROTATION_Z -> note.rotationZ; };
    }

    public void set(ChartManifest.Note note, double value) {
        switch (this) { case SCALE_X -> note.scaleX = value; case SCALE_Y -> note.scaleY = value; case SCALE_Z -> note.scaleZ = value;
            case ROTATION_X -> note.rotationX = value; case ROTATION_Y -> note.rotationY = value; case ROTATION_Z -> note.rotationZ = value; }
    }
}
