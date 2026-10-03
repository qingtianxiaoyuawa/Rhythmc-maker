package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.client.RhythmcMakerClient;
import cn.frkovo.rhythmcmaker.common.effect.EffectPropertyDefinition;
import cn.frkovo.rhythmcmaker.common.effect.EffectPropertyValueKind;
import cn.frkovo.rhythmcmaker.common.effect.EffectTypeDefinition;
import cn.frkovo.rhythmcmaker.common.effect.EffectTypeRegistry;
import cn.frkovo.rhythmcmaker.common.effect.animation.EffectAnimationEvaluator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiDataType;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ImGui view for the effect track. Raw JSON remains the persistence boundary,
 * so an unrecognised effect is displayed read-only and survives a save intact.
 */
public final class EffectEditorImGuiView {
    private static final int PANEL_FLAGS = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;
    private static final int TOOLBAR_FLAGS = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoSavedSettings;

    private final ChartManifest chart;
    private final List<JsonObject> workingEffects = new ArrayList<>();
    private final Runnable saveAction;
    private final Runnable closeAction;
    private final Runnable playbackAction;
    private final Consumer<Double> seekAction;
    private final Consumer<String> statusAction;
    private final EffectTypeRegistry registry = EffectTypeRegistry.getInstance();
    private final ImDouble beatField = new ImDouble();
    private final ImDouble durationField = new ImDouble();
    private final Map<String, ImString> textFields = new HashMap<>();
    private final Map<String, ImString> integerArrayFields = new HashMap<>();
    private final Map<String, ImDouble> decimalFields = new HashMap<>();
    private final Map<String, ImInt> integerFields = new HashMap<>();
    private final Map<String, ImBoolean> booleanFields = new HashMap<>();
    private final Map<String, float[]> colorFields = new HashMap<>();
    private final float[] playhead = new float[1];
    private final ImString timelinePositionField = new ImString("0", 64);
    private int selectedIndex;
    private int bufferIndex = Integer.MIN_VALUE;
    private String bufferType = "";
    private boolean dirty;
    private String status = "就绪";

    public EffectEditorImGuiView(ChartManifest chart, Runnable saveAction, Runnable closeAction, Runnable playbackAction,
                                 Consumer<Double> seekAction, Consumer<String> statusAction) {
        this.chart = chart;
        this.saveAction = saveAction;
        this.closeAction = closeAction;
        this.playbackAction = playbackAction;
        this.seekAction = seekAction;
        this.statusAction = statusAction;
        if (chart != null && chart.effects != null) {
            for (JsonObject effect : chart.effects) {
                if (effect != null) workingEffects.add(effect.deepCopy());
            }
        }
        this.selectedIndex = workingEffects.isEmpty() ? -1 : 0;
        this.playhead[0] = (float) selectedBeat();
        syncBuffers();
    }

    public void render(MinecraftClient client) {
        ImGuiIO io = ImGui.getIO();
        float width = Math.max(900.0f, io.getDisplaySizeX());
        float height = Math.max(600.0f, io.getDisplaySizeY());
        drawToolbar(width);
        if (chart == null) {
            drawEmptyState(width, height);
            return;
        }
        float leftWidth = Math.max(210.0f, Math.min(270.0f, width * 0.18f));
        float rightWidth = Math.max(290.0f, Math.min(360.0f, width * 0.24f));
        float centerWidth = Math.max(320.0f, width - leftWidth - rightWidth - 24.0f);
        float top = 54.0f;
        float contentHeight = Math.max(260.0f, height - 270.0f);
        drawLibrary(0.0f, top, leftWidth, contentHeight);
        drawPreview(leftWidth + 8.0f, top, centerWidth, contentHeight, client);
        drawInspector(leftWidth + centerWidth + 16.0f, top, rightWidth, contentHeight);
        drawTimeline(0.0f, top + contentHeight + 8.0f, width, Math.max(160.0f, height - top - contentHeight - 8.0f));
    }

    public void dispose() {
        textFields.clear();
        decimalFields.clear();
        integerFields.clear();
        booleanFields.clear();
        colorFields.clear();
    }

    public void applyPendingEdits() {
        if (chart == null) return;
        syncEventFromBuffers();
    }

