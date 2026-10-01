package cn.frkovo.rhythmcmaker;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import cn.frkovo.rhythmcmaker.chart.ChartStorage;
import net.casual.arcade.dimensions.ArcadeDimensions;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.casual.arcade.dimensions.level.LevelPersistence;
import net.casual.arcade.dimensions.level.builder.CustomLevelBuilder;
import net.casual.arcade.dimensions.utils.impl.VoidChunkGenerator;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class ChartDimensionManager {
    private static final String CHART_PREFIX = "chart_";

    private ChartDimensionManager() {
    }

    public static RegistryKey<World> key(ChartManifest chart) throws IOException {
        if (chart == null || chart.id == null || chart.id.isBlank()) {
            throw new IOException("谱面没有有效编号");
        }
        String expectedDimensionId = stableDimensionId(chart.id);
        if (!expectedDimensionId.equals(chart.dimensionId)) chart.dimensionId = expectedDimensionId;
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(RhythmcMaker.MOD_ID, chart.dimensionId));
    }

    public static RegistryKey<World> key(String chartId) throws IOException {
        if (chartId == null || chartId.isBlank()) throw new IOException("谱面没有有效编号");
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(RhythmcMaker.MOD_ID, stableDimensionId(chartId)));
    }

    public static boolean isChartWorld(RegistryKey<World> key) {
        if (key == null || !RhythmcMaker.MOD_ID.equals(key.getValue().getNamespace())) return false;
        String path = key.getValue().getPath();
        return path.startsWith(CHART_PREFIX) && !path.startsWith("chart_slot_");
    }

    public static ServerWorld getOrCreate(MinecraftServer server, ChartManifest chart) throws IOException {
        RegistryKey<World> dimensionKey = key(chart);
        ServerWorld existing = server.getWorld(dimensionKey);
        if (existing != null) return existing;

        CustomLevel loaded = ArcadeDimensions.load(server, dimensionKey);
        if (loaded != null) return loaded;

        CustomLevel created = ArcadeDimensions.add(server, new CustomLevelBuilder()
                .dimensionKey(dimensionKey)
                .spoofedDimensionKey(RhythmcMaker.CHARTER_DIMENSION)
                .dimensionType(server.getOverworld().getDimensionEntry())
                .chunkGenerator(new VoidChunkGenerator(server))
                .seed(0L)
                .tickTime(true)
                .generateStructures(false)
                .persistence(LevelPersistence.Persistent));
        if (created == null) throw new IOException("无法创建谱面动态维度：" + dimensionKey.getValue());
        return created;
    }

    public static void preloadCharts(MinecraftServer server) {
        try {
            for (ChartManifest chart : ChartStorage.list(server)) {
                if (chart.id == null || chart.id.isBlank()) continue;
                String previousDimensionId = chart.dimensionId;
                key(chart);
                boolean migrated = !chart.dimensionId.equals(previousDimensionId);
                if (migrated) ChartStorage.update(server, chart);
                ServerWorld world = getOrCreate(server, chart);
                RhythmcMaker.LOGGER.info("Loaded Rhythmc chart dimension {} -> {} ({})",
                        chart.id, chart.dimensionId, world.getRegistryKey().getValue());
            }
        } catch (IOException | RuntimeException exception) {
            RhythmcMaker.LOGGER.error("Failed to preload Rhythmc chart dimensions", exception);
        }
    }

    public static String stableDimensionId(String chartId) {
        String normalized = chartId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (normalized.length() > 40) normalized = normalized.substring(0, 40);
        return CHART_PREFIX + normalized + "_" + shortHash(chartId);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(12);
            for (int index = 0; index < 6; index++) result.append(String.format(Locale.ROOT, "%02x", digest[index]));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
