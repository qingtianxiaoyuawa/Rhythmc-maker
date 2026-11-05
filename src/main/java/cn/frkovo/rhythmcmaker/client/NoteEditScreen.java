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
    private static final float WINDOW_WIDTH = 420.0f;
    private final ChartManifest chart;
    private final ChartManifest.Note note;
    private final boolean showOnRight;
    private boolean firstFrame = true;
    private boolean beatPartsInitialized;
    private String operationStatus = "";
    private int operationStatusColor = 0xFFFFFFFF;
    private long operationStatusUntilNanos;
    private int pendingHoldGroup = -1;
    private boolean holdOperationPending;
    private final imgui.type.ImInt beatWhole = new imgui.type.ImInt();
    private final imgui.type.ImInt beatNumerator = new imgui.type.ImInt();
    private final imgui.type.ImInt beatDenominator = new imgui.type.ImInt(1);

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
            float windowWidth = Math.max(1.0f, Math.min(WINDOW_WIDTH, ImGui.getIO().getDisplaySizeX() / 3.0f));
            float windowX = showOnRight ? ImGui.getIO().getDisplaySizeX() - windowWidth - 16.0f : 16.0f;
            if (firstFrame) {
                ImGui.setNextWindowPos(windowX, ImGui.getIO().getDisplaySizeY() * 0.25f, ImGuiCond.Always);
                ImGui.setNextWindowSize(windowWidth, 260.0f, ImGuiCond.Always);
                firstFrame = false;
            }
            ImGui.begin("修改音符##rhythmc-note", ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse);
            if (operationStatusUntilNanos > 0L && System.nanoTime() >= operationStatusUntilNanos) operationStatus = "";
            if (note.holdGroup >= 0) {
                ImGui.text("音符类型");
                ImGui.beginDisabled();
                ImGui.button(noteTypeName(note.type) + "##note-type-locked");
                ImGui.endDisabled();
            } else {
            ImGui.text("音符类型");
            if (ImGui.beginCombo("##note-type", noteTypeName(note.type))) {
                int[] availableTypes = note.type == 2 ? new int[]{2} : new int[]{0, 1, 3};
                for (int availableType : availableTypes) {
                    if (ImGui.selectable(noteTypeName(availableType) + "##note-type-" + availableType, note.type == availableType)) {
                        updateType(client, availableType);
                    }
                }
                ImGui.endCombo();
            }
            }
            if (!operationStatus.isBlank()) ImGui.textColored(operationStatusColor, operationStatus);
            if (note.holdGroup >= 0) {
                ImGui.text("该音符已粘合，位置、时机和类型均已锁定");
                ImGui.text("请先解除粘合后再编辑");
                ImGui.beginDisabled(holdOperationPending);
                if (ImGui.button("解除粘合")) {
                    holdOperationPending = true;
                    pendingHoldGroup = note.holdGroup;
                    operationStatus = "正在解除粘合…";
                    operationStatusColor = 0xFFFFFF66;
                    operationStatusUntilNanos = System.nanoTime() + 5_000_000_000L;
                    client.player.networkHandler.sendChatCommand("rhythmc_note_unglue " + note.id);
                }
                ImGui.endDisabled();
                if (ImGui.button("关闭")) client.setScreen(null);
            } else {
            ImGui.text("相对位置 X");
            ImDouble x = new ImDouble(note.x);
            if (ImGui.inputDouble("##note-x", x, 0.0, 0.0, "%.3f")) update(client, x.get(), note.y, note.beat);
            ImGui.text("相对位置 Y");
            ImDouble y = new ImDouble(note.y - noteHeightBase(note.type));
            if (ImGui.inputDouble("##note-y", y, 0.0, 0.0, "%.3f")) update(client, note.x, y.get() + noteHeightBase(note.type), note.beat);
            ImGui.text("时机 Beat");
            if (!beatPartsInitialized) {
                BeatParts parts = beatParts(note.beat);
                beatWhole.set(parts.whole());
                beatNumerator.set(parts.numerator());
                beatDenominator.set(parts.denominator());
                beatPartsInitialized = true;
            }
            ImGui.setNextItemWidth(86.0f);
            boolean beatUpdated = ImGui.inputInt("n##note-beat", beatWhole, 1, 1);
            ImGui.sameLine();
            ImGui.text("+");
            ImGui.sameLine();
            ImGui.setNextItemWidth(86.0f);
            beatUpdated |= ImGui.inputInt("a##note-beat", beatNumerator, 1, 1);
            ImGui.sameLine();
            ImGui.text("/");
            ImGui.sameLine();
            ImGui.setNextItemWidth(86.0f);
            beatUpdated |= ImGui.inputInt("b##note-beat", beatDenominator, 1, 1);
            ImGui.sameLine();
            ImGui.text("beat");
            if (beatDenominator.get() < 1 || beatDenominator.get() > 32
                    || beatNumerator.get() < 0 || beatNumerator.get() >= beatDenominator.get()
                    || beatWhole.get() < 0 || beatWhole.get() > 1_000_000) {
                ImGui.textColored(0xFF7777FF, "Beat 需满足 n≥0、1≤b≤32、0≤a<b。");
            } else if (beatUpdated) {
                update(client, note.x, note.y, beatWhole.get() + beatNumerator.get() / (double) beatDenominator.get());
            }
            ImGui.beginDisabled(holdOperationPending);
            boolean glueRequested = note.type == 2 && ImGui.button("粘合相邻钻石块");
            ImGui.endDisabled();
            if (glueRequested) {
                java.util.List<ChartManifest.Note> connected = HoldNoteGroupMerger.connectedNotes(chart.notes, note);
                if (connected.size() > 1) {
                    int group = connected.stream().mapToInt(member -> member.holdGroup).distinct().count() == 1
                            && connected.getFirst().holdGroup >= 0
                            ? connected.getFirst().holdGroup : HoldNoteGroupMerger.nextGroupId(chart.notes);
                    holdOperationPending = true;
                    pendingHoldGroup = group;
                    operationStatus = "正在粘合 " + connected.size() + " 个钻石音符…";
                    operationStatusColor = 0xFFFFFF66;
                    operationStatusUntilNanos = System.nanoTime() + 5_000_000_000L;
                    client.player.networkHandler.sendChatCommand("rhythmc_note_glue " + note.id);
                } else {
                    operationStatus = "未找到同轨道且距离≤1的钻石音符";
                    operationStatusColor = 0xFF7777FF;
                    operationStatusUntilNanos = System.nanoTime() + 2_500_000_000L;
                }
            }
            if (note.type == 2) {
                int connectableCount = Math.max(0, HoldNoteGroupMerger.connectedNotes(chart.notes, note).size() - 1);
                ImGui.text(connectableCount == 0 ? "当前没有可粘合的钻石音符" : "当前可粘合：" + connectableCount + " 个钻石音符");
            }
            if (ImGui.button("关闭")) client.setScreen(null);
            }
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

    private void updateType(MinecraftClient client, int type) {
        if (type == note.type || client.player == null) return;
        double relativeY = note.y - noteHeightBase(note.type);
        note.type = type;
        note.y = relativeY + noteHeightBase(type);
        if (type != 2) note.holdGroup = -1;
        operationStatus = "正在更新音符类型…";
        operationStatusColor = 0xFFFFFF66;
        operationStatusUntilNanos = System.nanoTime() + 5_000_000_000L;
        client.player.networkHandler.sendChatCommand("rhythmc_note_type " + note.id + " " + type);
    }

    public void showServerStatus(String message, boolean success) {
        operationStatus = message;
        if (message.startsWith("已粘合 ") && success) {
            java.util.List<ChartManifest.Note> connected = HoldNoteGroupMerger.connectedNotes(chart.notes, note);
            int group = pendingHoldGroup >= 0 ? pendingHoldGroup : HoldNoteGroupMerger.nextGroupId(chart.notes);
            for (ChartManifest.Note member : connected) member.holdGroup = group;
            note.holdGroup = group;
            holdOperationPending = false;
            pendingHoldGroup = -1;
        } else if (message.startsWith("已解除粘合") && success) {
            for (ChartManifest.Note member : chart.notes) {
                if (member != null && member.trackId == note.trackId && member.holdGroup == pendingHoldGroup) member.holdGroup = -1;
            }
            note.holdGroup = -1;
            holdOperationPending = false;
            pendingHoldGroup = -1;
        } else if (message.startsWith("粘合失败")) {
            holdOperationPending = false;
            pendingHoldGroup = -1;
        }
        operationStatusColor = success ? 0xFF66FF66 : 0xFF7777FF;
        operationStatusUntilNanos = System.nanoTime() + 2_500_000_000L;
    }

    private static String noteTypeName(int type) {
        return switch (type) {
            case 1 -> "侦测器音符";
            case 2 -> "Hold 钻石音符";
            case 3 -> "红石块音符";
            default -> "TNT 音符";
        };
    }

    private static double noteHeightBase(int type) {
        return type == 2 ? 65.0 : 66.0;
    }

    private static BeatParts beatParts(double value) {
        double safeValue = Math.max(0.0, Double.isFinite(value) ? value : 0.0);
        int whole = (int) Math.floor(safeValue);
        double fraction = safeValue - whole;
        int bestNumerator = 0;
        int bestDenominator = 1;
        double bestError = Double.POSITIVE_INFINITY;
        for (int denominator = 1; denominator <= 32; denominator++) {
            int numerator = (int) Math.round(fraction * denominator);
            double error = Math.abs(fraction - numerator / (double) denominator);
            if (error < bestError) {
                bestError = error;
                bestNumerator = numerator;
                bestDenominator = denominator;
            }
        }
        if (bestNumerator >= bestDenominator) return new BeatParts(whole + 1, 0, 1);
        int divisor = greatestCommonDivisor(bestNumerator, bestDenominator);
        return new BeatParts(whole, bestNumerator / divisor, bestDenominator / divisor);
    }

    private static int greatestCommonDivisor(int first, int second) {
        int left = Math.abs(first);
        int right = Math.abs(second);
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return Math.max(1, left);
    }

    private record BeatParts(int whole, int numerator, int denominator) {}
}
