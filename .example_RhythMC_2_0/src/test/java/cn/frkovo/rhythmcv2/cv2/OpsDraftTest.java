package cn.frkovo.rhythmcv2.cv2;

import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.compile.LintService;
import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import cn.frkovo.rhythmcv2.cv2.draft.DraftCodec;
import cn.frkovo.rhythmcv2.cv2.draft.RmcdStore;
import cn.frkovo.rhythmcv2.cv2.ops.ModelHash;
import cn.frkovo.rhythmcv2.cv2.ops.Operation;
import cn.frkovo.rhythmcv2.cv2.ops.OperationApplier;
import cn.frkovo.rhythmcv2.cv2.ops.OperationLog;
import cn.frkovo.rhythmcv2.cv2.ops.OpsJson;
import cn.frkovo.rhythmcv2.cv2.session.EditorState;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpsDraftTest {

    /** 测试辅助：绕过操作日志直接注入音符（供 Lint/Codec 用）。 */
    private static void putNote(EditorChart chart, String trackId, int type, String beat, int holdGroup) {
        EditorNote n = new EditorNote(chart.nextNoteId(), type, BeatFraction.parse(beat));
        n.holdGroup = holdGroup;
        chart.active().ensureTrack(trackId).notes.put(n.id, n);
    }

    private Operation noteCreate(EditorChart chart, int type, String beat, int holdGroup) {
        EditorNote n = new EditorNote(chart.nextNoteId(), type, BeatFraction.parse(beat));
        n.holdGroup = holdGroup;
        return new Operation.NoteCreate("world", "track-0",
                OperationApplier.noteSnapshot(n));
    }

    @Test
    void applyUndoRedoConverges() {
        EditorChart chart = new EditorChart("d1", "song");
        OperationApplier applier = new OperationApplier();
        String initial = ModelHash.of(chart);

        // create → undo → 与初始一致
        Operation create = noteCreate(chart, EditorNote.TAP, "4", -1);
        OperationApplier.Result r = applier.apply(create, chart);
        assertTrue(r.ok());
        String afterCreate = ModelHash.of(chart);
        assertNotEquals(initial, afterCreate);
        applier.apply(r.inverse(), chart);
        assertEquals(initial, ModelHash.of(chart));

        // set beat：from/to 对称
        applier.apply(create, chart);
        OperationApplier.Result sb = applier.apply(
                new Operation.NoteSetBeat("world", "track-0", chart.active().track("track-0")
                        .notes.keySet().iterator().next(), "4", "11/3"), chart);
        assertTrue(sb.ok());
        applier.apply(sb.inverse(), chart);
        assertEquals(afterCreate, ModelHash.of(chart));

        // bpm：add/remove/set
        OperationApplier.Result ba = applier.apply(new Operation.BpmAdd("8", "150"), chart);
        assertTrue(ba.ok());
        applier.apply(ba.inverse(), chart);
        assertEquals(afterCreate, ModelHash.of(chart));
    }

    @Test
    void overlappingEventsRejected() {
        EditorChart chart = new EditorChart("d1", "song");
        OperationApplier applier = new OperationApplier();
        JsonObject ev1 = OperationApplier.eventSnapshot(new cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent(
                "e1", BeatFraction.ZERO, BeatFraction.of(4), BigDecimal.ONE, BigDecimal.ONE, 0));
        JsonObject ev2 = OperationApplier.eventSnapshot(new cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent(
                "e2", BeatFraction.of(2), BeatFraction.of(6), BigDecimal.ONE, BigDecimal.ONE, 0));
        assertTrue(applier.apply(new Operation.EventAdd("world", "track-0", "SPEED", ev1), chart).ok());
        assertFalse(applier.apply(new Operation.EventAdd("world", "track-0", "SPEED", ev2), chart).ok());
    }

    @Test
    void holdGroupLint() {
        EditorChart chart = new EditorChart("d1", "song");
        putNote(chart, "track-0", EditorNote.HOLD, "4", 1);
        // 跨轨同组
        putNote(chart, "track-1", EditorNote.HOLD, "6", 1);
        List<LintService.Issue> issues = LintService.lint(chart);
        assertTrue(issues.stream().anyMatch(i -> i.level().equals("error")
                && i.rule().equals("holdGroup") && i.message().contains("跨")));
    }

    @Test
    void densityWarn() {
        EditorChart chart = new EditorChart("d1", "song");
        // 200 BPM：1/4 拍间隔 = 75ms < 125
        chart.bpms.set(0, new BeatClock.BpmEvent(BeatFraction.ZERO, new BigDecimal("200")));
        putNote(chart, "track-0", EditorNote.TAP, "4", -1);
        putNote(chart, "track-0", EditorNote.TAP, "17/4", -1);
        List<LintService.Issue> issues = LintService.lint(chart);
        assertTrue(issues.stream().anyMatch(i -> i.level().equals("warn") && i.rule().equals("density")));
    }

    @Test
    void rmcdSaveLoadRoundTrip() throws IOException {
        Path dir = Files.createTempDirectory("rmcd-test");
        Path file = dir.resolve("song.rmcd");
        EditorChart chart = new EditorChart("draft-x", "song");
        putNote(chart, "track-0", EditorNote.TAP, "32/3", -1);
        EditorState state = new EditorState();
        state.cursorBeat = BeatFraction.parse("32/3");
        state.snap = 8;
        String hashBefore = ModelHash.of(chart);
        RmcdStore.save(file, new RmcdStore.SaveInput(chart, state,
                "{\"seq\":0,\"command\":\"NOTE_CREATE\"}\n"));

        DraftCodec.DraftData loaded = RmcdStore.load(file);
        String a = DraftCodec.modelJson(chart);
        String b = DraftCodec.modelJson(loaded.chart());
        if (!a.equals(b)) {
            System.out.println("[DEBUG-ORIG] " + a);
            System.out.println("[DEBUG-LOAD] " + b);
        }
        assertEquals(hashBefore, ModelHash.of(loaded.chart()));
        assertEquals("32/3", loaded.state().cursorBeat.toString());
        assertEquals(8, loaded.state().snap);
        String ndjson = RmcdStore.readEntry(file, "operations.ndjson");
        assertTrue(ndjson.contains("NOTE_CREATE"));
        String snapshot = RmcdStore.readEntry(file, "snapshots/000000.json");
        assertTrue(snapshot.contains("\"bpms\""));
    }

    @Test
    void operationReplayerVerifiesHash() throws IOException {
        EditorChart chart = new EditorChart("draft-y", "song");
        EditorState state = new EditorState();
        OperationApplier applier = new OperationApplier();
        OperationLog log = new OperationLog();

        record Step(Operation op) {
        }
        Operation create = noteCreate(chart, EditorNote.LOOK, "3", -1);
        log.append(create, ModelHash.of(chart), null);
        applier.apply(create, chart);
        String beatTo = "7";
        Operation move = new Operation.NoteSetBeat("world", "track-0",
                chart.active().track("track-0").notes.keySet().iterator().next(), "3", beatTo);
        log.append(move, ModelHash.of(chart), null);
        applier.apply(move, chart);

        // 重放：空快照模型 + 全部操作 → hash 与内存模型一致
        EditorChart snapshot = new EditorChart("draft-y", "song");
        String replayHash = RmcdStore.replayHash(snapshot, log.pendingEntries(), applier);
        assertEquals(ModelHash.of(chart), replayHash);

        // OpsJson 反序列化再走一遍（与日志持久化路径一致）
        OperationLog log2 = new OperationLog();
        for (OperationLog.Entry e : log.pendingEntries()) {
            Operation op = OpsJson.fromJson(e.payload(), e.command());
            assertTrue(applier.apply(op, snapshot).ok());
            log2.append(op, e.beforeHash(), e.afterHash());
        }
        assertEquals(ModelHash.of(chart), ModelHash.of(snapshot));
    }

    @Test
    void draftCodecBeatStringsNeverNumbers() {
        EditorChart chart = new EditorChart("draft-z", "song");
        putNote(chart, "track-0", EditorNote.HOLD, "1/3", 2);
        JsonObject draft = DraftCodec.draftJson(chart, new EditorState(), 0);
        String json = draft.toString();
        // beat 只能以字符串出现（"beat":"1/3"），禁止 JSON number beat
        assertTrue(json.contains("\"beat\":\"1/3\""));
        assertFalse(json.matches("(?s).*\"beat\":[0-9-].*"));
        assertTrue(json.contains("\"holdGroup\":2"));
    }
}
