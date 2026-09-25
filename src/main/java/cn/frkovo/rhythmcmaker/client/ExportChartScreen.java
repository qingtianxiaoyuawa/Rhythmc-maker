package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Path;

/** Editor-side export form for the active chart. */
final class ExportChartScreen extends RhythmcScreen {
    private final ChartManifest chart;

    ExportChartScreen() {
        this.chart = ClientChartAccess.resolveActiveChart();
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        int pageWidth = pageWidth();
        int innerWidth = pageWidth - 38;
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth), Sizing.content()), 10, 6, 0xEE101821, 0xFF6689B3);
        page.child(UIComponents.label(Text.literal("导出 RhythMC 3.0 谱面")).shadow(true));
        if (chart == null) {
            page.child(UIComponents.label(Text.literal("当前没有可导出的谱面").formatted(Formatting.RED)));
            page.child(UIComponents.button(Text.literal("关闭"), button -> close()).horizontalSizing(Sizing.fixed(100)));
            root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
            return;
        }

        FlowLayout content = spaced(UIContainers.verticalFlow(Sizing.fixed(innerWidth), Sizing.content()), 5);
        addConfirmationFields(content, innerWidth);
        page.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.fixed(Math.max(150, pageHeight() - 100)), content));
        page.child(UIComponents.button(Text.literal("导出"), button -> confirmExport()).horizontalSizing(Sizing.fill()).verticalSizing(Sizing.fixed(26)));
        page.child(UIComponents.button(Text.literal("返回"), button -> close()).horizontalSizing(Sizing.fixed(100)));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    private void addConfirmationFields(FlowLayout content, int width) {
        content.child(UIComponents.label(Text.literal("以下谱面信息将用于导出确认：").styled(style -> style.withColor(0xFFE6F2FF))));
        content.child(UIComponents.label(Text.literal("曲名：" + chart.title)));
        content.child(UIComponents.label(Text.literal("曲师：" + chart.artist)));
        content.child(UIComponents.label(Text.literal("谱师：" + chart.charter)));
        content.child(UIComponents.label(Text.literal("难度：" + chart.difficulty + "  定数：" + number(chart.level))));
        content.child(UIComponents.label(Text.literal("BPM：" + number(chart.bpm))));
        content.child(UIComponents.label(Text.literal("偏移值：" + chart.offsetMillis + " ms")));
        content.child(UIComponents.label(Text.literal("曲绘 / 3.0 icon：" + ClientChartAccess.itemName(chart.coverItemId))));
        ItemStack cover = ClientChartAccess.coverStack(chart.coverItemId);
        content.child(UIComponents.item(cover).horizontalSizing(Sizing.fixed(48)).verticalSizing(Sizing.fixed(48)));
    }

    private void confirmExport() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                client.setScreen(this);
                runExport();
            } else {
                client.setScreen(this);
            }
        }, Text.literal("确认导出？"), Text.literal("将覆盖此谱面之前导出的 RhythMC 3.0 文件。"), Text.literal("确认"), Text.literal("取消")));
    }

    private void runExport() {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            ClientChartAccess.setActiveChart(chart);
            if (client.getServer() == null) throw new IOException("当前版本仅支持单人制谱器世界");
            client.getServer().execute(() -> {
                try {
                    Path folder = ClientChartAccess.exportRhythmc3(chart);
                    client.execute(() -> exportSucceeded(folder));
                } catch (IOException | RuntimeException exception) {
                    client.execute(() -> ClientChartAccess.status("导出失败：" + exception.getMessage()));
                }
            });
        } catch (IOException exception) {
            ClientChartAccess.status("保存导出信息失败：" + exception.getMessage());
        }
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }

    private void exportSucceeded(Path folder) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        Text open = Text.literal("[点击打开文件夹]").setStyle(Style.EMPTY
            .withColor(Formatting.AQUA)
            .withUnderline(true)
            .withClickEvent(new ClickEvent.OpenFile(folder.toString())));
        client.player.sendMessage(Text.literal("RhythMC 3.0 导出成功：").formatted(Formatting.GREEN).append(open), false);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }
}
