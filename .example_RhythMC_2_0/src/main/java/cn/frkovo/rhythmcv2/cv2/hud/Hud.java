package cn.frkovo.rhythmcv2.cv2.hud;

import cn.frkovo.rhythmcv2.cv2.session.CharterSession;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD 时间轴（§9.5 MVP）：actionbar（拍号/snap/BPM/dirty/播放态）+ bossbar（全曲进度 + A-B 标记）。
 * 密度 scoreboard 留 M4。
 */
public final class Hud {

    private final cn.frkovo.rhythmcv2.cv2.Main plugin;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private BukkitTask task;

    public Hud(cn.frkovo.rhythmcv2.cv2.Main plugin) {
        this.plugin = plugin;
    }

    public void start(CharterSession session) {
        Player p = session.player();
        BossBar bar = Bukkit.createBossBar("第 0 小节", BarColor.BLUE, BarStyle.SEGMENTED_10);
        bar.addPlayer(p);
        bars.put(p.getUniqueId(), bar);
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 10L);
        }
    }

    public void stop(CharterSession session) {
        BossBar bar = bars.remove(session.player().getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        if (bars.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickAll() {
        for (cn.frkovo.rhythmcv2.cv2.session.CharterSession session : plugin.sessions().all()) {
            if (!session.player().isOnline()) {
                continue;
            }
            render(session);
        }
    }

    private void render(CharterSession session) {
        var clock = session.chart().clock();
        var beat = session.transport().currentBeat();
        long bar = clock.bar(beat);
        long bpm = clock.bpmAt(beat).longValue();
        StringBuilder sb = new StringBuilder();
        sb.append("§b#").append(bar + 1).append("  §7| §fsnap ")
                .append(session.state().snap > 0 ? "1/" + session.state().snap : "off");
        sb.append(" §7| §fBPM ").append(bpm);
        sb.append(" §7| §fbeat ").append(beat.toString());
        if (session.isDirty()) {
            sb.append(" §e●dirty");
        }
        switch (session.transport().mode()) {
            case PLAYING -> sb.append(" §a▶播放中");
            case PAUSED -> sb.append(" §e⏸暂停");
            default -> {
            }
        }
        if (session.audioStatus() == CharterSession.AudioStatus.NO_AUDIO) {
            sb.append(" §c✖缺音频");
        } else if (session.audioStatus() == CharterSession.AudioStatus.NO_MOD) {
            sb.append(" §c✖Mod 未就绪");
        }
        if (session.transport().isPlaying() && session.transport().lastDriftMs() > 0) {
            sb.append(" §7漂移 ").append(session.transport().lastDriftMs()).append("ms");
        }
        session.player().sendActionBar(net.kyori.adventure.text.Component.text(sb.toString()));
        BossBar bar1 = bars.get(session.player().getUniqueId());
        if (bar1 != null) {
            double total = Math.max(1, clock.msLong(BeatFraction.of(maxBeat(session))));
            double progress = Math.min(1.0, clock.msLong(beat) / total);
            bar1.setProgress(progress);
            bar1.setTitle("第 " + (bar + 1) + " 小节 · beat " + beat.toString());
        }
    }

    private long maxBeat(CharterSession session) {
        long max = 16;
        for (var level : session.chart().levels.values()) {
            for (var t : level.tracks.values()) {
                for (var n : t.notes.values()) {
                    max = Math.max(max, n.beat.ceil().longValue());
                }
            }
        }
        return max;
    }
}
