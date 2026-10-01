package cn.frkovo.rhythmcv2.cv2.ops;

import cn.frkovo.rhythmcv2.cv2.chart.Channel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.chart.EditorLevel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent;
import cn.frkovo.rhythmcv2.cv2.chart.EditorTrack;
import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 领域操作应用器：apply 在变更前由当前状态推导逆操作，undo/redo 因此只是「应用反向领域操作」。
 * 任何失败都不改变模型。
 */
public final class OperationApplier {

    public record Result(boolean ok, String message, Operation inverse) {
        public static Result ok(Operation inverse) {
            return new Result(true, null, inverse);
        }

        public static Result fail(String message) {
            return new Result(false, message, null);
        }
    }

    // ---- note 快照 JSON（操作日志与逆操作通用） ----

    public static JsonObject noteSnapshot(EditorNote n) {
        JsonObject o = new JsonObject();
        o.addProperty("id", n.id);
        o.addProperty("noteType", n.noteType);
        o.addProperty("beat", n.beat.toString());
        o.add("pos", strings(n.pos));
        o.add("scale", strings(n.scale));
        o.add("rotation", strings(n.rotation));
        o.addProperty("holdGroup", n.holdGroup);
        return o;
    }

    public static EditorNote noteFromSnapshot(JsonObject o) {
        EditorNote n = new EditorNote(o.get("id").getAsString(),
                o.get("noteType").getAsInt(),
                BeatFraction.parse(o.get("beat").getAsString()));
        BigDecimal[] pos = decimals(o.getAsJsonArray("pos"));
        BigDecimal[] scale = decimals(o.getAsJsonArray("scale"));
        BigDecimal[] rot = decimals(o.getAsJsonArray("rotation"));
        n.pos = pos;
        n.scale = scale;
        n.rotation = rot;
        n.holdGroup = o.get("holdGroup").getAsInt();
        return n;
    }

    public static JsonObject eventSnapshot(EditorNumEvent e) {
        JsonObject o = new JsonObject();
        o.addProperty("id", e.id);
        o.addProperty("startBeat", e.startBeat.toString());
        o.addProperty("endBeat", e.endBeat.toString());
        o.addProperty("startValue", e.startValue.toPlainString());
        o.addProperty("endValue", e.endValue.toPlainString());
        o.addProperty("easing", e.easing);
        return o;
    }

    public static EditorNumEvent eventFromSnapshot(JsonObject o) {
        return new EditorNumEvent(o.get("id").getAsString(),
                BeatFraction.parse(o.get("startBeat").getAsString()),
                BeatFraction.parse(o.get("endBeat").getAsString()),
                new BigDecimal(o.get("startValue").getAsString()),
                new BigDecimal(o.get("endValue").getAsString()),
                o.get("easing").getAsInt());
    }

    private static JsonArray strings(BigDecimal[] v) {
        JsonArray a = new JsonArray();
        for (BigDecimal d : v) {
            a.add(d.toPlainString());
        }
        return a;
    }

    private static BigDecimal[] decimals(JsonArray a) {
        BigDecimal[] out = new BigDecimal[3];
        for (int i = 0; i < 3; i++) {
            out[i] = new BigDecimal(a.get(i).getAsString());
        }
        return out;
    }

    private static JsonObject transformSnapshot(EditorNote n) {
        JsonObject o = new JsonObject();
        o.add("pos", strings(n.pos));
        o.add("scale", strings(n.scale));
        o.add("rotation", strings(n.rotation));
        return o;
    }

    // ---- apply ----

    public Result apply(Operation op, EditorChart chart) {
        return switch (op) {
            case Operation.BpmAdd o -> applyBpmAdd(o, chart);
            case Operation.BpmRemove o -> applyBpmRemove(o, chart);
            case Operation.BpmSet o -> applyBpmSet(o, chart);
            case Operation.OffsetSet o -> applyOffsetSet(o, chart);
            case Operation.NoteCreate o -> applyNoteCreate(o, chart);
            case Operation.NoteDelete o -> applyNoteDelete(o, chart);
            case Operation.NoteSetBeat o -> applyNoteSetBeat(o, chart);
            case Operation.NoteSetTransform o -> applyNoteSetTransform(o, chart);
            case Operation.TrackCreate o -> applyTrackCreate(o, chart);
            case Operation.TrackDelete o -> applyTrackDelete(o, chart);
            case Operation.EventAdd o -> applyEventAdd(o, chart);
            case Operation.EventRemove o -> applyEventRemove(o, chart);
        };
    }

