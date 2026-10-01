package cn.frkovo.rhythmcv2.cv2.session;

import cn.frkovo.rhythmcv2.cv2.Main;
import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.draft.RmcdStore;
import cn.frkovo.rhythmcv2.cv2.ops.ModelHash;
import cn.frkovo.rhythmcv2.cv2.ops.Operation;
import cn.frkovo.rhythmcv2.cv2.ops.OperationApplier;
import cn.frkovo.rhythmcv2.cv2.ops.OperationLog;
import cn.frkovo.rhythmcv2.cv2.ops.UndoManager;
import cn.frkovo.rhythmcv2.cv2.render.PreviewRenderer;
import cn.frkovo.rhythmcv2.cv2.transport.CharterAudioBridge;
import cn.frkovo.rhythmcv2.cv2.transport.Transport;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 单谱师编辑会话（会话与玩家 1:1；同一谱面文件夹同时只允许一个会话）。
 * 集中入口 apply()：应用领域操作 → 推导逆操作入撤销栈 → 追加操作日志 → 标脏。
 */
public final class CharterSession {

    public enum AudioStatus {
        NO_MOD, NO_AUDIO, OK
    }

    private final Main plugin;
    private final Player player;
    private final EditorChart chart;
    private final EditorState state;
    private final UndoManager undo = new UndoManager();
    private final OperationLog log = new OperationLog();
    private final OperationApplier applier = new OperationApplier();
    private final Transport transport;
    private final PreviewRenderer renderer;
    private final PlacementService placement = new PlacementService();
    private final World workspaceWorld;
    private final String sessionId;
    private final Path draftFile;
    private final Path opsSidecar;
    private final int slot;

    private boolean dirty;
    private AudioStatus audioStatus = AudioStatus.NO_MOD;
    private String audioReason = "";
    private String audioSha1 = "";
    private long audioLengthMs;
    private long lastAutosaveMs = System.currentTimeMillis();

    public CharterSession(Main plugin, Player player, EditorChart chart, EditorState state,
                          World workspaceWorld, int slot, Path draftFile) {
        this.plugin = plugin;
        this.player = player;
        this.chart = chart;
        this.state = state;
        this.workspaceWorld = workspaceWorld;
        this.slot = slot;
        this.draftFile = draftFile;
        this.sessionId = plugin.bridge().newSessionId();
        this.transport = new Transport(this);
        this.renderer = new PreviewRenderer(this);
        this.opsSidecar = draftFile.getParent().resolve("." + chart.draftId + ".ops.ndjson");
    }

    /** 会话建立后调用：恢复日志序号、通知 mod、开 HUD。 */
    public void begin() {
        if (Files.isRegularFile(opsSidecar)) {
            try {
                List<OperationLog.Entry> entries = OperationLog.readAll(opsSidecar);
                log.setNextSeq(entries.isEmpty() ? 0 : entries.getLast().seq() + 1);
            } catch (IOException ignored) {
            }
        }
        plugin.hud().start(this);
        plugin.bridge().sendChartMeta(this);
        renderer.markDirty();
    }

    // ---- 操作应用 / 撤销 ----

    public boolean apply(Operation op) {
        String before = ModelHash.of(chart);
        OperationApplier.Result result = applier.apply(op, chart);
        if (!result.ok()) {
            player.sendMessage("§c" + result.message());
            return false;
        }
        String after = ModelHash.of(chart);
        log.append(op, before, after);
        undo.pushApplied(op, result.inverse());
        dirty = true;
        renderer.markDirty();
        flushOpsSidecar();
        return true;
    }

    public boolean undo() {
        if (!undo.canUndo()) {
            player.sendMessage("§7没有可撤销的操作");
            return false;
        }
        UndoManager.UndoEntry entry = undo.popUndo();
        String before = ModelHash.of(chart);
        OperationApplier.Result result = applier.apply(entry.inverse(), chart);
        if (!result.ok()) {
            player.sendMessage("§c撤销失败: " + result.message());
            undo.pushUndone(entry.applied(), entry.inverse());
            return false;
        }
        log.append(entry.inverse(), before, ModelHash.of(chart));
        undo.pushRedone(entry);
        dirty = true;
        renderer.markDirty();
        flushOpsSidecar();
        player.sendMessage("§7已撤销 " + entry.applied().command());
        return true;
    }

