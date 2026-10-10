package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.RhythmcMaker;
import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartTiming;
import cn.frkovo.rhythmcmaker.chart.PlaybackCoordinates;
import cn.frkovo.rhythmcmaker.chart.TrackPlayback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.text.Text;
import net.minecraft.item.Item;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class RhythmcMakerClient implements ClientModInitializer {
    private static KeyBinding settingsKey;
    private static KeyBinding selectPlaybackStartKey;
    private static boolean controlSpeedHandled;
    private static int selectedPlaybackChunk = 1;
    private static String selectedPlaybackChartId;
    private static volatile Process audioProcess;
    private static long audioStartedAtNanos;
    private static long audioSessionCreatedAtNanos;
    private static volatile double audioProgressSeconds = Double.NaN;
    private static volatile long audioProgressUpdatedAtNanos;
    private static long lastFormalSyncNanos;
    private static long formalProgressMissingSinceNanos;
    private static ChartTiming.Prepared playbackTimingProfile;
    private static TrackPlayback.Prepared playbackTrackProfile;
    private static double playbackTrackSpeed = 1.0;
    private static boolean playbackStopRequested;
    private static double audioDurationSeconds;
    private static double playbackStartSeconds;
    private static boolean audioAttempted;
    private static Path audioErrorLog;
    private static CompletableFuture<Path> audioPlayerFuture;
    private static boolean audioPreparingNoticeShown;
    private static boolean audioRetryNoticeShown;
    private static int playbackAudioDelayTicks;
    private static int playbackToggleCooldownTicks;
    private static int playbackStartChunk = 1;
    private static double playbackStartBeat;
    private static boolean playbackAudioRequested;
    private static String pendingPlaybackCommand;
    private static String playbackMode = "formal";
    private static boolean effectEditorPreviewPrepared;
    private static boolean effectEditorPlaybackRequested;
    private static boolean effectEditorCameraLocked;
    private static double effectEditorPlayheadBeat;
    private static float effectEditorFov = -1.0f;
    private static double effectEditorCameraX;
    private static double effectEditorCameraHeight = 74.0;
    private static double effectEditorCameraZOffset;
    private static final double EFFECT_EDITOR_CAMERA_MIN_HEIGHT = 72.0;
    private static final double EFFECT_EDITOR_CAMERA_MAX_HEIGHT = 90.0;
    private static final double EFFECT_EDITOR_CAMERA_DRAG_SCALE = 0.08;
    private static Perspective effectEditorPreviousPerspective;
    private static SidebarSnapshot sidebarSnapshot;
    private static long sidebarSnapshotNanos;
    private static String sidebarSnapshotContext = "";
    private static double sidebarSnapshotIntervalSeconds = -1.0;
    private static boolean sidebarRefreshRequested = true;

    static void requestSidebarRefresh() {
        clearSidebarSnapshot();
    }

    @Override public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime.dispose());
        WorldRenderEvents.AFTER_ENTITIES.register(RhythmcMakerClient::renderScrollPlaybackBox);
        var category = KeyBinding.Category.create(net.minecraft.util.Identifier.of("rhythmc_maker", "main"));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.rhythmc_maker.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        selectPlaybackStartKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.rhythmc_maker.select_playback_start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category));
        audioPlayerFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return extractAudioPlayer();
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(RhythmcMaker.MOD_ID, "editor_sidebar"),
                (drawContext, tickCounter) -> renderEditorSidebar(drawContext));
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient()) return ActionResult.PASS;
            if (ClientSceneAccess.operationBusy()) return ActionResult.SUCCESS;
            Item item = player.getStackInHand(hand).getItem();
            MinecraftClient client = MinecraftClient.getInstance();
            if (item == RhythmcMaker.START_CHART_ITEM) client.setScreen(new ChartListScreen());
            else if (item == RhythmcMaker.AWA_ITEM) client.setScreen(new PlaceholderScreen("awa~", "awa-menu"));
            else if (item == RhythmcMaker.SETTINGS_ITEM) client.setScreen(new PlaceholderScreen("设置"));
            else if (item == RhythmcMaker.CHART_SETTINGS_ITEM) client.setScreen(new PlaceholderScreen("谱面设置", "chart-settings"));
            else if (item == RhythmcMaker.MORE_SETTINGS_ITEM) sendCommand(client, "rhythmc_more_options");
            else if (item == RhythmcMaker.QUICK_FUNCTIONS_ITEM) client.setScreen(new PlaceholderScreen("快捷功能", "quick-functions"));
            else if (item == RhythmcMaker.MORE_OPTIONS_RETURN_ITEM) sendCommand(client, "rhythmc_more_return");
            else if (item == RhythmcMaker.EXPORT_ITEM) client.setScreen(new ExportChartScreen());
            else if (item == RhythmcMaker.CHART_INFO_ITEM) { if (ClientChartAccess.activeChart() != null) client.setScreen(new ChartInfoScreen(ClientChartAccess.activeChart())); else if (client.player != null) client.player.sendMessage(Text.literal("当前未加载谱面"), true); }
            else if (item == RhythmcMaker.SCENE_EDIT_ITEM) client.setScreen(new SceneEditScreen());
            else if (item == RhythmcMaker.EFFECT_TRACK_ITEM) client.setScreen(new EffectTrackScreen());
            else if (item == RhythmcMaker.SAVE_SCENE_ITEM) sendCommand(client, "rhythmc_scene_save");
            else if (item == RhythmcMaker.PLAY_ITEM) {
                if (client.player != null && client.player.getInventory().getStack(0).isOf(RhythmcMaker.SAVE_SCENE_ITEM)) {
                    client.player.sendMessage(Text.literal("场景搭建期间不能播放谱面"), true);
                    return ActionResult.SUCCESS;
                }
                if (isShiftPressed(client)) {
                    syncPlaybackPreference();
                    client.setScreen(new PlaybackSettingsScreen());
                    return ActionResult.SUCCESS;
                }
                if (playbackToggleCooldownTicks > 0) return ActionResult.SUCCESS;
                syncPlaybackPreference();
                beginPlayback(client);
            }
            else if (item == RhythmcMaker.CHUNK_SCORE_ITEM) client.setScreen(new PlaceholderScreen("修改制谱器轨道", "chunk-score"));
            else if (item == RhythmcMaker.INCREASE_BEATS_ITEM) sendCommand(client, "rhythmc_beats 1");
            else if (item == RhythmcMaker.DECREASE_BEATS_ITEM) sendCommand(client, "rhythmc_beats -1");
            else return ActionResult.PASS;
            return ActionResult.SUCCESS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient()) return ActionResult.PASS;
            MinecraftClient client = MinecraftClient.getInstance();
            if (ClientSceneAccess.operationBusy()) return ActionResult.FAIL;
            if (client.player != player || client.currentScreen != null || !isEditorDimension(client) || !isAltPressed(client)) return ActionResult.PASS;
            ChartManifest chart = ClientChartAccess.resolveActiveChart();
            if (chart == null) return ActionResult.PASS;
            int divisions = Math.max(1, chart.divisionsPerChunk > 0 ? chart.divisionsPerChunk : chart.beatsPerMeasure);
            int currentChunk = Math.max(1, Math.min(Math.max(1, chart.chunkCount), (int) Math.floor(Math.max(0.0, -player.getZ() - 3.0) / divisions) + 1));
            selectedPlaybackChartId = chart.id;
            selectedPlaybackChunk = currentChunk;
            sendCommand(client, "rhythmc_start_chunk_set " + currentChunk);
            return ActionResult.FAIL;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (effectEditorPreviewPrepared) applyEffectEditorPerspective();
            if (client.player != null && client.world != null && client.world.getTime() % 5L == 0L
                    && ClientChartAccess.refreshActiveChartIfChanged()) {
                sidebarRefreshRequested = true;
                syncSelectedPlaybackChunk(ClientChartAccess.activeChart());
                if (playbackAudioRequested || audioProcess != null) finishPlayback(client, true);
            }
            String operationNotice = ClientSceneAccess.consumeOperationNotice();
            if (operationNotice != null) ClientSceneAccess.status(operationNotice);
            if (ClientSceneAccess.operationBusy() && client.currentScreen != null) client.setScreen(null);
            if (ClientSceneAccess.operationBusy()) {
                String operationStatus = ClientSceneAccess.operationStatus();
                if (operationStatus != null && client.player != null && client.world != null && client.world.getTime() % 10L == 0L) client.player.sendMessage(Text.literal(operationStatus), true);
            }
            updateEditorSidebarSnapshot(client);
            while (settingsKey.wasPressed()) client.setScreen(new PlaceholderScreen("设置"));
            boolean inPlaybackArea = client.player != null && isEditorDimension(client) && Math.abs(client.player.getX() - 200.5) < 2.0 && Math.abs(client.player.getY() - 66.0) < 2.0 && Math.abs(client.player.getZ() - 0.0) < 2.0;
            boolean inChartDimension = client.player != null && isEditorDimension(client);
            if (playbackToggleCooldownTicks > 0) playbackToggleCooldownTicks--;
            if (inChartDimension && playbackAudioDelayTicks > 0) playbackAudioDelayTicks--;
            if (playbackAudioRequested && playbackAudioDelayTicks <= 0 && audioProcess == null && !audioAttempted) {
                if (effectEditorPreviewPrepared) startAudioAtBeat(playbackStartBeat);
                else startAudio(playbackStartChunk);
            }
            if (playbackAudioRequested && pendingPlaybackCommand != null && audioProcess != null && audioProcess.isAlive() && audioStartedAtNanos > 0) {
                sendCommand(client, pendingPlaybackCommand);
                pendingPlaybackCommand = null;
            }
            if (playbackAudioRequested && audioAttempted && audioProcess == null && pendingPlaybackCommand == null) {
                retryAudioPlayback(client);
            }
            if (audioSessionCreatedAtNanos > 0L
                    && System.nanoTime() - audioSessionCreatedAtNanos >= 1_200_000_000_000L) {
                ClientChartAccess.statusWarning("Audio output session exceeded 20 minutes");
                finishPlayback(client, true);
                return;
            }
            if (playbackAudioRequested && !effectEditorPreviewPrepared) {
                long now = System.nanoTime();
                if (audioProcess != null && audioProcess.isAlive() && audioStartedAtNanos > 0) {
                    double elapsed = Math.max(0.0, (now - audioStartedAtNanos) / 1_000_000_000.0);
                    audioProgressSeconds = audioDurationSeconds > 0.0
                            ? Math.min(elapsed, audioDurationSeconds)
                            : elapsed;
                    audioProgressUpdatedAtNanos = now;
                }
                boolean progressFresh = Double.isFinite(audioProgressSeconds)
                        && audioProgressUpdatedAtNanos > 0
                        && now - audioProgressUpdatedAtNanos < 1_000_000_000L;
                if (!progressFresh) {
                    if (formalProgressMissingSinceNanos == 0L) formalProgressMissingSinceNanos = now;
                    if (now - formalProgressMissingSinceNanos >= 10_000_000_000L) {
                        ClientChartAccess.statusWarning("正式播放音频进度丢失，已结束播放");
                        finishPlayback(client, true);
                    }
                } else {
                    formalProgressMissingSinceNanos = 0L;
                }
            }
            if (audioProcess != null && !audioProcess.isAlive()) {
                double elapsed = audioStartedAtNanos <= 0 ? 0.0 : (System.nanoTime() - audioStartedAtNanos) / 1_000_000_000.0;
                if (audioDurationSeconds > 0.0 && elapsed + 0.5 < audioDurationSeconds) retryAudioPlayback(client);
                else finishPlayback(client, true);
            } else if (audioProcess != null && audioDurationSeconds > 0.0
                    && (System.nanoTime() - audioStartedAtNanos) / 1_000_000_000.0 >= audioDurationSeconds + 0.5) {
                finishPlayback(client, true);
            }
            if (playbackAudioRequested && !effectEditorPreviewPrepared && System.nanoTime() - lastFormalSyncNanos >= 250_000_000L
                    && audioStartedAtNanos > 0L) {
                double songSeconds = playbackStartSeconds
                        + Math.max(0.0, (System.nanoTime() - audioStartedAtNanos) / 1_000_000_000.0) * playbackPitchRate();
                sendCommand(client, "rhythmc_sync_formal_playback " + String.format(Locale.ROOT, "%.6f", songSeconds));
                lastFormalSyncNanos = System.nanoTime();
            }
            if (client.player == null || client.getWindow() == null) return;
            boolean control = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            int speedKey = 0;
            if (control) {
                if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_1) == GLFW.GLFW_PRESS) speedKey = 1;
                else if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_2) == GLFW.GLFW_PRESS) speedKey = 2;
                else if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_3) == GLFW.GLFW_PRESS) speedKey = 3;
                else if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_4) == GLFW.GLFW_PRESS) speedKey = 4;
            }
            if (speedKey == 0) { controlSpeedHandled = false; return; }
            if (!controlSpeedHandled) { setFlightSpeed(speedKey); controlSpeedHandled = true; }
        });
    }

    static void startConfiguredPlayback() {
        MinecraftClient client = MinecraftClient.getInstance();
        syncPlaybackPreference();
        client.setScreen(null);
        beginPlayback(client);
    }

    private static void beginPlayback(MinecraftClient client) {
        if (client.player == null) return;
        playbackToggleCooldownTicks = 6;
        boolean stopping = playbackAudioRequested || audioProcess != null;
        if (stopping) {
            finishPlayback(client, true);
            return;
        }
        playbackAudioRequested = true;
        playbackStopRequested = false;
        audioAttempted = false;
        audioPreparingNoticeShown = false;
        audioRetryNoticeShown = false;
        playbackAudioDelayTicks = 2;
        var activeChart = ClientChartAccess.resolveActiveChart();
        syncSelectedPlaybackChunk(activeChart);
        int startChunk = activeChart == null ? selectedPlaybackChunk : Math.max(1, Math.min(Math.max(1, activeChart.chunkCount), selectedPlaybackChunk));
        selectedPlaybackChunk = startChunk;
        playbackStartChunk = startChunk;
        playbackStartBeat = activeChart == null ? 0.0 : PlaybackCoordinates.beatAtChunkStart(startChunk);
        playbackStartSeconds = activeChart == null ? 0.0 : PlaybackCoordinates.songTimeAtBeat(activeChart, ChartTiming.prepare(activeChart), playbackStartBeat);
        audioStartedAtNanos = 0;
        audioSessionCreatedAtNanos = 0L;
        playbackTimingProfile = null;
        playbackTrackProfile = null;
        pendingPlaybackCommand = "rhythmc_play " + startChunk + " " + playbackMode;
        startAudio(startChunk);
        if (audioProcess != null && audioProcess.isAlive()) {
            sendCommand(client, pendingPlaybackCommand);
            pendingPlaybackCommand = null;
        }
    }

    static void prepareEffectEditorPreview() {
        MinecraftClient client = MinecraftClient.getInstance();
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        if (client.player == null || chart == null || chart.bpm <= 0) return;
        if (effectEditorPreviewPrepared) return;
        if (playbackAudioRequested || audioProcess != null) finishPlayback(client, true);
        syncPlaybackPreference();
        playbackMode = "scroll";
        effectEditorPreviewPrepared = true;
        effectEditorPlaybackRequested = false;
        effectEditorCameraLocked = true;
        effectEditorCameraX = 0.0;
        effectEditorCameraHeight = 74.0;
        effectEditorCameraZOffset = 0.0;
        effectEditorPreviousPerspective = client.options.getPerspective();
        applyEffectEditorPerspective();
        effectEditorFov = client.options.getFov().getValue().floatValue();
        effectEditorPlayheadBeat = Math.max(0.0, Math.min(Math.max(0.0, chart.totalBeats), effectEditorPlayheadBeat));
        playbackStartBeat = effectEditorPlayheadBeat;
        playbackTimingProfile = ChartTiming.prepare(chart);
        playbackStartSeconds = PlaybackCoordinates.songTimeAtBeat(chart, playbackTimingProfile, playbackStartBeat);
        playbackTrackProfile = PlaybackCoordinates.prepareDefaultTrack(chart);
        sendCommand(client, "rhythmc_effect_preview_prepare");
        sendCommand(client, "rhythmc_effect_preview_seek " + String.format(Locale.ROOT, "%.6f", effectEditorPlayheadBeat));
    }

    static void toggleEffectEditorPlayback() {
        if (effectEditorPlaybackRequested && playbackAudioRequested) {
            pauseEffectEditorPlayback();
            return;
        }
        playEffectEditorPlayback();
    }

    static void playEffectEditorPlayback() {
        MinecraftClient client = MinecraftClient.getInstance();
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        if (client.player == null || chart == null || chart.bpm <= 0
                || effectEditorPlaybackRequested && playbackAudioRequested) return;
        if (!effectEditorPreviewPrepared) prepareEffectEditorPreview();
        startEffectEditorPlayback(effectEditorPlayheadBeat);
    }

    static void replayEffectEditorPlayback() {
        if (effectEditorPlaybackRequested && playbackAudioRequested) stopEffectEditorPlaybackAudio();
        seekEffectEditorPreview(0.0);
    }

    public static double getPlaybackPitchPercent() {
        return ClientChartAccess.config().playbackPitchPercent;
    }

    public static void setPlaybackPitchPercent(double percent) {
        if (!Double.isFinite(percent)) return;
        double normalizedPercent = Math.max(1.0, Math.min(1000.0, percent));
        var config = ClientChartAccess.config();
        if (Double.compare(config.playbackPitchPercent, normalizedPercent) == 0) return;

        boolean restartPlayback = effectEditorPreviewPrepared && effectEditorPlaybackRequested && playbackAudioRequested;
        double currentBeat = restartPlayback ? effectEditorCurrentBeat() : effectEditorPlayheadBeat;
        config.playbackPitchPercent = normalizedPercent;
        try {
            ClientChartAccess.saveConfig(config);
        } catch (IOException exception) {
            ClientChartAccess.status("全局音高设置保存失败");
        }
        if (restartPlayback) seekEffectEditorPreview(currentBeat);
    }

    static void pauseEffectEditorPlayback() {
        if (!effectEditorPlaybackRequested || !playbackAudioRequested) return;
        MinecraftClient client = MinecraftClient.getInstance();
        stopEffectEditorPlaybackAudio();
        sendCommand(client, "rhythmc_effect_preview_seek " + String.format(Locale.ROOT, "%.6f", effectEditorPlayheadBeat));
    }

    private static void stopEffectEditorPlaybackAudio() {
        effectEditorPlayheadBeat = effectEditorCurrentBeat();
        stopAudio();
        playbackAudioRequested = false;
        effectEditorPlaybackRequested = false;
        pendingPlaybackCommand = null;
    }

    static void seekEffectEditorPreview(double beat) {
        MinecraftClient client = MinecraftClient.getInstance();
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        if (chart == null) return;
        effectEditorPlayheadBeat = Math.max(0.0, Math.min(Math.max(0.0, chart.totalBeats), beat));
        playbackStartBeat = effectEditorPlayheadBeat;
        playbackTimingProfile = ChartTiming.prepare(chart);
        playbackStartSeconds = PlaybackCoordinates.songTimeAtBeat(chart, playbackTimingProfile, playbackStartBeat);
        if (!effectEditorPlaybackRequested || !playbackAudioRequested) {
            if (effectEditorPreviewPrepared) sendCommand(client, "rhythmc_effect_preview_seek " + String.format(Locale.ROOT, "%.6f", effectEditorPlayheadBeat));
            return;
        }
        // Seek the server-side preview session before restarting local audio so the
        // virtual camera, judgment line, and audio clock move to the same Beat together.
        sendCommand(client, "rhythmc_effect_preview_seek " + String.format(Locale.ROOT, "%.6f", effectEditorPlayheadBeat));
        stopAudio();
        playbackAudioRequested = true;
        audioAttempted = false;
        audioPreparingNoticeShown = false;
        audioRetryNoticeShown = false;
        playbackAudioDelayTicks = 0;
        pendingPlaybackCommand = null;
        effectEditorCameraLocked = true;
        startAudioAtBeat(effectEditorPlayheadBeat);
        if (audioProcess != null && audioProcess.isAlive() && pendingPlaybackCommand != null) {
            sendCommand(client, pendingPlaybackCommand);
            pendingPlaybackCommand = null;
        }
    }

    private static void startEffectEditorPlayback(double beat) {
        MinecraftClient client = MinecraftClient.getInstance();
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        if (chart == null) return;
        effectEditorPlayheadBeat = Math.max(0.0, Math.min(Math.max(0.0, chart.totalBeats), beat));
        playbackStartBeat = effectEditorPlayheadBeat;
        playbackTimingProfile = ChartTiming.prepare(chart);
        playbackStartSeconds = PlaybackCoordinates.songTimeAtBeat(chart, playbackTimingProfile, playbackStartBeat);
        playbackAudioRequested = true;
        effectEditorPlaybackRequested = true;
        effectEditorCameraLocked = true;
        playbackStopRequested = false;
        audioAttempted = false;
        audioPreparingNoticeShown = false;
        audioRetryNoticeShown = false;
        playbackAudioDelayTicks = 0;
        pendingPlaybackCommand = null;
        sendCommand(client, "rhythmc_stop_playback");
        startAudioAtBeat(effectEditorPlayheadBeat);
        if (audioProcess != null && audioProcess.isAlive() && pendingPlaybackCommand != null) {
            sendCommand(client, pendingPlaybackCommand);
            pendingPlaybackCommand = null;
        }
    }

    static void stopEffectEditorPreview() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean previewActive = effectEditorPreviewPrepared;
        boolean active = effectEditorPreviewPrepared || effectEditorPlaybackRequested || playbackAudioRequested || audioProcess != null;
        effectEditorPreviewPrepared = false;
        effectEditorPlaybackRequested = false;
        effectEditorCameraLocked = false;
        effectEditorCameraX = 0.0;
        effectEditorCameraHeight = 74.0;
        effectEditorCameraZOffset = 0.0;
        effectEditorFov = -1.0f;
        restoreEffectEditorPerspective();
        pendingPlaybackCommand = null;
        if (playbackAudioRequested || audioProcess != null) stopAudio();
        playbackAudioRequested = false;
        if (active) sendCommand(client, previewActive ? "rhythmc_effect_preview_close" : "rhythmc_stop_playback");
    }

    private static void applyEffectEditorPerspective() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (effectEditorPreviewPrepared && client.options.getPerspective() != Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    private static void restoreEffectEditorPerspective() {
        MinecraftClient client = MinecraftClient.getInstance();
        Perspective previousPerspective = effectEditorPreviousPerspective;
        effectEditorPreviousPerspective = null;
        if (previousPerspective != null && client.options.getPerspective() != previousPerspective) {
            client.options.setPerspective(previousPerspective);
        }
    }

    public static boolean isEffectEditorCameraLocked() {
        return effectEditorPreviewPrepared && effectEditorCameraLocked;
    }

    public static Vec3d effectEditorCameraPosition() {
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        if (chart == null) return new Vec3d(effectEditorCameraX, effectEditorCameraHeight,
                PlaybackCoordinates.CHART_ORIGIN_Z + effectEditorCameraZOffset);
        double beat = effectEditorCurrentBeat();
        double z = PlaybackCoordinates.editorWorldZAtBeat(chart, beat);
        return new Vec3d(effectEditorCameraX, effectEditorCameraHeight, z + effectEditorCameraZOffset);
    }

    public static void adjustEffectEditorCamera(double mouseDeltaX, double mouseDeltaY) {
        if (!effectEditorPreviewPrepared || !Double.isFinite(mouseDeltaX) || !Double.isFinite(mouseDeltaY)) return;
        effectEditorCameraLocked = true;
        effectEditorCameraZOffset += mouseDeltaX * EFFECT_EDITOR_CAMERA_DRAG_SCALE * 0.5;
        effectEditorCameraX -= mouseDeltaY * EFFECT_EDITOR_CAMERA_DRAG_SCALE * 0.5;
    }

    public static void adjustEffectEditorCameraHeight(double wheelDelta) {
        if (!effectEditorPreviewPrepared || !Double.isFinite(wheelDelta)) return;
        effectEditorCameraLocked = true;
        effectEditorCameraHeight = Math.max(EFFECT_EDITOR_CAMERA_MIN_HEIGHT,
                Math.min(EFFECT_EDITOR_CAMERA_MAX_HEIGHT, effectEditorCameraHeight + wheelDelta));
    }

    public static float effectEditorCameraFov(float fallback) {
        return effectEditorFov > 0.0f ? effectEditorFov : fallback;
    }

    public static double effectEditorCurrentBeat() {
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        if (chart == null || playbackTimingProfile == null || !effectEditorPlaybackRequested || audioStartedAtNanos <= 0) return effectEditorPlayheadBeat;
        double beat = PlaybackCoordinates.beatAtSongTime(chart, playbackTimingProfile, effectEditorCurrentSongTime());
        return Math.max(0.0, Math.min(Math.max(0.0, chart.totalBeats), beat));
    }

    private static double effectEditorCurrentSongTime() {
        if (audioStartedAtNanos <= 0) return playbackStartSeconds;
        double elapsed = Math.max(0.0, (System.nanoTime() - audioStartedAtNanos) / 1_000_000_000.0)
                * playbackPitchRate();
        return playbackStartSeconds + elapsed;
    }

    private static double playbackPitchRate() {
        return ClientChartAccess.config().playbackPitchPercent / 100.0;
    }

    public static boolean isEffectEditorPlaybackActive() {
        return effectEditorPlaybackRequested && playbackAudioRequested && audioProcess != null && audioProcess.isAlive();
    }

    public static boolean isEffectEditorPlaybackRequested() {
        return effectEditorPlaybackRequested && playbackAudioRequested;
    }

    public static boolean isEffectEditorPreviewPrepared() {
        return effectEditorPreviewPrepared;
    }

    private static void syncPlaybackPreference() {
        var config = ClientChartAccess.config();
        playbackMode = "scroll".equals(config.playbackMode) ? "scroll" : "formal";
    }

    private static boolean isShiftPressed(MinecraftClient client) {
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static String pitchFilter(double pitchPercent) {
        if (Math.abs(pitchPercent - 100.0) < 0.000000001) return "";
        int baseSampleRate = 48_000;
        int shiftedSampleRate = (int) Math.round(baseSampleRate * pitchPercent / 100.0);
        return "aresample=" + baseSampleRate + ",asetrate=" + shiftedSampleRate
                + ",aresample=" + baseSampleRate;
    }

    static void setFlightSpeed(int multiplier) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        float value = switch (multiplier) { case 1 -> 0.05f; case 2 -> 0.075f; case 3 -> 0.1f; default -> 0.2f; };
        client.player.getAbilities().setFlySpeed(value);
        client.player.sendMessage(net.minecraft.text.Text.literal("飞行速度已设置为 " + (multiplier == 1 ? "1" : multiplier == 2 ? "1.5" : multiplier == 3 ? "2" : "4") + " 倍"), true);
    }

    private static boolean isAltPressed(MinecraftClient client) {
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    public static boolean consumePlaybackStartScroll(double vertical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || selectPlaybackStartKey == null || !selectPlaybackStartKey.isPressed() || vertical == 0.0) return false;
        if (!isEditorDimension(client)) return false;
        int delta = vertical > 0.0 ? 1 : -1;
        if (client.getServer() != null) {
            var playerId = client.player.getUuid();
            client.getServer().execute(() -> RhythmcMaker.adjustStartChunkFromClient(client.getServer(), playerId, delta));
        } else {
            sendCommand(client, "rhythmc_start_chunk " + delta);
        }
        var chart = ClientChartAccess.activeChart();
        if (chart != null) {
            syncSelectedPlaybackChunk(chart);
            selectedPlaybackChunk = Math.max(1, Math.min(Math.max(1, chart.chunkCount), selectedPlaybackChunk + (vertical > 0.0 ? 1 : -1)));
        }
        return true;
    }

    private static void syncSelectedPlaybackChunk(ChartManifest chart) {
        if (chart == null || chart.id == null || chart.id.equals(selectedPlaybackChartId)) return;
        selectedPlaybackChartId = chart.id;
        selectedPlaybackChunk = Math.max(1, Math.min(Math.max(1, chart.chunkCount), chart.selectedStartChunk));
    }
    private static void renderEditorSidebar(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || sidebarSnapshot == null) return;
        drawSidebar(drawContext, client, sidebarSnapshot.title(), sidebarSnapshot.lines());
    }

    private static void updateEditorSidebarSnapshot(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) {
            clearSidebarSnapshot();
            return;
        }
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        boolean playing = playbackAudioRequested || audioProcess != null;
        String mode = playing && isEditorDimension(client) && chart != null && chart.bpm > 0 ? "playback"
                : !playing && isEditorDimension(client) && chart != null && chart.bpm > 0 ? "editor"
                : !playing && isHubDimension(client) ? "hub" : "hidden";
        if ("hidden".equals(mode)) {
            clearSidebarSnapshot();
            return;
        }
        double intervalSeconds = sidebarRefreshIntervalSeconds();
        String context = sidebarContext(client, chart, mode);
        long now = System.nanoTime();
        boolean contextChanged = !context.equals(sidebarSnapshotContext);
        boolean intervalChanged = Double.compare(intervalSeconds, sidebarSnapshotIntervalSeconds) != 0;
        boolean due = sidebarSnapshot == null || sidebarRefreshRequested || contextChanged || intervalChanged
                || now - sidebarSnapshotNanos >= secondsToNanos(intervalSeconds);
        if (due) {
            sidebarSnapshot = switch (mode) {
                case "playback" -> playbackSidebar(chart);
                case "editor" -> editorSidebar(client, chart);
                case "hub" -> hubSidebar();
                default -> null;
            };
            sidebarSnapshotContext = context;
            sidebarSnapshotIntervalSeconds = intervalSeconds;
            sidebarSnapshotNanos = now;
            sidebarRefreshRequested = false;
        }
    }
    private static SidebarSnapshot playbackSidebar(ChartManifest chart) {
        double startSeconds = playbackStartSeconds;
        long clockStart = audioStartedAtNanos;
        double elapsed = clockStart <= 0 ? 0.0 : Math.max(0.0, (System.nanoTime() - clockStart) / 1_000_000_000.0) * playbackPitchRate();
        double totalSeconds = chart.durationSeconds > 0.0 ? chart.durationSeconds : ChartTiming.beatToSeconds(chart, chart.totalBeats);
        if (!Double.isFinite(totalSeconds) || totalSeconds <= 0.0) totalSeconds = Math.max(1.0, chart.chunkCount * 60.0 / chart.bpm);
        double currentSeconds = Math.max(0.0, Math.min(totalSeconds, startSeconds + elapsed));
        int divisions = Math.max(1, Math.min(32, chart.divisionsPerChunk > 0 ? chart.divisionsPerChunk : chart.beatsPerMeasure));
        ChartTiming.Prepared timing = playbackTimingProfile;
        if (timing == null) timing = ChartTiming.prepare(chart);
        double songBeats = PlaybackCoordinates.beatAtSongTime(chart, timing, currentSeconds);
        int totalChunks = Math.max(1, chart.chunkCount);
        int currentChunk = Math.max(1, Math.min(totalChunks, (int) Math.floor(songBeats) + 1));
        int currentDivision = Math.max(1, Math.min(divisions, (int) Math.floor((songBeats - Math.floor(songBeats)) * divisions) + 1));
        return new SidebarSnapshot("播放", new String[] {
            "歌曲时间 " + formatHudTime(currentSeconds) + "/" + formatHudTime(totalSeconds),
            "chunk数 " + currentChunk + "/" + totalChunks,
            "节拍数 " + currentDivision + "/" + divisions
        });
    }

    private static SidebarSnapshot editorSidebar(MinecraftClient client, ChartManifest chart) {
        int divisions = Math.max(1, Math.min(32, chart.divisionsPerChunk > 0 ? chart.divisionsPerChunk : chart.beatsPerMeasure));
        double progress = Math.max(0.0, (-client.player.getZ() - 2.0) / divisions);
        int totalChunks = Math.max(1, chart.chunkCount);
        int currentChunk = Math.max(1, Math.min(totalChunks, (int) Math.floor(progress) + 1));
        double totalSeconds = chart.durationSeconds > 0.0 ? chart.durationSeconds : ChartTiming.beatToSeconds(chart, chart.totalBeats);
        if (!Double.isFinite(totalSeconds) || totalSeconds <= 0.0) totalSeconds = totalChunks * 60.0 / chart.bpm;
        double currentSeconds = Math.max(0.0, Math.min(totalSeconds, ChartTiming.beatToSeconds(chart, progress)));
        return new SidebarSnapshot("制谱器", new String[] {
            "歌曲时间 " + formatHudTime(currentSeconds) + "/" + formatHudTime(totalSeconds),
            "chunk数 " + currentChunk + "/" + totalChunks,
            "当前每chunk分数 " + divisions,
            "当前轨道宽度 " + Math.max(1, Math.min(9, chart.laneCount))
        });
    }

    private static SidebarSnapshot hubSidebar() {
        return new SidebarSnapshot("制谱器大厅", new String[] { ClientChartAccess.config().lobbySidebarContent });
    }

    private static double sidebarRefreshIntervalSeconds() {
        double value = ClientChartAccess.config().sidebarRefreshIntervalSeconds;
        return Double.isFinite(value) ? Math.max(0.05, Math.min(3600.0, value)) : 1.0;
    }

    private static long secondsToNanos(double seconds) {
        return Math.max(1L, (long) Math.ceil(seconds * 1_000_000_000.0));
    }

    private static String sidebarContext(MinecraftClient client, ChartManifest chart, String mode) {
        String dimension = client.player == null ? "" : client.player.getEntityWorld().getRegistryKey().getValue().toString();
        if (chart == null) return mode + "|" + dimension + "|" + ClientChartAccess.config().lobbySidebarContent;
        return mode + "|" + dimension + "|" + String.valueOf(chart.id) + "|" + chart.divisionsPerChunk + "|"
                + chart.beatsPerMeasure + "|" + chart.laneCount + "|" + chart.chunkCount + "|" + chart.totalBeats + "|"
                + chart.durationSeconds + "|" + playbackStartChunk + "|" + playbackStartSeconds;
    }

    private static void clearSidebarSnapshot() {
        sidebarSnapshot = null;
        sidebarSnapshotNanos = 0L;
        sidebarSnapshotContext = "";
        sidebarSnapshotIntervalSeconds = -1.0;
        sidebarRefreshRequested = true;
    }

    private record SidebarSnapshot(String title, String[] lines) {}
    private static void drawSidebar(DrawContext drawContext, MinecraftClient client, String title, String[] lines) {
        String footer = "Rhythmc Maker";
        int contentWidth = Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(footer));
        for (String line : lines) contentWidth = Math.max(contentWidth, client.textRenderer.getWidth(line));
        int equalsWidth = Math.max(1, client.textRenderer.getWidth("="));
        int separatorCount = Math.max(22, (int) Math.ceil(contentWidth / (double) equalsWidth));
        String separator = "=".repeat(separatorCount);
        int sidebarWidth = client.textRenderer.getWidth(separator);
        int x = drawContext.getScaledWindowWidth() - sidebarWidth - 8;
        int y = 8;
        int lineHeight = 12;
        int titleColor = 0xFFF3F8FF;
        int separatorColor = 0xFF72E8FF;
        int bodyColor = 0xFFE6F2FF;
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(title), x + (sidebarWidth - client.textRenderer.getWidth(title)) / 2, y, titleColor);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(separator), x, y + lineHeight, separatorColor);
        for (int index = 0; index < lines.length; index++) {
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(lines[index]), x, y + lineHeight * (index + 2), bodyColor);
        }
        int bottom = y + lineHeight * (lines.length + 2);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(separator), x, bottom, separatorColor);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(footer), x + (sidebarWidth - client.textRenderer.getWidth(footer)) / 2, bottom + lineHeight, titleColor);
    }

    private static boolean isHubDimension(MinecraftClient client) {
        return client.player != null && client.player.getEntityWorld().getRegistryKey().equals(net.minecraft.world.World.OVERWORLD);
    }
    private static String formatHudTime(double seconds) {
        int totalCentiseconds = Math.max(0, (int) Math.floor(seconds * 100.0));
        int minutes = totalCentiseconds / 6000;
        return String.format(Locale.ROOT, "%02d:%05.2f", minutes, (totalCentiseconds % 6000) / 100.0);
    }

    private static boolean isEditorDimension(MinecraftClient client) {
        if (client.player == null) return false;
        var dimension = client.player.getEntityWorld().getRegistryKey().getValue();
        return cn.frkovo.rhythmcmaker.ChartDimensionManager.isChartWorld(client.player.getEntityWorld().getRegistryKey())
            || dimension.equals(RhythmcMaker.CHARTER_DIMENSION.getValue());
    }

    private static void startAudio(int startChunk) {
        startAudioAtBeat(PlaybackCoordinates.beatAtChunkStart(startChunk));
    }

    private static void startAudioAtBeat(double startBeat) {
        var chart = ClientChartAccess.resolveActiveChart();
        if (chart == null || chart.audioFile == null || chart.audioFile.isBlank() || chart.bpm <= 0) {
            audioAttempted = true;
            ClientChartAccess.status("音频播放失败：当前谱面或音频未加载");
            return;
        }
        if (audioPlayerFuture == null || !audioPlayerFuture.isDone()) {
            if (!audioPreparingNoticeShown) {
                ClientChartAccess.status("正在准备音频播放器...");
                audioPreparingNoticeShown = true;
            }
            return;
        }
        try {
            Path audio = ClientChartAccess.activeAudioPath();
            if (!Files.isRegularFile(audio)) throw new IOException("音频文件不存在");
            Path player = audioPlayerFuture.join();
            playbackStartBeat = Math.max(0.0, Math.min(Math.max(0.0, chart.totalBeats), startBeat));
            playbackTimingProfile = ChartTiming.prepare(chart);
            double startSeconds = PlaybackCoordinates.songTimeAtBeat(chart, playbackTimingProfile, playbackStartBeat);
            audioAttempted = true;
            audioErrorLog = Path.of(System.getProperty("java.io.tmpdir"), "rhythmc-maker", "audio-error.log");
            String pitch = pitchFilter(getPlaybackPitchPercent());
            String audioFilter = "volume=" + String.format(Locale.ROOT, "%.3f", ClientChartAccess.config().musicVolumeMultiplier)
                    + (pitch.isBlank() ? "" : "," + pitch);
            audioProcess = new ProcessBuilder(player.toString(), "-nodisp", "-autoexit", "-loglevel", "error", "-probesize", "32", "-analyzeduration", "0", "-ss", String.format(Locale.ROOT, "%.6f", startSeconds), "-i", audio.toString(), "-vn", "-af", audioFilter)
                .redirectError(ProcessBuilder.Redirect.to(audioErrorLog.toFile()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
            audioStartedAtNanos = System.nanoTime();
            if (audioSessionCreatedAtNanos == 0L) audioSessionCreatedAtNanos = audioStartedAtNanos;
            audioProgressSeconds = 0.0;
            audioProgressUpdatedAtNanos = audioStartedAtNanos;
            formalProgressMissingSinceNanos = 0L;
            playbackTrackProfile = PlaybackCoordinates.prepareDefaultTrack(chart);
            playbackTrackSpeed = ClientChartAccess.config().playerSpeed;
            playbackStartSeconds = startSeconds;
            double pitchRate = playbackPitchRate();
            audioDurationSeconds = Math.max(0, chart.durationSeconds - startSeconds) / pitchRate;
            if (effectEditorPreviewPrepared) {
                pendingPlaybackCommand = "rhythmc_effect_preview_play " + String.format(Locale.ROOT, "%.6f", playbackStartBeat);
            }
        } catch (IOException | CompletionException exception) {
            audioAttempted = true;
            audioProcess = null;
            Throwable cause = exception instanceof CompletionException && exception.getCause() != null ? exception.getCause() : exception;
            System.err.println("RhythMC audio playback unavailable: " + cause);
            ClientChartAccess.status("音频播放失败，请检查谱面音频文件");
        }
    }

    private static Path extractAudioPlayer() throws IOException {
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "rhythmc-maker", "native");
        Files.createDirectories(directory);
        Path executable = directory.resolve("ffplay.exe");
        java.net.URL bundled = RhythmcMakerClient.class.getResource("/rhythmc_maker/native/windows-x86_64/ffplay.exe");
        if (bundled == null) throw new IOException("Mod 内置音频播放器缺失");
        long bundledSize = bundled.openConnection().getContentLengthLong();
        if (Files.isRegularFile(executable) && Files.size(executable) == bundledSize) return executable;
        Path temporary = directory.resolve("ffplay.exe.part");
        try (var input = bundled.openStream()) {
            Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temporary, executable, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        executable.toFile().setExecutable(true);
        return executable;
    }

    private static void retryAudioPlayback(MinecraftClient client) {
        audioAttempted = false;
        playbackAudioDelayTicks = Math.max(playbackAudioDelayTicks, 10);
        if (!audioRetryNoticeShown) {
            ClientChartAccess.statusWarning("播放失败：正在重试音频播放器");
            audioRetryNoticeShown = true;
        }
        Process process = audioProcess;
        audioProcess = null;
        if (process != null && process.isAlive()) process.destroy();
    }

    private static void stopAudio() {
        Process process = audioProcess;
        audioProcess = null;
        audioDurationSeconds = 0;
        playbackStartSeconds = 0;
        audioStartedAtNanos = 0;
        audioSessionCreatedAtNanos = 0L;
        playbackTimingProfile = null;
        playbackTrackProfile = null;
        if (process != null && process.isAlive()) process.destroy();
        audioProgressSeconds = Double.NaN;
        audioProgressUpdatedAtNanos = 0L;
        formalProgressMissingSinceNanos = 0L;
    }

    private static void finishPlayback(MinecraftClient client, boolean stopServer) {
        if (effectEditorPlaybackRequested) {
            effectEditorPlayheadBeat = effectEditorCurrentBeat();
            effectEditorPlaybackRequested = false;
        }
        stopAudio();
        playbackAudioRequested = false;
        pendingPlaybackCommand = null;
        if (stopServer && effectEditorPreviewPrepared) {
            sendCommand(client, "rhythmc_effect_preview_seek " + String.format(Locale.ROOT, "%.6f", effectEditorPlayheadBeat));
        } else if (stopServer && !playbackStopRequested) {
            playbackStopRequested = true;
            sendCommand(client, "rhythmc_stop_playback");
        }
    }

    private static void renderScrollPlaybackBox(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean frozenEditorPreview = isEffectEditorCameraLocked() && !playbackAudioRequested;
        if ((!playbackAudioRequested && !frozenEditorPreview) || !"scroll".equals(playbackMode) || client.player == null || !isEditorDimension(client)) return;
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        if (chart == null) return;

        double centerZ;
        if (effectEditorPreviewPrepared) {
            centerZ = PlaybackCoordinates.editorWorldZAtBeat(chart, effectEditorCurrentBeat());
        } else {
            if (playbackTimingProfile == null || playbackTrackProfile == null) return;
            double songTime = playbackStartSeconds
                    + Math.max(0.0, (System.nanoTime() - audioStartedAtNanos) / 1_000_000_000.0) * playbackPitchRate();
            double beat = PlaybackCoordinates.beatAtSongTime(chart, playbackTimingProfile, songTime);
            centerZ = PlaybackCoordinates.editorWorldZAtBeat(chart, beat);
        }
        int halfWidth = Math.max(0, Math.min(9, chart.laneCount) / 2);
        int left = -halfWidth - 1;
        int right = halfWidth + 1;
        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();

        context.matrices().push();
        context.matrices().translate(-camera.x, -camera.y, -camera.z);
        VertexRendering.drawOutline(
            context.matrices(),
            context.consumers().getBuffer(RenderLayers.linesTranslucent()),
            VoxelShapes.cuboid(left, 65.0, centerZ - 0.5, right + 1.0, 70.0, centerZ + 0.5),
            0.0, 0.0, 0.0, 0xDFFFFFFF, 2.0f
        );
        context.matrices().pop();
    }
    public static boolean isSceneOperationBusy() { return ClientSceneAccess.operationBusy(); }
    static void sendChartCommand(String command) { sendCommand(MinecraftClient.getInstance(), command); }
    private static void sendCommand(MinecraftClient client, String command) { if (client.player != null) client.player.networkHandler.sendChatCommand(command); }
}