    private Result applyBpmAdd(Operation.BpmAdd o, EditorChart chart) {
        BeatFraction beat;
        BigDecimal bpm;
        try {
            beat = BeatFraction.parse(o.beat());
            bpm = new BigDecimal(o.bpm());
        } catch (RuntimeException e) {
            return Result.fail("数值格式错误: " + e.getMessage());
        }
        if (bpm.signum() <= 0) {
            return Result.fail("BPM 必须为正");
        }
        if (beat.isNegative()) {
            return Result.fail("beat 不能为负");
        }
        for (BeatClock.BpmEvent e : chart.bpms) {
            if (e.beat().compareTo(beat) == 0) {
                return Result.fail("该拍已有 BPM 事件（先删除）");
            }
        }
        chart.bpms.add(new BeatClock.BpmEvent(beat, bpm));
        chart.bpms.sort((a, b) -> a.beat().compareTo(b.beat()));
        return Result.ok(new Operation.BpmRemove(o.beat(), o.bpm()));
    }

    private Result applyBpmRemove(Operation.BpmRemove o, EditorChart chart) {
        BeatFraction beat;
        try {
            beat = BeatFraction.parse(o.beat());
        } catch (RuntimeException e) {
            return Result.fail("beat 格式错误");
        }
        if (beat.isZero()) {
            return Result.fail("不能删除首个 BPM 事件");
        }
        Iterator<BeatClock.BpmEvent> it = chart.bpms.iterator();
        BeatClock.BpmEvent found = null;
        while (it.hasNext()) {
            BeatClock.BpmEvent e = it.next();
            if (e.beat().compareTo(beat) == 0) {
                found = e;
                it.remove();
                break;
            }
        }
        if (found == null) {
            return Result.fail("该拍无 BPM 事件");
        }
        return Result.ok(new Operation.BpmAdd(found.beat().toString(), found.bpm().toPlainString()));
    }

    private Result applyBpmSet(Operation.BpmSet o, EditorChart chart) {
        BeatFraction beat;
        BigDecimal to;
        try {
            beat = BeatFraction.parse(o.beat());
            to = new BigDecimal(o.toBpm());
        } catch (RuntimeException e) {
            return Result.fail("数值格式错误");
        }
        if (to.signum() <= 0) {
            return Result.fail("BPM 必须为正");
        }
        for (int i = 0; i < chart.bpms.size(); i++) {
            if (chart.bpms.get(i).beat().compareTo(beat) == 0) {
                BigDecimal from = chart.bpms.get(i).bpm();
                chart.bpms.set(i, new BeatClock.BpmEvent(beat, to));
                return Result.ok(new Operation.BpmSet(o.beat(), o.toBpm(), from.toPlainString()));
            }
        }
        return Result.fail("该拍无 BPM 事件");
    }

    private Result applyOffsetSet(Operation.OffsetSet o, EditorChart chart) {
        try {
            Long.parseLong(o.toMs());
        } catch (RuntimeException e) {
            return Result.fail("offset 必须是整数毫秒");
        }
        String from = chart.offsetMs;
        chart.offsetMs = o.toMs();
        return Result.ok(new Operation.OffsetSet(o.toMs(), from));
    }

    private Result applyNoteCreate(Operation.NoteCreate o, EditorChart chart) {
        EditorLevel level = chart.level(o.level());
        if (level == null) {
            return Result.fail("难度不存在: " + o.level());
        }
        EditorTrack track = level.track(o.trackId());
        if (track == null) {
            return Result.fail("轨道不存在: " + o.trackId());
        }
        EditorNote n;
        try {
            n = noteFromSnapshot(o.note());
        } catch (RuntimeException e) {
            return Result.fail("音符快照损坏: " + e.getMessage());
        }
        if (track.notes.containsKey(n.id)) {
            return Result.fail("音符 id 已存在: " + n.id);
        }
        if (n.beat.isNegative()) {
            return Result.fail("beat 不能为负");
        }
        track.notes.put(n.id, n);
        return Result.ok(new Operation.NoteDelete(o.level(), o.trackId(), noteSnapshot(n)));
    }

    private Result applyNoteDelete(Operation.NoteDelete o, EditorChart chart) {
        EditorLevel level = chart.level(o.level());
        EditorTrack track = level == null ? null : level.track(o.trackId());
        if (track == null) {
            return Result.fail("轨道不存在");
        }
        EditorNote n = track.notes.remove(o.note().get("id").getAsString());
        if (n == null) {
            return Result.fail("音符不存在");
        }
        return Result.ok(new Operation.NoteCreate(o.level(), o.trackId(), noteSnapshot(n)));
    }

    private Result applyNoteSetBeat(Operation.NoteSetBeat o, EditorChart chart) {
        EditorNote n = findNote(chart, o.level(), o.trackId(), o.noteId());
        if (n == null) {
            return Result.fail("音符不存在");
        }
        BeatFraction to;
        try {
            to = BeatFraction.parse(o.to());
        } catch (RuntimeException e) {
            return Result.fail("beat 格式错误");
        }
        if (to.isNegative()) {
            return Result.fail("beat 不能为负");
        }
        String from = n.beat.toString();
        n.beat = to;
        return Result.ok(new Operation.NoteSetBeat(o.level(), o.trackId(), o.noteId(), o.to(), from));
    }

