package cn.frkovo.rhythmcmaker.chart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.github.luben.zstd.ZstdInputStream;
import org.yaml.snakeyaml.Yaml;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import cn.frkovo.rhythmcmaker.RhythmcMaker;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ChartStorage {
    public static final String ROOT_FOLDER = "rhythmc maker";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CHART_TYPE = new TypeToken<ChartManifest>() {}.getType();

    private ChartStorage() {
    }

    public static List<ChartManifest> list(MinecraftServer server) throws IOException {
        Path manifests = manifestsDirectory(server);
        if (!Files.exists(manifests)) return List.of();

        List<ChartManifest> charts = new ArrayList<>();
        try (var files = Files.list(manifests)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> readManifest(path, charts));
        }
        charts.sort(Comparator.comparing(chart -> chart.title == null ? "" : chart.title, String.CASE_INSENSITIVE_ORDER));
        return charts;
    }

    public static Path stageAudio(MinecraftServer server, Path source) throws IOException {
        String extension = extensionOf(source.getFileName().toString());
        if (!isSupportedAudioExtension(extension)) {
            throw new IOException("不支持的音频格式：" + extension);
        }
        Path destination = importsDirectory(server).resolve(UUID.randomUUID() + "." + extension);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    public record ImportedScene(Path schem, Path info) {}

    public static ChartManifest importRhythmc2(MinecraftServer server, Path difficultyFile, Path stagedAudio, double bpm, String difficulty, double level) throws IOException {
        JsonObject root = JsonParser.parseString(readChartText(difficultyFile)).getAsJsonObject();
        JsonObject meta = root.has("meta") && root.get("meta").isJsonObject() ? root.getAsJsonObject("meta") : new JsonObject();
        ChartManifest chart = new ChartManifest();
        chart.id = UUID.randomUUID().toString();
        chart.title = stripExtension(difficultyFile.getFileName().toString());
        chart.artist = "Unknown";
        chart.charter = firstJsonString(meta, "charter", "charter-alias", "Unknown");
        chart.coverItemId = coverItemId("minecraft:note_block");
        chart.difficulty = difficulty;
        chart.bpm = bpm;
        chart.divisionsPerChunk = 12;
        chart.bpms.add(new ChartManifest.BpmEvent(0, chart.bpm));
        chart.offsetMillis = Math.round(jsonNumber(meta, "offset", 0).doubleValue() * 50.0);
        chart.level = level;
        if (meta.has("initial-arena") && meta.get("initial-arena").isJsonPrimitive()) chart.initialArena = meta.get("initial-arena").getAsString();
        List<String> arenaNames = new ArrayList<>();
        if (chart.initialArena != null && !chart.initialArena.isBlank()) arenaNames.add(chart.initialArena);
        ChartTiming.Prepared timing = ChartTiming.prepare(chart);
        if (root.has("frames") && root.get("frames").isJsonArray()) {
            for (JsonElement frameElement : root.getAsJsonArray("frames")) {
                if (!frameElement.isJsonObject()) continue;
                JsonObject frame = frameElement.getAsJsonObject();
                double judgeTick = jsonNumber(frame, "judge-tick", 0).doubleValue();
                if (!Double.isFinite(judgeTick)) continue;
                double frameSeconds = Math.max(0.0, judgeTick / 20.0);
                double frameBeat = snapLegacyBeat(frameSeconds * bpm / 60.0, chart.divisionsPerChunk);
                if (!frame.has("notes") || !frame.get("notes").isJsonArray()) continue;
                for (JsonElement noteElement : frame.getAsJsonArray("notes")) {
                    if (!noteElement.isJsonObject()) continue;
                    JsonObject source = noteElement.getAsJsonObject();
                    int type = oldNoteType(source.has("type") ? source.get("type").getAsString() : "NOTE_CLICK");
                    int length = Math.max(1, jsonNumber(source, "length", 1).intValue());
                    int count = type == 2 ? length : 1;
                    for (int hold = 0; hold < count; hold++) {
                        ChartManifest.Note note = new ChartManifest.Note();
                        note.id = UUID.randomUUID().toString();
                        note.type = type;
                        double noteSeconds = Math.max(0.0, frameSeconds + hold / 20.0);
                        note.beat = snapLegacyBeat(noteSeconds * bpm / 60.0, chart.divisionsPerChunk);
                        note.time = timing.beatToSeconds(note.beat);
                        if (source.has("pos") && source.get("pos").isJsonArray()) {
                            JsonArray pos = source.getAsJsonArray("pos");
                            note.sourceX = pos.size() > 0 ? pos.get(0).getAsDouble() : 0;
                            note.sourceY = pos.size() > 1 ? pos.get(1).getAsDouble() : 0;
                        } else if (source.has("pos") && source.get("pos").isJsonPrimitive()) {
                            note.sourceX = source.get("pos").getAsDouble();
                            note.sourceY = 0.0;
                        }
                        note.x = note.sourceX == null ? 0.0 : note.sourceX;
                        note.y = type == 2 ? 65.0 : (note.sourceY == null ? 0.0 : note.sourceY) + 66.0;
                        note.z = PlaybackCoordinates.editorWorldZAtBeat(chart, note.beat);
                        chart.notes.add(note);
                        chart.totalBeats = Math.max(chart.totalBeats, note.beat);
                    }
                }
            }
        }
        if (root.has("effects") && root.get("effects").isJsonArray()) {
            for (JsonElement effectElement : root.getAsJsonArray("effects")) {
                if (!effectElement.isJsonObject()) continue;
                JsonObject effect = convertRhythmc2Effect(effectElement.getAsJsonObject(), bpm, chart.divisionsPerChunk);
                if (effect == null) continue;
                if (effect.has("start-tick") && effect.get("start-tick").isJsonPrimitive()) {
                    double startTick = effect.get("start-tick").getAsDouble();
                    if (Double.isFinite(startTick)) effect.addProperty("beat", snapLegacyBeat(Math.max(0.0, startTick / 20.0) * bpm / 60.0, chart.divisionsPerChunk));
                }
                if ("ARENA".equalsIgnoreCase(effect.has("effect-type") ? effect.get("effect-type").getAsString() : "")
                        && effect.has("arena") && effect.get("arena").isJsonPrimitive()) {
                    String arena = effect.get("arena").getAsString();
                    if (!arena.isBlank() && !arenaNames.contains(arena)) arenaNames.add(arena);
                    effect.addProperty("type", "ARENA");
                }
                chart.effects.add(effect);
            }
        }
        create(server, chart, stagedAudio);
        chart.trackLength = calculateImportedTrackLength(chart);
        writeManifest(manifestPath(server, chart.id), chart);
        writeChartDocument(chartDocumentPath(server, chart.id), chart);
        RhythmcMaker.installDefaultArenaScenes(server, chart, arenaNames);
        return chart;
    }    public static ChartManifest importRhythmc3(MinecraftServer server, Path difficultyFile, Path manifestFile, Path stagedAudio, List<ImportedScene> scenes) throws IOException {
        ChartManifest chart = importRhythmc3(server, difficultyFile, manifestFile, stagedAudio);
        if (scenes != null && !scenes.isEmpty()) {
            Path sceneRoot = root(server).resolve("scenes").resolve(chart.id).toAbsolutePath().normalize();
            Files.createDirectories(sceneRoot);
            int index = 0;
            for (ImportedScene scene : scenes) {
                if (scene == null || scene.schem() == null || scene.info() == null || !Files.isRegularFile(scene.schem()) || !Files.isRegularFile(scene.info())) continue;
                Path folder = sceneRoot.resolve(index == 0 ? "initial" : "scene" + index).normalize();
                if (!folder.startsWith(sceneRoot)) continue;
                Files.createDirectories(folder);
                Files.copy(scene.schem(), folder.resolve("arena.schem"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(scene.info(), folder.resolve("metadata.yml"), StandardCopyOption.REPLACE_EXISTING);
                String sceneKey = sceneKey(scene.info(), index == 0 ? "initial" : "scene" + index);
                String scenePath = folder.toString();
                chart.arenaBindings.put(sceneKey, scenePath);
                chart.arenaBindings.put(index == 0 ? "initial" : "scene" + index, scenePath);
                if (index == 0 && chart.initialArena != null && !chart.initialArena.isBlank()) chart.arenaBindings.put(chart.initialArena, scenePath);
                index++;
            }
        }
        if (chart.effects != null) for (JsonObject effect : chart.effects) {
            if (effect == null || !effect.has("arena")) continue;
            String arena = effect.get("arena").getAsString();
            String path = chart.arenaBindings.get(arena);
            if (path != null) effect.addProperty("arenaPath", path);
        }
        RhythmcMaker.installImportedScenes(server, chart, scenes);
        writeManifest(manifestPath(server, chart.id), chart);
        return chart;
    }

    public static ChartManifest importRhythmc3(MinecraftServer server, Path difficultyFile, Path manifestFile, Path stagedAudio) throws IOException {
        JsonObject root = JsonParser.parseString(readChartText(difficultyFile)).getAsJsonObject();
        JsonObject meta = root.getAsJsonObject("meta");
        if (meta == null) throw new IOException("3.0 难度文件缺少 meta");
        ChartManifest chart = new ChartManifest();
        Map<String, Object> manifest = readYaml(manifestFile);
        chart.id = UUID.randomUUID().toString(); chart.title = yamlString(manifest, "name", "未命名谱面"); chart.artist = yamlString(manifest, "composer", "Unknown"); chart.charter = yamlString(manifest, "charter", yamlString(manifest, "author", "Unknown")); chart.coverItemId = coverItemId(yamlString(manifest, "icon", yamlString(manifest, "cover", "minecraft:note_block"))); chart.difficulty = difficultyFromFile(difficultyFile); chart.divisionsPerChunk = readDivisionsPerChunk(meta);
        chart.offsetMillis = jsonNumber(meta, "offset", jsonNumber(meta, "offsetMillis", 0)).longValue(); chart.level = jsonNumber(meta, "level", 1).doubleValue();
        if (meta.has("initialArena") && meta.get("initialArena").isJsonPrimitive()) chart.initialArena = meta.get("initialArena").getAsString();
         if ((chart.charter == null || chart.charter.equals("Unknown")) && meta.has("charters") && meta.get("charters").isJsonArray() && meta.getAsJsonArray("charters").size() > 0) chart.charter = meta.getAsJsonArray("charters").get(0).getAsString();
        if (meta.has("bpms") && meta.get("bpms").isJsonArray()) for (JsonElement valueElement : meta.getAsJsonArray("bpms")) {
            if (!valueElement.isJsonObject()) continue;
            JsonObject value = valueElement.getAsJsonObject();
            double beat = jsonNumber(value, "beat", 0).doubleValue();
            double bpm = jsonNumber(value, "bpm", 120).doubleValue();
            if (bpm > 0) chart.bpms.add(new ChartManifest.BpmEvent(beat, bpm));
        }
        chart.bpms = ChartTiming.sorted(chart.bpms, 120);
        chart.bpm = chart.bpms.get(0).bpm;
        ChartTiming.Prepared timing = ChartTiming.prepare(chart);
        if (root.has("tracks") && root.get("tracks").isJsonArray()) for (JsonElement trackElement : root.getAsJsonArray("tracks")) {
            if (!trackElement.isJsonObject()) continue;
            importRhythmc3Track(chart, timing, trackElement.getAsJsonObject());
        }
        if (chart.tracks.isEmpty()) chart.tracks.add(new ChartManifest.Track(0));
        ChartManifest.Track primary = chart.tracks.stream().filter(track -> track.id == 0).findFirst().orElse(chart.tracks.get(0));
        chart.speedEvents = primary.speedEvents.stream().map(event -> new ChartManifest.SpeedEvent(event.startBeat, event.endBeat, event.startValue, event.endValue, event.easingType)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (root.has("effects") && root.get("effects").isJsonArray()) for (JsonElement effect : root.getAsJsonArray("effects")) if (effect.isJsonObject()) chart.effects.add(effect.getAsJsonObject().deepCopy());
        create(server, chart, stagedAudio); return chart;
    }
    private static int readDivisionsPerChunk(JsonObject meta) {
        int divisions = jsonNumber(meta, "divisionsPerChunk", jsonNumber(meta, "beatsPerChunk", jsonNumber(meta, "divisions", 4))).intValue();
        return Math.max(1, Math.min(32, divisions));
    }
    private static void importRhythmc3Track(ChartManifest chart, ChartTiming.Prepared timing, JsonObject source) {
        ChartManifest.Track track = new ChartManifest.Track(jsonNumber(source, "id", 0).intValue());
        track.speedEvents = readNumEvents(source, "speedEvents");
        track.xTransformEvents = readNumEvents(source, "xTransformEvents");
        track.yTransformEvents = readNumEvents(source, "yTransformEvents");
        track.zTransformEvents = readNumEvents(source, "zTransformEvents");
        track.xRotateEvents = readNumEvents(source, "xRotateEvents");
        track.yRotateEvents = readNumEvents(source, "yRotateEvents");
        track.zRotateEvents = readNumEvents(source, "zRotateEvents");
        track.xScaleEvents = readNumEvents(source, "xScaleEvents");
        track.yScaleEvents = readNumEvents(source, "yScaleEvents");
        track.zScaleEvents = readNumEvents(source, "zScaleEvents");
        chart.tracks.add(track);
        if (!source.has("notes") || !source.get("notes").isJsonArray()) return;
        for (JsonElement sourceElement : source.getAsJsonArray("notes")) {
            if (!sourceElement.isJsonObject()) continue;
            JsonObject noteSource = sourceElement.getAsJsonObject();
            double beat = jsonNumber(noteSource, "beat", 0).doubleValue();
            if (!Double.isFinite(beat)) continue;
            ChartManifest.Note note = new ChartManifest.Note();
            note.id = UUID.randomUUID().toString(); note.trackId = track.id; note.type = jsonNumber(noteSource, "noteType", 0).intValue();
            note.beat = beat; note.time = timing.beatToSeconds(beat); note.holdGroup = jsonNumber(noteSource, "holdGroup", -1).intValue();
            readVector(noteSource, "pos", values -> { note.sourceX = values[0]; note.sourceY = values[1]; note.sourceZ = values[2]; note.x = values[0]; note.y = values[1] + 66; });
            readVector(noteSource, "scale", values -> { note.scaleX = values[0]; note.scaleY = values[1]; note.scaleZ = values[2]; });
            readVector(noteSource, "rotation", values -> { note.rotationX = values[0]; note.rotationY = values[1]; note.rotationZ = values[2]; });
            note.z = PlaybackCoordinates.editorWorldZAtBeat(chart, note.beat); chart.notes.add(note); chart.totalBeats = Math.max(chart.totalBeats, note.beat);
        }
    }
    private interface VectorConsumer { void accept(double[] values); }
    private static void readVector(JsonObject object, String name, VectorConsumer consumer) {
        double[] values = {0, 0, 0}; if ("scale".equals(name)) java.util.Arrays.fill(values, 1.0);
        if (object.has(name) && object.get(name).isJsonArray()) { JsonArray array = object.getAsJsonArray(name); for (int index = 0; index < Math.min(3, array.size()); index++) try { values[index] = array.get(index).getAsDouble(); } catch (RuntimeException ignored) {} }
        consumer.accept(values);
    }
    private static List<ChartManifest.NumEvent> readNumEvents(JsonObject source, String key) {
        List<ChartManifest.NumEvent> result = new ArrayList<>(); if (!source.has(key) || !source.get(key).isJsonArray()) return result;
        for (JsonElement element : source.getAsJsonArray(key)) if (element.isJsonObject()) { JsonObject event = element.getAsJsonObject(); double start = jsonNumber(event, "startBeat", 0).doubleValue(), end = jsonNumber(event, "endBeat", start).doubleValue(), startValue = jsonNumber(event, "startValue", 0).doubleValue(), endValue = jsonNumber(event, "endValue", startValue).doubleValue(); if (Double.isFinite(start) && Double.isFinite(end) && Double.isFinite(startValue) && Double.isFinite(endValue)) result.add(new ChartManifest.NumEvent(start, end, startValue, endValue, jsonNumber(event, "easingType", jsonNumber(event, "easing", 0)).intValue())); }
        return result;
    }
    private static JsonObject convertRhythmc2Effect(JsonObject old, double bpm, int divisions) {
        String legacy = firstJsonString(old, "effect-type", "eventType", firstJsonString(old, "type", "", "")).toUpperCase(java.util.Locale.ROOT).replace(" ", "");
        if (legacy.equals("SPEED") || legacy.equals("JUDGEDOT")) return null;
        String type = switch (legacy) { case "REMOVEHOLOGRAM" -> "REMOVE_HOLOGRAM"; case "INVERT" -> "EFFECT"; case "CLREFFECT" -> "CLEAR_EFFECT"; case "COLOR" -> "GLOW_COLOR"; case "VISIBLE" -> "HIDE_NOTES"; case "TEXT" -> "TEXT_DISPLAY"; case "TRANSFORMATIONS" -> "TEXT_DISPLAY_EFFECT"; default -> legacy; };
        if (type.isBlank()) return null;
        JsonObject converted = new JsonObject(); converted.addProperty("eventType", type);
        double tick = jsonNumber(old, "start-tick", jsonNumber(old, "beat", 0)).doubleValue(); converted.addProperty("beat", snapLegacyBeat(Math.max(0.0, tick / 20.0) * bpm / 60.0, divisions));
        JsonObject properties = old.deepCopy(); properties.remove("effect-type"); properties.remove("start-tick"); properties.remove("beat");
        if (legacy.equals("INVERT")) properties.addProperty("type", "BLINDNESS");
        if (legacy.equals("VISIBLE")) properties.addProperty("hidden", !old.has("visible") || !old.get("visible").getAsBoolean());
        converted.add("properties", properties); return converted;
    }
    private static int oldNoteType(String type) {
        return switch (type == null ? "" : type.toUpperCase(java.util.Locale.ROOT)) {
            case "NOTE_LOOK" -> 1;
            case "NOTE_HOLD" -> 2;
            case "NOTE_DO_NOT_CLICK" -> 3;
            default -> 0;
        };
    }
    private static String firstJsonString(JsonObject object, String first, String second, String fallback) {
        if (object.has(first) && object.get(first).isJsonPrimitive() && !object.get(first).getAsString().isBlank()) return object.get(first).getAsString();
        if (object.has(second) && object.get(second).isJsonPrimitive() && !object.get(second).getAsString().isBlank()) return object.get(second).getAsString();
        return fallback;
    }
    private static String stripExtension(String name) { int dot = name.lastIndexOf('.'); return dot > 0 ? name.substring(0, dot) : name; }
    private static String coverItemId(String value) {
        if (value == null || value.isBlank()) return "minecraft:note_block";
        String normalized = value.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
    private static String readChartText(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length >= 4 && (bytes[0] & 255) == 0x28 && (bytes[1] & 255) == 0xB5 && (bytes[2] & 255) == 0x2F && (bytes[3] & 255) == 0xFD) {
            try (InputStream input = new ZstdInputStream(Files.newInputStream(file))) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYaml(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            Object value = new Yaml().load(input);
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (RuntimeException exception) { throw new IOException("无法解析 YAML：" + file.getFileName(), exception); }
    }
    private static String yamlString(Map<String, Object> values, String key, String fallback) { Object value = values.get(key); return value == null || value.toString().isBlank() ? fallback : value.toString(); }
    private static String sceneKey(Path file, String fallback) {
        try { return yamlString(readYaml(file), "name", fallback); } catch (IOException ignored) { return fallback; }
    }
    private static Number jsonNumber(JsonObject object, String key, Number fallback) { try { return object.has(key) ? object.get(key).getAsDouble() : fallback; } catch (RuntimeException ignored) { return fallback; } }
    private static String difficultyFromFile(Path file) { String name = file.getFileName().toString().toLowerCase(); return name.startsWith("nether") ? "NR" : name.startsWith("end") ? "ED" : name.startsWith("void") ? "VO" : "WD"; }
    private static double snapLegacyBeat(double beat, int divisionsPerChunk) {
        if (!Double.isFinite(beat)) return 0.0;
        int divisions = Math.max(1, Math.min(32, divisionsPerChunk));
        return Math.max(0.0, Math.rint(beat * divisions) / divisions);
    }

    private static int calculateImportedTrackLength(ChartManifest chart) {
        double beats = chart.durationSeconds > 0 && chart.bpm > 0 ? ChartTiming.secondsToBeat(chart, chart.durationSeconds) : 8.0;
        chart.totalBeats = beats;
        chart.chunkCount = Math.max(1, (int) Math.ceil(beats));
        return Math.max(8, chart.chunkCount * Math.max(1, Math.min(32, chart.divisionsPerChunk)));
    }

    public static void create(MinecraftServer server, ChartManifest chart, Path stagedAudio) throws IOException {
        if (chart.id == null || chart.id.isBlank()) chart.id = UUID.randomUUID().toString();
        if (chart.title == null || chart.title.isBlank()) throw new IOException("歌曲名称不能为空");
        if (chart.coverItemId == null || chart.coverItemId.isBlank()) throw new IOException("必须选择曲绘物品");
        if (chart.difficulty == null || chart.difficulty.isBlank()) throw new IOException("必须选择难度");
        if (chart.bpm <= 0) throw new IOException("BPM 必须大于 0");
        if (chart.level < 0) throw new IOException("定数不能小于 0");
        if (stagedAudio == null || !Files.exists(stagedAudio)) throw new IOException("必须上传歌曲文件");

        String extension = extensionOf(stagedAudio.getFileName().toString());
        chart.durationSeconds = readAudioDuration(stagedAudio);
        chart.audioFile = chart.id + "." + extension;
        chart.lastEdited = "";
        Files.move(stagedAudio, audioDirectory(server).resolve(chart.audioFile), StandardCopyOption.REPLACE_EXISTING);
        writeManifest(manifestPath(server, chart.id), chart);
        writeChartDocument(chartDocumentPath(server, chart.id), chart);
        Files.createDirectories(effectScenesDirectory(server, chart.id));
    }

    public static Path effectScenesDirectory(MinecraftServer server, String chartId) throws IOException {
        if (chartId == null || chartId.isBlank()) throw new IOException("谱面编号无效");
        Path rootDirectory = root(server).resolve("effect-scenes").toAbsolutePath().normalize();
        Path directory = rootDirectory.resolve(chartId).normalize();
        if (!directory.startsWith(rootDirectory)) throw new IOException("特效场景目录无效");
        Files.createDirectories(directory);
        return directory;
    }
    private static double readAudioDuration(Path audio) throws IOException {
        Path executable = extractFfprobe();
        Process process = new ProcessBuilder(executable.toString(), "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", audio.toAbsolutePath().toString())
                .redirectErrorStream(true).start();
        String output;
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce("", (left, right) -> left + right + "\n");
        }
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("读取音频时长超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("读取音频时长被中断", exception);
        }
        if (process.exitValue() != 0) throw new IOException("FFmpeg 无法识别该音频文件");
        try {
            double duration = Double.parseDouble(output.trim());
            if (!Double.isFinite(duration) || duration <= 0) throw new NumberFormatException();
            return duration;
        } catch (NumberFormatException exception) {
            throw new IOException("无法读取音频时间长度");
        }
    }

    public static BpmDetector.TimingAnalysis analyzeTiming(Path audio) throws IOException {
        return BpmDetector.detect(extractFfprobe(), audio);
    }

    private static Path extractFfprobe() throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            throw new IOException("当前版本仅内置 Windows x64 音频解析组件");
        }
        String resource = "rhythmc_maker/native/windows-x86_64/ffprobe.exe";
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "rhythmc-maker", "native");
        Files.createDirectories(directory);
        Path executable = directory.resolve("ffprobe.exe");
        try (var input = ChartStorage.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Mod 内置 FFmpeg 组件缺失");
            byte[] bundled = input.readAllBytes();
            if (!Files.exists(executable) || Files.size(executable) != bundled.length) {
                Files.write(executable, bundled);
            }
        }
        executable.toFile().setExecutable(true);
        return executable;
    }
    public static void update(MinecraftServer server, ChartManifest chart) throws IOException {
        writeManifest(manifestPath(server, chart.id), chart);
        writeChartDocument(chartDocumentPath(server, chart.id), chart);
        Files.createDirectories(effectScenesDirectory(server, chart.id));
    }

    public static ChartManifest find(MinecraftServer server, String chartId) throws IOException {
        Path path = manifestPath(server, chartId);
        if (!Files.exists(path)) return null;
        return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), CHART_TYPE);
    }

    public static ChartManifest findByDimensionId(MinecraftServer server, String dimensionId) throws IOException {
        if (dimensionId == null || dimensionId.isBlank()) return null;
        for (ChartManifest chart : list(server)) {
            if (cn.frkovo.rhythmcmaker.ChartDimensionManager.stableDimensionId(chart.id).equals(dimensionId)) return chart;
        }
        return null;
    }

    public static void delete(MinecraftServer server, ChartManifest chart) throws IOException {
        if (chart == null || chart.id == null || chart.id.isBlank() || !chart.id.matches("[a-zA-Z0-9_-]+")) {
            throw new IOException("谱面编号无效");
        }
        Files.deleteIfExists(manifestPath(server, chart.id));
        Files.deleteIfExists(chartDocumentPath(server, chart.id));
        if (chart.audioFile != null && !chart.audioFile.isBlank()) {
            Path audioRoot = audioDirectory(server).toAbsolutePath().normalize();
            Path audioPath = audioRoot.resolve(chart.audioFile).normalize();
            if (audioPath.startsWith(audioRoot)) Files.deleteIfExists(audioPath);
        }
    }

    public static Path root(MinecraftServer server) throws IOException {
        Path root = server.getSavePath(WorldSavePath.ROOT).resolve(ROOT_FOLDER);
        Files.createDirectories(root);
        return root;
    }

    public static Path audioPath(MinecraftServer server, ChartManifest chart) throws IOException {
        if (chart == null || chart.audioFile == null || chart.audioFile.isBlank()) throw new IOException("audio missing");
        Path audioRoot = audioDirectory(server).toAbsolutePath().normalize();
        Path audioPath = audioRoot.resolve(chart.audioFile).normalize();
        if (!audioPath.startsWith(audioRoot)) throw new IOException("invalid audio path");
        return audioPath;
    }

    private static Path manifestsDirectory(MinecraftServer server) throws IOException {
        Path directory = root(server).resolve("manifests");
        Files.createDirectories(directory);
        return directory;
    }

    private static Path audioDirectory(MinecraftServer server) throws IOException {
        Path directory = root(server).resolve("audio");
        Files.createDirectories(directory);
        return directory;
    }

    private static Path importsDirectory(MinecraftServer server) throws IOException {
        Path directory = root(server).resolve("imports");
        Files.createDirectories(directory);
        return directory;
    }

    private static Path chartDirectory(MinecraftServer server) throws IOException {
        Path directory = root(server).resolve("charts");
        Files.createDirectories(directory);
        return directory;
    }

    private static Path manifestPath(MinecraftServer server, String chartId) throws IOException {
        return manifestsDirectory(server).resolve(chartId + ".json");
    }

    private static Path chartDocumentPath(MinecraftServer server, String chartId) throws IOException {
        return chartDirectory(server).resolve(chartId + ".rmcc");
    }

    private static void readManifest(Path path, List<ChartManifest> charts) {
        try {
            ChartManifest chart = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), CHART_TYPE);
            if (chart != null && chart.id != null) charts.add(chart);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static void writeManifest(Path path, ChartManifest chart) throws IOException {
        Files.writeString(path, GSON.toJson(chart), StandardCharsets.UTF_8);
    }

    private static void writeChartDocument(Path path, ChartManifest chart) throws IOException {
        String document = GSON.toJson(new ChartDocument(chart));
        Files.writeString(path, document, StandardCharsets.UTF_8);
    }

    public static boolean isSupportedAudioExtension(String extension) {
        return switch (extension.toLowerCase()) {
            case "mp3", "flac", "wav", "ogg", "m4a", "aac" -> true;
            default -> false;
        };
    }

    private static String extensionOf(String filename) throws IOException {
        int index = filename.lastIndexOf('.');
        if (index < 1 || index == filename.length() - 1) throw new IOException("文件没有可用扩展名");
        return filename.substring(index + 1).toLowerCase();
    }

    private record ChartDocument(ChartManifest meta, List<ChartManifest.Note> tracks, List<com.google.gson.JsonObject> effects) {
        private ChartDocument(ChartManifest meta) {
            this(meta, meta.notes == null ? List.of() : meta.notes, meta.effects == null ? List.of() : meta.effects);
        }
    }
}




