package io.wispforest.owo.ui.component;

import cn.frkovo.rhythmcmaker.client.ImGuiRuntimeAccess;
import imgui.ImGui;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class ItemComponent extends UiComponent {
    private final ItemStack stack;

    public ItemComponent(ItemStack stack) { this.stack = stack == null ? ItemStack.EMPTY : stack; }

    @Override
    public void render() {
        int x = (int) ImGui.getCursorScreenPosX();
        int y = (int) ImGui.getCursorScreenPosY();
        if (!stack.isEmpty()) ImGuiRuntimeAccess.drawItem(stack, x, y);
        ImGui.dummy(40.0f, 40.0f);
        ImGui.sameLine();
        ImGui.beginGroup();
        ImGui.text(stack.isEmpty() ? "空物品" : stack.getName().getString());
        if (!stack.isEmpty()) {
            ImGui.textDisabled(Registries.ITEM.getId(stack.getItem()).toString());
            if (stack.getCount() > 1) ImGui.text("数量：" + stack.getCount());
        }
        ImGui.endGroup();
    }
}