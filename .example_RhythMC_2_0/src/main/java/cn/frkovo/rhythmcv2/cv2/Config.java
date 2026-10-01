package cn.frkovo.rhythmcv2.cv2;

import org.bukkit.configuration.file.FileConfiguration;

/** config.yml 读取（缺省值 = 设计稿推荐值）。 */
public final class Config {

    private final FileConfiguration raw;

    public Config(FileConfiguration raw) {
        this.raw = raw;
    }

    public String workspaceWorldName() {
        return raw.getString("workspace.world", "charter_workspace");
    }

    public double renderWindowBeats() {
        return raw.getDouble("render.window-beats", 8.0);
    }

    public double playerSpeed() {
        double v = raw.getDouble("render.player-speed", 1.0);
        return Math.max(0.1, Math.min(5.0, v));
    }

    public int autosaveSeconds() {
        return raw.getInt("autosave.seconds", 60);
    }

    public long syncThresholdMs() {
        return raw.getLong("transport.sync-threshold-ms", 60);
    }

    public boolean audioMissingPause() {
        return raw.getBoolean("transport.muted-audit", true);
    }

    public int maxSessions() {
        return raw.getInt("sessions.max", 8);
    }

    public String importRoot() {
        return raw.getString("import.root", "plugins/RhythMC-Charter-V2/import");
    }
}
