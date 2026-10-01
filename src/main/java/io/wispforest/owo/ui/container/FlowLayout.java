package io.wispforest.owo.ui.container;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import io.wispforest.owo.ui.component.UiComponent;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;

import java.util.ArrayList;
import java.util.List;

public class FlowLayout extends UiComponent {
    private final boolean horizontal;
    private final boolean scroll;
    private final List<UiComponent> children = new ArrayList<>();
    private int padding;
    private int gap = 4;
    private Surface surface;
    private HorizontalAlignment horizontalAlignment = HorizontalAlignment.LEFT;
    private VerticalAlignment verticalAlignment = VerticalAlignment.TOP;
    private float assignedHeight = -1.0f;

    public FlowLayout(boolean horizontal) { this(horizontal, false); }
    public FlowLayout(boolean horizontal, boolean scroll) { this.horizontal = horizontal; this.scroll = scroll; }
    public FlowLayout child(UiComponent child) { if (child != null) children.add(child); return this; }
    public void removeChild(UiComponent child) { children.remove(child); }
    public FlowLayout padding(Insets insets) { padding = insets.all(); return this; }
    public FlowLayout gap(int value) { gap = Math.max(0, value); return this; }
    public FlowLayout surface(Surface value) { surface = value; return this; }
    public FlowLayout horizontalAlignment(HorizontalAlignment value) { horizontalAlignment = value == null ? HorizontalAlignment.LEFT : value; return this; }
    public FlowLayout verticalAlignment(VerticalAlignment value) { verticalAlignment = value == null ? VerticalAlignment.TOP : value; return this; }

    @Override
    public float preferredHeight(float availableWidth) {
        if (verticalSizing.kind() == Sizing.Kind.FIXED) return verticalSizing.value();
        float innerWidth = Math.max(1.0f, availableWidth - 2.0f * padding);
        float contentHeight = 0.0f;
        int measuredChildren = 0;
        for (UiComponent child : children) {
            if (child.verticalSizing().kind() == Sizing.Kind.EXPAND) continue;
            float childWidth = child.horizontalSizing().kind() == Sizing.Kind.FIXED
                    ? child.horizontalSizing().value() : innerWidth;
            float childHeight = child.preferredHeight(childWidth);
            if (horizontal) contentHeight = Math.max(contentHeight, childHeight);
            else contentHeight += childHeight;
            measuredChildren++;
        }
        if (!horizontal && measuredChildren > 1) contentHeight += gap * (measuredChildren - 1);
        return contentHeight + 2.0f * padding;
    }

    void renderAtHeight(float height) {
        assignedHeight = Math.max(0.0f, height);
        try {
            render();
        } finally {
            assignedHeight = -1.0f;
        }
    }

    @Override
    public void render() {
        boolean wrapped = scroll || surface != null || horizontalSizing.kind() == Sizing.Kind.FIXED
                || verticalSizing.kind() == Sizing.Kind.FIXED || verticalSizing.kind() == Sizing.Kind.CONTENT;
        boolean open = true;
        if (wrapped) {
            int colors = 0;
            if (surface != null) {
                pushPackedColor(ImGuiCol.ChildBg, surface.background());
                pushPackedColor(ImGuiCol.Border, surface.outline());
                colors = 2;
            }
            float width = horizontalSizing.kind() == Sizing.Kind.FIXED ? horizontalSizing.value() : 0.0f;
            float height = assignedHeight >= 0.0f ? assignedHeight
                    : verticalSizing.kind() == Sizing.Kind.FIXED ? verticalSizing.value()
                    : verticalSizing.kind() == Sizing.Kind.CONTENT ? preferredHeight(ImGui.getContentRegionAvailX()) : 0.0f;
            boolean border = surface != null && (surface.outline() >>> 24) != 0;
            int childFlags = scroll ? imgui.flag.ImGuiWindowFlags.None
                    : imgui.flag.ImGuiWindowFlags.NoScrollbar | imgui.flag.ImGuiWindowFlags.NoScrollWithMouse;
            open = ImGui.beginChild("##layout_" + System.identityHashCode(this), width, height, border, childFlags);
            if (open) renderChildren();
            ImGui.endChild();
            if (colors > 0) ImGui.popStyleColor(colors);
        } else {
            ImGui.beginGroup();
            renderChildren();
            ImGui.endGroup();
        }
    }

