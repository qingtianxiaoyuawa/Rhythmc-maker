package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartStorage;
import cn.frkovo.rhythmcmaker.chart.Rhythmc3Exporter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class ClientChartAccess {
    private static ChartManifest activeChart;
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

    static void setActiveChart(ChartManifest chart) { activeChart = chart; }

    static ChartManifest activeChart() { return activeChart; }

    static ChartManifest resolveActiveChart() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getServer() == null) return activeChart;
        try {
            var serverPlayer = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            var worldKey = serverPlayer == null ? client.player.getEntityWorld().getRegistryKey() : serverPlayer.getEntityWorld().getRegistryKey();
            if (cn.frkovo.rhythmcmaker.ChartDimensionManager.isChartWorld(worldKey)) {
                ChartManifest chart = ChartStorage.findByDimensionId(client.getServer(), worldKey.getValue().getPath());
                if (chart != null) activeChart = chart;
                return chart == null ? activeChart : chart;
            }
            int slot = cn.frkovo.rhythmcmaker.ChartDimensionManager.slotOf(worldKey);
            if (slot != 0) {
                ChartManifest chart = ChartStorage.findByEditorSlot(client.getServer(), slot);
                if (chart != null) activeChart = chart;
                return chart == null ? activeChart : chart;
            }
        } catch (IOException ignored) {
        }
        return activeChart;
    }

    static Path activeAudioPath() throws IOException { return ChartStorage.audioPath(server(), resolveActiveChart()); }

    static cn.frkovo.rhythmcmaker.chart.BpmDetector.TimingAnalysis analyzeTiming(Path audio) throws IOException { return ChartStorage.analyzeTiming(audio); }

    static cn.frkovo.rhythmcmaker.config.RhythmcMakerConfig config() { return cn.frkovo.rhythmcmaker.RhythmcMaker.currentConfig(); }

    static void saveConfig(cn.frkovo.rhythmcmaker.config.RhythmcMakerConfig config) throws IOException { cn.frkovo.rhythmcmaker.RhythmcMaker.updateConfig(server(), config); }

    static void update(ChartManifest chart) throws IOException {
        ChartStorage.update(server(), chart);
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
