package cn.frkovo.rhythmcmaker.common.ui;

public record NoteEditorWindowSize(float width, float height) {
    public static NoteEditorWindowSize defaultSize(float displayWidth, float displayHeight) {
        if (!Float.isFinite(displayWidth) || !Float.isFinite(displayHeight) || displayWidth <= 0 || displayHeight <= 0) {
            throw new IllegalArgumentException("Invalid display dimensions");
        }
        float availableWidth = Math.max(1.0f, displayWidth - 32.0f);
        float availableHeight = Math.max(1.0f, displayHeight - 32.0f);
        float width = Math.min(520.0f, availableWidth);
        float height = Math.min(720.0f, availableHeight * 0.9f);
        double maximumArea = (double) displayWidth * displayHeight / 3.0;
        double factor = Math.min(1.0, Math.sqrt(maximumArea / ((double) width * height)));
        return new NoteEditorWindowSize((float) (width * factor), (float) (height * factor));
    }
}