    protected final void renderChildren() {
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();
        float availableWidth = Math.max(0.0f, ImGui.getContentRegionAvailX() - 2.0f * padding);
        float availableHeight = Math.max(0.0f, ImGui.getContentRegionAvailY() - 2.0f * padding);
        if (padding > 0) {
            ImGui.setCursorPos(startX + padding, startY + padding);
        }

        if (horizontal) {
            float fixedWidth = 0.0f;
            float maxHeight = 0.0f;
            for (UiComponent child : children) {
                float childWidth = child.horizontalSizing().kind() == Sizing.Kind.FIXED
                        ? child.horizontalSizing().value() : child.preferredWidth(availableWidth);
                fixedWidth += childWidth;
                maxHeight = Math.max(maxHeight, child.preferredHeight(availableWidth));
            }
            if (children.size() > 1) fixedWidth += gap * (children.size() - 1);
            if (horizontalAlignment == HorizontalAlignment.CENTER && fixedWidth > 0 && availableWidth > fixedWidth) {
                ImGui.setCursorPosX(ImGui.getCursorPosX() + (availableWidth - fixedWidth) / 2.0f);
            } else if (horizontalAlignment == HorizontalAlignment.RIGHT && availableWidth > fixedWidth) {
                ImGui.setCursorPosX(ImGui.getCursorPosX() + availableWidth - fixedWidth);
            }
            if (verticalAlignment == VerticalAlignment.CENTER && maxHeight > 0 && availableHeight > maxHeight) {
                ImGui.setCursorPosY(ImGui.getCursorPosY() + (availableHeight - maxHeight) / 2.0f);
            }
            for (int index = 0; index < children.size(); index++) {
                if (index > 0) ImGui.sameLine(0.0f, gap);
                children.get(index).render();
            }
        } else {
            float fixedHeight = 0.0f;
            int expandCount = 0;
            for (UiComponent child : children) {
                if (child.verticalSizing().kind() == Sizing.Kind.EXPAND) {
                    expandCount++;
                } else {
                    fixedHeight += child.preferredHeight(availableWidth);
                }
            }
            if (children.size() > 1) fixedHeight += gap * (children.size() - 1);
            float expandHeight = expandCount == 0 ? 0.0f : Math.max(0.0f, availableHeight - fixedHeight) / expandCount;
            float contentHeight = fixedHeight + expandHeight * expandCount;
            if (verticalAlignment == VerticalAlignment.CENTER && availableHeight > contentHeight) {
                ImGui.setCursorPosY(ImGui.getCursorPosY() + (availableHeight - contentHeight) / 2.0f);
            } else if (verticalAlignment == VerticalAlignment.BOTTOM && availableHeight > contentHeight) {
                ImGui.setCursorPosY(ImGui.getCursorPosY() + availableHeight - contentHeight);
            }
            for (int index = 0; index < children.size(); index++) {
                UiComponent child = children.get(index);
                if (child instanceof FlowLayout flow && child.verticalSizing().kind() == Sizing.Kind.EXPAND) {
                    flow.renderAtHeight(expandHeight);
                } else {
                    child.render();
                }
                if (gap > 0 && index + 1 < children.size()) ImGui.dummy(1.0f, gap);
            }
        }
        if (padding > 0) ImGui.setCursorPosX(startX);
    }

    private static void pushPackedColor(int slot, int argb) {
        float alpha = ((argb >>> 24) & 0xFF) / 255.0f;
        float red = ((argb >>> 16) & 0xFF) / 255.0f;
        float green = ((argb >>> 8) & 0xFF) / 255.0f;
        float blue = (argb & 0xFF) / 255.0f;
        ImGui.pushStyleColor(slot, red, green, blue, alpha);
    }
}