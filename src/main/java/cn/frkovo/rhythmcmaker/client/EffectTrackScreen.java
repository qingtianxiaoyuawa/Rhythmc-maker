package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.client.render.EffectEditorImGuiView;
import cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** ImGui-backed effect editor screen. The world remains visible behind the editor. */
public final class EffectTrackScreen extends Screen {
    private final ChartManifest chart;
    private final EffectEditorImGuiView editor;
    private boolean firstFrameLogged;
    private boolean previewInitialized;
    private boolean closeConfirmationPending;
    private boolean disposed;

    EffectTrackScreen() {
        this(ClientChartAccess.resolveActiveChart());
    }

    private EffectTrackScreen(ChartManifest chart) {
        super(Text.literal("RhythMC 特效编辑器"));
        this.chart = chart;
        this.editor = new EffectEditorImGuiView(chart, this::save, this::closeEditor,
                RhythmcMakerClient::toggleEffectEditorPlayback,
                RhythmcMakerClient::replayEffectEditorPlayback,
                RhythmcMakerClient::seekEffectEditorPreview, ClientChartAccess::status);
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
    @Override public boolean charTyped(CharInput input) { return true; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    private void save() {
        if (chart == null) return;
        try {
            editor.saveToChart();
            ClientChartAccess.update(chart);
            ClientChartAccess.setActiveChart(chart);
            editor.markSaved("已保存特效轨道");
        } catch (java.io.IOException exception) {
            editor.markSaveFailed("保存失败：" + exception.getMessage());
        }
    }

    private void closeEditor() {
        if (!editor.hasUnsavedChanges()) {
            closeConfirmed();
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        closeConfirmationPending = true;
        client.setScreen(new net.minecraft.client.gui.screen.ConfirmScreen(confirmed -> {
            if (confirmed) {
                editor.discardChanges();
                closeConfirmed();
            } else {
                closeConfirmationPending = false;
                client.setScreen(this);
            }
        }, Text.literal("特效编辑器"), Text.literal("存在未保存的修改，仍要关闭吗？"), Text.literal("关闭并丢弃"), Text.literal("继续编辑")));
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
