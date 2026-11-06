package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.RhythmcMaker;
import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.EditorTrackLayout;
import cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDataType;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class ChartMenuScreen extends Screen {
    private static final int PAGE_QUICK = 0;
    private static final int PAGE_INFO = 1;
    private static final int PAGE_TRACK = 2;
    private final ImInt divisions = new ImInt();
    private final ImInt lanes = new ImInt();
    private final ImDouble teleportBeat = new ImDouble();
    private int page = PAGE_QUICK;
    private int teleportMode;
    private final ImInt teleportModeSelection = new ImInt();
    private final ImInt laneSelection = new ImInt();
    private boolean initialized;
    public ChartMenuScreen() { super(Text.literal("谱面菜单")); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance(); if (!ImGuiRuntime.beginFrame(client)) return;
        try { renderMenu(client); } finally { ImGuiRuntime.endFrame(); }
    }
    private void renderMenu(MinecraftClient client) {
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        if (!initialized && chart != null) { divisions.set(Math.max(1, Math.min(32, chart.divisionsPerBeat > 0 ? chart.divisionsPerBeat : ClientChartAccess.config().defaultDivisionsPerBeat))); lanes.set(Math.max(1, Math.min(9, chart.laneCount))); teleportBeat.set(Math.max(1, Math.min(Math.max(1, chart.beatCount), chart.selectedStartBeat))); initialized = true; }
        float displayWidth = client.getWindow().getWidth(); float displayHeight = client.getWindow().getHeight(); float w = Math.min(Math.max(1.0f, displayWidth - 24.0f), Math.max(Math.min(560.0f, displayWidth - 24.0f), displayWidth * 0.82f)); float h = Math.min(Math.max(1.0f, displayHeight - 24.0f), Math.max(Math.min(360.0f, displayHeight - 24.0f), displayHeight * 0.78f));
        ImGui.setNextWindowPos((client.getWindow().getWidth() - w) * 0.5f, (client.getWindow().getHeight() - h) * 0.5f, ImGuiCond.Always); ImGui.setNextWindowSize(w, h, ImGuiCond.Always);
        if (ImGui.begin("谱面菜单##chart-menu", ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings)) {
            ImGui.text("谱面菜单"); ImGui.separator(); float side = Math.min(210.0f, Math.max(170.0f, w * 0.24f));
            boolean sidebarVisible = ImGui.beginChild("chart-menu-sidebar", side, -1.0f, true); if (sidebarVisible) { nav("快捷功能", PAGE_QUICK); nav("谱面信息", PAGE_INFO); nav("修改制谱器轨道", PAGE_TRACK); } ImGui.endChild(); ImGui.sameLine();
            boolean contentVisible = ImGui.beginChild("chart-menu-content", 0.0f, -1.0f, true); if (contentVisible) { if (page == PAGE_QUICK) quickPage(client, chart); else if (page == PAGE_TRACK) trackPage(client, chart); else infoPage(chart); } ImGui.endChild();
            ImGui.separator(); if (ImGui.button("关闭##chart-menu-close")) client.setScreen(null);
        } ImGui.end();
    }
    private void nav(String label, int target) { if (ImGui.selectable(label, page == target, 0, Math.max(1.0f, ImGui.getContentRegionAvailX()), 38.0f)) page = target; }
    private void quickPage(MinecraftClient client, ChartManifest chart) {
        ImGui.text("快捷传送"); ImGui.separator(); if (chart == null) { ImGui.text("当前未加载谱面"); return; }
        String[] modes = {"按 Beat 传送", "按歌曲时间传送"};
        ImGui.text("传送方式");
        ImGui.setNextItemWidth(Math.max(1.0f, ImGui.getContentRegionAvailX()));
        teleportModeSelection.set(teleportMode);
        if (ImGui.combo("##teleport-mode", teleportModeSelection, modes)) teleportMode = Math.max(0, Math.min(modes.length - 1, teleportModeSelection.get()));
        double maxValue = teleportMode == 0 ? Math.max(1.0, chart.beatCount) : chart.durationSeconds > 0.0 ? chart.durationSeconds : Math.max(1, chart.beatCount) * 60.0 / Math.max(1.0, chart.bpm);
        double minValue = teleportMode == 0 ? 1.0 : 0.0;
        ImGui.text(teleportMode == 0 ? "Beat（1 - " + (int) maxValue + "）" : "歌曲时间（秒）");
        ImGui.setNextItemWidth(Math.max(1.0f, ImGui.getContentRegionAvailX()));
        ImGui.sliderScalar("##teleport-value", ImGuiDataType.Double, teleportBeat, minValue, maxValue, teleportMode == 0 ? "%.0f" : "%.2f");
        teleportBeat.set(Math.max(minValue, Math.min(maxValue, teleportBeat.get())));
        if (ImGui.button("传送至制谱器平台##teleport-editor")) {
            if (teleportMode == 0) RhythmcMakerClient.sendChartCommand("rhythmc_teleport_beat " + (int) Math.round(teleportBeat.get()));
            else RhythmcMakerClient.sendChartCommand("rhythmc_teleport_time " + teleportBeat.get());
        }
        if (ImGui.button("传送至播放场景##teleport-playback")) RhythmcMakerClient.sendChartCommand("rhythmc_teleport_playback_scene");
        ImGui.separator(); ImGui.text("轨道吸附"); ImGui.text("吸附轨道可能导致音符时机改变，请确认操作"); boolean hasFractional = chart.notes != null && chart.notes.stream().anyMatch(note -> note != null && Math.abs(note.z - Math.rint(note.z)) > 1.0e-6);
        if (!hasFractional) ImGui.beginDisabled(); if (ImGui.button("确认吸附##snap")) client.setScreen(new SnapConfirmScreen(this)); if (!hasFractional) ImGui.endDisabled();
        boolean canUndo = client.player != null && client.getServer() != null && RhythmcMaker.canUndoTrackSnap(client.player.getUuid()); if (!canUndo) ImGui.beginDisabled(); if (ImGui.button("撤销吸附##snap-undo")) RhythmcMakerClient.sendChartCommand("rhythmc_track_snap_undo"); if (!canUndo) ImGui.endDisabled();
    }
    private void trackPage(MinecraftClient client, ChartManifest chart) {
        ImGui.text("修改制谱器轨道"); ImGui.separator(); if (chart == null) { ImGui.text("当前未加载谱面"); return; }
        ImGui.text("每beat分数 (1-32)"); ImGui.setNextItemWidth(Math.max(1.0f, Math.max(1.0f, Math.min(420.0f, ImGui.getContentRegionAvailX())))); ImGui.sliderInt("##divisions", divisions.getData(), 1, 32);
        ImGui.text("轨道宽度"); ImGui.setNextItemWidth(Math.min(420.0f, ImGui.getContentRegionAvailX())); laneSelection.set(laneOptionIndex(lanes.get())); String[] laneOptions = {"1", "3", "5", "7", "9"}; if (ImGui.combo("##lanes", laneSelection, laneOptions)) lanes.set(Integer.parseInt(laneOptions[Math.max(0, Math.min(laneOptions.length - 1, laneSelection.get()))]));
        ImGui.text("轨道高度"); ImGui.textDisabled("高度设置后续加入（当前高度保持不变）"); if (ImGui.button("确认修改##track")) {
            if (!EditorTrackLayout.isSupportedDivisionsPerBeat(divisions.get()) || !EditorTrackLayout.isSupportedLaneCount(lanes.get())) return;
            EditorTrackLayout layout = EditorTrackLayout.of(lanes.get(), divisions.get());
            PlaceholderScreen.invalidateTrackSnapState();
            RhythmcMakerClient.sendChartCommand("rhythmc_layout_set " + layout.divisionsPerBeat() + " " + layout.laneCount());
            ClientChartAccess.status("正在应用制谱器轨道：" + layout.laneCount() + " × " + layout.divisionsPerBeat());
        }
    }
    private void infoPage(ChartManifest chart) { ImGui.text("谱面信息"); ImGui.separator(); ImGui.textWrapped("谱面信息页面预留，后续接入新的谱面信息菜单。"); }
    private static int laneOptionIndex(int value) { return switch (value) { case 1 -> 0; case 3 -> 1; case 5 -> 2; case 7 -> 3; default -> 4; }; }
    @Override public boolean shouldPause() { return false; } @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {} @Override public boolean keyPressed(net.minecraft.client.input.KeyInput input) { if (input.isEscape()) { MinecraftClient.getInstance().setScreen(null); return true; } return true; }
}
