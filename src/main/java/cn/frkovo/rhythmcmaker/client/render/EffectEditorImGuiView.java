package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartTiming;
import cn.frkovo.rhythmcmaker.client.RhythmcMakerClient;
import cn.frkovo.rhythmcmaker.common.effect.EffectPropertyDefinition;
import cn.frkovo.rhythmcmaker.common.effect.EffectPropertyValueKind;
import cn.frkovo.rhythmcmaker.common.effect.EffectTypeDefinition;
import cn.frkovo.rhythmcmaker.common.effect.EffectTypeRegistry;
import cn.frkovo.rhythmcmaker.common.effect.animation.EffectAnimationEvaluator;
import cn.frkovo.rhythmcmaker.common.effect.editor.EffectEditorLayout;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private final ChartManifest chart;
    private final List<JsonObject> workingEffects = new ArrayList<>();
    private final Runnable saveAction;
    private final Runnable closeAction;
    private final Runnable togglePlaybackAction;
    private final Runnable replayAction;
    private final Consumer<Double> seekAction;
    private final Consumer<String> statusAction;
    private final Consumer<Double> pitchAction;
    private final EffectTypeRegistry registry = EffectTypeRegistry.getInstance();
    private final ImGuiDockNodeAccess dockNodeAccess = ImGuiDockNodeAccess.getInstance();
    private final EffectParameterEditor parameterEditor = new EffectParameterEditor();
    private final EffectTrackLayoutModel layoutModel;
    private final EffectPreviewViewport viewport = new EffectPreviewViewport();
    private final Consumer<Integer> divisionsAction;
    private final ImInt divisionsField = new ImInt();
    private final ImDouble pitchPercentField = new ImDouble(100.0);
    private boolean inputLocked;
    private final List<EffectEditorLayout.KeyframePosition> keyframePositions = new ArrayList<>();
    private List<TimelineRow> cachedTimelineRows;
    private double cachedTimelineMaxBeat;
    private String contextTrackId;
    private double contextBeat;
    private String pendingDeleteTrack;
    private boolean closeRequested;
    private Runnable confirmedCloseAction;
    private final ImDouble beatField = new ImDouble();
    private final float[] playhead = new float[1];
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
    private float timelineAvailableRowsHeight;
    private boolean applyDefaultDockLayout;
    private int libraryDockNodeId;
    private int inspectorDockNodeId;
    private int timelineDockNodeId;
    private int selectedIndex;
    private int bufferIndex = Integer.MIN_VALUE;
    private String bufferType = "";
    private boolean dirty;
    private String status = "就绪";
    private boolean cameraDragLogged;
    private final Set<Integer> selectedIndices = new LinkedHashSet<>();
    private final Deque<EditorSnapshot> undoHistory = new ArrayDeque<>();
    private final Deque<EditorSnapshot> redoHistory = new ArrayDeque<>();
    private boolean restoringSnapshot;
    private boolean selectionDragging;
    private float selectionStartX;
    private float selectionStartY;
    private float selectionEndX;
    private float selectionEndY;
    private boolean parameterEditHistoryArmed;

    private enum TimelineMarkerKind { EVENT, KEYFRAME }

    private enum TimelineControl { PLAY_PAUSE, RETURN_TO_START, PREVIOUS, NEXT }

    private record TimelineMarker(int eventIndex, int channelIndex, int keyframeIndex, double beat,
                                  TimelineMarkerKind kind) {
    }

    private record TimelineRow(String groupLabel, String label, int eventIndex, List<TimelineMarker> markers,
                               boolean groupHeader) {
    }

    private record EditorSnapshot(List<JsonObject> effects, EffectEditorLayout layout,
                                  List<EffectEditorLayout.KeyframePosition> keyframes, int selectedIndex) {
    }

    public EffectEditorImGuiView(ChartManifest chart, Runnable saveAction, Runnable closeAction,
                                 Runnable togglePlaybackAction, Runnable replayAction,
                                 Consumer<Double> seekAction, Consumer<String> statusAction, Consumer<Integer> divisionsAction,
                                 Consumer<Double> pitchAction) {
        this.chart = chart;
        this.saveAction = saveAction;
        this.closeAction = closeAction;
        this.togglePlaybackAction = togglePlaybackAction;
        this.replayAction = replayAction;
        this.seekAction = seekAction;
        this.statusAction = statusAction;
        this.divisionsAction = divisionsAction;
        this.pitchAction = pitchAction;
        pitchPercentField.set(RhythmcMakerClient.getPlaybackPitchPercent());
        divisionsField.set(chart == null ? 1 : chart.divisionsPerChunk);
        this.layoutModel = new EffectTrackLayoutModel(chart == null ? null : chart.effectEditorLayout);
        if (chart != null && chart.effects != null) {
            for (JsonObject effect : chart.effects) {
                if (effect != null) workingEffects.add(effect.deepCopy());
            }
        }
        for (int index = 0; index < workingEffects.size(); index++) {
            JsonObject event = workingEffects.get(index);
            EffectEditorLayout.Position saved = chart.effectEditorLayout != null
                    && chart.effectEditorLayout.positions != null && index < chart.effectEditorLayout.positions.size()
                    ? chart.effectEditorLayout.positions.get(index) : null;
            int denominator = saved != null && saved.denominator >= 1 && saved.denominator <= 32
                    ? saved.denominator : inferDenominator(eventBeat(event));
            layoutModel.addEvent(eventType(event), eventBeat(event), denominator, saved == null ? null : saved.trackId);
        }
        this.selectedIndex = workingEffects.isEmpty() ? -1 : 0;
        if (chart != null && chart.effectEditorLayout != null && chart.effectEditorLayout.keyframes != null) {
            for (EffectEditorLayout.KeyframePosition position : chart.effectEditorLayout.keyframes) {
                keyframePositions.add(new EffectEditorLayout.KeyframePosition(position.eventIndex,
                        position.channelIndex, position.keyframeIndex, position.beat, position.denominator));
            }
        }
        this.playhead[0] = (float) selectedBeat();
        syncBuffers();
    }

    public void render(MinecraftClient client) {
        ImGuiIO io = ImGui.getIO();
        float width = Math.max(1.0f, io.getDisplaySizeX());
        float height = Math.max(1.0f, io.getDisplaySizeY());
        ImGui.beginDisabled(inputLocked);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0xFF171D26);
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0xFF171D26);
        ImGui.pushStyleColor(ImGuiCol.DockingEmptyBg, 0xFF171D26);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, 0xFF202A36);
        drawToolbar(width);
        drawDockspace(width, height);
        handleCameraDrag();
        if (chart == null) {
            drawEmptyState(width, height);
            ImGui.endDisabled();
            ImGui.popStyleColor(4);
            return;
        }
        float leftWidth = Math.min(270.0f, Math.max(120.0f, width * 0.18f));
        float rightWidth = Math.min(360.0f, Math.max(190.0f, width * 0.24f));
        float centerWidth = Math.max(1.0f, width - leftWidth - rightWidth);
        float top = 54.0f;
        float timelineHeight = height * 0.32f;
        float contentHeight = Math.max(1.0f, height - top - timelineHeight);
        drawLibrary(0.0f, top, leftWidth, contentHeight);
        drawInspector(leftWidth + centerWidth, top, rightWidth, contentHeight);
        drawTimeline(0.0f, top + contentHeight, width, timelineHeight);
        drawCloseConfirmation();
        handleCameraWheelOutsideTimeline();
        ImGui.endDisabled();
        ImGui.popStyleColor(4);
        if (inputLocked) {
            ImGui.openPopup("正在同步轨道");
        }
        if (ImGui.beginPopupModal("正在同步轨道", ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoMove)) {
            if (!inputLocked) ImGui.closeCurrentPopup();
            ImGui.text(status);
            ImGui.endPopup();
        }
        applyDefaultDockLayout = false;
    }

    private void handleCameraDrag() {
            if (!inputLocked && viewport.contains(ImGui.getMousePosX(), ImGui.getMousePosY()) && ImGui.isMouseDragging(2, 0.0f)) {
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

    private void drawDockspace(float width, float height) {
        ImGui.setNextWindowPos(0.0f, 46.0f, ImGuiCond.Always);
        ImGui.setNextWindowSize(width, Math.max(1.0f, height - 46.0f), ImGuiCond.Always);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        if (ImGui.begin("##effect-editor-dockspace", DOCKSPACE_FLAGS)) {
            int dockspaceId = ImGui.getID("##effect-editor-dockspace-node-v4");
            initializeDefaultDockLayout(dockspaceId, ImGui.getContentRegionAvailX(),
                    ImGui.getContentRegionAvailY());
            ImGui.dockSpace(dockspaceId, 0.0f, 0.0f,
                    ImGuiDockNodeFlags.PassthruCentralNode);
            if (dockNodeAccess.hasCentralDockspace(dockspaceId)) {
                imgui.internal.ImGuiDockNode center = dockNodeAccess.centralNode(dockspaceId);
                viewport.setBounds(center.getPosX(), center.getPosY(), center.getSizeX(), center.getSizeY());
                viewport.drawPreview(ImGui.getWindowDrawList());
            } else {
                viewport.setBounds(0.0f, 0.0f, 0.0f, 0.0f);
            }
        }
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void initializeDefaultDockLayout(int dockspaceId, float width, float height) {
        if (dockNodeAccess.hasCentralDockspace(dockspaceId)) return;
        if (dockNodeAccess.exists(dockspaceId)) imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);

        int previousDockspaceId = ImGui.getID("##effect-editor-dockspace-node");
        if (dockNodeAccess.exists(previousDockspaceId)) {
            imgui.internal.ImGui.dockBuilderRemoveNode(previousDockspaceId);
        }
        int incompleteDockspaceId = ImGui.getID("##effect-editor-dockspace-node-v2");
        if (dockNodeAccess.exists(incompleteDockspaceId)) {
            imgui.internal.ImGui.dockBuilderRemoveNode(incompleteDockspaceId);
        }
        dockNodeAccess.addDockSpaceNode(dockspaceId);
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId, Math.max(1.0f, width), Math.max(1.0f, height));

        ImInt timelineNode = new ImInt();
        ImInt mainNode = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Down, 0.30f, timelineNode, mainNode);

        ImInt libraryNode = new ImInt();
        ImInt centerAndInspectorNode = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(mainNode.get(), ImGuiDir.Left, 0.18f,
                libraryNode, centerAndInspectorNode);

        ImInt inspectorNode = new ImInt();
        ImInt centralNode = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(centerAndInspectorNode.get(), ImGuiDir.Right, 0.29f,
                inspectorNode, centralNode);

        imgui.internal.ImGui.dockBuilderDockWindow("特效库##effect-library", libraryNode.get());
        imgui.internal.ImGui.dockBuilderDockWindow("特效参数设置##effect-inspector", inspectorNode.get());
        imgui.internal.ImGui.dockBuilderDockWindow("特效时间轴##effect-timeline", timelineNode.get());
        imgui.internal.ImGui.dockBuilderFinish(dockspaceId);
        libraryDockNodeId = libraryNode.get();
        inspectorDockNodeId = inspectorNode.get();
        timelineDockNodeId = timelineNode.get();
        applyDefaultDockLayout = true;
    }

    public void dispose() {
        viewport.close();
        cameraDragLogged = false;
        parameterEditor.clear();
    }

    public void applyPendingEdits() {
        if (chart == null) return;
        syncEventFromBuffers();
    }

    public void saveToChart() {
        if (chart == null) return;
        if (!validateParameters()) throw new IllegalStateException(status);
        applyPendingEdits();
        chart.effects = new ArrayList<>();
        for (JsonObject effect : workingEffects) chart.effects.add(effect.deepCopy());
        chart.effectEditorLayout = layoutModel.snapshot();
        chart.effectEditorLayout.keyframes = new ArrayList<>();
        for (EffectEditorLayout.KeyframePosition position : keyframePositions) {
            chart.effectEditorLayout.keyframes.add(new EffectEditorLayout.KeyframePosition(position.eventIndex,
                    position.channelIndex, position.keyframeIndex, position.beat, position.denominator));
        }
    }

    private EditorSnapshot snapshot() {
        List<JsonObject> effects = new ArrayList<>();
        for (JsonObject effect : workingEffects) effects.add(effect.deepCopy());
        EffectEditorLayout layout = layoutModel.snapshot();
        layout.keyframes = new ArrayList<>();
        for (EffectEditorLayout.KeyframePosition position : keyframePositions) {
            layout.keyframes.add(new EffectEditorLayout.KeyframePosition(position.eventIndex,
                    position.channelIndex, position.keyframeIndex, position.beat, position.denominator));
        }
        List<EffectEditorLayout.KeyframePosition> keyframes = new ArrayList<>();
        for (EffectEditorLayout.KeyframePosition position : keyframePositions) {
            keyframes.add(new EffectEditorLayout.KeyframePosition(position.eventIndex, position.channelIndex,
                    position.keyframeIndex, position.beat, position.denominator));
        }
        return new EditorSnapshot(effects, layout, keyframes, selectedIndex);
    }

    private void rememberForUndo() {
        if (restoringSnapshot) return;
        undoHistory.push(snapshot());
        redoHistory.clear();
    }

    private void restoreSnapshot(EditorSnapshot snapshot) {
        restoringSnapshot = true;
        try {
            workingEffects.clear();
            for (JsonObject effect : snapshot.effects()) workingEffects.add(effect.deepCopy());
            layoutModel.restore(snapshot.layout());
            keyframePositions.clear();
            for (EffectEditorLayout.KeyframePosition position : snapshot.keyframes()) {
                keyframePositions.add(new EffectEditorLayout.KeyframePosition(position.eventIndex,
                        position.channelIndex, position.keyframeIndex, position.beat, position.denominator));
            }
            selectedIndex = snapshot.selectedIndex();
            selectedIndices.clear();
            if (selectedIndex >= 0) selectedIndices.add(selectedIndex);
            cachedTimelineRows = null;
            bufferIndex = Integer.MIN_VALUE;
            syncBuffers();
        } finally {
            restoringSnapshot = false;
        }
    }

    private void undo() {
        if (undoHistory.isEmpty()) return;
        redoHistory.push(snapshot());
        restoreSnapshot(undoHistory.pop());
        dirty = true;
        status = "已撤销";
    }

    private void redo() {
        if (redoHistory.isEmpty()) return;
        undoHistory.push(snapshot());
        restoreSnapshot(redoHistory.pop());
        dirty = true;
        status = "已恢复";
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    public boolean validateParameters() {
        List<String> errors = parameterEditor.errors(workingEffects);
        if (errors.isEmpty()) return true;
        status = "参数校验失败：" + errors.get(0);
        statusAction.accept(status);
        return false;
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
        boolean canUndo = !undoHistory.isEmpty();
        boolean canRedo = !redoHistory.isEmpty();
        ImGui.beginDisabled(!canUndo);
        if (ImGui.button("撤销##effect-undo")) undo();
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(!canRedo);
        if (ImGui.button("恢复##effect-redo")) redo();
        ImGui.endDisabled();
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
        ImGui.setNextWindowBgAlpha(1.0f);
        if (applyDefaultDockLayout) ImGui.setNextWindowDockID(libraryDockNodeId, ImGuiCond.Always);
        if (!ImGui.begin("特效库##effect-library", PANEL_FLAGS)) {
            ImGui.end();
            return;
        }
        ImGui.textColored(0xFF66CCFF, "特效库");
        if (ImGui.inputInt("每 Chunk 分数", divisionsField, 1, 1, imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue)) {
            int divisions = divisionsField.get();
            if (divisions >= 1 && divisions <= 32 && divisions != chart.divisionsPerChunk) {
                rememberForUndo();
                divisionsAction.accept(divisions);
            }
            else divisionsField.set(chart.divisionsPerChunk);
        }
        ImGui.textWrapped(status);
        ImGui.separator();
        for (EffectTypeDefinition definition : registry.definitions()) {
            ImGui.pushID(definition.eventType());
            if (ImGui.selectable(displayText(definition.displayName()) + "##add", false, ImGuiSelectableFlags.SpanAllColumns)) {
                rememberForUndo();
                layoutModel.addTrack(definition.eventType());
                cachedTimelineRows = null;
                dirty = true;
                status = "已新增空轨道：" + definition.displayName();
            }
            ImGui.popID();
        }
        ImGui.end();
    }

    public double playheadBeat() {
        return Math.max(0.0, playhead[0]);
    }

    public void presentPreview(MinecraftClient client) {
        viewport.present(client);
    }

    public boolean isInputLocked() {
        return inputLocked;
    }

    public void setInputLocked(boolean locked, String message) {
        inputLocked = locked;
        status = message;
        if (!locked) divisionsField.set(chart.divisionsPerChunk);
        timelineDraggedEvent = -1;
        timelineDraggingRange = false;
        timelineDraggingScrollBar = false;
        timelineDraggingPlayhead = false;
        spaceKeyDown = false;
        statusAction.accept(message);
    }

    private void drawInspector(float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(width, height, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowBgAlpha(1.0f);
        if (applyDefaultDockLayout) ImGui.setNextWindowDockID(inspectorDockNodeId, ImGuiCond.Always);
        if (!ImGui.begin("特效参数设置##effect-inspector", PANEL_FLAGS)) {
            ImGui.end();
            return;
        }
        ImGui.textColored(0xFF66CCFF, "特效参数设置");
        if (selectedIndices.size() > 1) {
            ImGui.textDisabled("无法设置多个特效参数");
            if (ImGui.button("复制选中特效##copy-selected")) copySelectedEffects();
            ImGui.sameLine();
            if (ImGui.button("删除选中特效##delete-selected")) deleteSelectedEffects();
            ImGui.end();
            return;
        }
        JsonObject event = selectedEvent();
        if (event == null) {
            ImGui.textDisabled("请选择特效");
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
        ImGui.text("位置：" + layoutModel.position(selectedIndex).label());
        ImGui.separator();
        boolean parameterChanged = parameterEditor.render(event, workingEffects, chart);
        if (parameterChanged) {
            if (!parameterEditHistoryArmed && !restoringSnapshot) rememberForUndo();
            parameterEditHistoryArmed = true;
            dirty = true;
        }
        if (!ImGui.isAnyItemActive()) parameterEditHistoryArmed = false;
        EffectAnimationEvaluator.evaluate(event, RhythmcMakerClient.effectEditorCurrentBeat()).ifPresent(sample -> {
            if (!sample.values().isEmpty()) {
                ImGui.separator();
                ImGui.text("动画采样 " + formatChunkPosition(sample.beat()));
                for (var value : sample.values()) ImGui.textDisabled(displayText(value.channelName()) + "：" + displayText(value.value().toJson()));
            }
        });
        ImGui.separator();
        if (ImGui.button("新增关键帧##add-keyframe")) addKeyframe(event);
        ImGui.sameLine();
        if (ImGui.button("删除当前事件##delete-event")) deleteSelected();
        ImGui.end();
    }

    private void drawTimeline(float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(width, height, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowBgAlpha(1.0f);
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
        String positionText = formatChunkPosition(playhead[0]);
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
        ImGui.sameLine();
        ImGui.text("音高");
        ImGui.sameLine();
        ImGui.setNextItemWidth(78.0f);
        if (ImGui.inputDouble("##effect-pitch-percent", pitchPercentField, 0.0, 0.0, "%.1f",
                imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue)) {
            double requestedPercent = pitchPercentField.get();
            if (Double.isFinite(requestedPercent)) {
                double normalizedPercent = Math.max(1.0, Math.min(1000.0, requestedPercent));
                pitchPercentField.set(normalizedPercent);
                pitchAction.accept(normalizedPercent);
            } else {
                pitchPercentField.set(RhythmcMakerClient.getPlaybackPitchPercent());
            }
        }
        boolean pitchHovered = ImGui.isItemHovered();
        ImGui.sameLine();
        ImGui.text("%");
        if (pitchHovered) ImGui.setTooltip("范围：1%–1000%；100% 为原音高和原速度。该设置全局生效，按 Enter 应用。");
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
        if (inputLocked) return true;
        if (!ImGuiRuntime.isInitialized()) return false;
        ImGuiIO io = ImGui.getIO();
        boolean control = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && input.key() == GLFW.GLFW_KEY_Z) {
            if ((input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) redo();
            else undo();
            return true;
        }
        if (control && input.key() == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
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
        double normalStep = 1.0 / Math.max(1, chart == null ? 1 : chart.divisionsPerChunk);
        double divisionsPerChunk = chart == null ? normalStep : Math.max(1.0, Math.min(32.0, chart.divisionsPerChunk));
        double chunkStep = 1.0;
        seekTimelineBeat(currentBeat + direction * (fast ? chunkStep : normalStep), timelineMaxBeat());
    }

    /** Returns the current ImGui preview window rectangle used for mouse camera controls. */
    private void drawTimelineCanvas(float left, float top, float width, float height, double maxBeat) {
        float rulerHeight = 34.0f;
        float footerHeight = 20.0f;
        float availableRowsHeight = Math.max(24.0f, height - rulerHeight - footerHeight);
        timelineAvailableRowsHeight = availableRowsHeight;
        int visibleRowCapacity = Math.max(1, (int) Math.floor(availableRowsHeight / 24.0f));
        List<TimelineRow> allRows = buildTimelineRows();
        int rowStart = Math.min(Math.max(0, timelineRowOffset), maxTimelineRowOffset());
        timelineRowOffset = rowStart;
        int rowEnd = rowStart;
        float rowsHeight = 0.0f;
        while (rowEnd < allRows.size()) {
            float nextHeight = timelineRowHeight(allRows.get(rowEnd));
            if (rowEnd > rowStart && rowsHeight + nextHeight > availableRowsHeight) break;
            rowsHeight += nextHeight;
            rowEnd++;
        }
        List<TimelineRow> rows = allRows.subList(rowStart, rowEnd);
        if (rows.isEmpty()) rows = List.of(new TimelineRow("事件", "暂无事件", -1, List.of(), false));
        float trackWidth = Math.min(240.0f, Math.max(96.0f, width * 0.26f));
        float timelineLeft = left + trackWidth;
        float timelineRight = left + width;
        float rowsTop = top + rulerHeight;
        float rowsBottom = Math.max(rowsTop + 24.0f, top + height - footerHeight);
        timelineBoundsLeft = left;
        timelineBoundsTop = top;
        timelineBoundsRight = timelineRight;
        timelineBoundsBottom = top + height;
        timelineVisibleRowCapacity = visibleRowCapacity;
        float timelineWidth = Math.max(1.0f, timelineRight - timelineLeft);
        double zoomSpan = Math.max(minimumZoomSpan(), Math.min(1.0, timelineZoomMax - timelineZoomMin));
        timelineZoomMin = clamp01(timelineZoomMin);
        timelineZoomMax = Math.min(1.0, timelineZoomMin + zoomSpan);
        timelineZoomMin = Math.max(0.0, timelineZoomMax - zoomSpan);
        double minBeat = timelineZoomMin * maxBeat;
        double visibleBeatSpan = Math.max(1.0e-6, (timelineZoomMax - timelineZoomMin) * maxBeat);
        double maxVisibleBeat = minBeat + visibleBeatSpan;
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(left, top, timelineRight, top + height, true);
        drawList.addRectFilled(left, top, timelineRight, top + height, 0xFF0D1117);
        drawList.addRectFilled(left, top, timelineRight, rowsTop, 0xFF171D26);
        drawList.addRectFilled(left, rowsTop, timelineLeft, rowsBottom, 0xFF12171E);
        drawList.addLine(timelineLeft, top, timelineLeft, rowsBottom, 0xFF4D5968, 1.0f);
        drawList.addLine(left, rowsTop, timelineRight, rowsTop, 0xFF4D5968, 1.0f);

        double gridStep = 1.0 / chart.divisionsPerChunk;
        double majorStep = Math.max(gridStep, Math.ceil(timelineMajorStep(visibleBeatSpan, timelineWidth) / gridStep) * gridStep);
        double minorStep = Math.max(gridStep, Math.ceil(majorStep / 4.0 / gridStep) * gridStep);
        double firstMinor = Math.floor(minBeat / minorStep) * minorStep;
        for (double beat = firstMinor; beat <= maxVisibleBeat + minorStep * 0.5; beat += minorStep) {
            if (beat < -0.0001) continue;
            float markerX = timelineBeatToX(beat, minBeat, maxVisibleBeat, timelineLeft, timelineWidth);
            boolean major = Math.abs(beat / majorStep - Math.rint(beat / majorStep)) < 1.0e-5;
            int colour = major ? 0xFF536071 : 0xFF2B333F;
            drawList.addLine(markerX, top, markerX, rowsBottom, colour, major ? 1.0f : 0.5f);
            if (major && markerX >= timelineLeft - 40.0f && markerX <= timelineRight + 4.0f) {
                String label = formatChunkPosition(beat);
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
            drawList.addText(left + 20.0f, rowY + 5.0f, labelColour, displayText(row.label()));
            float markerY = rowY + 12.0f;
            for (TimelineMarker marker : row.markers()) {
                float markerX = timelineBeatToX(marker.beat(), minBeat, maxVisibleBeat, timelineLeft, timelineWidth);
                if (marker.beat() < minBeat || marker.beat() > maxVisibleBeat) continue;
                boolean selected = selectedIndices.contains(marker.eventIndex());
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
                if (Math.abs(ImGui.getMousePosX() - markerX) < 9 && Math.abs(ImGui.getMousePosY() - markerY) < 9) {
                    int denominator = marker.kind() == TimelineMarkerKind.EVENT
                            ? layoutModel.position(marker.eventIndex()).denominator : keyframePosition(marker).denominator;
                    ImGui.setTooltip(new EffectEditorLayout.Position("", marker.beat(), denominator).label());
                }
            }
            rowY = rowBottom;
        }

        float playheadX = timelineBeatToX(playhead[0], minBeat, maxVisibleBeat, timelineLeft, timelineWidth);
        playheadX = Math.max(timelineLeft, Math.min(timelineRight, playheadX));
        drawList.addLine(playheadX, top, playheadX, rowsBottom, 0xFFFFFFFF, 1.5f);
        drawList.addTriangleFilled(playheadX - 6.0f, top + 1.0f, playheadX + 6.0f, top + 1.0f,
                playheadX, top + 11.0f, 0xFFFFFFFF);
        if (selectionDragging) {
            float selectionLeft = Math.min(selectionStartX, selectionEndX);
            float selectionTop = Math.min(selectionStartY, selectionEndY);
            float selectionRight = Math.max(selectionStartX, selectionEndX);
            float selectionBottom = Math.max(selectionStartY, selectionEndY);
            drawList.addRectFilled(selectionLeft, selectionTop, selectionRight, selectionBottom, 0x303D8CC7);
            drawList.addRect(selectionLeft, selectionTop, selectionRight, selectionBottom, 0xFF69B8FF, 1.0f);
        }

        float footerTop = top + height - footerHeight;
        drawList.addRectFilled(left, footerTop, timelineRight, top + height, 0xFF151B23);
        drawList.addRectFilled(timelineLeft, footerTop + 6.0f, timelineRight, footerTop + 14.0f, 0xFF303A47);
        float rangeLeft = (float) (timelineLeft + timelineWidth * timelineZoomMin);
        float rangeRight = (float) (timelineLeft + timelineWidth * timelineZoomMax);
        drawList.addRectFilled(rangeLeft, footerTop + 4.0f, rangeRight, footerTop + 16.0f, 0xFF61A7D8);
        drawList.addText(left + 8.0f, footerTop + 4.0f, 0xFF9CA9B8,
                String.format(Locale.ROOT, "%.0f%%", (1.0 / zoomSpan) * 100.0));
        drawTimelineScrollBar(drawList, left + 1.0f, rowsTop, rowsBottom, visibleRowCapacity);
        drawList.popClipRect();

        handleTimelineInteraction(left, top, width, height, timelineLeft, timelineWidth, footerTop,
                maxBeat, minBeat, maxVisibleBeat, hits);
    }

    private void handleTimelineInteraction(float left, float top, float width, float height, float timelineLeft,
                                            float timelineWidth, float footerTop, double maxBeat,
                                            double minBeat, double maxVisibleBeat, List<TimelineHit> hits) {
        if (inputLocked || ImGui.isPopupOpen("关闭特效编辑器")) return;
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        float right = left + width;
        boolean hovered = ImGui.isMouseHoveringRect(left, top, right, top + height, true);
        boolean contentHovered = hovered && mouseX >= timelineLeft && mouseY >= top && mouseY < footerTop;
        ImGuiIO io = ImGui.getIO();
        boolean popupWasOpen = timelinePopupOpen();
        float rowsTop = top + 34.0f;
        if (!popupWasOpen && hovered && ImGui.isMouseClicked(1)) {
            List<TimelineRow> rows = buildTimelineRows();
            int rowIndex = timelineRowAt(rows, timelineRowOffset, rowsTop, mouseY);
            if (mouseY >= rowsTop && mouseY < footerTop && rowIndex >= 0 && rowIndex < rows.size()
                    && !rows.get(rowIndex).groupHeader()) {
                contextTrackId = rows.get(rowIndex).groupLabel();
                contextBeat = Math.max(0.0, Math.round(timelineXToBeat(mouseX, timelineLeft, timelineWidth, minBeat, maxVisibleBeat)
                        * chart.divisionsPerChunk) / (double) chart.divisionsPerChunk);
                if (mouseX < timelineLeft) {
                    ImGui.openPopup("轨道操作##track-context");
                } else {
                    TimelineHit hit = findTimelineHit(mouseX, mouseY, hits);
                    if (hit != null) {
                        select(hit.marker().eventIndex());
                        ImGui.openPopup("特效操作##event-context");
                    } else {
                        ImGui.openPopup("新增特效##create-context");
                    }
                }
            }
        }
        drawTimelineMenus();
        if (popupWasOpen || timelinePopupOpen()) return;
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
            if (mouseX <= left + 14.0f && mouseY >= rowsTop && mouseY < footerTop) {
                timelineDraggingScrollBar = true;
                timelineScrollBarDragOffset = 0.0f;
                moveTimelineScrollBar(mouseY, rowsTop, footerTop);
            } else if (mouseY >= footerTop) {
                timelineDraggingRange = true;
                moveTimelineRange(mouseX, timelineLeft, timelineWidth);
            } else if (mouseY >= top && mouseY < rowsTop) {
                timelineDraggingPlayhead = true;
                seekTimelineBeat(timelineXToBeat(mouseX, timelineLeft, timelineWidth, minBeat, maxVisibleBeat), maxBeat);
            } else if (contentHovered) {
                TimelineHit hit = findTimelineHit(mouseX, mouseY, hits);
                if (hit != null) {
                    TimelineMarker marker = hit.marker();
                    select(marker.eventIndex());
                    if (selectedIndices.size() <= 1 && registry.find(eventType(workingEffects.get(marker.eventIndex()))) != null) {
                        rememberForUndo();
                        timelineDraggedEvent = marker.eventIndex();
                        timelineDraggedChannel = marker.channelIndex();
                        timelineDraggedKeyframe = marker.keyframeIndex();
                        timelineDragBeatOffset = marker.beat() - timelineXToBeat(mouseX, timelineLeft, timelineWidth,
                                minBeat, maxVisibleBeat);
                    }
                } else {
                    selectedIndex = -1;
                    selectedIndices.clear();
                    bufferIndex = Integer.MIN_VALUE;
                    syncBuffers();
                    selectionDragging = true;
                    selectionStartX = selectionEndX = mouseX;
                    selectionStartY = selectionEndY = mouseY;
                }
            }
        }
        if (timelineDraggingScrollBar && ImGui.isMouseDown(0)) moveTimelineScrollBar(mouseY, rowsTop, footerTop);
        if (timelineDraggingRange && ImGui.isMouseDown(0)) moveTimelineRange(mouseX, timelineLeft, timelineWidth);
        if (timelineDraggingPlayhead && ImGui.isMouseDown(0)) {
            seekTimelineBeat(timelineXToBeat(mouseX, timelineLeft, timelineWidth, minBeat, maxVisibleBeat), maxBeat);
        }
        if (selectionDragging && ImGui.isMouseDown(0)) {
            selectionEndX = mouseX;
            selectionEndY = mouseY;
        }
        if (timelineDraggedEvent >= 0 && ImGui.isMouseDragging(0)) {
            double target = timelineXToBeat(mouseX, timelineLeft, timelineWidth, minBeat, maxVisibleBeat) + timelineDragBeatOffset;
            int denominator = Math.max(1, Math.min(32, chart.divisionsPerChunk));
            target = Math.max(0.0, Math.round(target * denominator) / (double) denominator);
            TimelineMarker marker = new TimelineMarker(timelineDraggedEvent, timelineDraggedChannel,
                    timelineDraggedKeyframe, target,
                    timelineDraggedChannel < 0 ? TimelineMarkerKind.EVENT : TimelineMarkerKind.KEYFRAME);
            updateTimelineMarkerBeat(marker, target);
            seekTimelineBeat(target, maxBeat);
        }
        if (ImGui.isMouseReleased(0)) {
            if (selectionDragging) {
                selectionDragging = false;
                selectMarkersInRectangle(hits);
            }
            timelineDraggingRange = false;
            timelineDraggingScrollBar = false;
            timelineDraggingPlayhead = false;
            timelineDraggedEvent = -1;
            timelineDraggedChannel = -1;
            timelineDraggedKeyframe = -1;
            timelineLastSeekBeat = Double.NaN;
        }
    }

    private void drawTimelineMenus() {
        if (ImGui.beginPopup("新增特效##create-context")) {
            if (ImGui.menuItem("新增特效")) {
                for (EffectEditorLayout.Track track : layoutModel.tracks()) {
                    if (!track.id.equals(contextTrackId)) continue;
                    EffectTypeDefinition definition = registry.find(track.type);
                    if (definition != null) {
                        playhead[0] = (float) contextBeat;
                        addEffect(definition);
                    }
                    break;
                }
            }
            ImGui.endPopup();
        }
        if (ImGui.beginPopup("特效操作##event-context")) {
            if (ImGui.menuItem("复制特效") && selectedEvent() != null) {
                rememberForUndo();
                JsonObject copy = selectedEvent().deepCopy();
                EffectEditorLayout.Position source = layoutModel.position(selectedIndex);
                workingEffects.add(copy);
                layoutModel.addEvent(eventType(copy), eventBeat(copy), source.denominator, source.trackId);
                copyKeyframePositions(selectedIndex, workingEffects.size() - 1);
                cachedTimelineRows = null;
                dirty = true;
                select(workingEffects.size() - 1);
            }
            if (ImGui.menuItem("删除特效")) deleteSelected();
            ImGui.endPopup();
        }
        if (ImGui.beginPopup("轨道操作##track-context")) {
            if (ImGui.menuItem("复制轨道")) copyTrack(contextTrackId);
            if (ImGui.menuItem("清空轨道")) clearTrack(contextTrackId);
            if (ImGui.menuItem("删除轨道")) pendingDeleteTrack = contextTrackId;
            ImGui.endPopup();
        }
        if (pendingDeleteTrack != null && !ImGui.isPopupOpen("删除轨道确认")) ImGui.openPopup("删除轨道确认");
        if (ImGui.beginPopupModal("删除轨道确认", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("是否删除轨道及其全部特效？");
            if (ImGui.button("删除")) {
                clearTrack(pendingDeleteTrack);
                layoutModel.removeTrack(pendingDeleteTrack);
                pendingDeleteTrack = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消")) {
                pendingDeleteTrack = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private boolean timelinePopupOpen() {
        return ImGui.isPopupOpen("新增特效##create-context") || ImGui.isPopupOpen("特效操作##event-context")
                || ImGui.isPopupOpen("轨道操作##track-context") || ImGui.isPopupOpen("删除轨道确认");
    }

    private void copyTrack(String trackId) {
        rememberForUndo();
        for (EffectEditorLayout.Track track : layoutModel.tracks()) {
            if (!track.id.equals(trackId)) continue;
            EffectEditorLayout.Track copy = layoutModel.addTrack(track.type);
            int count = workingEffects.size();
            for (int index = 0; index < count; index++) {
                EffectEditorLayout.Position source = layoutModel.position(index);
                if (!source.trackId.equals(trackId)) continue;
                workingEffects.add(workingEffects.get(index).deepCopy());
                layoutModel.addEvent(track.type, source.beat, source.denominator, copy.id);
                copyKeyframePositions(index, workingEffects.size() - 1);
            }
            dirty = true;
            cachedTimelineRows = null;
            return;
        }
    }

    private void clearTrack(String trackId) {
        rememberForUndo();
        for (int index = workingEffects.size() - 1; index >= 0; index--) {
            if (!layoutModel.position(index).trackId.equals(trackId)) continue;
            workingEffects.remove(index);
            layoutModel.removeEvent(index);
            removeKeyframePositions(index);
        }
        selectedIndex = -1;
        bufferIndex = Integer.MIN_VALUE;
        syncBuffers();
        dirty = true;
        cachedTimelineRows = null;
    }

    public void requestClose(Runnable action) {
        confirmedCloseAction = action;
        closeRequested = true;
    }

    private void drawCloseConfirmation() {
        if (closeRequested) {
            ImGui.openPopup("关闭特效编辑器");
            closeRequested = false;
        }
        if (ImGui.beginPopupModal("关闭特效编辑器", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("存在未保存的修改，仍要关闭吗？");
            if (ImGui.button("关闭并丢弃")) {
                ImGui.closeCurrentPopup();
                discardChanges();
                confirmedCloseAction.run();
            }
            ImGui.sameLine();
            if (ImGui.button("继续编辑")) ImGui.closeCurrentPopup();
            ImGui.endPopup();
        }
    }

    private double minimumZoomSpan() {
        return Math.min(1.0, 1.0 / (Math.max(1, chart.divisionsPerChunk) * timelineMaxBeat()));
    }

    private List<TimelineRow> buildTimelineRows() {
        if (cachedTimelineRows != null) return cachedTimelineRows;
        List<TimelineRow> rows = new ArrayList<>();
        cachedTimelineMaxBeat = Math.max(1.0, chart == null ? 1.0 : chart.totalBeats);
        List<EffectEditorLayout.Track> orderedTracks = new ArrayList<>(layoutModel.tracks());
        List<EffectTypeDefinition> definitions = registry.definitions();
        orderedTracks.sort((left, right) -> Integer.compare(definitionOrder(definitions, left.type), definitionOrder(definitions, right.type)));
        int trackNumber = 0;
        String previousType = "";
        for (EffectEditorLayout.Track track : orderedTracks) {
            EffectTypeDefinition definition = registry.find(track.type);
            if (!track.type.equals(previousType)) {
                String groupLabel = definition == null ? track.type : definition.displayName();
                rows.add(new TimelineRow(groupLabel, groupLabel, -1, List.of(), true));
                previousType = track.type;
            }
            List<TimelineMarker> markers = new ArrayList<>();
            int firstEvent = -1;
            for (int eventIndex = 0; eventIndex < workingEffects.size(); eventIndex++) {
                if (!track.id.equals(layoutModel.position(eventIndex).trackId)) continue;
                if (firstEvent < 0) firstEvent = eventIndex;
                markers.addAll(timelineMarkers(eventIndex, workingEffects.get(eventIndex)));
            }
            String label = ++trackNumber + " · " + (definition == null ? track.type : definition.displayName());
            rows.add(new TimelineRow(track.id, label, firstEvent, markers, false));
            for (TimelineMarker marker : markers) cachedTimelineMaxBeat = Math.max(cachedTimelineMaxBeat, marker.beat());
        }
        cachedTimelineRows = List.copyOf(rows);
        return cachedTimelineRows;
    }

    private int definitionOrder(List<EffectTypeDefinition> definitions, String type) {
        for (int index = 0; index < definitions.size(); index++) {
            if (definitions.get(index).eventType().equals(type)) return index;
        }
        return Integer.MAX_VALUE;
    }
    private int timelineRowAt(List<TimelineRow> rows, int offset, float rowsTop, float mouseY) {
        float rowY = rowsTop;
        for (int index = offset; index < rows.size(); index++) {
            float rowHeight = rows.get(index).groupHeader() ? 21.0f : 24.0f;
            if (mouseY >= rowY && mouseY < rowY + rowHeight) return index;
            rowY += rowHeight;
        }
        return -1;
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
                keyframePosition(markers.get(markers.size() - 1));
            }
        }
        return markers;
    }

    private double timelineMaxBeat() {
        buildTimelineRows();
        return cachedTimelineMaxBeat;
    }

    private void updateTimelineMarkerBeat(TimelineMarker marker, double beat) {
        if (marker.eventIndex() < 0 || marker.eventIndex() >= workingEffects.size()) return;
        JsonObject event = workingEffects.get(marker.eventIndex());
        if (marker.kind() == TimelineMarkerKind.EVENT) {
            event.addProperty("beat", beat);
            layoutModel.move(marker.eventIndex(), eventType(event), beat);
            layoutModel.position(marker.eventIndex()).denominator = Math.max(1, Math.min(32, chart.divisionsPerChunk));
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
            keyframePosition(marker).beat = beat;
            keyframePosition(marker).denominator = Math.max(1, Math.min(32, chart.divisionsPerChunk));
        }
        dirty = true;
        cachedTimelineRows = null;
    }

    private double snapPosition(double beat) {
        int denominator = Math.max(1, Math.min(32, chart.divisionsPerChunk));
        return Math.max(0.0, Math.round(beat * denominator) / (double) denominator);
    }

    private void seekTimelineBeat(double beat, double maxBeat) {
        double target = snapPosition(Math.max(0.0, Math.min(maxBeat, beat)));
        target = Math.max(0.0, Math.min(maxBeat, target));
        playhead[0] = (float) target;
        if (Double.isNaN(timelineLastSeekBeat) || Math.abs(timelineLastSeekBeat - target) > 0.0001) {
            seekAction.accept(target);
            timelineLastSeekBeat = target;
        }
        status = "播放头已跳转到 " + formatChunkPosition(target);
    }

    private void selectMarkersInRectangle(List<TimelineHit> hits) {
        float selectionLeft = Math.min(selectionStartX, selectionEndX);
        float selectionTop = Math.min(selectionStartY, selectionEndY);
        float selectionRight = Math.max(selectionStartX, selectionEndX);
        float selectionBottom = Math.max(selectionStartY, selectionEndY);
        selectedIndices.clear();
        for (TimelineHit hit : hits) {
            if (hit.x() >= selectionLeft && hit.x() <= selectionRight
                    && hit.y() >= selectionTop && hit.y() <= selectionBottom) {
                selectedIndices.add(hit.marker().eventIndex());
            }
        }
        if (selectedIndices.isEmpty()) {
            selectedIndex = -1;
        } else {
            selectedIndex = selectedIndices.iterator().next();
        }
        bufferIndex = Integer.MIN_VALUE;
        syncBuffers();
    }

    private void handleCameraWheelOutsideTimeline() {
        if (inputLocked || ImGui.getIO().getWantCaptureMouse()) return;
        ImGuiIO io = ImGui.getIO();
        float wheel = io.getMouseWheel();
        if (Math.abs(wheel) <= 0.001f) return;
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        boolean overTimeline = mouseX >= timelineBoundsLeft && mouseX <= timelineBoundsRight
                && mouseY >= timelineBoundsTop && mouseY <= timelineBoundsBottom;
        if (!overTimeline && viewport.contains(mouseX, mouseY)) {
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
        return cachedTimelineRows == null ? buildTimelineRows().size() : cachedTimelineRows.size();
    }

    private float timelineRowHeight(TimelineRow row) {
        return row.groupHeader() ? 21.0f : 24.0f;
    }

    private float timelineRowsHeight(int start, int end) {
        if (cachedTimelineRows == null) buildTimelineRows();
        float height = 0.0f;
        for (int index = Math.max(0, start); index < Math.min(end, cachedTimelineRows.size()); index++) {
            height += timelineRowHeight(cachedTimelineRows.get(index));
        }
        return height;
    }

    private int maxTimelineRowOffset() {
        int totalRows = totalTimelineRows();
        int maxOffset = 0;
        for (int offset = 0; offset < totalRows; offset++) {
            if (timelineRowsHeight(offset, totalRows) <= timelineAvailableRowsHeight + 0.01f) maxOffset = offset;
        }
        return maxOffset;
    }

    private void drawTimelineScrollBar(ImDrawList drawList, float left, float top, float bottom, int visibleRows) {
        float contentHeight = Math.max(1.0f, timelineRowsHeight(0, totalTimelineRows()));
        float trackHeight = Math.max(1.0f, bottom - top);
        float thumbHeight = Math.max(24.0f, trackHeight * Math.min(1.0f, timelineAvailableRowsHeight / contentHeight));
        float travel = Math.max(0.0f, trackHeight - thumbHeight);
        float progress = maxTimelineRowOffset() == 0 ? 0.0f : timelineRowOffset / (float) maxTimelineRowOffset();
        float thumbTop = top + travel * progress;
        drawList.addRectFilled(left, top, left + 8.0f, bottom, 0xFF202832, 3.0f);
        drawList.addRectFilled(left, thumbTop, left + 8.0f, thumbTop + thumbHeight, 0xFF6B91B5, 3.0f);
    }

    private float timelineScrollBarTop(float top, float bottom) {
        float contentHeight = Math.max(1.0f, timelineRowsHeight(0, totalTimelineRows()));
        float trackHeight = Math.max(1.0f, bottom - top);
        float thumbHeight = Math.max(24.0f, trackHeight * Math.min(1.0f, timelineAvailableRowsHeight / contentHeight));
        float travel = Math.max(0.0f, trackHeight - thumbHeight);
        float progress = maxTimelineRowOffset() == 0 ? 0.0f : timelineRowOffset / (float) maxTimelineRowOffset();
        return top + travel * progress;
    }

    private void moveTimelineScrollBar(float mouseY, float top, float bottom) {
        float contentHeight = Math.max(1.0f, timelineRowsHeight(0, totalTimelineRows()));
        float trackHeight = Math.max(1.0f, bottom - top);
        float thumbHeight = Math.max(24.0f, trackHeight * Math.min(1.0f, timelineAvailableRowsHeight / contentHeight));
        float travel = Math.max(1.0f, trackHeight - thumbHeight);
        float target = Math.max(0.0f, Math.min(travel, mouseY - timelineScrollBarDragOffset - top));
        timelineRowOffset = (int) Math.round(target / travel * maxTimelineRowOffset());
    }

    private float timelineRight(float timelineLeft, float timelineWidth) {
        return timelineLeft + timelineWidth;
    }

    private void zoomTimelineAt(float mouseX, float timelineLeft, float timelineWidth, float wheel) {
        double focus = clamp01((mouseX - timelineLeft) / Math.max(1.0f, timelineWidth));
        double currentSpan = Math.max(minimumZoomSpan(), timelineZoomMax - timelineZoomMin);
        double nextSpan = Math.max(minimumZoomSpan(), Math.min(1.0, currentSpan * (wheel > 0.0f ? 0.8 : 1.25)));
        double focusBeat = timelineZoomMin + focus * currentSpan;
        timelineZoomMin = Math.max(0.0, Math.min(1.0 - nextSpan, focusBeat - focus * nextSpan));
        timelineZoomMax = timelineZoomMin + nextSpan;
    }

    private void moveTimelineRange(float mouseX, float timelineLeft, float timelineWidth) {
        double centre = clamp01((mouseX - timelineLeft) / Math.max(1.0f, timelineWidth));
        double span = Math.max(minimumZoomSpan(), timelineZoomMax - timelineZoomMin);
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
        return Math.max(1.0e-6, step * magnitude);
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
        rememberForUndo();
        JsonObject event = new JsonObject();
        event.addProperty("eventType", definition.eventType());
        event.addProperty("beat", Math.max(0.0, contextBeat));
        JsonObject properties = cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterCodec.getInstance()
                .createDefaults(definition.eventType(), workingEffects);
        event.add("properties", properties);
        workingEffects.add(event);
        layoutModel.addEvent(definition.eventType(), eventBeat(event), chart.divisionsPerChunk, contextTrackId);
        dirty = true;
        cachedTimelineRows = null;
        select(workingEffects.size() - 1);
        status = "已新增 " + definition.displayName();
    }

    private void addKeyframe(JsonObject event) {
        rememberForUndo();
        JsonObject animation = event.has("animation") && event.get("animation").isJsonObject() ? event.getAsJsonObject("animation") : new JsonObject();
        JsonArray channels = animation.has("channels") && animation.get("channels").isJsonArray() ? animation.getAsJsonArray("channels") : new JsonArray();
        JsonObject channel = new JsonObject();
        channel.addProperty("name", "value");
        channel.addProperty("interpolation", "CONTINUOUS");
        JsonArray keyframes = new JsonArray();
        JsonObject keyframe = new JsonObject();
        keyframe.addProperty("beat", Math.max(0.0, Math.round(playhead[0] * chart.divisionsPerChunk) / (double) chart.divisionsPerChunk));
        keyframe.addProperty("value", 1.0);
        keyframe.addProperty("easing", 0);
        keyframes.add(keyframe);
        channel.add("keyframes", keyframes);
        channels.add(channel);
        animation.add("channels", channels);
        event.add("animation", animation);
        keyframePositions.add(new EffectEditorLayout.KeyframePosition(selectedIndex, channels.size() - 1, 0,
                keyframe.get("beat").getAsDouble(), chart.divisionsPerChunk));
        dirty = true;
        cachedTimelineRows = null;
        status = "已新增关键帧";
    }

    private void deleteSelected() {
        JsonObject event = selectedEvent();
        if (event == null || registry.find(eventType(event)) == null) return;
        rememberForUndo();
        workingEffects.remove(selectedIndex);
        layoutModel.removeEvent(selectedIndex);
        removeKeyframePositions(selectedIndex);
        bufferIndex = Integer.MIN_VALUE;
        selectedIndex = Math.min(selectedIndex, workingEffects.size() - 1);
        dirty = true;
        cachedTimelineRows = null;
        syncBuffers();
    }

    private void copySelectedEffects() {
        if (selectedIndices.isEmpty()) return;
        rememberForUndo();
        List<Integer> sourceIndices = new ArrayList<>(selectedIndices);
        selectedIndices.clear();
        for (int sourceIndex : sourceIndices) {
            if (sourceIndex < 0 || sourceIndex >= workingEffects.size()) continue;
            JsonObject copy = workingEffects.get(sourceIndex).deepCopy();
            EffectEditorLayout.Position source = layoutModel.position(sourceIndex);
            workingEffects.add(copy);
            layoutModel.addEvent(eventType(copy), eventBeat(copy), source.denominator, source.trackId);
            copyKeyframePositions(sourceIndex, workingEffects.size() - 1);
            selectedIndices.add(workingEffects.size() - 1);
        }
        selectedIndex = selectedIndices.isEmpty() ? -1 : selectedIndices.iterator().next();
        dirty = true;
        cachedTimelineRows = null;
        syncBuffers();
    }

    private void deleteSelectedEffects() {
        if (selectedIndices.isEmpty()) return;
        rememberForUndo();
        List<Integer> indices = new ArrayList<>(selectedIndices);
        indices.sort(java.util.Collections.reverseOrder());
        for (int index : indices) {
            if (index < 0 || index >= workingEffects.size()) continue;
            workingEffects.remove(index);
            layoutModel.removeEvent(index);
            removeKeyframePositions(index);
        }
        selectedIndices.clear();
        selectedIndex = workingEffects.isEmpty() ? -1 : Math.min(indices.get(indices.size() - 1), workingEffects.size() - 1);
        dirty = true;
        cachedTimelineRows = null;
        syncBuffers();
    }

    private void select(int index) {
        if (chart == null || index < 0 || index >= workingEffects.size()) return;
        selectedIndex = index;
        selectedIndices.clear();
        selectedIndices.add(index);
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
        beatField.set(event == null ? 0.0 : eventBeat(event));
    }

    private void syncEventFromBuffers() {
        JsonObject event = selectedEvent();
        if (event == null || registry.find(eventType(event)) == null) return;
        event.addProperty("beat", Math.max(0.0, beatField.get()));
    }

    private JsonObject selectedEvent() {
        return chart == null || selectedIndex < 0 || selectedIndex >= workingEffects.size() ? null : workingEffects.get(selectedIndex);
    }

    private double selectedBeat() {
        JsonObject event = selectedEvent();
        return event == null ? 0.0 : eventBeat(event);
    }

    private static String eventType(JsonObject event) {
        return event == null ? "" : cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterCodec.getInstance().type(event);
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
        return new EffectEditorLayout.Position("", Math.max(0.0, beat), chart == null ? 1 : chart.divisionsPerChunk).label();
    }

    private int inferDenominator(double beat) {
        int preferred = Math.max(1, Math.min(32, chart.divisionsPerChunk));
        if (Math.abs(beat * preferred - Math.rint(beat * preferred)) < 1.0e-8) return preferred;
        for (int denominator = 1; denominator <= 32; denominator++) {
            if (Math.abs(beat * denominator - Math.rint(beat * denominator)) < 1.0e-8) return denominator;
        }
        return preferred;
    }

    private EffectEditorLayout.KeyframePosition keyframePosition(TimelineMarker marker) {
        for (EffectEditorLayout.KeyframePosition position : keyframePositions) {
            if (position.eventIndex == marker.eventIndex() && position.channelIndex == marker.channelIndex()
                    && position.keyframeIndex == marker.keyframeIndex()) return position;
        }
        EffectEditorLayout.KeyframePosition position = new EffectEditorLayout.KeyframePosition(marker.eventIndex(),
                marker.channelIndex(), marker.keyframeIndex(), marker.beat(), inferDenominator(marker.beat()));
        keyframePositions.add(position);
        return position;
    }

    private void copyKeyframePositions(int sourceIndex, int targetIndex) {
        for (EffectEditorLayout.KeyframePosition position : List.copyOf(keyframePositions)) {
            if (position.eventIndex != sourceIndex) continue;
            keyframePositions.add(new EffectEditorLayout.KeyframePosition(targetIndex, position.channelIndex,
                    position.keyframeIndex, position.beat, position.denominator));
        }
    }

    private void removeKeyframePositions(int eventIndex) {
        keyframePositions.removeIf(position -> position.eventIndex == eventIndex);
        for (EffectEditorLayout.KeyframePosition position : keyframePositions) {
            if (position.eventIndex > eventIndex) position.eventIndex--;
        }
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
                double position = chunk - 1 + parseFraction(value.substring(plus + 1));
                return chunk >= 1 && Double.isFinite(position) && position >= 0.0 ? position : null;
            }
            return null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parsePositionDenominator(String raw) {
        int slash = raw.lastIndexOf('/');
        if (slash < 0) return null;
        try {
            int denominator = Integer.parseInt(raw.substring(slash + 1).trim());
            return denominator >= 1 && denominator <= 32 ? denominator : null;
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
