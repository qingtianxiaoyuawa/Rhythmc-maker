package io.wispforest.owo.ui.component;

import imgui.ImGui;
import imgui.type.ImString;
import io.wispforest.owo.ui.core.Sizing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TextBoxComponent extends UiComponent {
    private final ImString value;
    private final List<Consumer<String>> listeners = new ArrayList<>();
    private boolean notifying;

    public TextBoxComponent(Sizing sizing, String text) {
        horizontalSizing(sizing);
        value = new ImString(text == null ? "" : text, 2048);
    }

    public String getText() { return value.get(); }
    public void text(String text) { value.set(text == null ? "" : text); notifyListeners(); }
    public ChangeStream onChanged() { return new ChangeStream(); }

    @Override
    public void render() {
        float width = horizontalSizing.kind() == Sizing.Kind.FIXED
                ? horizontalSizing.value() : horizontalSizing.kind() == Sizing.Kind.FILL
                ? ImGui.getContentRegionAvailX() : -1.0f;
        ImGui.setNextItemWidth(width);
        if (ImGui.inputText("##input_" + System.identityHashCode(this), value)) notifyListeners();
    }

    private void notifyListeners() {
        if (notifying) return;
        notifying = true;
        try {
            for (Consumer<String> listener : List.copyOf(listeners)) listener.accept(value.get());
        } finally {
            notifying = false;
        }
    }
    public final class ChangeStream {
        public void subscribe(Consumer<String> listener) { if (listener != null) listeners.add(listener); }
    }
}