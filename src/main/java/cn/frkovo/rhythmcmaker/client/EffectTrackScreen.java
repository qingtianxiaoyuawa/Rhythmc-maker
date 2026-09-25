package cn.frkovo.rhythmcmaker.client;

import com.google.gson.JsonObject;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;

final class EffectTrackScreen extends RhythmcScreen {
    private final cn.frkovo.rhythmcmaker.chart.ChartManifest chart;
    private TextBoxComponent beatBox;
    private TextBoxComponent textBox;

    EffectTrackScreen() {
        this.chart = ClientChartAccess.resolveActiveChart();
    }

    @Override protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(pageWidth()), Sizing.fixed(pageHeight())), 10, 7, 0xEE0E1620, 0xFF5B7596);
        page.child(UIComponents.label(Text.literal("特效轨道")).shadow(true));
        if (chart == null) {
            page.child(wrappedLabel("当前没有加载谱面", pageWidth() - 24));
        } else {
            page.child(wrappedLabel("特效会按 Beat 时间在播放时触发；导入的 RhythMC 3.0 effects 会保留。", pageWidth() - 24));
            beatBox = UIComponents.textBox(Sizing.fill(), "0");
            numericOnly(beatBox);
            page.child(UIComponents.label(Text.literal("事件 Beat")));
            page.child(beatBox);
            textBox = UIComponents.textBox(Sizing.fill(), "Ready");
            page.child(UIComponents.label(Text.literal("标题/提示文字（标题事件使用）")));
            page.child(textBox);
            page.child(UIComponents.button(Text.literal("添加标题特效"), button -> addEffect("TITLE"))
                    .horizontalSizing(Sizing.fill()));
            page.child(UIComponents.button(Text.literal("添加粒子闪光"), button -> addEffect("FLASH"))
                    .horizontalSizing(Sizing.fill()));
            FlowLayout events = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
            events.gap(3);
            if (chart.effects == null || chart.effects.isEmpty()) {
                events.child(wrappedLabel("暂无特效事件", pageWidth() - 24));
            } else {
                for (int index = 0; index < chart.effects.size(); index++) {
                    JsonObject effect = chart.effects.get(index);
                    String type = effect.has("type") ? effect.get("type").getAsString() : "PARTICLE";
                    String beat = effect.has("beat") ? effect.get("beat").getAsString() : "?";
                    int eventIndex = index;
                    FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
                    row.gap(5);
                    row.child(wrappedLabel(type + " @ Beat " + beat, Math.max(140, pageWidth() - 150)));
                    row.child(UIComponents.button(Text.literal("删除"), button -> removeEffect(eventIndex))
                            .horizontalSizing(Sizing.fixed(70)));
                    events.child(row);
                }
            }
            page.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.expand(), events));
        }
        page.child(UIComponents.button(Text.literal("返回"), button -> close()).horizontalSizing(Sizing.fixed(100)));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    private void addEffect(String type) {
        if (chart == null) return;
        try {
            double beat = Double.parseDouble(beatBox.getText().trim());
            if (!Double.isFinite(beat) || beat < 0.0) throw new NumberFormatException();
            JsonObject effect = new JsonObject();
            effect.addProperty("type", type);
            effect.addProperty("beat", beat);
            effect.addProperty("track", "effects-main");
            if (type.equals("TITLE")) effect.addProperty("text", textBox.getText().trim());
            chart.effects.add(effect);
            ClientChartAccess.update(chart);
            ClientChartAccess.status("特效事件已保存");
            MinecraftClient.getInstance().setScreen(new EffectTrackScreen());
        } catch (NumberFormatException exception) {
            ClientChartAccess.status("Beat 必须为不小于 0 的数字");
        } catch (IOException exception) {
            ClientChartAccess.status("特效保存失败：" + exception.getMessage());
        }
    }

    private void removeEffect(int index) {
        if (chart == null || chart.effects == null || index < 0 || index >= chart.effects.size()) return;
        chart.effects.remove(index);
        try {
            ClientChartAccess.update(chart);
            ClientChartAccess.status("特效事件已删除");
            MinecraftClient.getInstance().setScreen(new EffectTrackScreen());
        } catch (IOException exception) {
            ClientChartAccess.status("特效删除失败：" + exception.getMessage());
        }
    }
}
