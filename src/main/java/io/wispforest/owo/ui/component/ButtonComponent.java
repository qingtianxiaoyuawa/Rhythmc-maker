package io.wispforest.owo.ui.component;

import imgui.ImGui;
import net.minecraft.text.Text;
import java.util.function.Consumer;

public final class ButtonComponent extends UiComponent {
    private final Text text;
    private final Consumer<ButtonComponent> action;

    public ButtonComponent(Text text, Consumer<ButtonComponent> action) {
        this.text = text;
        this.action = action;
    }

    @Override
    public void render() {
        float width = horizontalSizing.kind() == io.wispforest.owo.ui.core.Sizing.Kind.FIXED
                ? horizontalSizing.value() : horizontalSizing.kind() == io.wispforest.owo.ui.core.Sizing.Kind.FILL
                ? ImGui.getContentRegionAvailX() : 0.0f;
        float height = verticalSizing.kind() == io.wispforest.owo.ui.core.Sizing.Kind.FIXED ? verticalSizing.value() : 0.0f;
        if (ImGui.button(text.getString(), width, height) && action != null) action.accept(this);
    }
}