package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.io.IOException;

final class ChartInfoScreen extends RhythmcScreen {
    private final ChartManifest chart;
    private TextBoxComponent titleBox;
    private TextBoxComponent artistBox;
    private TextBoxComponent charterBox;
    private TextBoxComponent levelBox;

    ChartInfoScreen(ChartManifest chart) {
        this.chart = chart;
    }

    @Override protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        int pageWidth = pageWidth();
        int innerWidth = pageWidth - 38;
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth), Sizing.fixed(pageHeight())), 10, 6, 0xEE0F1721, 0xFF55779E);
        page.child(UIComponents.label(Text.literal("谱面信息")).shadow(true));
        FlowLayout content = spaced(UIContainers.verticalFlow(Sizing.fixed(innerWidth), Sizing.content()), 6);
        addCover(content, innerWidth);
        addInfo(content, innerWidth);
        page.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.expand(), content));
        page.child(UIComponents.button(Text.literal("保存"), button -> save()).horizontalSizing(Sizing.fill()));
        page.child(UIComponents.button(Text.literal("关闭"), button -> close()).horizontalSizing(Sizing.fixed(100)));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    private void addCover(FlowLayout content, int width) {
        FlowLayout section = panel(width);
        ItemStack cover = ClientChartAccess.coverStack(chart.coverItemId);
        section.child(UIComponents.label(Text.literal("曲绘")).shadow(true));
        section.child(UIComponents.item(cover).horizontalSizing(Sizing.fixed(68)).verticalSizing(Sizing.fixed(68)));
        section.child(wrappedLabel(cover.getName().getString(), width - 24));
        section.child(UIComponents.button(Text.literal("修改曲绘"), button -> chooseCover()).horizontalSizing(Sizing.fill()));
        content.child(section);
    }

    private void addInfo(FlowLayout content, int width) {
        FlowLayout section = panel(width);
        section.child(UIComponents.label(Text.literal("曲目名称（可修改）")));
        titleBox = UIComponents.textBox(Sizing.fill(), chart.title);
        section.child(titleBox);
        section.child(UIComponents.label(Text.literal("难度：" + chart.difficulty + "（类型不可修改）").styled(style -> style.withColor(ClientChartAccess.difficultyColor(chart.difficulty)))));
        section.child(UIComponents.label(Text.literal("定数（可输入小数）").styled(style -> style.withColor(ClientChartAccess.difficultyColor(chart.difficulty)))));
        levelBox = UIComponents.textBox(Sizing.fill(), number(chart.level));
        numericOnly(levelBox);
        section.child(levelBox);
        section.child(UIComponents.label(Text.literal("BPM（不可修改）：" + number(chart.bpm))));
        section.child(UIComponents.label(Text.literal("曲师（可修改）")));
        artistBox = UIComponents.textBox(Sizing.fill(), chart.artist);
        section.child(artistBox);
        section.child(UIComponents.label(Text.literal("谱师（可修改）")));
        charterBox = UIComponents.textBox(Sizing.fill(), chart.charter);
        section.child(charterBox);
        section.child(UIComponents.label(Text.literal("最后修改日期：" + (chart.lastEdited == null || chart.lastEdited.isBlank() ? "—" : chart.lastEdited))));
        content.child(section);
    }

    private void chooseCover() {
        if (!syncChart()) return;
        MinecraftClient.getInstance().setScreen(new ItemPickerScreen(this, stack -> {
            chart.coverItemId = Registries.ITEM.getId(stack.getItem()).toString();
            try {
                ClientChartAccess.update(chart);
                ClientChartAccess.setActiveChart(chart);
                ClientChartAccess.status("曲绘已保存");
            } catch (IOException exception) {
                ClientChartAccess.status("保存曲绘失败：" + exception.getMessage());
            }
            MinecraftClient.getInstance().setScreen(new ChartInfoScreen(chart));
        }));
    }

    private void save() {
        try {
            if (!syncChart()) return;
            ClientChartAccess.update(chart);
            ClientChartAccess.setActiveChart(chart);
            ClientChartAccess.status("谱面信息已保存");
            MinecraftClient.getInstance().setScreen(new ChartInfoScreen(chart));
        } catch (IOException exception) {
            ClientChartAccess.status("保存失败：" + exception.getMessage());
        }
    }

    private boolean syncChart() {
        try {
            chart.title = titleBox == null ? chart.title : titleBox.getText().trim();
            chart.artist = artistBox == null ? chart.artist : artistBox.getText().trim();
            chart.charter = charterBox == null ? chart.charter : charterBox.getText().trim();
            chart.level = levelBox == null ? chart.level : Double.parseDouble(levelBox.getText().trim());
            if (!Double.isFinite(chart.level)) throw new NumberFormatException();
            return true;
        } catch (NumberFormatException exception) {
            ClientChartAccess.status("定数必须为有效数字");
            return false;
        }
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
}
