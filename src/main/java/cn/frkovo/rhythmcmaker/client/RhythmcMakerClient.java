package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.RhythmcMaker;
import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartTiming;
import cn.frkovo.rhythmcmaker.chart.PlaybackCoordinates;
import cn.frkovo.rhythmcmaker.chart.TrackPlayback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
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
    private static final double PLAYBACK_FRAME_SPEED_MULTIPLIER = 0.2;
    private static KeyBinding settingsKey;
    private static KeyBinding selectPlaybackStartKey;
    private static boolean controlSpeedHandled;
    private static int selectedPlaybackChunk = 1;
    private static String selectedPlaybackChartId;
    private static volatile Process audioProcess;
    private static long audioStartedAtNanos;
    private static ChartTiming.Prepared playbackTimingProfile;
    private static TrackPlayback.Prepared playbackTrackProfile;
    private static double playbackTrackSpeed = 1.0;
    private static boolean playbackStopRequested;
    private static double audioDurationSeconds;
    private static double playbackStartSeconds;
    private static double playbackFrameStartZ;
    private static double playbackFrameVelocity;
    private static boolean audioAttempted;
    private static Path audioErrorLog;
    private static CompletableFuture<Path> audioPlayerFuture;
    private static boolean audioPreparingNoticeShown;
    private static int playbackAudioDelayTicks;
    private static int playbackToggleCooldownTicks;
    private static int playbackStartChunk = 1;
    private static boolean playbackAudioRequested;
    private static String pendingPlaybackCommand;
    private static String playbackMode = "formal";
    private static double playbackRate = 1.0;
    private static SidebarSnapshot sidebarSnapshot;
    private static long sidebarSnapshotNanos;
    private static String sidebarSnapshotContext = "";
    private static double sidebarSnapshotIntervalSeconds = -1.0;
    private static boolean sidebarRefreshRequested = true;

    @Override public void onInitializeClient() {
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
            else if (item == RhythmcMaker.CHART_INFO_ITEM) { if (ClientChartAccess.activeChart() != null) client.setScreen(new ChartInfoScreen(ClientChartAccess.activeChart())); else if (client.player != null) client.player.sendMessage(net.minecraft.text.Text.literal("当前谱面信息尚未加载"), true); }
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
            String operationNotice = ClientSceneAccess.consumeOperationNotice();
            if (operationNotice != null) ClientSceneAccess.status(operationNotice);
            if (ClientSceneAccess.operationBusy() && client.currentScreen != null) client.setScreen(null);
            if (ClientSceneAccess.operationBusy()) {
                String operationStatus = ClientSceneAccess.operationStatus();
                if (operationStatus != null && client.player != null && client.world != null && client.world.getTime() % 10L == 0L) client.player.sendMessage(Text.literal(operationStatus), true);
            }
            updateEditorSidebarSnapshot(client);
            while (settingsKey.wasPressed()) client.setScreen(new PlaceholderScreen("设置"));
            boolean inPlaybackArea = client.player != null && isEditorDimension(client) && Math.abs(client.player.getX() - 200.5) < 2.0 && Math.abs(client.player.getY() - 66.0) < 2.0 && Math.abs(client.player.getZ() - 0.5) < 2.0;
            boolean inChartDimension = client.player != null && isEditorDimension(client);
            if (playbackToggleCooldownTicks > 0) playbackToggleCooldownTicks--;
            if (inChartDimension && playbackAudioDelayTicks > 0) playbackAudioDelayTicks--;
            if (playbackAudioRequested && playbackAudioDelayTicks <= 0 && audioProcess == null && !audioAttempted) startAudio(playbackStartChunk);
            if (playbackAudioRequested && pendingPlaybackCommand != null && audioProcess != null && audioProcess.isAlive() && audioStartedAtNanos > 0 && System.nanoTime() - audioStartedAtNanos >= 150_000_000L) {
                sendCommand(client, pendingPlaybackCommand);
                pendingPlaybackCommand = null;
            }
            if (playbackAudioRequested && audioAttempted && audioProcess == null && pendingPlaybackCommand == null) {
                finishPlayback(client, true);
            }
            if (audioProcess != null && (!audioProcess.isAlive()
                    || (audioDurationSeconds > 0 && (System.nanoTime() - audioStartedAtNanos) / 1_000_000_000.0 >= audioDurationSeconds + 0.5))) {
                finishPlayback(client, true);
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
        playbackAudioDelayTicks = 2;
        var activeChart = ClientChartAccess.resolveActiveChart();
        syncSelectedPlaybackChunk(activeChart);
        int startChunk = activeChart == null ? selectedPlaybackChunk : Math.max(1, Math.min(Math.max(1, activeChart.chunkCount), selectedPlaybackChunk));
        selectedPlaybackChunk = startChunk;
        playbackStartChunk = startChunk;
        playbackStartSeconds = activeChart == null ? 0.0 : ChartTiming.beatToSeconds(activeChart, Math.max(0, startChunk - 1.0));
        audioStartedAtNanos = 0;
        playbackTimingProfile = null;
        playbackTrackProfile = null;
        pendingPlaybackCommand = "rhythmc_play " + startChunk + " " + playbackMode + " " + String.format(Locale.ROOT, "%.2f", playbackRate);
        startAudio(startChunk);
        if (audioProcess != null && audioProcess.isAlive()) {
            sendCommand(client, pendingPlaybackCommand);
            pendingPlaybackCommand = null;
        }
    }

    private static void syncPlaybackPreference() {
        var config = ClientChartAccess.config();
        playbackMode = "scroll".equals(config.playbackMode) ? "scroll" : "formal";
        playbackRate = normalizePlaybackRate(config.playbackSpeed);
    }

    private static double normalizePlaybackRate(double value) {
        double[] choices = {0.25, 0.5, 0.75, 1.0, 1.5, 2.0};
        double nearest = choices[0];
        for (double choice : choices) if (Math.abs(choice - value) < Math.abs(nearest - value)) nearest = choice;
        return nearest;
    }

    private static boolean isShiftPressed(MinecraftClient client) {
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static String tempoFilter(double rate) {
        if (Math.abs(rate - 1.0) < 0.001) return "";
        if (Math.abs(rate - 0.25) < 0.001) return "atempo=0.5,atempo=0.5";
        return "atempo=" + String.format(Locale.ROOT, "%.2f", rate);
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
        double elapsed = clockStart <= 0 ? 0.0 : Math.max(0.0, (System.nanoTime() - clockStart) / 1_000_000_000.0) * playbackRate;
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
        double progress = Math.max(0.0, -client.player.getZ() - 3.0) / divisions;
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
            double divisionsPerChunk = Math.max(1.0, Math.min(32.0, chart.divisionsPerChunk));
            double audioStartBeat = Math.max(0, (startChunk - 1.0) * divisionsPerChunk);
            double startSeconds = ChartTiming.beatToSeconds(chart, audioStartBeat);
            audioAttempted = true;
            audioErrorLog = Path.of(System.getProperty("java.io.tmpdir"), "rhythmc-maker", "audio-error.log");
            String tempo = tempoFilter(playbackRate);
            String audioFilter = "volume=" + String.format(Locale.ROOT, "%.3f", ClientChartAccess.config().musicVolumeMultiplier) + (tempo.isBlank() ? "" : "," + tempo);
            audioProcess = new ProcessBuilder(player.toString(), "-nodisp", "-autoexit", "-loglevel", "error", "-probesize", "32", "-analyzeduration", "0", "-ss", String.format(Locale.ROOT, "%.6f", startSeconds), "-i", audio.toString(), "-vn", "-af", audioFilter)
                .redirectError(ProcessBuilder.Redirect.to(audioErrorLog.toFile()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
            audioStartedAtNanos = System.nanoTime();
            playbackTimingProfile = ChartTiming.prepare(chart);
            playbackTrackProfile = PlaybackCoordinates.prepareDefaultTrack(chart);
            playbackTrackSpeed = ClientChartAccess.config().playerSpeed;
            playbackStartSeconds = startSeconds;
            double sampleSeconds = 1.0 / 20.0;
            playbackFrameStartZ = PlaybackCoordinates.editorWorldZAtSongTime(chart, playbackTimingProfile, startSeconds);
            double nextFrameZ = PlaybackCoordinates.editorWorldZAtSongTime(chart, playbackTimingProfile, startSeconds + sampleSeconds);
            playbackFrameVelocity = (nextFrameZ - playbackFrameStartZ) / sampleSeconds * PLAYBACK_FRAME_SPEED_MULTIPLIER;
            audioDurationSeconds = Math.max(0, chart.durationSeconds - startSeconds) / playbackRate;
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
        Path marker = directory.resolve("ffplay-v1.ready");
        if (Files.isRegularFile(executable) && Files.isRegularFile(marker)) return executable;
        Path temporary = directory.resolve("ffplay.exe.part");
        try (var input = RhythmcMakerClient.class.getResourceAsStream("/rhythmc_maker/native/windows-x86_64/ffplay.exe")) {
            if (input == null) throw new IOException("Mod 内置音频播放器缺失");
            Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temporary, executable, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        Files.writeString(marker, "ffplay-v1");
        executable.toFile().setExecutable(true);
        return executable;
    }

    private static void stopAudio() {
        Process process = audioProcess;
        audioProcess = null;
        audioDurationSeconds = 0;
        playbackStartSeconds = 0;
        playbackFrameStartZ = 0;
        playbackFrameVelocity = 0;
        audioStartedAtNanos = 0;
        playbackTimingProfile = null;
        playbackTrackProfile = null;
        if (process != null && process.isAlive()) process.destroy();
    }

    private static void finishPlayback(MinecraftClient client, boolean stopServer) {
        stopAudio();
        playbackAudioRequested = false;
        pendingPlaybackCommand = null;
        if (stopServer && !playbackStopRequested) {
            playbackStopRequested = true;
            sendCommand(client, "rhythmc_stop_playback");
        }
    }

    private static void renderScrollPlaybackBox(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!playbackAudioRequested || !"scroll".equals(playbackMode) || client.player == null || !isEditorDimension(client)) return;
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        if (chart == null) return;

        long clockStart = audioStartedAtNanos;
        double elapsed = clockStart <= 0 ? 0.0 : Math.max(0.0, (System.nanoTime() - clockStart) / 1_000_000_000.0) * playbackRate;
        if (playbackTimingProfile == null || playbackTrackProfile == null) return;
        double centerZ = playbackFrameStartZ + playbackFrameVelocity * elapsed;
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
