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
import java.util.Locale;

final class EffectTrackScreen extends RhythmcScreen {
    private final cn.frkovo.rhythmcmaker.chart.ChartManifest chart;
    private int selectedIndex;
    private TextBoxComponent beatField;
    private TextBoxComponent durationField;
    private TextBoxComponent textField;
    private TextBoxComponent colorField;
    private TextBoxComponent countField;
    private double playheadBeat;

    EffectTrackScreen() {
        this(ClientChartAccess.resolveActiveChart(), 0);
    }

    private EffectTrackScreen(cn.frkovo.rhythmcmaker.chart.ChartManifest chart, int selectedIndex) {
        this.chart = chart;
        this.selectedIndex = chart == null || chart.effects == null || chart.effects.isEmpty() ? -1 : Math.max(0, Math.min(selectedIndex, chart.effects.size() - 1));
        this.playheadBeat = selectedEffectBeat(chart, this.selectedIndex);
    }

    @Override protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        int workspaceWidth = pageWidth();
        FlowLayout page = layout(UIContainers.verticalFlow(Sizing.fixed(workspaceWidth), Sizing.fixed(pageHeight())), 8, 6, 0xF20E1620, 0xFF30465D);
        FlowLayout header = spaced(UIContainers.horizontalFlow(Sizing.fill(), Sizing.content()), 8);
        header.child(UIComponents.label(Text.literal("RhythMC MAKER")).shadow(true));
        header.child(UIComponents.label(Text.literal("特效编辑器")));
        if (chart == null) {
            page.child(header);
            page.child(wrappedLabel("请先从谱面列表加载一个谱面，再打开特效编辑器。", workspaceWidth - 28));
            page.child(UIComponents.button(Text.literal("返回"), button -> close()).horizontalSizing(Sizing.fixed(120)));
            root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
            return;
        }
        header.child(UIComponents.label(Text.literal((chart.title == null || chart.title.isBlank() ? "未命名谱面" : chart.title) + "  ·  BPM " + format(chart.bpm))));
        header.child(UIComponents.label(Text.literal("Beat " + format(playheadBeat))));
        header.child(UIComponents.button(Text.literal("撤销"), button -> ClientChartAccess.status("撤销功能沿用现有编辑器历史记录")));
        header.child(UIComponents.button(Text.literal("保存"), button -> save()));
        header.child(UIComponents.button(Text.literal("播放"), button -> ClientChartAccess.status("播放预览由现有播放控制接管")));
        header.child(UIComponents.button(Text.literal("关闭"), button -> close()));
        page.child(header);

