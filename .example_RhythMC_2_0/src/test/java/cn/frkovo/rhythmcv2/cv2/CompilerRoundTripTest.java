package cn.frkovo.rhythmcv2.cv2;

import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.compile.Compiler;
import cn.frkovo.rhythmcv2.cv2.compile.LintService;
import cn.frkovo.rhythmcv2.cv2.compile.RmccImporter;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerRoundTripTest {

    private static void putNote(EditorChart chart, String trackId, int type, String beat, int holdGroup) {
        EditorNote n = new EditorNote(chart.nextNoteId(), type, BeatFraction.parse(beat));
        n.holdGroup = holdGroup;
        chart.active().ensureTrack(trackId).notes.put(n.id, n);
    }

    @Test
    void compileProducesRebornLayoutAndReport() throws Exception {
        Path dir = Files.createTempDirectory("compile-test");
        EditorChart chart = new EditorChart("draft-c", "my_song");
        chart.manifestName = "测试曲";
        chart.composer = "Composer";
        chart.charters.add("谱师A");
        chart.albums.add("album-1");
        chart.length = "0";
        putNote(chart, "track-0", EditorNote.TAP, "4", -1);
        putNote(chart, "track-0", EditorNote.TAP, "14/3", -1); // 无限小数 → PRECISION_LOSS
        putNote(chart, "track-0", EditorNote.TAP, "8", -1);

        Compiler.CompileResult result = Compiler.compile(chart, dir);
        assertTrue(result.success(), "errors=" + result.errors());
        Path out = result.dir();
        assertTrue(Files.isRegularFile(out.resolve("manifest.yml")));
        assertTrue(Files.isRegularFile(out.resolve("world.rmcc")));
        assertTrue(Files.isRegularFile(out.resolve("compile-report.json")));
        assertFalse(Files.exists(out.resolve("nether.rmcc")));

        // 1/3 必须出现在精度损失报告
        assertTrue(result.precision().stream().anyMatch(p ->
                p.kind().equals("PRECISION_LOSS") && p.source().equals("14/3")));

        // .rmcc 内容检查：meta/charters/track id/beat 数值
        JsonObject root = JsonParser.parseString(Files.readString(out.resolve("world.rmcc")))
                .getAsJsonObject();
        JsonObject meta = root.getAsJsonObject("meta");
        assertEquals(1, meta.get("uid").getAsInt()); // songId 0 × 10 + world=1（Reborn GameUtils 约定）
        assertEquals("谱师A", meta.getAsJsonArray("charters").get(0).getAsString());
        JsonArray bpms = meta.getAsJsonArray("bpms");
        assertEquals(120.0, bpms.get(0).getAsJsonObject().get("bpm").getAsDouble(), 1e-9);
        JsonArray tracks = root.getAsJsonArray("tracks");
        JsonObject track0 = tracks.get(0).getAsJsonObject();
        assertEquals(0, track0.get("id").getAsInt());
        JsonArray notes = track0.getAsJsonArray("notes");
        assertEquals(3, notes.size());
        // beat 导出为 18 位小数 JSON number
        assertEquals(4.0, notes.get(0).getAsJsonObject().get("beat").getAsDouble(), 1e-9);
        // 排序不变性：4 < 14/3 ≈ 4.667 < 8
        assertTrue(notes.get(0).getAsJsonObject().get("beat").getAsDouble()
                < notes.get(1).getAsJsonObject().get("beat").getAsDouble());
        assertTrue(notes.get(1).getAsJsonObject().get("beat").getAsDouble()
                < notes.get(2).getAsJsonObject().get("beat").getAsDouble());

        // manifest.yml：albums 字段（Reborn 反序列化器读取 albums 而非 tags）
        String manifest = Files.readString(out.resolve("manifest.yml"));
        assertTrue(manifest.contains("albums"));
        assertTrue(manifest.contains("name: "));
        assertFalse(manifest.contains("tags:"));

        // 自动 length = 末音符 8 拍 × 500ms + 2000 = 6000
        assertTrue(manifest.contains("6000"));
    }

    @Test
    void importExportRoundTripPreservesSemantics() throws Exception {
        // 导出目录 → RmccImporter 导入 → 语义一致
        Path dir = Files.createTempDirectory("roundtrip");
        EditorChart chart = new EditorChart("draft-r", "rt_song");
        chart.bpms.add(new cn.frkovo.rhythmcv2.cv2.core.BeatClock.BpmEvent(
                BeatFraction.of(4), new BigDecimal("90")));
        putNote(chart, "track-0", EditorNote.LOOK, "0", -1);
        putNote(chart, "track-0", EditorNote.HOLD, "3", 7);
        putNote(chart, "track-0", EditorNote.HOLD, "11/2", 7);
        Compiler.CompileResult result = Compiler.compile(chart, dir);
        assertTrue(result.success());

        EditorChart re = RmccImporter.importSong("rt_song", result.dir());
        assertEquals("rt_song", re.songFolder);
        assertEquals(2, re.bpms.size());
        assertEquals(0, re.bpms.get(1).bpm().compareTo(new BigDecimal("90")));
        assertEquals(3, re.active().track("track-0").notes.size());
        // 精确分数经 18 位小数往返后仍在容差内（1e-12 拍）
        List<BeatFraction> beats = re.active().track("track-0").notes.values().stream()
                .map(n -> n.beat).sorted().toList();
        assertTrue(beats.get(2).subtract(BeatFraction.parse("11/2")).toBigDecimal(15)
                .abs().doubleValue() < 1e-9);
        // holdGroup 语义保留
        long holds = re.active().track("track-0").notes.values().stream()
                .filter(n -> n.holdGroup == 7).count();
        assertEquals(2, holds);
    }

    @Test
    void lintErrorBlocksCompile() throws Exception {
        Path dir = Files.createTempDirectory("lint-block");
        EditorChart chart = new EditorChart("draft-l", "bad");
        // 重叠 speed 事件
        chart.active().track("track-0").events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.SPEED)
                .add(new cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent("e1", BeatFraction.ZERO,
                        BeatFraction.of(4), BigDecimal.ONE, BigDecimal.ONE, 0));
        chart.active().track("track-0").events.get(cn.frkovo.rhythmcv2.cv2.chart.Channel.SPEED)
                .add(new cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent("e2", BeatFraction.of(2),
                        BeatFraction.of(6), BigDecimal.ONE, BigDecimal.ONE, 0));
        List<LintService.Issue> issues = LintService.lint(chart);
        assertTrue(issues.stream().anyMatch(i -> i.level().equals("error")));
        Compiler.CompileResult result = Compiler.compile(chart, dir);
        assertFalse(result.success());
    }

    @Test
    void i18nUnderscoreKeysStrippedOnExport() throws Exception {
        Path dir = Files.createTempDirectory("i18n-strip");
        EditorChart chart = new EditorChart("draft-i", "i18n_song");
        chart.i18n.addProperty("zh-cn", "中文");
        JsonObject en = new JsonObject();
        en.addProperty("text", "hello");
        en.addProperty("_tool_meta", "strip-me");
        chart.i18n.add("en-us", en);
        putNote(chart, "track-0", EditorNote.TAP, "4", -1);
        Compiler.CompileResult result = Compiler.compile(chart, dir);
        assertTrue(result.success());
        String manifest = Files.readString(result.dir().resolve("manifest.yml"));
        assertFalse(manifest.contains("_tool_meta"));
        assertTrue(manifest.contains("hello"));
        assertTrue(manifest.contains("中文"));
    }
}
