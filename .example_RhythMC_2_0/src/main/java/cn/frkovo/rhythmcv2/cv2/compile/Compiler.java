package cn.frkovo.rhythmcv2.cv2.compile;

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
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯领域编译管线（§7.4 MVP 级）：.rmcd 模型 → Reborn 兼容 manifest.yml + *.rmcc + compile-report.json。
 * - beat 按 exportScale=18 转有限小数 JSON number；pos 用 double、scale/rotation 用 float（Reborn record 语义）。
 * - ReimportVerifier 用与 Reborn 相同的字段语义重读产物，校验排序不变性并产出误差报告。
 * - 无限小数（如 1/3）必然产生 PRECISION_LOSS；--strict 发布由调用方依据报告阻断。
 */
public final class Compiler {

    public static final int EXPORT_SCALE = 18;
    public static final String REPORT_NAME = "compile-report.json";

    public record PrecisionEntry(String kind, String where, String source, String exported,
                                 String reread, String drift, boolean blocked) {
    }

    public record TrackMapping(String draftId, int runtimeId) {
    }

    public record CompileResult(boolean success, Path dir, List<LintService.Issue> lint,
                                List<PrecisionEntry> precision, List<String> errors,
                                Map<String, List<TrackMapping>> trackMaps, long revision) {
    }

    private Compiler() {
    }

    public static BigDecimal exportBeat(BeatFraction beat) {
        return beat.toBigDecimal(EXPORT_SCALE);
    }

    public static CompileResult compile(EditorChart chart, Path songDir) throws IOException {
        List<String> errors = new ArrayList<>();
        List<LintService.Issue> lint = LintService.lint(chart);
        for (LintService.Issue i : lint) {
            if (i.level().equals("error")) {
                errors.add("[lint/" + i.rule() + "] " + i.message());
            }
        }

        Path outDir = songDir.resolve("build").resolve(chart.draftId).resolve(String.valueOf(chart.revision));
        Files.createDirectories(outDir);

        List<PrecisionEntry> precision = new ArrayList<>();
        Map<String, List<TrackMapping>> trackMaps = new LinkedHashMap<>();
        List<String> levelNames = List.of("world", "nether", "end", "void");

        if (errors.isEmpty()) {
            // manifest.yml
            writeManifest(chart, outDir);
            // 各难度 .rmcc
            BeatClock clock = chart.clock();
            for (String levelName : levelNames) {
                EditorLevel level = chart.level(levelName);
                if (level == null) {
                    continue;
                }
                JsonObject root = exportLevel(chart, clock, level, precision, trackMaps);
                Files.writeString(outDir.resolve(levelName + ".rmcc"),
                        root.toString(), StandardCharsets.UTF_8);
            }
            // ReimportVerifier
            verifyReimport(chart, clock, outDir, levelNames, precision, errors);
        }

        JsonObject report = new JsonObject();
        report.addProperty("draftId", chart.draftId);
        report.addProperty("revision", chart.revision);
        report.addProperty("exportScale", EXPORT_SCALE);
        JsonArray lintArr = new JsonArray();
        for (LintService.Issue i : lint) {
            JsonObject o = new JsonObject();
            o.addProperty("level", i.level());
            o.addProperty("rule", i.rule());
            o.addProperty("message", i.message());
            lintArr.add(o);
        }
        report.add("lint", lintArr);
        JsonArray precArr = new JsonArray();
        for (PrecisionEntry p : precision) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", p.kind());
            o.addProperty("where", p.where());
            o.addProperty("source", p.source());
            o.addProperty("exported", p.exported());
            o.addProperty("reread", p.reread());
            o.addProperty("drift", p.drift());
            o.addProperty("blocked", p.blocked());
            precArr.add(o);
        }
        report.add("precision", precArr);
        JsonArray errArr = new JsonArray();
        for (String e : errors) {
            errArr.add(e);
        }
        report.add("errors", errArr);
        Files.writeString(outDir.resolve(REPORT_NAME), report.toString(), StandardCharsets.UTF_8);

