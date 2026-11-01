package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.HoldNoteGroupMerger;
import cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImDouble;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

public final class NoteEditScreen extends Screen {
    private static final float WINDOW_WIDTH = 350.0f;
    private final ChartManifest chart;
    private final ChartManifest.Note note;
    private final boolean showOnRight;

    public NoteEditScreen(ChartManifest chart, ChartManifest.Note note) {
        super(Text.literal("修改音符"));
        this.chart = chart;
        this.note = note;
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d relative = new Vec3d(note.x - client.player.getX(),
                note.y - client.player.getY(), note.z - client.player.getZ());
        double yaw = Math.toRadians(client.gameRenderer.getCamera().getYaw());
        double horizontalRight = relative.x * Math.cos(yaw) + relative.z * Math.sin(yaw);
        this.showOnRight = horizontalRight < 0.0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ImGuiRuntime.beginFrame(client)) return;
        try {
            float windowWidth = Math.max(1.0f, Math.min(WINDOW_WIDTH, ImGui.getIO().getDisplaySizeX() - 32.0f));
            float windowX = showOnRight ? ImGui.getIO().getDisplaySizeX() - windowWidth - 16.0f : 16.0f;
            ImGui.setNextWindowPos(windowX, ImGui.getIO().getDisplaySizeY() * 0.25f, ImGuiCond.Always);
            ImGui.setNextWindowSize(windowWidth, 260.0f, ImGuiCond.Always);
            ImGui.begin("修改音符##rhythmc-note", ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse);
            ImGui.text("位置 X");
            ImDouble x = new ImDouble(note.x);
            if (ImGui.inputDouble("##note-x", x, 0.0, 0.0, "%.3f")) update(client, x.get(), note.y, note.beat);
            ImGui.text("位置 Y");
            ImDouble y = new ImDouble(note.y);
            if (ImGui.inputDouble("##note-y", y, 0.0, 0.0, "%.3f")) update(client, note.x, y.get(), note.beat);
            ImGui.text("时机 Beat");
            ImDouble beat = new ImDouble(note.beat);
            if (ImGui.inputDouble("##note-beat", beat, 0.0, 0.0, "%.6f")) update(client, note.x, note.y, beat.get());
            if (note.type == 2 && ImGui.button("粘合相邻钻石块")) {
                java.util.List<ChartManifest.Note> connected = HoldNoteGroupMerger.connectedNotes(chart.notes, note);
                if (connected.size() > 1) {
                    int group = HoldNoteGroupMerger.nextGroupId(chart.notes);
                    for (ChartManifest.Note member : connected) member.holdGroup = group;
                    client.player.networkHandler.sendChatCommand("rhythmc_note_glue " + note.id);
                }
            }
            if (ImGui.button("关闭")) client.setScreen(null);
            ImGui.end();
        } finally {
            ImGuiRuntime.endFrame();
        }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return true; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.isEscape()) {
            MinecraftClient.getInstance().setScreen(null);
            return true;
        }
        return true;
    }

    private void update(MinecraftClient client, double x, double y, double beat) {
        if (client.player == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(beat) || beat < 0.0) return;
        note.x = x;
        note.y = y;
        note.beat = beat;
        note.z = -3.0 - beat * Math.max(1, Math.min(32, chart.divisionsPerBeat));
        note.time = cn.frkovo.rhythmcmaker.chart.ChartTiming.beatToSeconds(chart, beat);
        client.player.networkHandler.sendChatCommand("rhythmc_note_edit " + note.id + " "
                + String.format(Locale.ROOT, "%.9f %.9f %.9f", x, y, beat));
    }
}
