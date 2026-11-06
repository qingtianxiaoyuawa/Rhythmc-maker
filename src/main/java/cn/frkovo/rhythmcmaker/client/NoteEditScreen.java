package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.EditorTrackLayout;
import cn.frkovo.rhythmcmaker.chart.HoldGroupRules;
import cn.frkovo.rhythmcmaker.chart.NoteEditRules;
import cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime;
import cn.frkovo.rhythmcmaker.common.note.EditorBeat;
import cn.frkovo.rhythmcmaker.common.note.NoteEditingModel;
import cn.frkovo.rhythmcmaker.common.note.NoteGeometry;
import cn.frkovo.rhythmcmaker.common.note.NoteKind;
import cn.frkovo.rhythmcmaker.common.note.NoteTransformField;
import cn.frkovo.rhythmcmaker.common.ui.NoteEditorWindowSize;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDataType;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiSliderFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Locale;

public final class NoteEditScreen extends Screen {
    private ChartManifest chart;
    private ChartManifest.Note note;
    private final boolean showOnRight;
    private boolean fieldsNeedRefresh;
    private String operationStatus = "";
    private int operationStatusColor;
    private long operationStatusUntilNanos;
    private final ImInt beatWhole = new ImInt();
    private final ImInt beatNumerator = new ImInt();
    private final ImInt beatDenominator = new ImInt(1);
    private final ImDouble relativeX = new ImDouble();
    private final ImDouble relativeY = new ImDouble();
    private final ImDouble[] transforms = new ImDouble[NoteTransformField.values().length];

    public NoteEditScreen(ChartManifest chart, ChartManifest.Note note) {
        super(Text.literal("修改音符"));
        this.chart = chart;
        this.note = note;
        MinecraftClient client = MinecraftClient.getInstance();
        double yaw = Math.toRadians(client.gameRenderer.getCamera().getYaw());
        this.showOnRight = client.player != null && (note.x - client.player.getX()) * Math.cos(yaw)
                + (client.player == null ? 0.0 : note.z - client.player.getZ()) * Math.sin(yaw) < 0.0;
        for (NoteTransformField field : NoteTransformField.values()) transforms[field.ordinal()] = new ImDouble();
        syncFields();
    }

