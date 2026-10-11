package cn.frkovo.rhythmcmaker.client.camera;

public record EffectEditorCameraState(double horizontalOffset, double height, double timelineOffset) {
    public EffectEditorCameraState {
        if (!Double.isFinite(horizontalOffset) || !Double.isFinite(height) || !Double.isFinite(timelineOffset)
                || height < 72.0 || height > 90.0) {
            throw new IllegalArgumentException("Invalid effect editor camera state");
        }
    }
}
