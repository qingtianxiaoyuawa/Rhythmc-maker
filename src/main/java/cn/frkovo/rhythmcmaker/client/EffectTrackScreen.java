package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.client.render.EffectEditorImGuiView;
import cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * ImGui-backed effect editor screen. The world remains visible behind the editor.
 */
public final class EffectTrackScreen extends Screen {
    private final ChartManifest chart;
    private final EffectEditorImGuiView editor;
    private boolean firstFrameLogged;
    private boolean previewInitialized;
    private boolean closeConfirmationPending;
    private boolean disposed;
    private java.util.concurrent.CompletableFuture<Boolean> layoutRequest;
    private java.util.concurrent.CompletableFuture<Boolean> layoutPoll;
    private int requestedDivisions;
    private java.util.concurrent.CompletableFuture<Void> saveRequest;

    EffectTrackScreen() {
        this(ClientChartAccess.resolveActiveChart());
    }

    private EffectTrackScreen(ChartManifest chart) {
        super(Text.literal("RhythMC 特效编辑器"));
        this.chart = chart;
        this.editor = new EffectEditorImGuiView(chart, this::save, this::closeEditor,
                RhythmcMakerClient::toggleEffectEditorPlayback,
                RhythmcMakerClient::replayEffectEditorPlayback,
                RhythmcMakerClient::seekEffectEditorPreview, ClientChartAccess::status, this::requestDivisions,
                RhythmcMakerClient::setPlaybackPitchPercent);
    }

    @Override
    protected void init() {
        super.init();
        if (!previewInitialized) {
            RhythmcMakerClient.seekEffectEditorPreview(editor.playheadBeat());
            RhythmcMakerClient.prepareEffectEditorPreview();
            previewInitialized = true;
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ImGuiRuntime.beginFrame(client)) return;
        try {
            firstFrameLogged = true;
            editor.render(client);
        } finally {
            ImGuiRuntime.endFrame();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void blur() {
    }

    @Override
    protected void applyBlur(DrawContext drawContext) {
    }

    @Override
    public void removed() {
        if (!closeConfirmationPending) {
            RhythmcMakerClient.stopEffectEditorPreview();
            disposeEditor();
        }
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // ImGui consumes the wheel in the active preview window during its own
        // frame. Keep Minecraft's callback consumed so the event cannot also
        // reach an unrelated screen control.
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (editor.isInputLocked()) return true;
        if (input.isEscape()) {
            closeEditor();
            return true;
        }
        editor.handlePlaybackKey(input);
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        editor.handlePlaybackKeyRelease(input);
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void save() {
        if (chart == null || editor.isInputLocked() || !editor.validateParameters()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getServer() == null || client.player == null) return;
        editor.saveToChart();
        java.util.List<com.google.gson.JsonObject> effects = new java.util.ArrayList<>();
        for (com.google.gson.JsonObject effect : chart.effects) effects.add(effect.deepCopy());
        cn.frkovo.rhythmcmaker.common.effect.editor.EffectEditorLayout layout = chart.effectEditorLayout;
        editor.setInputLocked(true, "正在保存特效轨道……");
        saveRequest = new java.util.concurrent.CompletableFuture<>();
        java.util.UUID playerId = client.player.getUuid();
        net.minecraft.server.MinecraftServer server = client.getServer();
        server.execute(() -> {
            try {
                net.minecraft.server.network.ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) throw new java.io.IOException("玩家已离开制谱器");
                cn.frkovo.rhythmcmaker.RhythmcMaker.saveEffectEditor(server, player, effects, layout);
                saveRequest.complete(null);
            } catch (java.io.IOException exception) {
                saveRequest.completeExceptionally(exception);
            }
        });
    }

    private void closeEditor() {
        if (editor.isInputLocked()) return;
        if (!editor.hasUnsavedChanges()) {
            closeConfirmed();
            return;
        }
        editor.requestClose(this::closeConfirmed);
    }

    public void presentPreview(MinecraftClient client) {
        editor.presentPreview(client);
    }

    private void requestDivisions(int divisions) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (chart == null || client.player == null || client.getServer() == null || editor.isInputLocked()) return;
        requestedDivisions = divisions;
        editor.setInputLocked(true, "正在同步每 Chunk 分数，请等待……");
        RhythmcMakerClient.stopEffectEditorPreview();
        java.util.UUID playerId = client.player.getUuid();
        net.minecraft.server.MinecraftServer server = client.getServer();
        layoutRequest = new java.util.concurrent.CompletableFuture<>();
        server.execute(() -> {
            net.minecraft.server.network.ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            try {
                layoutRequest.complete(player != null && cn.frkovo.rhythmcmaker.RhythmcMaker.updateEffectEditorDivisions(player, divisions));
            } catch (java.io.IOException | com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                layoutRequest.complete(false);
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (saveRequest != null && saveRequest.isDone()) {
            boolean saved = !saveRequest.isCompletedExceptionally();
            editor.setInputLocked(false, saved ? "已保存特效轨道" : "保存失败，请重试");
            if (saved) editor.markSaved("已保存特效轨道");
            else editor.markSaveFailed("保存失败，请重试");
            saveRequest = null;
        }
        if (!editor.isInputLocked() || layoutRequest == null || !layoutRequest.isDone()) return;
        if (!layoutRequest.join()) {
            finishLayout(false);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getServer() == null || client.player == null) {
            finishLayout(false);
            return;
        }
        if (layoutPoll == null) {
            java.util.UUID playerId = client.player.getUuid();
            layoutPoll = new java.util.concurrent.CompletableFuture<>();
            client.getServer().execute(() -> layoutPoll.complete(cn.frkovo.rhythmcmaker.RhythmcMaker.isEditorTrackAdjustmentPending(playerId)));
        } else if (layoutPoll.isDone()) {
            if (!layoutPoll.join()) finishLayout(true);
            else layoutPoll = null;
        }
    }

    private void finishLayout(boolean successful) {
        if (successful) {
            cn.frkovo.rhythmcmaker.chart.EditorTrackLayout layout = cn.frkovo.rhythmcmaker.chart.EditorTrackLayout.of(chart.laneCount, requestedDivisions);
            layout.applyTo(chart);
            if (chart.notes != null) {
                cn.frkovo.rhythmcmaker.chart.ChartTiming.Prepared timing = cn.frkovo.rhythmcmaker.chart.ChartTiming.prepare(chart);
                for (ChartManifest.Note note : chart.notes) {
                    note.time = timing.beatToSeconds(note.beat);
                    note.z = cn.frkovo.rhythmcmaker.chart.PlaybackCoordinates.editorWorldZAtBeat(chart, note.beat);
                }
            }
            chart.trackLength = Math.max(8, Math.max(1, chart.chunkCount) * chart.divisionsPerChunk);
            ClientChartAccess.applyLocalLayout(layout);
        }
        editor.setInputLocked(false, successful ? "同步完成" : "同步失败，已恢复旧值");
        layoutRequest = null;
        layoutPoll = null;
        RhythmcMakerClient.prepareEffectEditorPreview();
    }

    private void closeConfirmed() {
        closeConfirmationPending = false;
        RhythmcMakerClient.stopEffectEditorPreview();
        disposeEditor();
        MinecraftClient.getInstance().setScreen(null);
    }

    private void disposeEditor() {
        if (disposed) return;
        disposed = true;
        editor.dispose();
    }
}