    public boolean redo() {
        if (!undo.canRedo()) {
            player.sendMessage("§7没有可重做的操作");
            return false;
        }
        UndoManager.UndoEntry entry = undo.popRedo();
        String before = ModelHash.of(chart);
        OperationApplier.Result result = applier.apply(entry.applied(), chart);
        if (!result.ok()) {
            player.sendMessage("§c重做失败: " + result.message());
            undo.pushRedone(entry);
            return false;
        }
        log.append(entry.applied(), before, ModelHash.of(chart));
        undo.pushUndone(entry.applied(), entry.inverse());
        dirty = true;
        renderer.markDirty();
        flushOpsSidecar();
        player.sendMessage("§7已重做 " + entry.applied().command());
        return true;
    }

    private void flushOpsSidecar() {
        try {
            log.flushTo(opsSidecar);
        } catch (IOException e) {
            plugin.getLogger().warning("操作日志写盘失败: " + e.getMessage());
        }
    }

    // ---- 保存 ----

    public synchronized void save() throws IOException {
        String ndjson = Files.isRegularFile(opsSidecar)
                ? Files.readString(opsSidecar, StandardCharsets.UTF_8) : "";
        RmcdStore.save(draftFile, new RmcdStore.SaveInput(chart, state, ndjson));
        dirty = false;
        lastAutosaveMs = System.currentTimeMillis();
    }

    // ---- mod 事件回调 ----

    public void onAudioStatus(boolean ok, String reason, String sha1, long lengthMs) {
        this.audioStatus = ok ? AudioStatus.OK : AudioStatus.NO_AUDIO;
        this.audioReason = reason;
        this.audioSha1 = sha1;
        this.audioLengthMs = lengthMs;
        if (!ok) {
            player.sendMessage("§e[Charter] 音频未匹配：" + reason + "（进入静音审计态，可编辑不可有声彩排）");
        } else {
            player.sendMessage("§a[Charter] 音频已匹配（SHA-1 " + (sha1.isEmpty() ? "未计算" : sha1.substring(0, 8)) + "…）");
        }
    }

    public void onModState(boolean playing, double positionMs, float speed) {
        // 静音审计态（附录 D）：音频缺失时 mod 端自行空转 transport 指令；
        // 插件侧对账只在有 STATE 上报时进行（Transport.tick）。
        if (audioStatus == AudioStatus.NO_AUDIO && playing) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§c[Charter] 缺音频 · 静音审计态：" + audioReason));
        }
    }

    public void close() {
        transport.stop();
        renderer.destroy();
        plugin.hud().stop(this);
    }

    // ---- 访问器 ----

    public Player player() {
        return player;
    }

    public EditorChart chart() {
        return chart;
    }

    public EditorState state() {
        return state;
    }

    public Transport transport() {
        return transport;
    }

    public PreviewRenderer renderer() {
        return renderer;
    }

    public PlacementService placement() {
        return placement;
    }

    public Main plugin() {
        return plugin;
    }

    public CharterAudioBridge bridge() {
        return plugin.bridge();
    }

    public World workspaceWorld() {
        return workspaceWorld;
    }

    public int slot() {
        return slot;
    }

    public String sessionId() {
        return sessionId;
    }

    public Path draftFile() {
        return draftFile;
    }

    public boolean isDirty() {
        return dirty;
    }

    public AudioStatus audioStatus() {
        return audioStatus;
    }

    public String audioReason() {
        return audioReason;
    }

    public long audioLengthMs() {
        return audioLengthMs;
    }

    public UndoManager undoManager() {
        return undo;
    }

    public OperationLog operationLog() {
        return log;
    }

    public boolean autosaveDue() {
        return dirty && System.currentTimeMillis() - lastAutosaveMs > plugin.config().autosaveSeconds() * 1000L;
    }

    public void markAutosaved() {
        lastAutosaveMs = System.currentTimeMillis();
    }

    public Location spawnLocation() {
        return new Location(workspaceWorld, cn.frkovo.rhythmcv2.cv2.render.ChartMath.gameCenterX(slot),
                cn.frkovo.rhythmcv2.cv2.render.ChartMath.gameCenterY(),
                cn.frkovo.rhythmcv2.cv2.render.ChartMath.gameCenterZ(), 180f, 0f);
    }
}
