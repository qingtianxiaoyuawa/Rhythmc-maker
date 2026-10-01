package cn.frkovo.rhythmcv2.cv2.chart;

import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存谱面草稿模型（.rmcd 权威源的运行时形态）。
 * manifest 可读字段以字符串透传（谱师可改），写 manifest 时按 Reborn 反序列化器字段名输出。
 * 注意：name/composer/charters 永不本地化（跨仓硬规则）。
 */
public final class EditorChart {

    public String draftId;
    public String songFolder;
    public long revision = 0;

    /** 全局毫秒偏移（整数毫秒，字符串保存）。 */
    public String offsetMs = "0";
    /** BPM 事件表：beat 严格递增、首项 beat=0。 */
    public final List<BeatClock.BpmEvent> bpms = new java.util.ArrayList<>();

    public final Map<String, EditorLevel> levels = new LinkedHashMap<>();
    public String activeLevel = "world";

    // ---- manifest 透传字段（向导填初值；导出按字段名直写） ----
    public String manifestName = "Unknown Title";
    public String composer = "Unknown Composer";
    /** 红线：name/composer/charters 永不本地化，原样写 manifest 与 .rmcc meta。 */
    public List<String> charters = new java.util.ArrayList<>();
    public String icon = "NOTE_BLOCK";
    public String alias = "";
    /** 毫秒；0 = 未设置（编译时按末音符+2s 填默认，Lint 校验偏差）。 */
    public String length = "0";
    public String respackSha1 = "";
    public String description = "";
    public String songId = "0";
    public String version = "1.0";
    public String defaultLocale = "zh-cn";
    public List<String> comments = new java.util.ArrayList<>();
    public List<String> albums = new java.util.ArrayList<>();
    /** i18n 块透传（导入源原样保留；`_` 前缀键在导出时剥离）。 */
    public com.google.gson.JsonObject i18n = new com.google.gson.JsonObject();

    /** 实体 id 计数器（删除后不复用）。 */
    public final AtomicLong noteSeq = new AtomicLong();
    public final AtomicLong trackSeq = new AtomicLong();
    public final AtomicLong eventSeq = new AtomicLong();
    public final AtomicLong holdGroupSeq = new AtomicLong();

    public EditorChart(String draftId, String songFolder) {
        this.draftId = draftId;
        this.songFolder = songFolder;
        this.bpms.add(new BeatClock.BpmEvent(BeatFraction.ZERO, new BigDecimal("120")));
        EditorLevel world = new EditorLevel("world");
        world.ensureTrack("track-0");
        this.levels.put("world", world);
    }

    public EditorLevel level(String name) {
        return levels.get(name);
    }

    public EditorLevel active() {
        return levels.computeIfAbsent(activeLevel, EditorLevel::new);
    }

    public BeatClock clock() {
        List<BeatClock.BpmEvent> copy = new java.util.ArrayList<>(bpms);
        return new BeatClock(copy, Long.parseLong(offsetMs));
    }

    public EditorChart deepCopy() {
        EditorChart c = new EditorChart(draftId, songFolder);
        c.revision = revision;
        c.offsetMs = offsetMs;
        c.bpms.clear();
        for (BeatClock.BpmEvent e : bpms) {
            c.bpms.add(new BeatClock.BpmEvent(e.beat(), e.bpm()));
        }
        for (Map.Entry<String, EditorLevel> e : levels.entrySet()) {
            c.levels.put(e.getKey(), EditorLevel.copyOf(e.getValue()));
        }
        c.activeLevel = activeLevel;
        c.manifestName = manifestName;
        c.composer = composer;
        c.charters = new java.util.ArrayList<>(charters);
        c.icon = icon;
        c.alias = alias;
        c.length = length;
        c.respackSha1 = respackSha1;
        c.description = description;
        c.songId = songId;
        c.version = version;
        c.defaultLocale = defaultLocale;
        c.comments = new java.util.ArrayList<>(comments);
        c.albums = new java.util.ArrayList<>(albums);
        c.i18n = i18n.deepCopy();
        c.noteSeq.set(noteSeq.get());
        c.trackSeq.set(trackSeq.get());
        c.eventSeq.set(eventSeq.get());
        c.holdGroupSeq.set(holdGroupSeq.get());
        return c;
    }

    public String nextNoteId() {
        return "note-" + noteSeq.incrementAndGet();
    }

    public String nextTrackId() {
        return "track-" + trackSeq.incrementAndGet();
    }

    public String nextEventId() {
        return "event-" + eventSeq.incrementAndGet();
    }

    public int nextHoldGroupId() {
        return (int) holdGroupSeq.incrementAndGet();
    }
}