    private void syncFields() {
        relativeX.set(note.x);
        relativeY.set(note.type == 2 ? 0.0 : NoteEditRules.editorRelativeY(note.y, note.type));
        EditorBeat beat = note.editorBeat != null && note.editorBeat.represents(note.beat)
                ? note.editorBeat : EditorBeat.fromValue(note.beat, chart.divisionsPerBeat);
        setBeatFields(beat);
        for (NoteTransformField field : NoteTransformField.values()) transforms[field.ordinal()].set(field.get(note));
        fieldsNeedRefresh = false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ImGuiRuntime.beginFrame(client)) return;
        try {
            float displayWidth = ImGui.getIO().getDisplaySizeX();
            float displayHeight = ImGui.getIO().getDisplaySizeY();
            NoteEditorWindowSize size = NoteEditorWindowSize.defaultSize(displayWidth, displayHeight);
            ImGui.setNextWindowSize(size.width(), size.height(), ImGuiCond.FirstUseEver);
            ImGui.setNextWindowPos(showOnRight ? Math.max(0.0f, displayWidth - size.width() - 16.0f) : 16.0f,
                    Math.max(0.0f, (displayHeight - size.height()) * 0.5f), ImGuiCond.FirstUseEver);
            ImGui.setNextWindowSizeConstraints(Math.min(260.0f, size.width()), Math.min(220.0f, size.height()),
                    Math.max(1.0f, displayWidth - 16.0f), Math.max(1.0f, displayHeight - 16.0f));
            if (ImGui.begin("修改音符##rhythmc-note", ImGuiWindowFlags.NoCollapse)) {
                if (fieldsNeedRefresh && !ImGui.isAnyItemActive()) {
                    refreshSelectedNote();
                    syncFields();
                }
                if (operationStatusUntilNanos > 0 && System.nanoTime() >= operationStatusUntilNanos) operationStatus = "";
                float footerHeight = ImGui.getFrameHeightWithSpacing() + ImGui.getStyle().getItemSpacingY() + 4.0f;
                boolean contentVisible = ImGui.beginChild("note-edit-content", -1.0f,
                        Math.max(1.0f, ImGui.getContentRegionAvailY() - footerHeight), true);
                if (contentVisible) renderEditableFields(client);
                ImGui.endChild();
                ImGui.separator();
                float buttonWidth = Math.max(1.0f, (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) * 0.5f);
                if (ImGui.button("删除音符##note-delete", buttonWidth, 0.0f)) {
                    send(client, "rhythmc_note_delete " + note.id);
                    client.setScreen(null);
                }
                ImGui.sameLine();
                if (ImGui.button("关闭##note-close", buttonWidth, 0.0f)) client.setScreen(null);
            }
            ImGui.end();
        } finally {
            ImGuiRuntime.endFrame();
        }
    }

    private void renderEditableFields(MinecraftClient client) {
        boolean group = note.type == 2 && note.holdGroup >= 0;
        boolean canEdit = NoteEditingModel.getInstance().canEdit(chart, note);
        ImGui.text("音符类型");
        ImGui.beginDisabled(group || !canEdit);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.beginCombo("##note-type", group ? NoteKind.HOLD_GROUP.label() : NoteKind.ofType(note.type).label())) {
            for (int type = 0; type <= 3; type++) {
                if (ImGui.selectable(NoteKind.ofType(type).label() + "##note-type-" + type, type == note.type)
                        && type != note.type) send(client, "rhythmc_note_type " + note.id + " " + type);
            }
            ImGui.endCombo();
        }
        ImGui.endDisabled();
        if (group) ImGui.textWrapped("HOLD组 音符类型固定，只能修改头尾或删除整个组。");
        if (!operationStatus.isBlank()) ImGui.textColored(operationStatusColor, operationStatus);
        if (!canEdit) ImGui.textWrapped("HOLD组头尾顺序无效，数值编辑已禁用；仍可删除整个组。");
        ImGui.separator();
        ImGui.beginDisabled(!canEdit);
        EditorTrackLayout layout = EditorTrackLayout.of(chart);
        renderCoordinate(client, "X 相对坐标", "x", relativeX, -layout.halfWidth(), layout.halfWidth(), false);
        renderCoordinate(client, "Y 相对坐标", "y", relativeY, 0.0,
                note.type == 2 ? 0.0 : layout.maximumRelativeY(), note.type == 2);
        ImGui.separator();
        ImGui.text("修改时机：n + a / b beat");
        renderBeatPart(client, "整数 n", beatWhole, 0);
        renderBeatPart(client, "分子 a", beatNumerator, 1);
        renderBeatPart(client, "分母 b（1–1000）", beatDenominator, 2);
        ImGui.separator();
        for (NoteTransformField field : NoteTransformField.values()) {
            ImDouble value = transforms[field.ordinal()];
            double previous = value.get();
            ImGui.text(transformLabel(field));
            ImGui.setNextItemWidth(-1.0f);
            if (ImGui.sliderScalar("##transform-" + field.name(), ImGuiDataType.Double, value,
                    field.minimum(), field.maximum(), field.ordinal() < 3 ? "%.1f" : "%.1f°", ImGuiSliderFlags.AlwaysClamp)) {
                double snapped = Double.isFinite(value.get())
                        ? NoteGeometry.stepped(value.get(), field.minimum(), field.maximum(), field.step()) : previous;
                value.set(snapped);
                if (Math.abs(snapped - previous) > 1.0e-9) send(client, "rhythmc_note_transform " + note.id + " "
                        + field.name().toLowerCase(Locale.ROOT) + " " + Double.toString(snapped));
            }
        }
        ImGui.endDisabled();
        ImGui.textDisabled("滑条支持 Ctrl + 左键输入数值。");
    }

    private void renderCoordinate(MinecraftClient client, String label, String axis, ImDouble value,
                                  double minimum, double maximum, boolean disabled) {
        ImGui.text(label);
        double previous = value.get();
        ImGui.beginDisabled(disabled || maximum <= minimum);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.sliderScalar("##coordinate-" + axis, ImGuiDataType.Double, value, minimum, maximum,
                "%.2f", ImGuiSliderFlags.AlwaysClamp)) {
            double snapped = Double.isFinite(value.get()) ? NoteGeometry.stepped(value.get(), minimum, maximum, 0.05) : previous;
            value.set(snapped);
            if (Math.abs(snapped - previous) > 1.0e-9) send(client, "rhythmc_note_coordinate " + note.id + " "
                    + axis + " " + Double.toString(snapped));
        }
        ImGui.endDisabled();
        if (previous < minimum || previous > maximum || Math.abs(previous / 0.05 - Math.rint(previous / 0.05)) > 1.0e-7) {
            ImGui.textWrapped("历史值：" + Double.toString(value.get()) + "；操作滑条后才进入当前范围及 0.05 步进。");
        }
    }

    private void renderBeatPart(MinecraftClient client, String label, ImInt field, int part) {
        ImGui.text(label);
        EditorBeat previous = beatFields();
        ImGui.setNextItemWidth(Math.max(1.0f, ImGui.getContentRegionAvailX() - 72.0f));
        if (ImGui.inputInt("##beat-input-" + part, field, 0, 0, ImGuiInputTextFlags.CharsDecimal)) {
            EditorBeat changed = beatFields();
            if (allowedBeat(changed)) submitBeat(client, changed);
            else setBeatFields(previous);
        }
        ImGui.sameLine();
        renderBeatStep(client, field, part, -1);
        ImGui.sameLine();
        renderBeatStep(client, field, part, 1);
    }

    private void renderBeatStep(MinecraftClient client, ImInt field, int part, int direction) {
        int previous = field.get();
        long next = (long) previous + direction;
        field.set(next < Integer.MIN_VALUE || next > Integer.MAX_VALUE ? previous : (int) next);
        EditorBeat candidate = beatFields();
        field.set(previous);
        ImGui.beginDisabled(!allowedBeat(candidate) || next < Integer.MIN_VALUE || next > Integer.MAX_VALUE);
        if (ImGui.button((direction < 0 ? "-" : "+") + "##beat-step-" + part + "-" + direction, 28.0f, 0.0f)) {
            setBeatFields(candidate);
            submitBeat(client, candidate);
        }
        ImGui.endDisabled();
    }

    private boolean allowedBeat(EditorBeat beat) {
        return beat.isValid() && HoldGroupRules.canSetBeat(chart, note, beat.value());
    }

    private EditorBeat beatFields() {
        return new EditorBeat(beatWhole.get(), beatNumerator.get(), beatDenominator.get());
    }

    private void setBeatFields(EditorBeat beat) {
        beatWhole.set(beat.whole());
        beatNumerator.set(beat.numerator());
        beatDenominator.set(beat.denominator());
    }

    private void submitBeat(MinecraftClient client, EditorBeat beat) {
        send(client, "rhythmc_note_fraction " + note.id + " " + beat.whole() + " " + beat.numerator() + " " + beat.denominator());
    }

    private static void send(MinecraftClient client, String command) {
        if (client.player != null) client.player.networkHandler.sendChatCommand(command);
    }

    public void showServerStatus(String message, boolean success) {
        operationStatus = message;
        operationStatusColor = success ? 0xFF66FF66 : 0xFF7777FF;
        operationStatusUntilNanos = System.nanoTime() + 2_500_000_000L;
        fieldsNeedRefresh = true;
    }

    private void refreshSelectedNote() {
        ChartManifest refreshed = ClientChartAccess.refreshActiveChartNow();
        if (refreshed == null || refreshed.notes == null) return;
        ChartManifest.Note updated = refreshed.notes.stream().filter(candidate -> candidate != null && note.id.equals(candidate.id))
                .findFirst().orElse(null);
        if (updated != null) {
            chart = refreshed;
            note = updated;
        }
    }

    private static String transformLabel(NoteTransformField field) {
        return switch (field) {
            case SCALE_X -> "缩放 X";
            case SCALE_Y -> "缩放 Y";
            case SCALE_Z -> "缩放 Z";
            case ROTATION_X -> "旋转 X";
            case ROTATION_Y -> "旋转 Y";
            case ROTATION_Z -> "旋转 Z";
        };
    }

    @Override public void removed() { ImGuiRuntime.saveWindowSettings(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return true; }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.isEscape()) MinecraftClient.getInstance().setScreen(null);
        return true;
    }
}
