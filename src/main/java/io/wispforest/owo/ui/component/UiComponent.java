package io.wispforest.owo.ui.component;

import imgui.ImGui;
import io.wispforest.owo.ui.core.Sizing;

public abstract class UiComponent {
    protected Sizing horizontalSizing = Sizing.content();
    protected Sizing verticalSizing = Sizing.content();

    public <T extends UiComponent> T horizontalSizing(Sizing sizing) {
        horizontalSizing = sizing;
        return cast();
    }

    public <T extends UiComponent> T verticalSizing(Sizing sizing) {
        verticalSizing = sizing;
        return cast();
    }

    public Sizing horizontalSizing() { return horizontalSizing; }
    public Sizing verticalSizing() { return verticalSizing; }

    @SuppressWarnings("unchecked")
    private <T extends UiComponent> T cast() { return (T) this; }

    public int x() { return (int) ImGui.getCursorScreenPosX(); }
    public int y() { return (int) ImGui.getCursorScreenPosY(); }
    public int height() { return (int) (verticalSizing.kind() == Sizing.Kind.FIXED ? verticalSizing.value() : ImGui.getFrameHeight()); }

    public float preferredWidth(float availableWidth) {
        return horizontalSizing.kind() == Sizing.Kind.FIXED ? horizontalSizing.value() : availableWidth;
    }

    public float preferredHeight(float availableWidth) {
        return verticalSizing.kind() == Sizing.Kind.FIXED ? verticalSizing.value() : ImGui.getFrameHeight();
    }

    public abstract void render();
}