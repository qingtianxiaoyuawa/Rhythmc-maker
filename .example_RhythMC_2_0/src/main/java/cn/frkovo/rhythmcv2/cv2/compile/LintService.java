package cn.frkovo.rhythmcv2.cv2.compile;

import cn.frkovo.rhythmcv2.cv2.chart.Channel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.chart.EditorLevel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent;
import cn.frkovo.rhythmcv2.cv2.chart.EditorTrack;
import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 发布前静态检查（§14 MVP 子集）。error 阻断编译，warn 仅提示。 */
public final class LintService {

    public record Issue(String level, String rule, String message) {
        public static Issue error(String rule, String message) {
            return new Issue("error", rule, message);
        }

        public static Issue warn(String rule, String message) {
            return new Issue("warn", rule, message);
        }
    }

    private LintService() {
    }

    public static List<Issue> lint(EditorChart chart) {
        List<Issue> issues = new ArrayList<>();
        lintTiming(chart, issues);
        for (EditorLevel level : chart.levels.values()) {
            lintLevel(chart, level, issues);
        }
        lintManifest(chart, issues);
        return issues;
    }

    private static void lintTiming(EditorChart chart, List<Issue> issues) {
        if (chart.bpms.isEmpty()) {
            issues.add(Issue.error("bpms", "没有 BPM 事件"));
            return;
        }
        if (!chart.bpms.getFirst().beat().isZero()) {
            issues.add(Issue.error("bpms", "首个 BPM 事件 beat 必须 = 0"));
        }
        for (int i = 1; i < chart.bpms.size(); i++) {
            if (chart.bpms.get(i - 1).beat().compareTo(chart.bpms.get(i).beat()) >= 0) {
                issues.add(Issue.error("bpms", "BPM 事件 beat 非严格递增（索引 " + i + "）"));
            }
        }
        for (BeatClock.BpmEvent e : chart.bpms) {
            if (e.bpm().signum() <= 0) {
                issues.add(Issue.error("bpms", "BPM ≤ 0（beat " + e.beat() + "）"));
            }
        }
    }

    private static void lintLevel(EditorChart chart, EditorLevel level, List<Issue> issues) {
        BeatClock clock = chart.clock();
        // 音轨 id 与音符
        Map<Integer, java.util.Set<String>> holdGroupTracks = new HashMap<>();
        Map<Integer, List<EditorNote>> holdGroups = new HashMap<>();
        for (EditorTrack track : level.tracks.values()) {
            if (track.events.get(Channel.SPEED).isEmpty()) {
                issues.add(Issue.warn("speedEvents",
                        "[" + level.name + "/" + track.id + "] 无 speedEvents（Reborn 语义下距离恒 0，音符不走）"));
            }
            // NumEvent 重叠
            for (Channel c : Channel.values()) {
                List<EditorNumEvent> evs = new ArrayList<>(track.events.get(c));
                evs.sort((a, b) -> a.startBeat.compareTo(b.startBeat));
                for (int i = 1; i < evs.size(); i++) {
                    EditorNumEvent prev = evs.get(i - 1);
                    EditorNumEvent cur = evs.get(i);
                    boolean sameStart = cur.startBeat.compareTo(prev.startBeat) == 0
                            && cur.endBeat.compareTo(cur.startBeat) > 0;
                    if (cur.startBeat.compareTo(prev.endBeat) < 0 || sameStart) {
                        issues.add(Issue.error("events",
                                "[" + level.name + "/" + track.id + "/" + c.json + "] 事件区间重叠（Reborn 拒绝加载）"));
                    }
                }
            }
            for (EditorNote n : track.notes.values()) {
                if (n.beat.isNegative()) {
                    issues.add(Issue.error("notes",
                            "[" + level.name + "/" + track.id + "] 音符 " + n.id + " beat < 0"));
                }
                if (n.noteType == EditorNote.HOLD && n.holdGroup >= 0) {
                    holdGroups.computeIfAbsent(n.holdGroup, k -> new ArrayList<>()).add(n);
                    holdGroupTracks.computeIfAbsent(n.holdGroup, k -> new java.util.HashSet<>()).add(track.id);
                } else if (n.noteType != EditorNote.HOLD && n.holdGroup >= 0) {
                    issues.add(Issue.error("holdGroup",
                            "[" + level.name + "] 音符 " + n.id + " 类型非 HOLD 却带 holdGroup=" + n.holdGroup));
                }
            }
            // 同轨可交互音符密度（建议级）
            List<EditorNote> interactive = new ArrayList<>(track.notes.values());
            interactive.sort((a, b) -> a.beat.compareTo(b.beat));
            for (int i = 1; i < interactive.size(); i++) {
                BeatFraction gap = interactive.get(i).beat.subtract(interactive.get(i - 1).beat);
                long ms = clock.msLong(gap);
                if (ms > 0 && ms < 125) {
                    issues.add(Issue.warn("density",
                            "[" + level.name + "/" + track.id + "] 同轨音符间隔 " + ms
                                    + "ms < 125ms（beat " + interactive.get(i - 1).beat + "→"
                                    + interactive.get(i).beat + "），试玩确认可玩性"));
                }
            }
        }
        // holdGroup：跨轨 / 乱序
        for (Map.Entry<Integer, java.util.Set<String>> e : holdGroupTracks.entrySet()) {
            if (e.getValue().size() > 1) {
                issues.add(Issue.error("holdGroup", "[" + level.name + "] holdGroup " + e.getKey()
                        + " 跨越多个轨道（必须同轨）"));
            }
        }
        for (Map.Entry<Integer, List<EditorNote>> e : holdGroups.entrySet()) {
            List<EditorNote> notes = e.getValue();
            notes.sort((a, b) -> a.beat.compareTo(b.beat));
            for (int i = 1; i < notes.size(); i++) {
                if (notes.get(i).beat.compareTo(notes.get(i - 1).beat) == 0) {
                    issues.add(Issue.error("holdGroup", "[" + level.name + "] holdGroup " + e.getKey()
                            + " 内 beat 相同（必须严格递增）"));
                }
            }
        }
    }

    private static void lintManifest(EditorChart chart, List<Issue> issues) {
        long length;
        try {
            length = Long.parseLong(chart.length);
        } catch (RuntimeException e) {
            issues.add(Issue.error("manifest", "length 非整数毫秒"));
            return;
        }
        if (length == 0) {
            return; // 编译期自动补末音符+2s
        }
        BeatClock clock = chart.clock();
        BeatFraction last = BeatFraction.ZERO;
        for (EditorLevel level : chart.levels.values()) {
            for (EditorTrack t : level.tracks.values()) {
                for (EditorNote n : t.notes.values()) {
                    if (n.beat.compareTo(last) > 0) {
                        last = n.beat;
                    }
                }
            }
        }
        long expected = clock.msLong(last) + 2000;
        if (Math.abs(expected - length) > 5000) {
            issues.add(Issue.warn("manifest",
                    "manifest length 与末音符偏差 > 5s（length=" + length + "ms, 末音符+2s=" + expected + "ms）"));
        }
        if (chart.respackSha1.isBlank()) {
            issues.add(Issue.warn("manifest", "respack_sha1 为空（正式播放依赖服务端资源包配置）"));
        }
    }
}
