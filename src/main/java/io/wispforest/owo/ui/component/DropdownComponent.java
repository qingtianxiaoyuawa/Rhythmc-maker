package io.wispforest.owo.ui.component;

import imgui.ImGui;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.text.Text;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class DropdownComponent extends FlowLayout {
    private boolean popup;
    private boolean openRequested;
    private float popupX;
    private float popupY;
    private final String popupId = "##dropdown_" + System.identityHashCode(this);

    public DropdownComponent() { super(false); }

    public DropdownComponent button(Text text, Consumer<DropdownComponent> action) {
        child(UIComponents.button(text, ignored -> {
            if (action != null) action.accept(this);
            ImGui.closeCurrentPopup();
        }));
        return this;
    }

    public void popupAt(int x, int y) {
        popup = true;
        openRequested = true;
        popupX = x;
        popupY = y;
    }

    @Override
    public void render() {
        if (!popup) {
            super.render();
            return;
        }
        if (openRequested) {
            ImGui.setNextWindowPos(popupX, popupY);
            ImGui.openPopup(popupId);
            openRequested = false;
        }
        if (ImGui.beginPopup(popupId)) {
            renderChildren();
            ImGui.endPopup();
        } else if (!ImGui.isPopupOpen(popupId)) {
            popup = false;
        }
    }

    public static void openContextMenu(Object screen, FlowLayout root, BiConsumer<FlowLayout, DropdownComponent> attach,
                                       int x, int y, Consumer<DropdownComponent> populate) {
        DropdownComponent menu = new DropdownComponent();
        menu.popupAt(x, y);
        populate.accept(menu);
        attach.accept(root, menu);
    }
}