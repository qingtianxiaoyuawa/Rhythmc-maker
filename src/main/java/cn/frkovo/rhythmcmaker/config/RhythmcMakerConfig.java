package cn.frkovo.rhythmcmaker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RhythmcMakerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public double autosaveIntervalSeconds = 30.0;
    public double sidebarRefreshIntervalSeconds = 1.0;
    public int defaultDivisionsPerChunk = 4;
    public double playerSpeed = 1.0;
    public double musicVolumeMultiplier = 1.0;
    public double noteJudgementVolumeMultiplier = 1.0;
    public boolean preventChunkDisplacement = true;
    public String lobbySidebarContent = "可以在设置修改此处显示内容~";

    public RhythmcMakerConfig() {
    }

    public static RhythmcMakerConfig load(MinecraftServer server) {
        RhythmcMakerConfig config = new RhythmcMakerConfig();
        try {
            Path path = path(server);
            if (Files.exists(path)) {
                RhythmcMakerConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), RhythmcMakerConfig.class);
                if (loaded != null) config = loaded;
            }
            config.normalize();
            config.save(server);
        } catch (IOException | RuntimeException exception) {
            config.normalize();
        }
        return config;
    }

    public void save(MinecraftServer server) throws IOException {
        normalize();
        Path path = path(server);
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    private static Path path(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("rhythmc maker").resolve("settings.json");
    }
    private void normalize() {
        autosaveIntervalSeconds = Math.max(0.05, Math.min(86400.0, autosaveIntervalSeconds));
        sidebarRefreshIntervalSeconds = Math.max(0.05, Math.min(3600.0, sidebarRefreshIntervalSeconds));
        defaultDivisionsPerChunk = Math.max(1, Math.min(32, defaultDivisionsPerChunk));
        playerSpeed = Double.isFinite(playerSpeed) ? Math.max(0.1, Math.min(5.0, playerSpeed)) : 1.0;
        musicVolumeMultiplier = Double.isFinite(musicVolumeMultiplier) ? Math.max(0.1, Math.min(2.0, musicVolumeMultiplier)) : 1.0;
        noteJudgementVolumeMultiplier = Double.isFinite(noteJudgementVolumeMultiplier) ? Math.max(0.1, Math.min(2.0, noteJudgementVolumeMultiplier)) : 1.0;
        if (lobbySidebarContent == null || lobbySidebarContent.isBlank()) lobbySidebarContent = "可以在设置修改此处显示内容~";
        if (lobbySidebarContent.length() > 120) lobbySidebarContent = lobbySidebarContent.substring(0, 120);
    }
}
