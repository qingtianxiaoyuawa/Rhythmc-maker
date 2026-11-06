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
    private ChartManifest chart;
    private long fractionalCount;

    public SnapConfirmScreen(Screen parent) {
        super(Text.literal("音符吸附"));
        this.parent = parent;
    }

    @Override protected void init() {
        chart = ClientChartAccess.refreshActiveChartNow();
        fractionalCount = chart == null || chart.notes == null ? 0 : chart.notes.stream()
                .filter(note -> note != null && !note.visualOnly && Math.abs(note.z - Math.rint(note.z)) > 1.0e-6).count();
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ImGuiRuntime.beginFrame(client)) return;
        try {
            float displayWidth = ImGui.getIO().getDisplaySizeX();
            float displayHeight = ImGui.getIO().getDisplaySizeY();
            float width = Math.max(1.0f, Math.min(520.0f, displayWidth - 32.0f));
            float windowHeight = Math.max(1.0f, Math.min(380.0f, displayHeight - 32.0f));
            ImGui.setNextWindowPos((displayWidth - width) * 0.5f, (displayHeight - windowHeight) * 0.5f, ImGuiCond.FirstUseEver);
            ImGui.setNextWindowSize(width, windowHeight, ImGuiCond.FirstUseEver);
            ImGui.setNextWindowSizeConstraints(Math.min(280.0f, width), Math.min(200.0f, windowHeight),
                    Math.max(1.0f, displayWidth - 16.0f), Math.max(1.0f, displayHeight - 16.0f));
            if (ImGui.begin("音符吸附##snap-confirm", ImGuiWindowFlags.NoCollapse)) {
                float footer = ImGui.getFrameHeightWithSpacing() * 2.0f + 8.0f;
                boolean messageVisible = ImGui.beginChild("snap-message", -1.0f,
                        Math.max(1.0f, ImGui.getContentRegionAvailY() - footer), true);
                if (messageVisible) {
                    ImGui.textWrapped("将把当前谱面中位于小数轨道位置的音符吸附到最近的轨道分割位置。重叠音符可能合并，此操作将立即写回谱面文件。");
                    if (chart != null) {
                        ImGui.separator();
                        ImGui.textWrapped("当前谱面：" + chart.title);
                        ImGui.text("待吸附音符：" + fractionalCount);
                    }
                }
                ImGui.endChild();
                ImGui.beginDisabled(fractionalCount == 0);
                if (ImGui.button("确认##snap-confirm-ok", -1.0f, 0.0f)) {
                    RhythmcMakerClient.sendChartCommand("rhythmc_track_snap");
                    client.setScreen(parent);
                }
                ImGui.endDisabled();
                if (ImGui.button("取消##snap-confirm-cancel", -1.0f, 0.0f)) client.setScreen(parent);
            }
            ImGui.end();
        } finally {
            ImGuiRuntime.endFrame();
        }
    }

    @Override public void removed() { ImGuiRuntime.saveWindowSettings(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.isEscape()) MinecraftClient.getInstance().setScreen(parent);
        return true;
    }
}
