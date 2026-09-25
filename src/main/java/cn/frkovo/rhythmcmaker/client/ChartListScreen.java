package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.List;

final class ChartListScreen extends RhythmcScreen {
    private final List<ChartManifest> charts;
    private final String loadError;

    ChartListScreen() {
        List<ChartManifest> loadedCharts = List.of();
        String error = "";
        try {
            loadedCharts = ClientChartAccess.charts();
        } catch (IOException exception) {
            error = exception.getMessage();
        }
        this.charts = loadedCharts;
        this.loadError = error;
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        int pageWidth = pageWidth();
        int pageHeight = pageHeight();
        int listWidth = Math.max(200, Math.min(280, pageWidth * 40 / 100));
        int infoWidth = pageWidth - listWidth - 34;
        FlowLayout page = layout(UIContainers.horizontalFlow(Sizing.fixed(pageWidth), Sizing.fixed(pageHeight)), 10, 8, 0xEE0E1620, 0xFF5B7596);
        page.child(buildListPanel(listWidth));
        page.child(buildWelcomePanel(infoWidth));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    private FlowLayout buildListPanel(int width) {
        FlowLayout panel = layout(UIContainers.verticalFlow(Sizing.fixed(width), Sizing.fill()), 8, 6, 0xDD182332, 0xFF4A607C);
        panel.child(UIComponents.label(Text.literal("谱面列表")).shadow(true));
        if (!loadError.isBlank()) panel.child(wrappedLabel("读取失败：" + loadError, width - 20));
        FlowLayout entries = spaced(UIContainers.verticalFlow(Sizing.fixed(width - 22), Sizing.content()), 4);
        if (charts.isEmpty()) {
            entries.child(wrappedLabel("暂无谱面，点击下方按钮创建。", width - 22));
        } else {
            for (ChartManifest chart : charts) {
                String text = chart.title + "  [" + ClientChartAccess.difficultyText(chart) + "]";
                entries.child(UIComponents.button(Text.literal(text).styled(style -> style.withColor(ClientChartAccess.difficultyColor(chart.difficulty))),
                        button -> MinecraftClient.getInstance().setScreen(new ChartDetailScreen(chart)))
                        .horizontalSizing(Sizing.fixed(width - 22)).verticalSizing(Sizing.fixed(28)));
            }
        }
        panel.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.expand(), entries));
        panel.child(UIComponents.button(Text.literal("创建新谱面"), button -> MinecraftClient.getInstance().setScreen(new CreateChartScreen()))
                .horizontalSizing(Sizing.fill()).verticalSizing(Sizing.fixed(32)));
        return panel;
    }

    private FlowLayout buildWelcomePanel(int width) {
        FlowLayout panel = layout(UIContainers.verticalFlow(Sizing.fixed(width), Sizing.fill()), 8, 7, 0xDD182332, 0xFF4A607C);
        panel.child(UIComponents.label(Text.literal("谱面信息")).shadow(true));
        panel.child(wrappedLabel("欢迎使用Rhythmc Maker", width - 20));
        panel.child(UIComponents.button(Text.literal("导入谱面"), button -> MinecraftClient.getInstance().setScreen(new ImportChartScreen()))
                .horizontalSizing(Sizing.fill()).verticalSizing(Sizing.fixed(32)));
        return panel;
    }
}
