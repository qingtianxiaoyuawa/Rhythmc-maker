package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartStorage;
import cn.frkovo.rhythmcmaker.chart.Rhythmc3Exporter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class ClientChartAccess {
    private static ChartManifest activeChart;
    private static RegistryKey<World> activeChartWorld;
    private static MinecraftServer activeChartServer;
    private static java.nio.file.attribute.FileTime activeChartModifiedTime;
    private static long activeChartFileSize = -1L;
    private static final ExecutorService REFRESH_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rhythmc-maker-chart-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private static Future<?> refreshTask;
    private static volatile RefreshResult completedRefresh;
    private static volatile long refreshGeneration;
    private static String lastRefreshRequest;
    private static String lastRefreshError;
    private ClientChartAccess() {
    }

    static MinecraftServer server() throws IOException {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server == null) {
            throw new IOException("当前版本仅支持单人制谱器世界");
        }
        return server;
    }

    static List<ChartManifest> charts() throws IOException {
        return ChartStorage.list(server());
    }

    static Path stageAudio(Path source) throws IOException {
        return ChartStorage.stageAudio(server(), source);
    }

    static void create(ChartManifest chart, Path stagedAudio) throws IOException {
        ChartStorage.create(server(), chart, stagedAudio);
    }

    static ChartManifest importRhythmc3(Path difficultyFile, Path manifestFile, Path stagedAudio) throws IOException { return ChartStorage.importRhythmc3(server(), difficultyFile, manifestFile, stagedAudio); }
    static ChartManifest importRhythmc2(Path difficultyFile, Path stagedAudio, double bpm, String difficulty, double level) throws IOException { return ChartStorage.importRhythmc2(server(), difficultyFile, stagedAudio, bpm, difficulty, level); }
    static ChartManifest importRhythmc3(Path difficultyFile, Path manifestFile, Path stagedAudio, List<ImportedScene> scenes) throws IOException {
        return ChartStorage.importRhythmc3(server(), difficultyFile, manifestFile, stagedAudio, scenes.stream().map(scene -> new ChartStorage.ImportedScene(scene.schem(), scene.info())).toList());
    }
    record ImportedScene(Path schem, Path info) {}

    static void setActiveChart(ChartManifest chart) {
        refreshGeneration++;
        completedRefresh = null;
        lastRefreshRequest = null;
        lastRefreshError = null;
        activeChart = chart;
        activeChartWorld = null;
        activeChartModifiedTime = null;
        activeChartFileSize = -1L;
    }

    static ChartManifest activeChart() { return activeChart; }

    static ChartManifest resolveActiveChart() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getServer() == null) return activeChart;
        synchronizeServer(client.getServer());
        try {
            var serverPlayer = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            RegistryKey<World> worldKey = serverPlayer == null ? client.player.getEntityWorld().getRegistryKey() : serverPlayer.getEntityWorld().getRegistryKey();
            if (worldKey.equals(activeChartWorld)) return activeChart;
            if (cn.frkovo.rhythmcmaker.ChartDimensionManager.isChartWorld(worldKey)) {
                ChartManifest chart = ChartStorage.findByDimensionId(client.getServer(), worldKey.getValue().getPath());
                if (chart != null) activeChart = chart;
                activeChartWorld = worldKey;
                return chart == null ? activeChart : chart;
            }
        } catch (IOException ignored) {
        }
        return activeChart;
    }

    static boolean refreshActiveChartIfChanged() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getServer() == null) return false;
        synchronizeServer(client.getServer());
        boolean changed = applyCompletedRefresh(client.getServer());
        try {
            var serverPlayer = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            RegistryKey<World> worldKey = serverPlayer == null ? client.player.getEntityWorld().getRegistryKey() : serverPlayer.getEntityWorld().getRegistryKey();
            if (!cn.frkovo.rhythmcmaker.ChartDimensionManager.isChartWorld(worldKey)) return changed;
            boolean sameChart = activeChart != null && worldKey.equals(activeChartWorld);
            FileTime modified = null;
            long size = -1L;
            if (sameChart) {
                var attributes = ChartStorage.manifestAttributes(client.getServer(), activeChart.id);
                modified = attributes.lastModifiedTime();
                size = attributes.size();
                if (modified.equals(activeChartModifiedTime) && size == activeChartFileSize) return changed;
            }
            String request = worldKey.getValue().getPath() + "|" + (sameChart ? activeChart.id : "") + "|" + modified + "|" + size;
            if (refreshTask == null || refreshTask.isDone()) {
                if (!request.equals(lastRefreshRequest) && !request.equals(lastRefreshError)) {
                    long generation = ++refreshGeneration;
                    lastRefreshRequest = request;
                    MinecraftServer server = client.getServer();
                    String chartId = sameChart ? activeChart.id : null;
                    FileTime requestedModified = modified;
                    long requestedSize = size;
                    refreshTask = REFRESH_EXECUTOR.submit(() -> loadRefresh(server, worldKey, chartId, requestedModified, requestedSize, generation, request));
                }
            }
            return changed;
        } catch (IOException ignored) {
            return changed;
        }
    }

    private static void loadRefresh(MinecraftServer server, RegistryKey<World> worldKey, String chartId, FileTime modified, long size, long generation, String request) {
        try {
            ChartManifest fresh = chartId == null
                    ? ChartStorage.findByDimensionId(server, worldKey.getValue().getPath())
                    : ChartStorage.find(server, chartId);
            if (fresh == null) {
                completedRefresh = new RefreshResult(generation, null, worldKey, null, -1L, request, "未找到对应谱面文件");
                return;
            }
            var attributes = ChartStorage.manifestAttributes(server, fresh.id);
            completedRefresh = new RefreshResult(generation, fresh, worldKey, attributes.lastModifiedTime(), attributes.size(), request, null);
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            completedRefresh = new RefreshResult(generation, null, worldKey, modified, size, request, message);
        }
    }

    private static boolean applyCompletedRefresh(MinecraftServer server) {
        RefreshResult result = completedRefresh;
        if (result == null || result.generation != refreshGeneration) return false;
        completedRefresh = null;
        if (result.error != null) {
            if (!result.request.equals(lastRefreshError)) {
                lastRefreshError = result.request;
                status("谱面刷新失败，已保留当前谱面：" + result.error);
            }
            return false;
        }
        lastRefreshError = null;
        if (result.chart == null) return false;
        boolean changed = activeChart == null
                || !result.chart.id.equals(activeChart.id)
                || result.chart.divisionsPerChunk != activeChart.divisionsPerChunk
                || result.chart.laneCount != activeChart.laneCount
                || result.chart.trackLength != activeChart.trackLength
                || result.chart.chunkCount != activeChart.chunkCount;
        activeChart = result.chart;
        activeChartWorld = result.worldKey;
        activeChartModifiedTime = result.modified;
        activeChartFileSize = result.size;
        return changed;
    }

    static Path activeAudioPath() throws IOException { return ChartStorage.audioPath(server(), resolveActiveChart()); }

    private static void synchronizeServer(MinecraftServer server) {
        if (activeChartServer == server) return;
        activeChartServer = server;
        refreshGeneration++;
        completedRefresh = null;
        if (refreshTask != null) refreshTask.cancel(false);
        refreshTask = null;
        lastRefreshRequest = null;
        lastRefreshError = null;
        activeChart = null;
        activeChartWorld = null;
        activeChartModifiedTime = null;
        activeChartFileSize = -1L;
    }

    private record RefreshResult(long generation, ChartManifest chart, RegistryKey<World> worldKey,
                                 FileTime modified, long size, String request, String error) {}

    static cn.frkovo.rhythmcmaker.chart.BpmDetector.TimingAnalysis analyzeTiming(Path audio) throws IOException { return ChartStorage.analyzeTiming(audio); }

    static cn.frkovo.rhythmcmaker.config.RhythmcMakerConfig config() { return cn.frkovo.rhythmcmaker.RhythmcMaker.currentConfig(); }

    static void saveConfig(cn.frkovo.rhythmcmaker.config.RhythmcMakerConfig config) throws IOException { cn.frkovo.rhythmcmaker.RhythmcMaker.updateConfig(server(), config); }

    static void update(ChartManifest chart) throws IOException {
        cn.frkovo.rhythmcmaker.RhythmcMaker.updateChart(server(), chart);
    }
    static Path exportRhythmc3(ChartManifest chart) throws IOException {
        return Rhythmc3Exporter.export(server(), chart);
    }

    static void delete(ChartManifest chart) throws IOException {
        ChartStorage.delete(server(), chart);
    }

    static int defaultDivisionsPerChunk() {
        return cn.frkovo.rhythmcmaker.RhythmcMaker.defaultDivisionsPerChunk();
    }

    static ItemStack coverStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return new ItemStack(Items.MUSIC_DISC_13);
        try {
            return new ItemStack(Registries.ITEM.get(Identifier.of(itemId)));
        } catch (RuntimeException ignored) {
            return new ItemStack(Items.MUSIC_DISC_13);
        }
    }

    static String itemName(String itemId) {
        return coverStack(itemId).getName().getString();
    }

    static String difficultyText(ChartManifest chart) {
        return chart.difficulty + "  " + String.format(Locale.ROOT, "%.1f", chart.level);
    }

    static int difficultyColor(String difficulty) {
        return switch (difficulty) {
            case "WD" -> 0x63D98A;
            case "NR" -> 0xF05B64;
            case "ED" -> 0xB77BFF;
            case "VO" -> 0xA7ADB7;
            default -> 0xFFFFFF;
        };
    }

    static void status(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal(message), true);
    }
}