        boolean success = errors.isEmpty();
        return new CompileResult(success, success ? outDir : null, lint, precision, errors, trackMaps, chart.revision);
    }

    // ---- manifest ----

    private static void writeManifest(EditorChart chart, Path outDir) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", chart.manifestName);
        yaml.set("composer", chart.composer);
        yaml.set("icon", chart.icon);
        yaml.set("alias", chart.alias);
        yaml.set("length", manifestLength(chart));
        yaml.set("respack_sha1", chart.respackSha1);
        yaml.set("description", chart.description);
        yaml.set("song_id", parseIntSafe(chart.songId));
        yaml.set("version", chart.version);
        yaml.set("comments", chart.comments);
        yaml.set("albums", chart.albums);
        yaml.set("default_locale", chart.defaultLocale);
        if (chart.i18n.size() > 0) {
            yaml.set("i18n", stripUnderscoreKeys(chart.i18n));
        }
        yaml.save(outDir.resolve("manifest.yml").toFile());
    }

    private static long manifestLength(EditorChart chart) {
        try {
            long len = Long.parseLong(chart.length);
            if (len > 0) {
                return len;
            }
        } catch (RuntimeException ignored) {
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
        return clock.msLong(last) + 2000;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** i18n 块内 `_` 前缀键为工具元数据，导出时剥离（跨仓硬规则）。 */
    public static java.util.Map<String, Object> stripUnderscoreKeys(JsonObject i18n) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : i18n.entrySet()) {
            if (e.getKey().startsWith("_")) {
                continue;
            }
            out.put(e.getKey(), stripNode(e.getValue()));
        }
        return out;
    }

    private static Object stripNode(com.google.gson.JsonElement el) {
        if (el.isJsonObject()) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> e : el.getAsJsonObject().entrySet()) {
                if (e.getKey().startsWith("_")) {
                    continue;
                }
                m.put(e.getKey(), stripNode(e.getValue()));
            }
            return m;
        }
        if (el.isJsonArray()) {
            List<Object> l = new ArrayList<>();
            for (com.google.gson.JsonElement e : el.getAsJsonArray()) {
                l.add(stripNode(e));
            }
            return l;
        }
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return null;
    }

    // ---- 难度导出 ----

    private static int difficultyId(String levelName) {
        return switch (levelName) {
            case "nether" -> 2;
            case "end" -> 3;
            case "void" -> 4;
            default -> 1;
        };
    }

    private static JsonObject exportLevel(EditorChart chart, BeatClock clock, EditorLevel level,
                                          List<PrecisionEntry> precision,
                                          Map<String, List<TrackMapping>> trackMaps) {
        JsonObject meta = new JsonObject();
        meta.addProperty("uid", parseIntSafe(chart.songId) * 10 + difficultyId(level.name));
        if (!level.initialArena.isBlank()) {
            meta.addProperty("initialArena", level.initialArena);
        }
        meta.addProperty("offset", clock.offsetMs());
        meta.addProperty("level", levelValue(level));
        JsonArray chartersArr = new JsonArray();
        for (String c : chart.charters) {
            chartersArr.add(c);
        }
        meta.add("charters", chartersArr);
        JsonArray commentsArr = new JsonArray();
        for (String c : chart.comments) {
            commentsArr.add(c);
        }
        meta.add("comments", commentsArr);
        JsonArray bpms = new JsonArray();
        for (BeatClock.BpmEvent e : chart.bpms) {
            JsonObject b = new JsonObject();
            addBeat(b, "beat", e.beat(), clock, "meta.bpms", precision);
            b.addProperty("bpm", e.bpm().doubleValue());
            bpms.add(b);
        }
        meta.add("bpms", bpms);

        JsonObject root = new JsonObject();
        root.add("meta", meta);

        List<EditorTrack> tracks = new ArrayList<>(level.tracks.values());
        tracks.sort(Comparator.comparing(t -> t.id));
        List<TrackMapping> mappings = new ArrayList<>();
        JsonArray tracksArr = new JsonArray();
        for (int i = 0; i < tracks.size(); i++) {
            EditorTrack t = tracks.get(i);
            int runtimeId = parseTrackIndex(t.id, i);
            mappings.add(new TrackMapping(t.id, runtimeId));
            tracksArr.add(exportTrack(chart, clock, t, runtimeId, level.name, precision));
        }
        trackMaps.put(level.name, mappings);
        root.add("tracks", tracksArr);

        JsonArray effects = new JsonArray();
        for (JsonObject fx : level.effects) {
            effects.add(fx.deepCopy());
        }
        root.add("effects", effects);
        return root;
    }

    private static BigDecimal levelValue(EditorLevel level) {
        try {
            return new BigDecimal(level.levelValue);
        } catch (RuntimeException e) {
            return BigDecimal.ONE;
        }
    }

    private static int parseTrackIndex(String draftId, int fallback) {
        try {
            return Integer.parseInt(draftId.replaceFirst("^track-", ""));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static JsonObject exportTrack(EditorChart chart, BeatClock clock, EditorTrack t, int runtimeId,
                                          String levelName, List<PrecisionEntry> precision) {
        JsonObject o = new JsonObject();
        o.addProperty("id", runtimeId);
        for (Channel c : Channel.values()) {
            List<EditorNumEvent> evs = t.events.get(c);
            if (evs.isEmpty()) {
                continue;
            }
            List<EditorNumEvent> sorted = new ArrayList<>(evs);
            sorted.sort(Comparator.comparing(e -> e.startBeat));
            JsonArray arr = new JsonArray();
            for (EditorNumEvent ev : sorted) {
                JsonObject e = new JsonObject();
                addBeat(e, "startBeat", ev.startBeat, clock, levelName + "/" + t.id + "/" + c.json, precision);
                addBeat(e, "endBeat", ev.endBeat, clock, levelName + "/" + t.id + "/" + c.json, precision);
                e.addProperty("startValue", ev.startValue.doubleValue());
                e.addProperty("endValue", ev.endValue.doubleValue());
                e.addProperty("easingType", ev.easing);
                arr.add(e);
            }
            o.add(c.json, arr);
        }
        List<EditorNote> notes = new ArrayList<>(t.notes.values());
        notes.sort(Comparator.comparing(n -> n.beat));
        JsonArray notesArr = new JsonArray();
        for (EditorNote n : notes) {
            JsonObject nn = new JsonObject();
            nn.addProperty("noteType", n.noteType);
            addBeat(nn, "beat", n.beat, clock, levelName + "/" + t.id + "/note " + n.id, precision);
            nn.add("pos", doubles(n.pos));
            nn.add("scale", floats(n.scale));
            nn.add("rotation", floats(n.rotation));
            nn.addProperty("holdGroup", n.holdGroup);
            notesArr.add(nn);
        }
        o.add("notes", notesArr);
        return o;
    }

    private static void addBeat(JsonObject o, String key, BeatFraction beat, BeatClock clock,
                                String where, List<PrecisionEntry> precision) {
        BigDecimal exported = exportBeat(beat);
        o.addProperty(key, exported);
        if (!beat.isExact()) {
            BigDecimal reread = BigDecimal.valueOf(exported.doubleValue());
            BigDecimal drift = reread.subtract(beat.toBigDecimal(EXPORT_SCALE + 2))
                    .multiply(BigDecimal.valueOf(60000))
                    .divide(clock.bpmAt(beat), 3, RoundingMode.HALF_UP);
            precision.add(new PrecisionEntry("PRECISION_LOSS", where + "." + key,
                    beat.toString(), exported.toPlainString(), reread.toPlainString(),
                    drift.toPlainString() + "ms", false));
        }
    }

    private static JsonArray doubles(BigDecimal[] v) {
        JsonArray a = new JsonArray();
        for (BigDecimal d : v) {
            a.add(new com.google.gson.JsonPrimitive(d.doubleValue()));
        }
        return a;
    }

    private static JsonArray floats(BigDecimal[] v) {
        JsonArray a = new JsonArray();
        for (BigDecimal d : v) {
            float f = d.floatValue();
            a.add(new com.google.gson.JsonPrimitive(f));
        }
        return a;
    }

    // ---- ReimportVerifier ----

    private static void verifyReimport(EditorChart chart, BeatClock clock, Path outDir,
                                       List<String> levelNames, List<PrecisionEntry> precision,
                                       List<String> errors) {
        for (String levelName : levelNames) {
            Path f = outDir.resolve(levelName + ".rmcc");
            if (!Files.isRegularFile(f)) {
                continue;
            }
            try {
                JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                JsonArray tracks = root.getAsJsonArray("tracks");
                List<Integer> ids = new ArrayList<>();
                for (com.google.gson.JsonElement te : tracks) {
                    JsonObject t = te.getAsJsonObject();
                    int id = t.get("id").getAsInt();
                    if (ids.contains(id)) {
                        errors.add("[reimport] 重复运行时轨道 id: " + id);
                    }
                    ids.add(id);
                    List<Double> noteBeats = new ArrayList<>();
                    if (t.has("notes")) {
                        for (com.google.gson.JsonElement ne : t.getAsJsonArray("notes")) {
                            noteBeats.add(ne.getAsJsonObject().get("beat").getAsDouble());
                        }
                    }
                    for (int i = 1; i < noteBeats.size(); i++) {
                        if (noteBeats.get(i) < noteBeats.get(i - 1)) {
                            errors.add("[reimport] " + levelName + " 轨 " + id + " 音符 beat 排序被破坏");
                            break;
                        }
                    }
                    // 每通道事件区间重叠复核（Reborn 语义）
                    for (Channel c : Channel.values()) {
                        if (!t.has(c.json)) {
                            continue;
                        }
                        List<double[]> spans = new ArrayList<>();
                        for (com.google.gson.JsonElement ee : t.getAsJsonArray(c.json)) {
                            JsonObject ev = ee.getAsJsonObject();
                            spans.add(new double[]{ev.get("startBeat").getAsDouble(),
                                    ev.get("endBeat").getAsDouble()});
                        }
                        spans.sort(Comparator.comparingDouble(s -> s[0]));
                        for (int i = 1; i < spans.size(); i++) {
                            if (spans.get(i)[0] < spans.get(i - 1)[1]
                                    || (spans.get(i)[0] == spans.get(i - 1)[0]
                                    && spans.get(i)[1] > spans.get(i)[0])) {
                                errors.add("[reimport] " + levelName + " 轨 " + id + " " + c.json + " 区间重叠");
                                break;
                            }
                        }
                    }
                }
                JsonArray bpms = root.getAsJsonObject("meta").getAsJsonArray("bpms");
                for (int i = 1; i < bpms.size(); i++) {
                    double prev = bpms.get(i - 1).getAsJsonObject().get("beat").getAsDouble();
                    double cur = bpms.get(i).getAsJsonObject().get("beat").getAsDouble();
                    if (cur <= prev) {
                        errors.add("[reimport] " + levelName + " bpm 事件 beat 非严格递增");
                    }
                }
            } catch (RuntimeException | IOException e) {
                errors.add("[reimport] 重读失败: " + e.getMessage());
            }
        }
    }
}
