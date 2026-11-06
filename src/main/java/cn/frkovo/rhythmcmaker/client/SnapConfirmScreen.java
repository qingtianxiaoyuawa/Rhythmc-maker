package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class SnapConfirmScreen extends Screen {
    private final Screen parent;
    public SnapConfirmScreen(Screen parent) { super(Text.literal("音符吸附")); this.parent = parent; }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) { MinecraftClient client = MinecraftClient.getInstance(); if (!ImGuiRuntime.beginFrame(client)) return; try { float displayWidth = client.getWindow().getWidth(); float displayHeight = client.getWindow().getHeight(); float w = Math.max(1.0f, Math.min(520.0f, displayWidth - 48.0f)); float h = Math.max(1.0f, Math.min(380.0f, displayHeight - 48.0f)); ImGui.setNextWindowPos((client.getWindow().getWidth() - w) * 0.5f, (client.getWindow().getHeight() - h) * 0.5f, ImGuiCond.Always); ImGui.setNextWindowSize(w, h, ImGuiCond.Always); if (ImGui.begin("音符吸附##snap-confirm", ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings)) { ImGui.text("音符吸附"); ImGui.separator(); ChartManifest chart = ClientChartAccess.resolveActiveChart(); boolean messageVisible = ImGui.beginChild("snap-message", -1.0f, Math.max(90.0f, h - 145.0f), true); if (messageVisible) { ImGui.textWrapped("将把当前谱面中位于小数轨道位置的音符吸附到最近的轨道分割位置。重叠音符可能合并，此操作将立即写回谱面文件。"); if (chart != null) { ImGui.separator(); ImGui.text("当前谱面：" + chart.title); long count = chart.notes == null ? 0 : chart.notes.stream().filter(note -> note != null && Math.abs(note.z - Math.rint(note.z)) > 1.0e-6).count(); ImGui.text("待吸附音符：" + count); } } ImGui.endChild(); if (ImGui.button("确认##snap-confirm-ok", -1.0f, 34.0f)) { RhythmcMakerClient.sendChartCommand("rhythmc_track_snap"); client.setScreen(parent); } if (ImGui.button("取消##snap-confirm-cancel", -1.0f, 34.0f)) client.setScreen(parent); } ImGui.end(); } finally { ImGuiRuntime.endFrame(); } }
    @Override public boolean shouldPause() { return false; } @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {} @Override public boolean keyPressed(net.minecraft.client.input.KeyInput input) { if (input.isEscape()) { MinecraftClient.getInstance().setScreen(parent); return true; } return true; }
}
