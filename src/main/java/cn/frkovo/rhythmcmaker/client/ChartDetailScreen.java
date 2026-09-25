package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.io.IOException;

final class ChartDetailScreen extends RhythmcScreen {
    private final ChartManifest chart;
    private TextBoxComponent titleBox;
    private TextBoxComponent artistBox;
    private TextBoxComponent charterBox;
    private TextBoxComponent levelBox;

    ChartDetailScreen(ChartManifest chart) {
        this.chart = chart;
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        int pageWidth = pageWidth();
        int pageHeight = pageHeight();
        int innerWidth = pageWidth - 38;
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth), Sizing.fixed(pageHeight)), 10, 6, 0xEE0F1721, 0xFF55779E);
        page.child(UIComponents.label(Text.literal("谱面信息")).shadow(true));
        FlowLayout content = spaced(UIContainers.verticalFlow(Sizing.fixed(innerWidth), Sizing.content()), 6);
        addCover(content, innerWidth);
        addInfo(content, innerWidth);
        page.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.expand(), content));
        int buttonWidth = Math.max(48, (innerWidth - 8) / 3);
        FlowLayout controls = spaced(UIContainers.horizontalFlow(Sizing.fixed(innerWidth), Sizing.content()), 4);
        controls.child(UIComponents.button(Text.literal("保存"), button -> save())
                .horizontalSizing(Sizing.fixed(buttonWidth)).verticalSizing(Sizing.fixed(26)));
        controls.child(UIComponents.button(Text.literal("开始写谱"), button -> startCharting())
                .horizontalSizing(Sizing.fixed(buttonWidth)).verticalSizing(Sizing.fixed(26)));
        controls.child(UIComponents.button(Text.literal("返回"), button -> MinecraftClient.getInstance().setScreen(new ChartListScreen()))
                .horizontalSizing(Sizing.fixed(buttonWidth)).verticalSizing(Sizing.fixed(26)));
        page.child(controls);
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    private void startCharting() {
        try {
            if (!syncChart()) return;
            ClientChartAccess.update(chart);
            ClientChartAccess.setActiveChart(chart);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                ClientChartAccess.status("当前没有可用玩家");
                return;
            }
            client.player.networkHandler.sendChatCommand("rhythmc_enter " + chart.id);
        } catch (IOException | NumberFormatException exception) {
            ClientChartAccess.status("进入制谱器失败：" + exception.getMessage());
        }
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
        section.child(UIComponents.button(Text.literal("删除谱面"), button -> confirmDeleteFirst())
                .horizontalSizing(Sizing.fill()).verticalSizing(Sizing.fixed(28)));
        content.child(section);
    }

    private void confirmDeleteFirst() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) confirmDeleteSecond();
            else client.setScreen(this);
        }, Text.literal("确认删除？"), Text.literal("删除谱面“" + chart.title + "”吗？"), Text.literal("确认"), Text.literal("取消")));
    }

    private void confirmDeleteSecond() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (!confirmed) {
                client.setScreen(this);
                return;
            }
            try {
                ClientChartAccess.delete(chart);
                ClientChartAccess.status("谱面已删除");
                client.setScreen(new ChartListScreen());
            } catch (IOException exception) {
                ClientChartAccess.status("删除谱面失败：" + exception.getMessage());
                client.setScreen(this);
            }
        }, Text.literal("你真的要删除吗？"), Text.literal("此操作不可恢复"), Text.literal("确认"), Text.literal("取消")));
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
            MinecraftClient.getInstance().setScreen(new ChartDetailScreen(chart));
        }));
    }

    private void save() {
        try {
            if (!syncChart()) return;
            ClientChartAccess.update(chart);
            ClientChartAccess.setActiveChart(chart);
            ClientChartAccess.status("谱面信息已保存");
            MinecraftClient.getInstance().setScreen(new ChartDetailScreen(chart));
        } catch (IOException | NumberFormatException exception) {
            ClientChartAccess.status("保存失败：" + exception.getMessage());
        }
    }

    private boolean syncChart() {
        try {
            chart.title = titleBox.getText().trim();
            chart.artist = artistBox.getText().trim();
            chart.charter = charterBox.getText().trim();
            chart.level = Double.parseDouble(levelBox.getText().trim());
            return true;
        } catch (NumberFormatException exception) {
            ClientChartAccess.status("定数必须为有效数字");
            return false;
        }
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }
}