    private Result applyNoteSetTransform(Operation.NoteSetTransform o, EditorChart chart) {
        EditorNote n = findNote(chart, o.level(), o.trackId(), o.noteId());
        if (n == null) {
            return Result.fail("音符不存在");
        }
        JsonObject from;
        try {
            from = transformSnapshot(n);
            JsonObject to = o.to();
            n.pos = decimals(to.getAsJsonArray("pos"));
            n.scale = decimals(to.getAsJsonArray("scale"));
            n.rotation = decimals(to.getAsJsonArray("rotation"));
        } catch (RuntimeException e) {
            return Result.fail("变换快照损坏: " + e.getMessage());
        }
        return Result.ok(new Operation.NoteSetTransform(o.level(), o.trackId(), o.noteId(), o.to(), from));
    }

    private Result applyTrackCreate(Operation.TrackCreate o, EditorChart chart) {
        EditorLevel level = chart.level(o.level());
        if (level == null) {
            return Result.fail("难度不存在");
        }
        if (level.tracks.containsKey(o.trackId())) {
            return Result.fail("轨道已存在");
        }
        level.ensureTrack(o.trackId());
        return Result.ok(new Operation.TrackDelete(o.level(), o.trackId()));
    }

    private Result applyTrackDelete(Operation.TrackDelete o, EditorChart chart) {
        EditorLevel level = chart.level(o.level());
        if (level == null) {
            return Result.fail("难度不存在");
        }
        EditorTrack removed = level.tracks.remove(o.trackId());
        if (removed == null) {
            return Result.fail("轨道不存在");
        }
        if (level.tracks.isEmpty()) {
            level.ensureTrack(chart.nextTrackId());
        }
        return Result.ok(new Operation.TrackCreate(o.level(), o.trackId()));
    }

    private Result applyEventAdd(Operation.EventAdd o, EditorChart chart) {
        EditorTrack track = findTrack(chart, o.level(), o.trackId());
        if (track == null) {
            return Result.fail("轨道不存在");
        }
        Channel channel;
        try {
            channel = Channel.valueOf(o.channel());
        } catch (RuntimeException e) {
            return Result.fail("通道不存在: " + o.channel());
        }
        EditorNumEvent ev;
        try {
            ev = eventFromSnapshot(o.event());
        } catch (RuntimeException e) {
            return Result.fail("事件快照损坏");
        }
        if (ev.endBeat.compareTo(ev.startBeat) < 0) {
            return Result.fail("endBeat < startBeat");
        }
        List<EditorNumEvent> list = track.events.get(channel);
        for (EditorNumEvent other : list) {
            if (other.id.equals(ev.id)) {
                return Result.fail("事件 id 已存在");
            }
            boolean overlap = other.startBeat.compareTo(ev.endBeat) < 0 && ev.startBeat.compareTo(other.endBeat) < 0;
            boolean zeroDup = other.startBeat.compareTo(ev.startBeat) == 0
                    && other.endBeat.compareTo(other.startBeat) > 0;
            if (overlap || zeroDup) {
                return Result.fail("与既有事件重叠（Reborn 加载会失败）");
            }
        }
        list.add(ev);
        list.sort((a, b) -> a.startBeat.compareTo(b.startBeat));
        return Result.ok(new Operation.EventRemove(o.level(), o.trackId(), o.channel(), eventSnapshot(ev)));
    }

    private Result applyEventRemove(Operation.EventRemove o, EditorChart chart) {
        EditorTrack track = findTrack(chart, o.level(), o.trackId());
        if (track == null) {
            return Result.fail("轨道不存在");
        }
        List<EditorNumEvent> list = track.events.get(Channel.valueOf(o.channel()));
        String id = o.event().get("id").getAsString();
        EditorNumEvent removed = null;
        for (EditorNumEvent ev : list) {
            if (ev.id.equals(id)) {
                removed = ev;
                break;
            }
        }
        if (removed == null) {
            return Result.fail("事件不存在");
        }
        list.remove(removed);
        return Result.ok(new Operation.EventAdd(o.level(), o.trackId(), o.channel(), eventSnapshot(removed)));
    }

    private static EditorTrack findTrack(EditorChart chart, String level, String trackId) {
        EditorLevel l = chart.level(level);
        return l == null ? null : l.track(trackId);
    }

    private static EditorNote findNote(EditorChart chart, String level, String trackId, String noteId) {
        EditorTrack t = findTrack(chart, level, trackId);
        return t == null ? null : t.note(noteId);
    }

    /** 未使用的旧辅助（保留给快照压缩路径）。 */
    public static List<EditorNote> sortedNotes(EditorTrack track) {
        List<EditorNote> out = new ArrayList<>(track.notes.values());
        out.sort((a, b) -> a.beat.compareTo(b.beat));
        return out;
    }
}
