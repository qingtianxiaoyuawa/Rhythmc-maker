package cn.frkovo.rhythmcmaker.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

public final class ImGuiRuntimeAccess {
    private ImGuiRuntimeAccess() {}
    public static void begin(float delta, DrawContext context, int mouseX, int mouseY) { ImGuiRuntime.beginFrame(delta, context, mouseX, mouseY); }
    public static void end() { ImGuiRuntime.endFrame(); }
    public static void renderPending() { ImGuiRuntime.renderPending(); }
    public static void scroll(double horizontal, double vertical) { ImGuiRuntime.addScroll(horizontal, vertical); }
    public static void character(int codepoint) { ImGuiRuntime.addCharacter(codepoint); }
    public static void key(int keyCode, boolean pressed, int modifiers) { ImGuiRuntime.setKey(keyCode, pressed, modifiers); }
    public static void drawItem(ItemStack stack, int x, int y) { ImGuiRuntime.queueItem(stack, x, y); }
}
