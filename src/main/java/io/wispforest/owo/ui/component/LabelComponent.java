package io.wispforest.owo.ui.component;

import imgui.ImGui;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

public final class LabelComponent extends UiComponent {
    private final Text text;
    private boolean shadow;
    private int maxWidth = 0;
    public LabelComponent(Text text) { this.text = text; }
    public LabelComponent shadow(boolean value) { shadow = value; return this; }
    public LabelComponent maxWidth(int value) { maxWidth = value; return this; }
    @Override public void render() {
        if (maxWidth > 0) ImGui.pushTextWrapPos(ImGui.getCursorPosX() + maxWidth);
        TextColor color = text.getStyle().getColor();
        if (color != null) {
            int rgb = color.getRgb();
            ImGui.textColored(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f, 1.0f, text.getString());
        } else if (shadow) {
            ImGui.textColored(0.45f, 0.82f, 1.0f, 1.0f, text.getString());
        } else {
            ImGui.textWrapped(text.getString());
        }
        if (maxWidth > 0) ImGui.popTextWrapPos();
    }
}