    public void saveToChart() {
        if (chart == null) return;
        applyPendingEdits();
        chart.effects = new ArrayList<>();
        for (JsonObject effect : workingEffects) chart.effects.add(effect.deepCopy());
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    public void discardChanges() {
        dirty = false;
    }

    public void markSaved(String message) {
        dirty = false;
        status = message;
        statusAction.accept(message);
    }

    public void markSaveFailed(String message) {
        status = message;
        statusAction.accept(message);
    }

    private void drawToolbar(float width) {
        ImGui.setNextWindowPos(0.0f, 0.0f);
        ImGui.setNextWindowSize(width, 46.0f);
        if (!ImGui.begin("##effect-toolbar", TOOLBAR_FLAGS)) {
            ImGui.end();
            return;
        }
        ImGui.textColored(0xFF66CCFF, "RhythMC Maker");
        ImGui.sameLine();
        ImGui.text("特效编辑器");
        ImGui.sameLine(Math.max(210.0f, width - 480.0f));
        ImGui.text(chart == null ? "未加载谱面" : chartTitle() + "  ·  BPM " + format(chart.bpm));
        ImGui.sameLine();
        ImGui.text(dirty ? "● 未保存" : "已保存");
        ImGui.sameLine();
        if (ImGui.button("撤销##effect-undo")) status = "撤销由现有编辑历史接管";
        ImGui.sameLine();
        if (ImGui.button("保存##effect-save")) saveAction.run();
        ImGui.sameLine();
        if (ImGui.button(RhythmcMakerClient.isEffectEditorPlaybackActive() ? "暂停##effect-play" : "播放##effect-play")) playbackAction.run();
        ImGui.sameLine();
        if (ImGui.button("关闭##effect-close")) closeAction.run();
        ImGui.end();
    }

    private void drawEmptyState(float width, float height) {
        ImGui.setNextWindowPos(24.0f, 66.0f);
        ImGui.setNextWindowSize(width - 48.0f, height - 90.0f);
        if (ImGui.begin("特效编辑器##empty", PANEL_FLAGS)) {
            ImGui.text("请先从谱面列表加载一个谱面，再打开特效编辑器。");
            if (ImGui.button("返回##empty-close")) closeAction.run();
        }
        ImGui.end();
    }

    private void drawLibrary(float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(width, height);
        if (!ImGui.begin("特效库##effect-library", PANEL_FLAGS)) {
            ImGui.end();
            return;
        }
        ImGui.textColored(0xFF66CCFF, "特效库");
        ImGui.textWrapped("仅注册表中的类型允许新增；未知类型保留原 JSON 并只读显示。");
        ImGui.separator();
        for (EffectTypeDefinition definition : registry.definitions()) {
            ImGui.pushID(definition.eventType());
            if (ImGui.selectable(definition.displayName() + "##add", false, ImGuiSelectableFlags.SpanAllColumns)) addEffect(definition);
            ImGui.popID();
        }
        ImGui.end();
    }

    private void drawPreview(float x, float y, float width, float height, MinecraftClient client) {
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(width, height);
        if (!ImGui.begin("世界预览##effect-preview", PANEL_FLAGS | ImGuiWindowFlags.NoBackground)) {
            ImGui.end();
            return;
        }
        ImGui.textColored(0xFF66CCFF, "世界预览");
        if (client.player == null) {
            ImGui.textDisabled("当前没有客户端玩家。");
        } else {
            ImGui.text("维度：" + client.player.getEntityWorld().getRegistryKey().getValue());
            var camera = RhythmcMakerClient.effectEditorCameraPosition();
            ImGui.text(String.format(Locale.ROOT, "主摄像机：%.2f, %.2f, %.2f", camera.x, camera.y, camera.z));
        }
        ImGui.separator();
        JsonObject event = selectedEvent();
        if (event == null) {
            ImGui.textWrapped("从左侧选择一个特效类型，或在下方时间线选择已有事件。");
        } else {
            ImGui.text("播放头 " + formatChunkPosition(playhead[0]));
            ImGui.text("当前事件：" + eventType(event));
            ImGui.textWrapped("中心区域透出当前 Minecraft 主摄像机画面；播放时 Camera 会跟随滚动判定线。");
        }
        ImGui.separator();
        ImGui.text("状态：" + (RhythmcMakerClient.isEffectEditorPlaybackActive() ? "播放中" : status));
        ImGui.end();
    }

    public double playheadBeat() {
        return Math.max(0.0, playhead[0]);
    }

    private void drawInspector(float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(width, height);
        if (!ImGui.begin("特效参数设置##effect-inspector", PANEL_FLAGS)) {
            ImGui.end();
            return;
        }
        ImGui.textColored(0xFF66CCFF, "特效参数设置");
        JsonObject event = selectedEvent();
        if (event == null) {
            ImGui.textDisabled("请选择一个事件。");
            ImGui.end();
            return;
        }
        EffectTypeDefinition definition = registry.find(eventType(event));
        ImGui.text("类型：" + eventType(event));
        if (definition == null) {
            ImGui.textColored(0xFFFFAA55, "未知类型：只读，保存时原样保留。");
            ImGui.textWrapped(event.toString());
            ImGui.end();
            return;
        }
        if (ImGui.inputScalar("拍点", ImGuiDataType.Double, beatField, 0.25, 1.0, "%.3f")) {
            beatField.set(Math.max(0.0, beatField.get()));
            event.addProperty("beat", beatField.get());
            playhead[0] = (float) beatField.get();
            seekAction.accept(beatField.get());
            dirty = true;
        }
        if (ImGui.inputScalar("持续时间##duration", ImGuiDataType.Double, durationField, 0.25, 1.0, "%.3f")) {
            writeProperty(event, "duration", durationField.get());
            dirty = true;
        }
        ImGui.separator();
        for (EffectPropertyDefinition property : definition.properties()) drawProperty(event, property);
        EffectAnimationEvaluator.evaluate(event, RhythmcMakerClient.effectEditorCurrentBeat()).ifPresent(sample -> {
            if (!sample.values().isEmpty()) {
                ImGui.separator();
                ImGui.text("动画采样 Beat " + String.format(Locale.ROOT, "%.3f", sample.beat()));
                for (var value : sample.values()) ImGui.textDisabled(value.channelName() + "：" + value.value().toJson());
            }
        });
        ImGui.separator();
        if (ImGui.button("新增关键帧##add-keyframe")) addKeyframe(event);
        ImGui.sameLine();
        if (ImGui.button("删除当前事件##delete-event")) deleteSelected();
        ImGui.end();
    }

    private void drawProperty(JsonObject event, EffectPropertyDefinition definition) {
        String name = definition.name();
        switch (definition.valueKind()) {
            case TEXT, ENUM -> {
                ImString field = textFields.computeIfAbsent(name, key -> new ImString(readString(event, key, ""), 512));
                if (ImGui.inputText(definition.displayName() + "##" + name, field)) {
                    writeProperty(event, name, field.get());
                    dirty = true;
                }
            }
            case INTEGER -> {
                ImInt field = integerFields.computeIfAbsent(name, key -> new ImInt((int) readDouble(event, key, 0.0)));
                if (ImGui.inputScalar(definition.displayName() + "##" + name, ImGuiDataType.S32, field, 1, 10)) {
                    writeProperty(event, name, field.get());
                    dirty = true;
                }
            }
            case DECIMAL -> {
                ImDouble field = decimalFields.computeIfAbsent(name, key -> new ImDouble(readDouble(event, key, 0.0)));
                if (ImGui.inputScalar(definition.displayName() + "##" + name, ImGuiDataType.Double, field, 0.1, 1.0, "%.3f")) {
                    writeProperty(event, name, field.get());
                    dirty = true;
                }
            }
            case BOOLEAN -> {
                ImBoolean field = booleanFields.computeIfAbsent(name, key -> new ImBoolean(readBoolean(event, key, false)));
                if (ImGui.checkbox(definition.displayName() + "##" + name, field)) {
                    writeProperty(event, name, field.get());
                    dirty = true;
                }
            }
            case INTEGER_ARRAY -> {
                ImString field = integerArrayFields.computeIfAbsent(name, key -> new ImString(readIntegerArrayText(event, key), 256));
                if (ImGui.inputText(definition.displayName() + "##" + name, field)) {
                    writeProperty(event, name, parseIntegerArray(field.get()));
                    dirty = true;
                }
            }
            case COLOR -> {
                float[] field = colorFields.computeIfAbsent(name, key -> readColor(event, key));
                if (ImGui.colorEdit4(definition.displayName() + "##" + name, field)) {
                    writeProperty(event, name, packColor(field));
                    dirty = true;
                }
            }
            case VECTOR, JSON -> ImGui.textDisabled(definition.displayName() + "：" + readElement(event, name));
        }
    }

    private void drawTimeline(float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(width, height);
        if (!ImGui.begin("特效时间轴##effect-timeline", PANEL_FLAGS)) {
            ImGui.end();
            return;
        }
        ImGui.textColored(0xFF66CCFF, "特效时间轴");
        float maxBeat = (float) Math.max(1.0, chart.totalBeats);
        if (RhythmcMakerClient.isEffectEditorPlaybackActive()) playhead[0] = (float) RhythmcMakerClient.effectEditorCurrentBeat();
        if (ImGui.sliderFloat("播放头##playhead", playhead, 0.0f, maxBeat, "Beat %.2f")) {
            playhead[0] = Math.max(0.0f, Math.min(maxBeat, playhead[0]));
            seekAction.accept((double) playhead[0]);
            status = "播放头已跳转到 " + formatChunkPosition(playhead[0]);
        }
        if (ImGui.inputText("Chunk/Sub-beat##position", timelinePositionField)) {
            Double targetBeat = parseChunkPosition(timelinePositionField.get());
            if (targetBeat != null) {
                playhead[0] = (float) Math.max(0.0, Math.min(maxBeat, targetBeat));
                seekAction.accept((double) playhead[0]);
                status = "播放头已跳转到 " + formatChunkPosition(playhead[0]);
            }
        }
        if (!ImGui.isItemActive()) timelinePositionField.set(formatChunkPosition(playhead[0]));
        ImGui.text(String.format(Locale.ROOT, "Beat 0     16     32     48     64     %.2f", chart.totalBeats));
        for (int index = 0; index < workingEffects.size(); index++) {
            JsonObject event = workingEffects.get(index);
            String label = String.format(Locale.ROOT, "%s  ·  Beat %.3f", eventType(event), eventBeat(event));
            if (ImGui.selectable(label + "##event-" + index, index == selectedIndex, ImGuiSelectableFlags.SpanAllColumns)) select(index);
        }
        ImGui.end();
    }

    private void addEffect(EffectTypeDefinition definition) {
        JsonObject event = new JsonObject();
        event.addProperty("eventType", definition.eventType());
        event.addProperty("beat", Math.max(0.0, Math.min(chart.totalBeats, playhead[0])));
        JsonObject properties = new JsonObject();
        for (EffectPropertyDefinition property : definition.properties()) addDefault(properties, property, definition.eventType());
        event.add("properties", properties);
        workingEffects.add(event);
        dirty = true;
        select(workingEffects.size() - 1);
        status = "已新增 " + definition.displayName();
    }

    private void addDefault(JsonObject properties, EffectPropertyDefinition property, String type) {
        switch (property.valueKind()) {
            case TEXT -> properties.addProperty(property.name(), "TITLE".equals(type) ? "标题显示" : "特效");
            case ENUM -> properties.addProperty(property.name(), "");
            case INTEGER -> properties.addProperty(property.name(), 0);
            case DECIMAL -> properties.addProperty(property.name(), 0.0);
            case BOOLEAN -> properties.addProperty(property.name(), false);
            case COLOR -> properties.addProperty(property.name(), 0x66CCFF);
            case INTEGER_ARRAY -> properties.add(property.name(), new JsonArray());
            case VECTOR -> properties.add(property.name(), new JsonArray());
            case JSON -> properties.add(property.name(), new JsonObject());
        }
    }

    private void addKeyframe(JsonObject event) {
        JsonObject animation = event.has("animation") && event.get("animation").isJsonObject() ? event.getAsJsonObject("animation") : new JsonObject();
        JsonArray channels = animation.has("channels") && animation.get("channels").isJsonArray() ? animation.getAsJsonArray("channels") : new JsonArray();
        JsonObject channel = new JsonObject();
        channel.addProperty("name", "value");
        channel.addProperty("interpolation", "CONTINUOUS");
        JsonArray keyframes = new JsonArray();
        JsonObject keyframe = new JsonObject();
        keyframe.addProperty("beat", playhead[0]);
        keyframe.addProperty("value", 1.0);
        keyframe.addProperty("easing", 0);
        keyframes.add(keyframe);
        channel.add("keyframes", keyframes);
        channels.add(channel);
        animation.add("channels", channels);
        event.add("animation", animation);
        dirty = true;
        status = "已新增关键帧";
    }

    private void deleteSelected() {
        JsonObject event = selectedEvent();
        if (event == null || registry.find(eventType(event)) == null) return;
        workingEffects.remove(selectedIndex);
        selectedIndex = Math.min(selectedIndex, workingEffects.size() - 1);
        dirty = true;
        syncBuffers();
    }

    private void select(int index) {
        if (chart == null || index < 0 || index >= workingEffects.size()) return;
        selectedIndex = index;
        playhead[0] = (float) eventBeat(workingEffects.get(index));
        seekAction.accept((double) playhead[0]);
        syncBuffers();
    }

    private void syncBuffers() {
        JsonObject event = selectedEvent();
        String type = event == null ? "" : eventType(event);
        if (selectedIndex == bufferIndex && type.equals(bufferType)) return;
        bufferIndex = selectedIndex;
        bufferType = type;
        textFields.clear();
        integerArrayFields.clear();
        decimalFields.clear();
        integerFields.clear();
        booleanFields.clear();
        colorFields.clear();
        beatField.set(event == null ? 0.0 : eventBeat(event));
        durationField.set(event == null ? 0.0 : readDouble(event, "duration", 0.0));
    }

    private void syncEventFromBuffers() {
        JsonObject event = selectedEvent();
        if (event == null || registry.find(eventType(event)) == null) return;
        event.addProperty("beat", Math.max(0.0, beatField.get()));
        if (durationField.get() > 0.0) writeProperty(event, "duration", durationField.get());
        for (Map.Entry<String, ImString> entry : textFields.entrySet()) writeProperty(event, entry.getKey(), entry.getValue().get());
        for (Map.Entry<String, ImDouble> entry : decimalFields.entrySet()) writeProperty(event, entry.getKey(), entry.getValue().get());
        for (Map.Entry<String, ImInt> entry : integerFields.entrySet()) writeProperty(event, entry.getKey(), entry.getValue().get());
        for (Map.Entry<String, ImBoolean> entry : booleanFields.entrySet()) writeProperty(event, entry.getKey(), entry.getValue().get());
        for (Map.Entry<String, ImString> entry : integerArrayFields.entrySet()) writeProperty(event, entry.getKey(), parseIntegerArray(entry.getValue().get()));
        for (Map.Entry<String, float[]> entry : colorFields.entrySet()) writeProperty(event, entry.getKey(), packColor(entry.getValue()));
    }

    private JsonObject selectedEvent() {
        return chart == null || selectedIndex < 0 || selectedIndex >= workingEffects.size() ? null : workingEffects.get(selectedIndex);
    }

    private double selectedBeat() {
        JsonObject event = selectedEvent();
        return event == null ? 0.0 : eventBeat(event);
    }

    private static String eventType(JsonObject event) {
        if (event == null) return "EFFECT";
        if (event.has("eventType") && event.get("eventType").isJsonPrimitive()) return event.get("eventType").getAsString();
        if (event.has("type") && event.get("type").isJsonPrimitive()) return event.get("type").getAsString();
        return "EFFECT";
    }

    private static double eventBeat(JsonObject event) {
        try { return event != null && event.has("beat") ? Math.max(0.0, event.get("beat").getAsDouble()) : 0.0; }
        catch (RuntimeException ignored) { return 0.0; }
    }

    private static JsonElement readElement(JsonObject event, String name) {
        if (event == null) return null;
        if (event.has(name)) return event.get(name);
        if (event.has("properties") && event.get("properties").isJsonObject()) return event.getAsJsonObject("properties").get(name);
        return null;
    }

    private static String readString(JsonObject event, String name, String fallback) {
        try { JsonElement element = readElement(event, name); return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static double readDouble(JsonObject event, String name, double fallback) {
        try { JsonElement element = readElement(event, name); return element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean readBoolean(JsonObject event, String name, boolean fallback) {
        try { JsonElement element = readElement(event, name); return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String readIntegerArrayText(JsonObject event, String name) {
        JsonElement element = readElement(event, name);
        if (element == null || !element.isJsonArray()) return "";
        StringBuilder result = new StringBuilder();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
            if (result.length() > 0) result.append(", ");
            result.append(value.getAsInt());
        }
        return result.toString();
    }

    private static JsonArray parseIntegerArray(String value) {
        JsonArray result = new JsonArray();
        if (value == null || value.isBlank()) return result;
        for (String trackValue : value.split("[,\\s]+")) {
            if (trackValue.isBlank()) continue;
            try { result.add(Integer.parseInt(trackValue)); }
            catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static float[] readColor(JsonObject event, String name) {
        int packed = readColorValue(readElement(event, name), 0x66CCFF);
        return new float[] { ((packed >> 16) & 255) / 255.0f, ((packed >> 8) & 255) / 255.0f, (packed & 255) / 255.0f, 1.0f };
    }

    private static int readColorValue(JsonElement element, int fallback) {
        try {
            if (element == null || !element.isJsonPrimitive()) return fallback;
            if (element.getAsJsonPrimitive().isNumber()) return element.getAsInt();
            String value = element.getAsString().trim();
            if (value.isBlank()) return fallback;
            String normalized = value.toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "RED" -> 0xFF0000;
                case "GREEN" -> 0x00FF00;
                case "BLUE" -> 0x0000FF;
                case "YELLOW" -> 0xFFFF00;
                case "WHITE" -> 0xFFFFFF;
                case "BLACK" -> 0x000000;
                case "PURPLE" -> 0x800080;
                case "CYAN" -> 0x00FFFF;
                case "ORANGE" -> 0xFFA500;
                default -> Integer.decode(value.startsWith("#") ? "0x" + value.substring(1) : value);
            };
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int packColor(float[] value) {
        int red = Math.max(0, Math.min(255, Math.round(value[0] * 255.0f)));
        int green = Math.max(0, Math.min(255, Math.round(value[1] * 255.0f)));
        int blue = Math.max(0, Math.min(255, Math.round(value[2] * 255.0f)));
        return red << 16 | green << 8 | blue;
    }

    private static void writeProperty(JsonObject event, String name, String value) { propertyTarget(event, name).addProperty(name, value); }
    private static void writeProperty(JsonObject event, String name, int value) { propertyTarget(event, name).addProperty(name, value); }
    private static void writeProperty(JsonObject event, String name, double value) { propertyTarget(event, name).addProperty(name, value); }
    private static void writeProperty(JsonObject event, String name, boolean value) { propertyTarget(event, name).addProperty(name, value); }
    private static void writeProperty(JsonObject event, String name, JsonArray value) { propertyTarget(event, name).add(name, value); }
    private static JsonObject propertyTarget(JsonObject event, String name) {
        if (event.has(name)) return event;
        if (event.has("properties") && event.get("properties").isJsonObject()) return event.getAsJsonObject("properties");
        return event;
    }

    private String chartTitle() { return chart.title == null || chart.title.isBlank() ? "未命名谱面" : chart.title; }
    private static String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    private String formatChunkPosition(double beat) {
        double divisions = Math.max(1.0, Math.min(32.0, chart == null ? 1.0 : chart.divisionsPerChunk));
        int chunk = (int) Math.floor(Math.max(0.0, beat) / divisions) + 1;
        double within = Math.max(0.0, beat - (chunk - 1) * divisions);
        return String.format(Locale.ROOT, "Chunk %d + %s (Beat %.3f)", chunk, fractionText(within / divisions), beat);
    }

    private static String fractionText(double value) {
        int bestNumerator = 0;
        int bestDenominator = 1;
        double bestError = Math.abs(value);
        for (int denominator = 1; denominator <= 32; denominator++) {
            int numerator = (int) Math.round(value * denominator);
            double error = Math.abs(value - numerator / (double) denominator);
            if (error < bestError - 1.0e-9 || (Math.abs(error - bestError) <= 1.0e-9 && denominator < bestDenominator)) {
                bestNumerator = numerator;
                bestDenominator = denominator;
                bestError = error;
            }
        }
        if (bestDenominator == 1) return Integer.toString(bestNumerator);
        return bestNumerator + "/" + bestDenominator;
    }

    private Double parseChunkPosition(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT).replace("chunk", "").replace(" ", "");
        double divisions = Math.max(1.0, Math.min(32.0, chart == null ? 1.0 : chart.divisionsPerChunk));
        try {
            int plus = value.indexOf('+');
            if (plus >= 0) {
                int chunk = Integer.parseInt(value.substring(0, plus));
                return Math.max(0.0, (chunk - 1) * divisions + parseFraction(value.substring(plus + 1)) * divisions);
            }
            return parseFraction(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static double parseFraction(String value) {
        int slash = value.indexOf('/');
        if (slash < 1 || slash == value.length() - 1) return Double.parseDouble(value);
        double numerator = Double.parseDouble(value.substring(0, slash));
        double denominator = Double.parseDouble(value.substring(slash + 1));
        if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || denominator == 0.0) throw new NumberFormatException("invalid fraction");
        return numerator / denominator;
    }
}
