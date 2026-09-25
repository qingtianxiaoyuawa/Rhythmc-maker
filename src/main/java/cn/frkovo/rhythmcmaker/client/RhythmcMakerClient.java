package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.RhythmcMaker;
import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.item.Item;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
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
    private static volatile Process audioProcess;
    private static long audioStartedAtNanos;
    private static double audioDurationSeconds;
    private static double playbackStartSeconds;
    private static boolean audioAttempted;
    private static Path audioErrorLog;
    private static CompletableFuture<Path> audioPlayerFuture;
    private static boolean audioPreparingNoticeShown;
    private static int playbackAudioDelayTicks;
    private static int playbackToggleCooldownTicks;
    private static boolean playbackAudioRequested;
    private static String pendingPlaybackCommand;

    @Override public void onInitializeClient() {
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
            Item item = player.getStackInHand(hand).getItem();
            MinecraftClient client = MinecraftClient.getInstance();
            if (item == RhythmcMaker.START_CHART_ITEM) client.setScreen(new ChartListScreen());
            else if (item == RhythmcMaker.UNNAMED_ITEM) client.setScreen(new PlaceholderScreen("awa~", "awa-menu"));
            else if (item == RhythmcMaker.SETTINGS_ITEM) client.setScreen(new PlaceholderScreen("设置"));
            else if (item == RhythmcMaker.CHART_SETTINGS_ITEM) client.setScreen(new PlaceholderScreen("谱面设置", "chart-settings"));
            else if (item == RhythmcMaker.MORE_SETTINGS_ITEM) sendCommand(client, "rhythmc_more_options");
            else if (item == RhythmcMaker.QUICK_FUNCTIONS_ITEM) client.setScreen(new PlaceholderScreen("快捷功能", "quick-functions"));
            else if (item == RhythmcMaker.MORE_OPTIONS_RETURN_ITEM) sendCommand(client, "rhythmc_more_return");
            else if (item == RhythmcMaker.EXPORT_ITEM) client.setScreen(new ExportChartScreen());
            else if (item == RhythmcMaker.CHART_INFO_ITEM) { if (ClientChartAccess.activeChart() != null) client.setScreen(new ChartInfoScreen(ClientChartAccess.activeChart())); else if (client.player != null) client.player.sendMessage(net.minecraft.text.Text.literal("当前谱面信息尚未加载"), true); }
            else if (item == RhythmcMaker.EFFECT_TRACK_ITEM) client.setScreen(new EffectTrackScreen());
            else if (item == RhythmcMaker.SCENE_EDIT_ITEM) client.setScreen(new SceneEditScreen());
            else if (item == RhythmcMaker.PLAY_ITEM) {
                if (playbackToggleCooldownTicks > 0) return ActionResult.SUCCESS;
                playbackToggleCooldownTicks = 6;
                boolean stopping = playbackAudioRequested || audioProcess != null;
                if (stopping) {
                    stopAudio();
                    playbackAudioRequested = false;
                    pendingPlaybackCommand = null;
                    sendCommand(client, "rhythmc_play");
                    return ActionResult.SUCCESS;
                }
                playbackAudioRequested = true;
                audioAttempted = false;
                audioPreparingNoticeShown = false;
                playbackAudioDelayTicks = 0;
                var activeChart = ClientChartAccess.resolveActiveChart();
                int startChunk = activeChart == null ? selectedPlaybackChunk : Math.max(1, activeChart.selectedStartChunk);
                selectedPlaybackChunk = startChunk;
                pendingPlaybackCommand = "rhythmc_play " + startChunk;
                startAudio();
                if (audioProcess != null || audioAttempted) {
                    sendCommand(client, pendingPlaybackCommand);
                    pendingPlaybackCommand = null;
                }
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
            if (client.player != player || client.currentScreen != null || !isEditorDimension(client) || !isAltPressed(client)) return ActionResult.PASS;
            ChartManifest chart = ClientChartAccess.resolveActiveChart();
            if (chart == null) return ActionResult.PASS;
            int divisions = Math.max(1, chart.divisionsPerChunk > 0 ? chart.divisionsPerChunk : chart.beatsPerMeasure);
            int currentChunk = Math.max(1, Math.min(Math.max(1, chart.chunkCount), (int) Math.floor(Math.max(0.0, -player.getZ() - 3.0) / divisions) + 1));
            sendCommand(client, "rhythmc_start_chunk_set " + currentChunk);
            return ActionResult.FAIL;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.wasPressed()) client.setScreen(new PlaceholderScreen("设置"));
            boolean inPlaybackArea = client.player != null && isEditorDimension(client) && Math.abs(client.player.getX() - 200.5) < 2.0 && Math.abs(client.player.getY() - 66.0) < 2.0 && Math.abs(client.player.getZ() - 0.5) < 2.0;
            boolean inChartDimension = client.player != null && isEditorDimension(client);
            if (playbackToggleCooldownTicks > 0) playbackToggleCooldownTicks--;
            if (inChartDimension && playbackAudioDelayTicks > 0) playbackAudioDelayTicks--;
            if (playbackAudioRequested && playbackAudioDelayTicks <= 0 && audioProcess == null && !audioAttempted) startAudio();
            if (playbackAudioRequested && pendingPlaybackCommand != null && (audioProcess != null || audioAttempted)) {
                sendCommand(client, pendingPlaybackCommand);
                pendingPlaybackCommand = null;
            }
            if (audioProcess != null) {
                if (!audioProcess.isAlive() || (audioDurationSeconds > 0 && (System.nanoTime() - audioStartedAtNanos) / 1_000_000_000.0 >= audioDurationSeconds + 0.5)) { stopAudio(); playbackAudioRequested = false; }
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
        sendCommand(client, "rhythmc_start_chunk " + (vertical > 0.0 ? 1 : -1));
        var chart = ClientChartAccess.activeChart();
        if (chart != null) selectedPlaybackChunk = Math.max(1, Math.min(Math.max(1, chart.chunkCount), selectedPlaybackChunk + (vertical > 0.0 ? 1 : -1)));
        return true;
    }
    private static void renderEditorSidebar(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) return;
        ChartManifest chart = ClientChartAccess.resolveActiveChart();
        boolean playing = playbackAudioRequested || audioProcess != null;
        if (playing && isEditorDimension(client) && chart != null && chart.bpm > 0) {
            renderPlaybackSidebar(drawContext, client, chart);
        } else if (!playing && isEditorDimension(client) && chart != null && chart.bpm > 0) {
            renderEditorSidebarContent(drawContext, client, chart);
        } else if (!playing && isHubDimension(client)) {
            renderHubSidebar(drawContext, client);
        }
    }

    private static void renderPlaybackSidebar(DrawContext drawContext, MinecraftClient client, ChartManifest chart) {
        double startSeconds = playbackStartSeconds;
        double elapsed = audioProcess == null ? 0.0 : Math.max(0.0, (System.nanoTime() - audioStartedAtNanos) / 1_000_000_000.0);
        double totalSeconds = chart.durationSeconds > 0.0 ? chart.durationSeconds : chart.totalBeats * 60.0 / chart.bpm;
        if (!Double.isFinite(totalSeconds) || totalSeconds <= 0.0) totalSeconds = Math.max(1.0, chart.chunkCount * 60.0 / chart.bpm);
        double currentSeconds = Math.max(0.0, Math.min(totalSeconds, startSeconds + elapsed));
        int divisions = Math.max(1, Math.min(32, chart.divisionsPerChunk > 0 ? chart.divisionsPerChunk : chart.beatsPerMeasure));
        double songBeats = currentSeconds * chart.bpm / 60.0;
        int totalChunks = Math.max(1, chart.chunkCount);
        int currentChunk = Math.max(1, Math.min(totalChunks, (int) Math.floor(songBeats) + 1));
        int currentDivision = Math.max(1, Math.min(divisions, (int) Math.floor((songBeats - Math.floor(songBeats)) * divisions) + 1));
        drawSidebar(drawContext, client, "播放", new String[] {
            "歌曲时间 " + formatHudTime(currentSeconds) + "/" + formatHudTime(totalSeconds),
            "chunk数 " + currentChunk + "/" + totalChunks,
            "节拍数 " + currentDivision + "/" + divisions
        });
    }

    private static void renderEditorSidebarContent(DrawContext drawContext, MinecraftClient client, ChartManifest chart) {
        int divisions = Math.max(1, Math.min(32, chart.divisionsPerChunk > 0 ? chart.divisionsPerChunk : chart.beatsPerMeasure));
        double progress = Math.max(0.0, -client.player.getZ() - 3.0) / divisions;
        int totalChunks = Math.max(1, chart.chunkCount);
        int currentChunk = Math.max(1, Math.min(totalChunks, (int) Math.floor(progress) + 1));
        double totalSeconds = chart.durationSeconds > 0.0 ? chart.durationSeconds : chart.totalBeats * 60.0 / chart.bpm;
        if (!Double.isFinite(totalSeconds) || totalSeconds <= 0.0) totalSeconds = totalChunks * 60.0 / chart.bpm;
        double currentSeconds = Math.max(0.0, Math.min(totalSeconds, progress * 60.0 / chart.bpm));
        drawSidebar(drawContext, client, "制谱器", new String[] {
            "歌曲时间 " + formatHudTime(currentSeconds) + "/" + formatHudTime(totalSeconds),
            "chunk数 " + currentChunk + "/" + totalChunks,
            "当前每chunk分数 " + divisions,
            "当前轨道宽度 " + Math.max(1, Math.min(9, chart.laneCount))
        });
    }

    private static void renderHubSidebar(DrawContext drawContext, MinecraftClient client) {
        String content = ClientChartAccess.config().lobbySidebarContent;
        drawSidebar(drawContext, client, "制谱器大厅", new String[] { content });
    }

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
            || cn.frkovo.rhythmcmaker.ChartDimensionManager.isSlotWorld(client.player.getEntityWorld().getRegistryKey())
            || dimension.equals(RhythmcMaker.CHARTER_DIMENSION.getValue())
            || (dimension.getNamespace().equals(RhythmcMaker.MOD_ID) && dimension.getPath().startsWith("chart_"));
    }

    private static void startAudio() {
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
            double startSeconds = Math.max(0, chart.selectedStartChunk - 1) * 60.0 / chart.bpm;
            audioAttempted = true;
            audioErrorLog = Path.of(System.getProperty("java.io.tmpdir"), "rhythmc-maker", "audio-error.log");
            audioProcess = new ProcessBuilder(player.toString(), "-nodisp", "-autoexit", "-loglevel", "error", "-probesize", "32", "-analyzeduration", "0", "-ss", String.format(Locale.ROOT, "%.6f", startSeconds), "-i", audio.toString(), "-vn", "-af", "volume=" + String.format(Locale.ROOT, "%.3f", ClientChartAccess.config().musicVolumeMultiplier))
                .redirectError(ProcessBuilder.Redirect.to(audioErrorLog.toFile()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
            audioStartedAtNanos = System.nanoTime();
            playbackStartSeconds = startSeconds;
            audioDurationSeconds = Math.max(0, chart.durationSeconds - startSeconds);
        } catch (IOException | CompletionException exception) {
            audioAttempted = true;
            audioProcess = null;
            Throwable cause = exception instanceof CompletionException && exception.getCause() != null ? exception.getCause() : exception;
            if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("音频播放失败：" + cause.getMessage()), true);
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
        if (process != null && process.isAlive()) process.destroy();
    }
    static void sendChartCommand(String command) { sendCommand(MinecraftClient.getInstance(), command); }
    private static void sendCommand(MinecraftClient client, String command) { if (client.player != null) client.player.networkHandler.sendChatCommand(command); }
}