        FlowLayout content = UIContainers.horizontalFlow(Sizing.fill(), Sizing.expand());
        int libraryWidth = Math.max(170, Math.min(220, workspaceWidth / 5));
        int inspectorWidth = Math.max(230, Math.min(290, workspaceWidth / 4));
        content.child(buildLibrary(libraryWidth));
        content.child(buildPreview(Math.max(260, workspaceWidth - libraryWidth - inspectorWidth - 18)));
        content.child(buildInspector(inspectorWidth));
        page.child(content);
        page.child(buildTimeline(workspaceWidth - 16));
        root.child(centered(UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill())).child(page));
    }

    private FlowLayout buildPreview(int width) {
        FlowLayout panel = panel(width);
        panel.child(UIComponents.label(Text.literal("场景预览")).shadow(true));
        panel.child(wrappedLabel("Minecraft 场景与当前特效效果将在此处预览。预览沿用现有 framebuffer 生命周期。", width - 20));
        panel.child(UIComponents.label(Text.literal("选择工具     移动     旋转     缩放")));
        panel.child(UIComponents.label(Text.literal("当前播放头：Beat " + format(playheadBeat))));
        panel.child(wrappedLabel("选中事件后，右侧参数会直接写回现有特效对象。", width - 20));
        return panel;
    }

    private FlowLayout buildLibrary(int width) {
        FlowLayout panel = panel(width);
        panel.child(UIComponents.label(Text.literal("特效库")).shadow(true));
        panel.child(wrappedLabel("选择模板后会在当前播放位置附近插入事件。", width - 20));
        addTemplate(panel, "标题", "TITLE");
        addTemplate(panel, "动作栏消息", "ACTIONBAR");
        addTemplate(panel, "粒子", "PARTICLE");
        addTemplate(panel, "闪光", "FLASH");
        addTemplate(panel, "时间显示", "TIME");
        addTemplate(panel, "场景切换", "SCENE");
        return panel;
    }

    private void addTemplate(FlowLayout panel, String label, String type) {
        panel.child(UIComponents.button(Text.literal(label), button -> addEffect(type)).horizontalSizing(Sizing.fill()));
    }

    private FlowLayout buildEventList(int width) {
        FlowLayout panel = panel(width);
        panel.child(UIComponents.label(Text.literal("时间线事件（" + chart.effects.size() + "）")).shadow(true));
        FlowLayout events = UIContainers.verticalFlow(Sizing.fill(), Sizing.expand());
        for (int index = 0; index < chart.effects.size(); index++) {
            JsonObject effect = chart.effects.get(index);
            String type = effect.has("type") ? effect.get("type").getAsString() : "特效";
            double beat = effect.has("beat") ? effect.get("beat").getAsDouble() : 0.0;
            String prefix = index == selectedIndex ? "◆ " : "  ";
            final int eventIndex = index;
            events.child(UIComponents.button(Text.literal(prefix + type + " · Beat " + format(beat)), button -> select(eventIndex)).horizontalSizing(Sizing.fill()));
        }
        panel.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.fixed(Math.max(100, pageHeight() - 205)), events));
        return panel;
    }

    private FlowLayout buildInspector(int width) {
        FlowLayout panel = panel(width);
        panel.child(UIComponents.label(Text.literal("事件参数")).shadow(true));
        JsonObject effect = selectedEffect();
        if (effect == null) {
            panel.child(wrappedLabel("请选择一个事件，或从特效库新增事件。", width - 20));
            return panel;
        }
        panel.child(UIComponents.label(Text.literal("类型：" + effect.get("type").getAsString())));
        beatField = field(effect, "beat", "0");
        durationField = field(effect, "duration", "4");
        textField = field(effect, "text", "特效");
        colorField = field(effect, "color", "0xB77BFF");
        countField = field(effect, "count", "12");
        addField(panel, "拍点", beatField, true);
        addField(panel, "时长（拍）", durationField, true);
        addField(panel, "文本", textField, false);
        addField(panel, "颜色（十进制或 0x）", colorField, false);
        addField(panel, "数量（1-64）", countField, true);
        panel.child(UIComponents.button(Text.literal("新增关键帧"), button -> addKeyframe()).horizontalSizing(Sizing.fill()));
        panel.child(UIComponents.button(Text.literal("删除当前事件"), button -> deleteSelected()).horizontalSizing(Sizing.fill()));
        return panel;
    }

    private FlowLayout buildTimeline(int width) {
        FlowLayout panel = panelFill();
        panel.child(UIComponents.label(Text.literal("特效时间轴  ·  Beat 0     16     32     48     64     " + format(chart.totalBeats))).shadow(true));
        panel.child(wrappedLabel("播放头 Beat " + format(playheadBeat) + "  ·  事件 " + chart.effects.size() + "  ·  拖动与复制操作沿用现有事件模型。", width - 20));
        panel.child(buildEventList(Math.max(220, width - 20)));
        return panel;
    }

    private TextBoxComponent field(JsonObject effect, String key, String fallback) {
        return UIComponents.textBox(Sizing.fill(), effect.has(key) ? effect.get(key).getAsString() : fallback);
    }

    private void addField(FlowLayout panel, String label, TextBoxComponent field, boolean numeric) {
        panel.child(UIComponents.label(Text.literal(label)));
        if (numeric) numericOnly(field);
        panel.child(field);
    }

    private void select(int index) {
        syncInspector();
        playheadBeat = selectedEffectBeat(chart, index);
        MinecraftClient.getInstance().setScreen(new EffectTrackScreen(chart, index));
    }

    private JsonObject selectedEffect() {
        return chart == null || chart.effects == null || selectedIndex < 0 || selectedIndex >= chart.effects.size() ? null : chart.effects.get(selectedIndex);
    }

    private void addEffect(String type) {
        JsonObject effect = new JsonObject();
        effect.addProperty("type", type);
        effect.addProperty("beat", currentBeat());
        effect.addProperty("duration", 4.0);
        effect.addProperty("text", type.equals("TITLE") ? "标题显示" : "特效");
        effect.addProperty("color", 0xB77BFF);
        effect.addProperty("count", 12);
        chart.effects.add(effect);
        MinecraftClient.getInstance().setScreen(new EffectTrackScreen(chart, chart.effects.size() - 1));
    }

    private void addKeyframe() {
        JsonObject effect = selectedEffect();
        if (effect == null) return;
        syncInspector();
        JsonObject keyframe = new JsonObject();
        keyframe.addProperty("beat", currentBeat());
        keyframe.addProperty("value", 1.0);
        if (!effect.has("keyframes")) effect.add("keyframes", new com.google.gson.JsonArray());
        effect.getAsJsonArray("keyframes").add(keyframe);
        ClientChartAccess.status("已为当前事件添加关键帧");
    }

    private void deleteSelected() {
        if (selectedEffect() == null) return;
        chart.effects.remove(selectedIndex);
        MinecraftClient.getInstance().setScreen(new EffectTrackScreen(chart, Math.max(0, selectedIndex - 1)));
    }

    private double currentBeat() { return Math.max(0.0, Math.min(chart.totalBeats, playheadBeat)); }

    private static double selectedEffectBeat(cn.frkovo.rhythmcmaker.chart.ChartManifest chart, int index) {
        if (chart == null || chart.effects == null || index < 0 || index >= chart.effects.size()) return 0.0;
        JsonObject effect = chart.effects.get(index);
        try { return effect != null && effect.has("beat") ? Math.max(0.0, effect.get("beat").getAsDouble()) : 0.0; }
        catch (RuntimeException ignored) { return 0.0; }
    }

    private void syncInspector() {
        JsonObject effect = selectedEffect();
        if (effect == null || beatField == null) return;
        putDouble(effect, "beat", beatField, 0.0);
        putDouble(effect, "duration", durationField, 4.0);
        effect.addProperty("text", textField.getText());
        try { effect.addProperty("color", Integer.decode(colorField.getText().trim())); } catch (NumberFormatException ignored) { }
        try { effect.addProperty("count", Math.max(1, Math.min(64, Integer.parseInt(countField.getText().trim())))); } catch (NumberFormatException ignored) { }
    }

    private static void putDouble(JsonObject effect, String key, TextBoxComponent field, double fallback) {
        try { effect.addProperty(key, Double.parseDouble(field.getText().trim())); } catch (NumberFormatException ignored) { effect.addProperty(key, fallback); }
    }

    private void save() {
        syncInspector();
        try {
            ClientChartAccess.update(chart);
            ClientChartAccess.setActiveChart(chart);
            ClientChartAccess.status("特效轨道已保存");
        } catch (IOException exception) {
            ClientChartAccess.status("特效轨道保存失败：" + exception.getMessage());
        }
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    @Override public boolean shouldCloseOnEsc() { return true; }
}
