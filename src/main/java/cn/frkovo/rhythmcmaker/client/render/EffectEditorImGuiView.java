package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartTiming;
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
import imgui.ImDrawList;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiDataType;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectEditorImGuiView.class);
    private static final int PANEL_FLAGS = ImGuiWindowFlags.None;
    private static final int TOOLBAR_FLAGS = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoSavedSettings;
    private static final int DOCKSPACE_FLAGS = ImGuiWindowFlags.NoDecoration
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoBackground
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoNavFocus;
    private static final int INPUT_TRIGGER_FLAGS = ImGuiWindowFlags.NoDecoration
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoBackground
            | ImGuiWindowFlags.NoBringToFrontOnFocus
            | ImGuiWindowFlags.NoNavFocus
            | ImGuiWindowFlags.NoMouseInputs;

    private final ChartManifest chart;
    private final List<JsonObject> workingEffects = new ArrayList<>();
    private final Runnable saveAction;
    private final Runnable closeAction;
    private final Runnable togglePlaybackAction;
    private final Runnable replayAction;
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
    private double timelineZoomMin;
    private double timelineZoomMax = 1.0;
    private int timelineDraggedEvent = -1;
    private int timelineDraggedChannel = -1;
    private int timelineDraggedKeyframe = -1;
    private double timelineDragBeatOffset;
    private boolean timelineDraggingPlayhead;
    private boolean timelineDraggingRange;
    private boolean spaceKeyDown;
    private double timelineLastSeekBeat = Double.NaN;
    private int timelineRowOffset;
    private boolean timelineDraggingScrollBar;
    private float timelineScrollBarDragOffset;
    private float timelineBoundsLeft = -1.0f;
    private float timelineBoundsTop = -1.0f;
    private float timelineBoundsRight = -1.0f;
    private float timelineBoundsBottom = -1.0f;
    private int timelineVisibleRowCapacity;
    private boolean applyDefaultDockLayout;
    private int libraryDockNodeId;
    private int previewDockNodeId;
    private int inspectorDockNodeId;
    private int timelineDockNodeId;
    private int selectedIndex;
    private int bufferIndex = Integer.MIN_VALUE;
    private String bufferType = "";
    private boolean dirty;
    private String status = "就绪";
    private float previewWindowX = -1.0f;
    private float previewWindowY = -1.0f;
    private float previewWindowWidth;
    private float previewWindowHeight;
    private float previewContentX = -1.0f;
    private float previewContentY = -1.0f;
    private float previewContentWidth;
    private float previewContentHeight;
    private boolean cameraDragLogged;

    private enum TimelineMarkerKind { EVENT, KEYFRAME }

    private enum TimelineControl { PLAY_PAUSE, RETURN_TO_START, PREVIOUS, NEXT }

    private record TimelineMarker(int eventIndex, int channelIndex, int keyframeIndex, double beat,
                                  TimelineMarkerKind kind) {
    }

    private record TimelineRow(String groupLabel, String label, int eventIndex, List<TimelineMarker> markers,
                               boolean groupHeader) {
    }

    public EffectEditorImGuiView(ChartManifest chart, Runnable saveAction, Runnable closeAction,
                                 Runnable togglePlaybackAction, Runnable replayAction,
                                 Consumer<Double> seekAction, Consumer<String> statusAction) {
        this.chart = chart;
        this.saveAction = saveAction;
        this.closeAction = closeAction;
        this.togglePlaybackAction = togglePlaybackAction;
        this.replayAction = replayAction;
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
        float width = Math.max(1.0f, io.getDisplaySizeX());
        float height = Math.max(1.0f, io.getDisplaySizeY());
        drawToolbar(width);
        drawInputTriggerLayer(width, height);
        drawDockspace(width, height);
        if (chart == null) {
            drawEmptyState(width, height);
            return;
        }
        float leftWidth = Math.min(270.0f, Math.max(120.0f, width * 0.18f));
        float rightWidth = Math.min(360.0f, Math.max(190.0f, width * 0.24f));
        float centerWidth = Math.max(1.0f, width - leftWidth - rightWidth - 24.0f);
        float top = 54.0f;
        float timelineHeight = height * 0.32f;
        float contentHeight = Math.max(1.0f, height - top - timelineHeight - 8.0f);
        drawLibrary(0.0f, top, leftWidth, contentHeight);
        drawPreview(leftWidth + 8.0f, top, centerWidth, contentHeight, client);
        drawInspector(leftWidth + centerWidth + 16.0f, top, rightWidth, contentHeight);
        drawTimeline(0.0f, top + contentHeight + 8.0f, width, timelineHeight);
        handleCameraWheelOutsideTimeline();
        applyDefaultDockLayout = false;
    }

    /** A transparent bottom layer that gives editor wheel input one stable owner. */
    private void drawInputTriggerLayer(float width, float height) {
        ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.Always);
        ImGui.setNextWindowSize(width, height, ImGuiCond.Always);
        if (ImGui.begin("##effect-editor-input-trigger", INPUT_TRIGGER_FLAGS)) {
            if (ImGui.isMouseDragging(2, 0.0f)) {
                float deltaX = ImGui.getMouseDragDeltaX(2, 0.0f);
                float deltaY = ImGui.getMouseDragDeltaY(2, 0.0f);
                if (Float.isFinite(deltaX) && Float.isFinite(deltaY)
                        && (Math.abs(deltaX) > 0.001f || Math.abs(deltaY) > 0.001f)) {
                    RhythmcMakerClient.adjustEffectEditorCamera(deltaX, deltaY);
                    if (!cameraDragLogged) {
                        cameraDragLogged = true;
                        LOGGER.info("Effect camera middle drag delta=({}, {}), position={}",
                                deltaX, deltaY, RhythmcMakerClient.effectEditorCameraPosition());
                    }
                    ImGui.resetMouseDragDelta(2);
                }
            } else if (ImGui.isMouseReleased(2)) {
                cameraDragLogged = false;
            }
        }
        ImGui.end();
    }

    private void drawDockspace(float width, float height) {
        ImGui.setNextWindowPos(0.0f, 46.0f, ImGuiCond.Always);
        ImGui.setNextWindowSize(width, Math.max(1.0f, height - 46.0f), ImGuiCond.Always);
        if (ImGui.begin("##effect-editor-dockspace", DOCKSPACE_FLAGS)) {
            int dockspaceId = ImGui.getID("##effect-editor-dockspace-node-v3");
            initializeDefaultDockLayout(dockspaceId, ImGui.getContentRegionAvailX(),
                    ImGui.getContentRegionAvailY());
            ImGui.dockSpace(dockspaceId, 0.0f, 0.0f,
                    ImGuiDockNodeFlags.PassthruCentralNode);
        }
        ImGui.end();
    }

    private void initializeDefaultDockLayout(int dockspaceId, float width, float height) {
        if (imgui.internal.ImGui.dockBuilderGetNode(dockspaceId) != null) return;

        int previousDockspaceId = ImGui.getID("##effect-editor-dockspace-node");
        if (imgui.internal.ImGui.dockBuilderGetNode(previousDockspaceId) != null) {
            imgui.internal.ImGui.dockBuilderRemoveNode(previousDockspaceId);
        }
        int incompleteDockspaceId = ImGui.getID("##effect-editor-dockspace-node-v2");
        if (imgui.internal.ImGui.dockBuilderGetNode(incompleteDockspaceId) != null) {
            imgui.internal.ImGui.dockBuilderRemoveNode(incompleteDockspaceId);
        }
        imgui.internal.ImGui.dockBuilderAddNode(dockspaceId);
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId, Math.max(1.0f, width), Math.max(1.0f, height));

        ImInt timelineNode = new ImInt();
        ImInt mainNode = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Down, 0.30f, timelineNode, mainNode);

        ImInt libraryNode = new ImInt();
        ImInt centerAndInspectorNode = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(mainNode.get(), ImGuiDir.Left, 0.18f,
                libraryNode, centerAndInspectorNode);

        ImInt inspectorNode = new ImInt();
        ImInt previewNode = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(centerAndInspectorNode.get(), ImGuiDir.Right, 0.29f,
                inspectorNode, previewNode);

        imgui.internal.ImGui.dockBuilderDockWindow("特效库##effect-library", libraryNode.get());
        imgui.internal.ImGui.dockBuilderDockWindow("特效参数设置##effect-inspector", inspectorNode.get());
        imgui.internal.ImGui.dockBuilderDockWindow("特效时间轴##effect-timeline", timelineNode.get());
        imgui.internal.ImGui.dockBuilderDockWindow("世界预览##effect-preview", previewNode.get());
        imgui.internal.ImGui.dockBuilderFinish(dockspaceId);
        libraryDockNodeId = libraryNode.get();
        previewDockNodeId = previewNode.get();
        inspectorDockNodeId = inspectorNode.get();
        timelineDockNodeId = timelineNode.get();
        applyDefaultDockLayout = true;
    }

    public void dispose() {
        cameraDragLogged = false;
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
        ImGui.text(chart == null ? "未加载谱面" : displayText(chartTitle()) + "  ·  BPM " + format(chart.bpm));
        ImGui.sameLine();
        ImGui.text(dirty ? "● 未保存" : "已保存");
        ImGui.sameLine();
        if (ImGui.button("撤销##effect-undo")) status = "撤销由现有编辑历史接管";
        ImGui.sameLine();
        if (ImGui.button("保存##effect-save")) saveAction.run();
        ImGui.sameLine();
        if (ImGui.button("关闭##effect-close")) closeAction.run();
        ImGui.end();
    }

    private void drawEmptyState(float width, float height) {
        ImGui.setNextWindowPos(24.0f, 66.0f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(width - 48.0f, height - 90.0f, ImGuiCond.FirstUseEver);
        if (ImGui.begin("特效编辑器##empty", PANEL_FLAGS)) {
            ImGui.text("请先从谱面列表加载一个谱面，再打开特效编辑器。");
            if (ImGui.button("返回##empty-close")) closeAction.run();
        }
        ImGui.end();
    }

    private void drawLibrary(float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(width, height, ImGuiCond.FirstUseEver);
        if (applyDefaultDockLayout) ImGui.setNextWindowDockID(libraryDockNodeId, ImGuiCond.Always);
        if (!ImGui.begin("特效库##effect-library", PANEL_FLAGS)) {
            ImGui.end();
            return;
        }
        ImGui.textColored(0xFF66CCFF, "特效库");
        ImGui.textWrapped("仅注册表中的类型允许新增；未知类型保留原 JSON 并只读显示。");
        ImGui.separator();
        for (EffectTypeDefinition definition : registry.definitions()) {
            ImGui.pushID(definition.eventType());
            if (ImGui.selectable(displayText(definition.displayName()) + "##add", false, ImGuiSelectableFlags.SpanAllColumns)) addEffect(definition);
            ImGui.popID();
        }
        ImGui.end();
    }

    private void drawPreview(float x, float y, float width, float height, MinecraftClient client) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(width, height, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowBgAlpha(0.5f);
        if (applyDefaultDockLayout) ImGui.setNextWindowDockID(previewDockNodeId, ImGuiCond.Always);
        if (!ImGui.begin("世界预览##effect-preview", PANEL_FLAGS)) {
            ImGui.end();
            return;
        }
        previewWindowX = ImGui.getWindowPosX();
        previewWindowY = ImGui.getWindowPosY();
        previewWindowWidth = ImGui.getWindowSizeX();
        previewWindowHeight = ImGui.getWindowSizeY();
        previewContentX = previewWindowX + ImGui.getWindowContentRegionMinX();
        previewContentY = previewWindowY + ImGui.getWindowContentRegionMinY();
        previewContentWidth = Math.max(0.0f, ImGui.getWindowContentRegionMaxX() - ImGui.getWindowContentRegionMinX());
        previewContentHeight = Math.max(0.0f, ImGui.getWindowContentRegionMaxY() - ImGui.getWindowContentRegionMinY());
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
            ImGui.text("当前事件：" + displayText(eventType(event)));
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
        ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(width, height, ImGuiCond.FirstUseEver);
        if (applyDefaultDockLayout) ImGui.setNextWindowDockID(inspectorDockNodeId, ImGuiCond.Always);
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
        ImGui.text("类型：" + displayText(eventType(event)));
        if (definition == null) {
            ImGui.textColored(0xFFFFAA55, "未知类型：只读，保存时原样保留。");
            ImGui.textWrapped(displayText(event.toString()));
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
                for (var value : sample.values()) ImGui.textDisabled(displayText(value.channelName()) + "：" + displayText(value.value().toJson()));
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
                if (ImGui.inputText(displayText(definition.displayName()) + "##" + name, field)) {
                    writeProperty(event, name, field.get());
                    dirty = true;
                }
            }
            case INTEGER -> {
                ImInt field = integerFields.computeIfAbsent(name, key -> new ImInt((int) readDouble(event, key, 0.0)));
                if (ImGui.inputScalar(displayText(definition.displayName()) + "##" + name, ImGuiDataType.S32, field, 1, 10)) {
                    writeProperty(event, name, field.get());
                    dirty = true;
                }
            }
            case DECIMAL -> {
                ImDouble field = decimalFields.computeIfAbsent(name, key -> new ImDouble(readDouble(event, key, 0.0)));
                if (ImGui.inputScalar(displayText(definition.displayName()) + "##" + name, ImGuiDataType.Double, field, 0.1, 1.0, "%.3f")) {
                    writeProperty(event, name, field.get());
                    dirty = true;
                }
            }
            case BOOLEAN -> {
                ImBoolean field = booleanFields.computeIfAbsent(name, key -> new ImBoolean(readBoolean(event, key, false)));
                if (ImGui.checkbox(displayText(definition.displayName()) + "##" + name, field)) {
                    writeProperty(event, name, field.get());
                    dirty = true;
                }
            }
            case INTEGER_ARRAY -> {
                ImString field = integerArrayFields.computeIfAbsent(name, key -> new ImString(readIntegerArrayText(event, key), 256));
                if (ImGui.inputText(displayText(definition.displayName()) + "##" + name, field)) {
                    writeProperty(event, name, parseIntegerArray(field.get()));
                    dirty = true;
                }
            }
            case COLOR -> {
                float[] field = colorFields.computeIfAbsent(name, key -> readColor(event, key));
                if (ImGui.colorEdit4(displayText(definition.displayName()) + "##" + name, field)) {
                    writeProperty(event, name, packColor(field));
                    dirty = true;
                }
            }
            case VECTOR, JSON -> ImGui.textDisabled(displayText(definition.displayName()) + "：" + displayText(readElement(event, name)));
        }
    }

    private void drawTimeline(float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(width, height, ImGuiCond.FirstUseEver);
        if (applyDefaultDockLayout) ImGui.setNextWindowDockID(timelineDockNodeId, ImGuiCond.Always);
        if (!ImGui.begin("特效时间轴##effect-timeline", PANEL_FLAGS)) {
            ImGui.end();
            return;
        }
        drawTimelineControls();
        double maxBeat = timelineMaxBeat();
        if (RhythmcMakerClient.isEffectEditorPreviewPrepared()) {
            playhead[0] = (float) Math.max(0.0, Math.min(maxBeat, RhythmcMakerClient.effectEditorCurrentBeat()));
        }
        float positionRowWidth = ImGui.getContentRegionAvailX();
        float positionWidth = Math.max(56.0f, Math.min(170.0f, positionRowWidth * 0.24f));
        ImGui.setNextItemWidth(positionWidth);
        String positionLabel = positionRowWidth < 420.0f ? "Chunk##position" : "Chunk/Sub-beat##position";
        if (ImGui.inputText(positionLabel, timelinePositionField)) {
            Double targetBeat = parseChunkPosition(timelinePositionField.get());
            if (targetBeat != null) {
                seekTimelineBeat(targetBeat, maxBeat);
            }
        }
        if (!ImGui.isItemActive()) timelinePositionField.set(formatChunkPosition(playhead[0]));
        ImGui.sameLine();
        String positionText = ImGui.getContentRegionAvailX() >= 240.0f
                ? String.format(Locale.ROOT, "Beat %.3f  ·  %s", playhead[0], formatChunkPosition(playhead[0]))
                : String.format(Locale.ROOT, "Beat %.3f", playhead[0]);
        ImGui.text(positionText);

        float canvasWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float canvasHeight = Math.max(1.0f, ImGui.getContentRegionAvailY());
        float canvasLeft = ImGui.getCursorScreenPosX();
        float canvasTop = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton("##timeline-canvas", canvasWidth, canvasHeight);
        drawTimelineCanvas(canvasLeft, canvasTop, canvasWidth, canvasHeight, maxBeat);
        ImGui.end();
    }

    private void drawTimelineControls() {
        float contentWidth = ImGui.getContentRegionAvailX();
        ImGui.textColored(0xFF66CCFF, "特效时间轴");
        ImGui.sameLine();
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 3.0f, 2.0f);
        drawTimelineControl("timeline-play-pause", "", TimelineControl.PLAY_PAUSE);
        ImGui.sameLine();
        drawTimelineControl("timeline-return-to-start", "回到开头", TimelineControl.RETURN_TO_START);
        ImGui.sameLine();
        if (contentWidth >= 540.0f) {
            drawTimelineControl("timeline-previous", "前移（←）", TimelineControl.PREVIOUS);
            ImGui.sameLine();
            drawTimelineControl("timeline-next", "后移（→）", TimelineControl.NEXT);
        } else {
            ImGui.newLine();
            ImGui.indent();
            drawTimelineControl("timeline-previous", "前移（←）", TimelineControl.PREVIOUS);
            ImGui.sameLine();
            drawTimelineControl("timeline-next", "后移（→）", TimelineControl.NEXT);
            ImGui.unindent();
        }
        ImGui.popStyleVar();
    }

    private void drawTimelineControl(String id, String tooltip, TimelineControl control) {
        float size = 24.0f;
        if (ImGui.invisibleButton("##" + id, size, size)) activateTimelineControl(control);
        float left = ImGui.getItemRectMinX();
        float top = ImGui.getItemRectMinY();
        float right = ImGui.getItemRectMaxX();
        float bottom = ImGui.getItemRectMaxY();
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        ImDrawList drawList = ImGui.getWindowDrawList();
        if (hovered || active) {
            drawList.addRectFilled(left, top, right, bottom, active ? 0xFF36475B : 0xFF263443, 3.0f);
        }
        drawTimelineControlIcon(drawList, control, (left + right) * 0.5f, (top + bottom) * 0.5f,
                hovered ? 0xFF8EDCFF : 0xFFD5E2EF);
        if (hovered) {
            String currentTooltip = control == TimelineControl.PLAY_PAUSE
                    ? RhythmcMakerClient.isEffectEditorPlaybackRequested() ? "暂停" : "播放"
                    : tooltip;
            ImGui.setTooltip(currentTooltip);
        }
    }

    private void activateTimelineControl(TimelineControl control) {
        switch (control) {
            case PLAY_PAUSE -> togglePlaybackAction.run();
            case RETURN_TO_START -> {
                replayAction.run();
                playhead[0] = 0.0f;
                timelinePositionField.set(formatChunkPosition(0.0));
                timelineLastSeekBeat = 0.0;
                status = "已回到开头";
            }
            case PREVIOUS -> moveTimelinePlayhead(-1, false);
            case NEXT -> moveTimelinePlayhead(1, false);
        }
    }

    private static void drawTimelineControlIcon(ImDrawList drawList, TimelineControl control,
                                                float centerX, float centerY, int colour) {
        switch (control) {
            case PLAY_PAUSE -> {
                if (RhythmcMakerClient.isEffectEditorPlaybackRequested()) {
                    drawList.addRectFilled(centerX - 5.0f, centerY - 6.0f, centerX - 1.0f, centerY + 6.0f, colour);
                    drawList.addRectFilled(centerX + 1.0f, centerY - 6.0f, centerX + 5.0f, centerY + 6.0f, colour);
                } else {
                    drawList.addTriangleFilled(centerX - 4.0f, centerY - 6.0f,
                            centerX - 4.0f, centerY + 6.0f, centerX + 6.0f, centerY, colour);
                }
            }
            case RETURN_TO_START -> {
                drawList.addRectFilled(centerX - 5.0f, centerY - 5.0f,
                        centerX + 5.0f, centerY + 5.0f, colour);
            }
            case PREVIOUS -> {
                drawList.addRectFilled(centerX + 4.0f, centerY - 6.0f, centerX + 6.0f, centerY + 6.0f, colour);
                drawList.addTriangleFilled(centerX + 2.0f, centerY - 6.0f,
                        centerX + 2.0f, centerY + 6.0f, centerX - 6.0f, centerY, colour);
            }
            case NEXT -> {
                drawList.addRectFilled(centerX - 6.0f, centerY - 6.0f, centerX - 4.0f, centerY + 6.0f, colour);
                drawList.addTriangleFilled(centerX - 2.0f, centerY - 6.0f,
                        centerX - 2.0f, centerY + 6.0f, centerX + 6.0f, centerY, colour);
            }
        }
    }

    public boolean handlePlaybackKey(KeyInput input) {
        if (!ImGuiRuntime.isInitialized()) return false;
        ImGuiIO io = ImGui.getIO();
        if (io.getWantCaptureKeyboard() || ImGui.isAnyItemActive()) return false;
        if (input.key() == GLFW.GLFW_KEY_SPACE) {
            if (!spaceKeyDown) {
                spaceKeyDown = true;
                togglePlaybackAction.run();
            }
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_LEFT || input.key() == GLFW.GLFW_KEY_RIGHT) {
            boolean fast = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            moveTimelinePlayhead(input.key() == GLFW.GLFW_KEY_LEFT ? -1 : 1, fast);
            return true;
        }
        return false;
    }

    public void handlePlaybackKeyRelease(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_SPACE) spaceKeyDown = false;
    }

    private void moveTimelinePlayhead(int direction, boolean fast) {
        double currentBeat = RhythmcMakerClient.isEffectEditorPlaybackRequested()
                ? RhythmcMakerClient.effectEditorCurrentBeat() : playhead[0];
        double normalStep = 1.0;
        double divisionsPerChunk = chart == null ? normalStep : Math.max(1.0, Math.min(32.0, chart.divisionsPerChunk));
        double chunkStep = Math.max(normalStep, divisionsPerChunk);
        seekTimelineBeat(currentBeat + direction * (fast ? chunkStep : normalStep), timelineMaxBeat());
    }

    /** Returns the current ImGui preview window rectangle used for mouse camera controls. */
    public boolean isPreviewRegion(double mouseX, double mouseY) {
        return previewWindowX >= 0.0f
                && mouseX >= previewWindowX
                && mouseX <= previewWindowX + previewWindowWidth
                && mouseY >= previewWindowY
                && mouseY <= previewWindowY + previewWindowHeight;
    }

    private void drawTimelineCanvas(float left, float top, float width, float height, double maxBeat) {
        int visibleRowCapacity = Math.max(1, (int) Math.floor((height - 54.0f) / 24.0f));
        List<TimelineRow> rows = buildTimelineRows(visibleRowCapacity, timelineRowOffset);
        if (rows.isEmpty()) rows = List.of(new TimelineRow("事件", "暂无事件", -1, List.of(), false));
        float trackWidth = Math.min(240.0f, Math.max(96.0f, width * 0.26f));
        float timelineLeft = left + trackWidth;
        float timelineRight = left + width;
        float rulerHeight = 34.0f;
        float footerHeight = 20.0f;
        float rowsTop = top + rulerHeight;
        float rowsBottom = Math.max(rowsTop + 24.0f, top + height - footerHeight);
        timelineBoundsLeft = left;
        timelineBoundsTop = top;
        timelineBoundsRight = timelineRight;
        timelineBoundsBottom = top + height;
        timelineVisibleRowCapacity = visibleRowCapacity;
        float timelineWidth = Math.max(1.0f, timelineRight - timelineLeft);
        double zoomSpan = Math.max(0.05, Math.min(1.0, timelineZoomMax - timelineZoomMin));
        timelineZoomMin = clamp01(timelineZoomMin);
        timelineZoomMax = Math.min(1.0, timelineZoomMin + zoomSpan);
        timelineZoomMin = Math.max(0.0, timelineZoomMax - zoomSpan);
        double minBeat = timelineZoomMin * maxBeat;
        double visibleBeatSpan = Math.max(1.0e-6, (timelineZoomMax - timelineZoomMin) * maxBeat);
        double maxVisibleBeat = minBeat + visibleBeatSpan;
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(left, top, timelineRight, top + height, true);
        drawList.addRectFilled(left, top, timelineRight, top + height, 0xF20D1117);
        drawList.addRectFilled(left, top, timelineRight, rowsTop, 0xFF171D26);
        drawList.addRectFilled(left, rowsTop, timelineLeft, rowsBottom, 0xFF12171E);
        drawList.addLine(timelineLeft, top, timelineLeft, rowsBottom, 0xFF4D5968, 1.0f);
        drawList.addLine(left, rowsTop, timelineRight, rowsTop, 0xFF4D5968, 1.0f);

        double majorStep = timelineMajorStep(visibleBeatSpan, timelineWidth);
        double minorStep = Math.max(majorStep / 4.0, 0.0001);
        double firstMinor = Math.floor(minBeat / minorStep) * minorStep;
        for (double beat = firstMinor; beat <= maxVisibleBeat + minorStep * 0.5; beat += minorStep) {
            if (beat < -0.0001) continue;
            float markerX = timelineBeatToX(beat, minBeat, maxVisibleBeat, timelineLeft, timelineWidth);
            boolean major = Math.abs(beat / majorStep - Math.rint(beat / majorStep)) < 1.0e-5;
            int colour = major ? 0xFF536071 : 0xFF2B333F;
            drawList.addLine(markerX, top, markerX, rowsBottom, colour, major ? 1.0f : 0.5f);
            if (major && markerX >= timelineLeft - 40.0f && markerX <= timelineRight + 4.0f) {
                String label = String.format(Locale.ROOT, "B %.2f  %.2fs", beat, ChartTiming.beatToSeconds(chart, beat));
                drawList.addText(markerX + 4.0f, top + 7.0f, 0xFFD5DCE5, label);
            }
        }

        List<TimelineHit> hits = new ArrayList<>();
        float rowY = rowsTop;
        for (TimelineRow row : rows) {
            if (row.groupHeader()) {
                drawList.addRectFilled(left, rowY, timelineRight, rowY + 21.0f, 0xFF202A36);
                drawList.addText(left + 8.0f, rowY + 4.0f, 0xFF9FD7FF, displayText(row.groupLabel()));
                drawList.addLine(left, rowY + 20.0f, timelineRight, rowY + 20.0f, 0xFF3A4654, 1.0f);
                rowY += 21.0f;
                continue;
            }
            float rowBottom = rowY + 24.0f;
            drawList.addLine(left, rowBottom, timelineRight, rowBottom, 0xFF27303B, 1.0f);
            int labelColour = row.eventIndex() == selectedIndex ? 0xFFFFFFFF : 0xFFB5BFCC;
            drawList.addText(left + 13.0f, rowY + 5.0f, labelColour, displayText(row.label()));
            float markerY = rowY + 12.0f;
            for (TimelineMarker marker : row.markers()) {
                float markerX = timelineBeatToX(marker.beat(), minBeat, maxVisibleBeat, timelineLeft, timelineWidth);
                if (markerX < timelineLeft - 9.0f || markerX > timelineRight + 9.0f) continue;
                boolean selected = marker.eventIndex() == selectedIndex;
                int colour = marker.kind() == TimelineMarkerKind.EVENT ? 0xFF4DB7FF : 0xFFE9B96E;
                if (selected && marker.kind() == TimelineMarkerKind.EVENT) {
                    drawList.addCircleFilled(markerX, markerY, 8.0f, 0xFFFFFFFF, 16);
                }
                if (marker.kind() == TimelineMarkerKind.EVENT) {
                    drawList.addTriangleFilled(markerX, markerY - 7.0f, markerX + 7.0f, markerY,
                            markerX, markerY + 7.0f, colour);
                    drawList.addTriangleFilled(markerX, markerY - 7.0f, markerX - 7.0f, markerY,
                            markerX, markerY + 7.0f, colour);
                } else {
                    drawList.addCircleFilled(markerX, markerY, 4.5f, colour, 12);
                }
                hits.add(new TimelineHit(markerX, markerY, marker));
            }
            rowY = rowBottom;
        }

        float playheadX = timelineBeatToX(playhead[0], minBeat, maxVisibleBeat, timelineLeft, timelineWidth);
        playheadX = Math.max(timelineLeft, Math.min(timelineRight, playheadX));
        drawList.addLine(playheadX, top, playheadX, rowsBottom, 0xFFFFFFFF, 1.5f);
        drawList.addTriangleFilled(playheadX - 6.0f, top + 1.0f, playheadX + 6.0f, top + 1.0f,
                playheadX, top + 11.0f, 0xFFFFFFFF);

        float footerTop = top + height - footerHeight;
        drawList.addRectFilled(left, footerTop, timelineRight, top + height, 0xFF151B23);
        drawList.addRectFilled(timelineLeft, footerTop + 6.0f, timelineRight, footerTop + 14.0f, 0xFF303A47);
        float rangeLeft = (float) (timelineLeft + timelineWidth * timelineZoomMin);
        float rangeRight = (float) (timelineLeft + timelineWidth * timelineZoomMax);
        drawList.addRectFilled(rangeLeft, footerTop + 4.0f, rangeRight, footerTop + 16.0f, 0xFF61A7D8);
        drawTimelineScrollBar(drawList, timelineRight - 10.0f, rowsTop, rowsBottom, visibleRowCapacity);
        drawList.addText(left + 8.0f, footerTop + 4.0f, 0xFF9CA9B8,
                String.format(Locale.ROOT, "%.0f%%", (1.0 / Math.max(0.05, zoomSpan)) * 100.0));
        drawList.popClipRect();

        handleTimelineInteraction(left, top, width, height, timelineLeft, timelineWidth, footerTop,
                maxBeat, minBeat, maxVisibleBeat, hits);
    }

    private void handleTimelineInteraction(float left, float top, float width, float height, float timelineLeft,
                                            float timelineWidth, float footerTop, double maxBeat,
                                            double minBeat, double maxVisibleBeat, List<TimelineHit> hits) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        float right = left + width;
        boolean hovered = ImGui.isMouseHoveringRect(left, top, right, top + height, true);
        boolean contentHovered = hovered && mouseX >= timelineLeft && mouseY >= top && mouseY < footerTop;
        ImGuiIO io = ImGui.getIO();
        boolean scrollBarHovered = hovered && mouseX >= timelineRight(timelineLeft, timelineWidth) - 12.0f
                && mouseY >= top + 34.0f && mouseY < footerTop;
        if (hovered && Math.abs(io.getMouseWheel()) > 0.001f) {
            float wheel = io.getMouseWheel();
            if (isControlPressed()) {
                if (contentHovered) zoomTimelineAt(mouseX, timelineLeft, timelineWidth, wheel);
            } else {
                int direction = wheel > 0.0f ? -1 : 1;
                timelineRowOffset = Math.max(0, Math.min(maxTimelineRowOffset(), timelineRowOffset + direction * 3));
            }
            io.setMouseWheel(0.0f);
        }
        if (ImGui.isMouseClicked(0) && hovered) {
            if (scrollBarHovered) {
                timelineDraggingScrollBar = true;
                timelineScrollBarDragOffset = mouseY - timelineScrollBarTop(top + 34.0f, footerTop);
            } else if (mouseY >= footerTop) {
                timelineDraggingRange = true;
                moveTimelineRange(mouseX, timelineLeft, timelineWidth);
            } else if (contentHovered) {
                TimelineHit hit = findTimelineHit(mouseX, mouseY, hits);
                if (hit != null) {
                    TimelineMarker marker = hit.marker();
                    select(marker.eventIndex());
                    seekTimelineBeat(marker.beat(), maxBeat);
                    if (registry.find(eventType(workingEffects.get(marker.eventIndex()))) != null) {
                        timelineDraggedEvent = marker.eventIndex();
                        timelineDraggedChannel = marker.channelIndex();
                        timelineDraggedKeyframe = marker.keyframeIndex();
                        timelineDragBeatOffset = marker.beat() - timelineXToBeat(mouseX, timelineLeft, timelineWidth,
                                minBeat, maxVisibleBeat);
                    }
                } else {
                    timelineDraggingPlayhead = true;
                    seekTimelineBeat(timelineXToBeat(mouseX, timelineLeft, timelineWidth, minBeat, maxVisibleBeat), maxBeat);
                }
            }
        }
        if (timelineDraggingRange && ImGui.isMouseDown(0)) moveTimelineRange(mouseX, timelineLeft, timelineWidth);
        if (timelineDraggingScrollBar && ImGui.isMouseDown(0)) moveTimelineScrollBar(mouseY, top + 34.0f, footerTop);
        if (timelineDraggingPlayhead && ImGui.isMouseDown(0)) {
            seekTimelineBeat(timelineXToBeat(mouseX, timelineLeft, timelineWidth, minBeat, maxVisibleBeat), maxBeat);
        }
        if (timelineDraggedEvent >= 0 && ImGui.isMouseDown(0)) {
            double target = timelineXToBeat(mouseX, timelineLeft, timelineWidth, minBeat, maxVisibleBeat) + timelineDragBeatOffset;
            target = Math.max(0.0, Math.min(maxBeat, target));
            TimelineMarker marker = new TimelineMarker(timelineDraggedEvent, timelineDraggedChannel,
                    timelineDraggedKeyframe, target,
                    timelineDraggedChannel < 0 ? TimelineMarkerKind.EVENT : TimelineMarkerKind.KEYFRAME);
            updateTimelineMarkerBeat(marker, target);
            seekTimelineBeat(target, maxBeat);
        }
        if (ImGui.isMouseReleased(0)) {
            timelineDraggingRange = false;
            timelineDraggingScrollBar = false;
            timelineDraggingPlayhead = false;
            timelineDraggedEvent = -1;
            timelineDraggedChannel = -1;
            timelineDraggedKeyframe = -1;
            timelineLastSeekBeat = Double.NaN;
        }
    }

    private List<TimelineRow> buildTimelineRows(int visibleRowCapacity, int rowOffset) {
        List<TimelineRow> rows = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        for (JsonObject event : workingEffects) {
            String type = eventType(event);
            if (!groups.contains(type)) groups.add(type);
        }
        for (String group : groups) {
            rows.add(new TimelineRow(group, group, -1, List.of(), true));
            for (int eventIndex = 0; eventIndex < workingEffects.size(); eventIndex++) {
                JsonObject event = workingEffects.get(eventIndex);
                if (!group.equals(eventType(event))) continue;
                String label = String.format(Locale.ROOT, "  #%d  Beat %.3f", eventIndex + 1, eventBeat(event));
                rows.add(new TimelineRow(group, label, eventIndex, timelineMarkers(eventIndex, event), false));
            }
        }
        return rows;
    }

    private List<TimelineMarker> timelineMarkers(int eventIndex, JsonObject event) {
        List<TimelineMarker> markers = new ArrayList<>();
        markers.add(new TimelineMarker(eventIndex, -1, -1, eventBeat(event), TimelineMarkerKind.EVENT));
        if (!event.has("animation") || !event.get("animation").isJsonObject()) return markers;
        JsonObject animation = event.getAsJsonObject("animation");
        if (!animation.has("channels") || !animation.get("channels").isJsonArray()) return markers;
        JsonArray channels = animation.getAsJsonArray("channels");
        for (int channelIndex = 0; channelIndex < channels.size(); channelIndex++) {
            JsonElement channelElement = channels.get(channelIndex);
            if (!channelElement.isJsonObject()) continue;
            JsonObject channel = channelElement.getAsJsonObject();
            if (!channel.has("keyframes") || !channel.get("keyframes").isJsonArray()) continue;
            JsonArray keyframes = channel.getAsJsonArray("keyframes");
            for (int keyframeIndex = 0; keyframeIndex < keyframes.size(); keyframeIndex++) {
                JsonElement keyframeElement = keyframes.get(keyframeIndex);
                if (!keyframeElement.isJsonObject()) continue;
                JsonObject keyframe = keyframeElement.getAsJsonObject();
                double beat = readJsonDouble(keyframe, "beat", eventBeat(event));
                markers.add(new TimelineMarker(eventIndex, channelIndex, keyframeIndex, Math.max(0.0, beat),
                        TimelineMarkerKind.KEYFRAME));
            }
        }
        return markers;
    }

    private double timelineMaxBeat() {
        double maxBeat = Math.max(1.0, chart == null ? 1.0 : chart.totalBeats);
        for (int eventIndex = 0; eventIndex < workingEffects.size(); eventIndex++) {
            JsonObject event = workingEffects.get(eventIndex);
            maxBeat = Math.max(maxBeat, eventBeat(event));
            for (TimelineMarker marker : timelineMarkers(eventIndex, event)) maxBeat = Math.max(maxBeat, marker.beat());
        }
        return maxBeat;
    }

    private void updateTimelineMarkerBeat(TimelineMarker marker, double beat) {
        if (marker.eventIndex() < 0 || marker.eventIndex() >= workingEffects.size()) return;
        JsonObject event = workingEffects.get(marker.eventIndex());
        if (marker.kind() == TimelineMarkerKind.EVENT) {
            event.addProperty("beat", beat);
            if (marker.eventIndex() == selectedIndex) beatField.set(beat);
        } else if (event.has("animation") && event.get("animation").isJsonObject()) {
            JsonObject animation = event.getAsJsonObject("animation");
            if (!animation.has("channels") || !animation.get("channels").isJsonArray()) return;
            JsonArray channels = animation.getAsJsonArray("channels");
            if (marker.channelIndex() < 0 || marker.channelIndex() >= channels.size()) return;
            JsonElement channelElement = channels.get(marker.channelIndex());
            if (!channelElement.isJsonObject()) return;
            JsonObject channel = channelElement.getAsJsonObject();
            if (!channel.has("keyframes") || !channel.get("keyframes").isJsonArray()) return;
            JsonArray keyframes = channel.getAsJsonArray("keyframes");
            if (marker.keyframeIndex() < 0 || marker.keyframeIndex() >= keyframes.size()) return;
            JsonElement keyframeElement = keyframes.get(marker.keyframeIndex());
            if (!keyframeElement.isJsonObject()) return;
            keyframeElement.getAsJsonObject().addProperty("beat", beat);
        }
        dirty = true;
    }

    private void seekTimelineBeat(double beat, double maxBeat) {
        double target = Math.max(0.0, Math.min(maxBeat, beat));
        playhead[0] = (float) target;
        if (Double.isNaN(timelineLastSeekBeat) || Math.abs(timelineLastSeekBeat - target) > 0.0001) {
            seekAction.accept(target);
            timelineLastSeekBeat = target;
        }
        status = "播放头已跳转到 " + formatChunkPosition(target);
    }

    private void handleCameraWheelOutsideTimeline() {
        ImGuiIO io = ImGui.getIO();
        float wheel = io.getMouseWheel();
        if (Math.abs(wheel) <= 0.001f) return;
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        boolean overTimeline = mouseX >= timelineBoundsLeft && mouseX <= timelineBoundsRight
                && mouseY >= timelineBoundsTop && mouseY <= timelineBoundsBottom;
        if (!overTimeline) {
            RhythmcMakerClient.adjustEffectEditorCameraHeight(wheel);
            io.setMouseWheel(0.0f);
        }
    }

    private boolean isControlPressed() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private int totalTimelineRows() {
        List<String> types = new ArrayList<>();
        for (JsonObject event : workingEffects) {
            String type = eventType(event);
            if (!types.contains(type)) types.add(type);
        }
        return types.size() + workingEffects.size();
    }

    private int maxTimelineRowOffset() {
        return Math.max(0, totalTimelineRows() - Math.max(1, timelineVisibleRowCapacity));
    }

    private void drawTimelineScrollBar(ImDrawList drawList, float left, float top, float bottom, int visibleRows) {
        int totalRows = Math.max(1, totalTimelineRows());
        float trackHeight = Math.max(1.0f, bottom - top);
        float thumbHeight = Math.max(24.0f, trackHeight * Math.min(1.0f, visibleRows / (float) totalRows));
        float travel = Math.max(0.0f, trackHeight - thumbHeight);
        float progress = maxTimelineRowOffset() == 0 ? 0.0f : timelineRowOffset / (float) maxTimelineRowOffset();
        float thumbTop = top + travel * progress;
        drawList.addRectFilled(left, top, left + 8.0f, bottom, 0xFF202832, 3.0f);
        drawList.addRectFilled(left, thumbTop, left + 8.0f, thumbTop + thumbHeight, 0xFF6B91B5, 3.0f);
    }

    private float timelineScrollBarTop(float top, float bottom) {
        int totalRows = Math.max(1, totalTimelineRows());
        float trackHeight = Math.max(1.0f, bottom - top);
        float thumbHeight = Math.max(24.0f, trackHeight * Math.min(1.0f, timelineVisibleRowCapacity / (float) totalRows));
        float travel = Math.max(0.0f, trackHeight - thumbHeight);
        float progress = maxTimelineRowOffset() == 0 ? 0.0f : timelineRowOffset / (float) maxTimelineRowOffset();
        return top + travel * progress;
    }

    private void moveTimelineScrollBar(float mouseY, float top, float bottom) {
        int totalRows = Math.max(1, totalTimelineRows());
        float trackHeight = Math.max(1.0f, bottom - top);
        float thumbHeight = Math.max(24.0f, trackHeight * Math.min(1.0f, timelineVisibleRowCapacity / (float) totalRows));
        float travel = Math.max(1.0f, trackHeight - thumbHeight);
        float target = Math.max(0.0f, Math.min(travel, mouseY - timelineScrollBarDragOffset - top));
        timelineRowOffset = (int) Math.round(target / travel * maxTimelineRowOffset());
    }

    private float timelineRight(float timelineLeft, float timelineWidth) {
        return timelineLeft + timelineWidth;
    }

    private void zoomTimelineAt(float mouseX, float timelineLeft, float timelineWidth, float wheel) {
        double focus = clamp01((mouseX - timelineLeft) / Math.max(1.0f, timelineWidth));
        double currentSpan = Math.max(0.05, timelineZoomMax - timelineZoomMin);
        double nextSpan = Math.max(0.05, Math.min(1.0, currentSpan * (wheel > 0.0f ? 0.8 : 1.25)));
        double focusBeat = timelineZoomMin + focus * currentSpan;
        timelineZoomMin = Math.max(0.0, Math.min(1.0 - nextSpan, focusBeat - focus * nextSpan));
        timelineZoomMax = timelineZoomMin + nextSpan;
    }

    private void moveTimelineRange(float mouseX, float timelineLeft, float timelineWidth) {
        double centre = clamp01((mouseX - timelineLeft) / Math.max(1.0f, timelineWidth));
        double span = Math.max(0.05, timelineZoomMax - timelineZoomMin);
        timelineZoomMin = Math.max(0.0, Math.min(1.0 - span, centre - span * 0.5));
        timelineZoomMax = timelineZoomMin + span;
    }

    private static TimelineHit findTimelineHit(float mouseX, float mouseY, List<TimelineHit> hits) {
        TimelineHit closest = null;
        float closestDistance = 11.0f;
        for (TimelineHit hit : hits) {
            float dx = hit.x() - mouseX;
            float dy = hit.y() - mouseY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < closestDistance) {
                closest = hit;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static float timelineBeatToX(double beat, double minBeat, double maxBeat, float left, float width) {
        if (maxBeat <= minBeat) return left;
        double amount = (beat - minBeat) / (maxBeat - minBeat);
        return left + (float) (Math.max(0.0, Math.min(1.0, amount)) * width);
    }

    private static double timelineXToBeat(float x, float left, float width, double minBeat, double maxBeat) {
        double amount = clamp01((x - left) / Math.max(1.0f, width));
        return minBeat + amount * (maxBeat - minBeat);
    }

    private static double timelineMajorStep(double visibleSpan, float width) {
        double target = visibleSpan / Math.max(3.0, width / 100.0);
        double magnitude = Math.pow(10.0, Math.floor(Math.log10(Math.max(0.0001, target))));
        double normalised = target / magnitude;
        double step = normalised <= 1.0 ? 1.0 : normalised <= 2.0 ? 2.0 : normalised <= 5.0 ? 5.0 : 10.0;
        return Math.max(0.25, step * magnitude);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double readJsonDouble(JsonObject object, String name, double fallback) {
        try {
            if (!object.has(name) || !object.get(name).isJsonPrimitive()) return fallback;
            double value = object.get(name).getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record TimelineHit(float x, float y, TimelineMarker marker) {
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
        try {
            double value = event != null && event.has("beat") ? event.get("beat").getAsDouble() : 0.0;
            return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
        }
        catch (RuntimeException ignored) { return 0.0; }
    }

    private static JsonElement readElement(JsonObject event, String name) {
        if (event == null) return null;
        if (event.has(name)) return event.get(name);
        if (event.has("properties") && event.get("properties").isJsonObject()) return event.getAsJsonObject("properties").get(name);
        return null;
    }

    private static String readString(JsonObject event, String name, String fallback) {
        try { JsonElement element = readElement(event, name); return element != null && element.isJsonPrimitive() ? displayText(element.getAsString()) : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String displayText(JsonElement element) {
        return element == null ? "" : displayText(element.toString());
    }

    private static String displayText(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\0' || Character.isISOControl(character) && character != '\n' && character != '\t') {
                result.append('\uFFFD');
            } else if (Character.isHighSurrogate(character)) {
                if (index + 1 < value.length() && Character.isLowSurrogate(value.charAt(index + 1))) {
                    result.append(character).append(value.charAt(++index));
                } else {
                    result.append('\uFFFD');
                }
            } else if (Character.isLowSurrogate(character)) {
                result.append('\uFFFD');
            } else {
                result.append(character);
            }
        }
        return result.toString();
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
        int suffix = value.indexOf('(');
        if (suffix >= 0) value = value.substring(0, suffix);
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
