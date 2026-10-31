package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class ImportProgressScreen extends Screen {
    private final State state;
    private ButtonWidget backButton;
    private long successVisibleAtNanos;

    ImportProgressScreen(State state) {
        super(Text.literal("导入谱面"));
        this.state = state;
    }

    @Override
    protected void init() {
        backButton = ButtonWidget.builder(Text.literal("返回谱面库"), button -> client.setScreen(new ChartListScreen()))
                .dimensions(width / 2 - 60, height / 2 + 52, 120, 20)
                .build();
        backButton.active = false;
        addDrawableChild(backButton);
    }

    @Override
    public void tick() {
        if (state.awaitingSceneImport && state.chart != null) {
            if (ClientChartAccess.isChartImporting(state.chart.id)) {
                state.update(88, "正在写入导入场景...");
                return;
            }
            state.awaitingSceneImport = false;
            state.success(state.chart);
        }
        if (!state.finished) return;
        backButton.active = state.failure != null;
        if (state.failure != null) return;
        if (successVisibleAtNanos == 0L) successVisibleAtNanos = System.nanoTime();
        if (System.nanoTime() - successVisibleAtNanos >= 900_000_000L && state.chart != null) {
            ClientChartAccess.status("导入成功");
            client.setScreen(new ChartDetailScreen(state.chart));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        int centerY = height / 2;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("导入谱面"), centerX, centerY - 58, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(state.message), centerX, centerY - 35, 0xFFE2E8F0);
        int barWidth = Math.min(420, Math.max(220, width - 48));
        int barHeight = 12;
        int left = centerX - barWidth / 2;
        int top = centerY - 8;
        context.fill(left, top, left + barWidth, top + barHeight, 0xFF26384D);
        int filled = Math.max(0, Math.min(barWidth, barWidth * state.percent / 100));
        if (filled > 0) context.fill(left, top, left + filled, top + barHeight, state.failure == null ? 0xFF4D9EFF : 0xFFE05A67);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(state.percent + "%"), centerX, top + 20, 0xFFFFFFFF);
        if (state.failure != null) {
            String message = state.failure.getMessage() == null || state.failure.getMessage().isBlank() ? "未知错误" : state.failure.getMessage();
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("导入失败：" + message), centerX, centerY + 34, 0xFFFF6B76);
        } else if (state.finished) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("导入成功，正在打开开始写谱菜单"), centerX, centerY + 34, 0xFF69D98A);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return state.failure != null;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    static final class State {
        volatile int percent;
        volatile String message = "准备导入...";
        volatile boolean finished;
        volatile Throwable failure;
        volatile ChartManifest chart;
        volatile boolean awaitingSceneImport;

        void update(int percent, String message) {
            this.percent = Math.max(0, Math.min(99, percent));
            this.message = message;
        }

        void awaitSceneImport(ChartManifest chart) {
            this.chart = chart;
            this.awaitingSceneImport = true;
            this.message = "等待场景导入完成...";
        }

        void success(ChartManifest chart) {
            this.chart = chart;
            this.percent = 100;
            this.message = "导入完成";
            this.finished = true;
        }

        void fail(Throwable failure) {
            this.failure = failure;
            this.message = "导入失败";
            this.finished = true;
        }
    }
}