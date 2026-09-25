package cn.frkovo.rhythmcmaker.chart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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

    public static ChartManifest importRhythmc3(MinecraftServer server, Path difficultyFile, Path manifestFile, Path stagedAudio) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(difficultyFile, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject meta = root.getAsJsonObject("meta");
        if (meta == null) throw new IOException("3.0 难度文件缺少 meta");
        ChartManifest chart = new ChartManifest();
        chart.id = UUID.randomUUID().toString(); chart.title = yamlValue(manifestFile, "name", "未命名谱面"); chart.artist = yamlValue(manifestFile, "composer", "Unknown"); chart.coverItemId = "minecraft:note_block"; chart.difficulty = difficultyFromFile(difficultyFile); chart.divisionsPerChunk = 4;
        chart.bpm = meta.has("bpms") && meta.getAsJsonArray("bpms").size() > 0 ? meta.getAsJsonArray("bpms").get(0).getAsJsonObject().get("bpm").getAsDouble() : 120.0; chart.offsetMillis = meta.has("offset") ? meta.get("offset").getAsLong() : 0L; chart.level = meta.has("level") ? meta.get("level").getAsDouble() : 1.0;
        if (root.has("tracks")) for (JsonElement trackElement : root.getAsJsonArray("tracks")) { JsonObject track = trackElement.getAsJsonObject(); if (!track.has("notes")) continue; for (JsonElement sourceElement : track.getAsJsonArray("notes")) { JsonObject source = sourceElement.getAsJsonObject(); ChartManifest.Note note = new ChartManifest.Note(); note.id = UUID.randomUUID().toString(); note.type = source.has("noteType") ? source.get("noteType").getAsInt() : 0; note.beat = source.has("beat") ? source.get("beat").getAsDouble() : 0; if (source.has("pos")) { JsonArray pos = source.getAsJsonArray("pos"); note.x = pos.size() > 0 ? pos.get(0).getAsDouble() : 0; note.y = (pos.size() > 1 ? pos.get(1).getAsDouble() : 0) + 66; note.z = pos.size() > 2 ? pos.get(2).getAsDouble() : 0; } chart.notes.add(note); chart.totalBeats = Math.max(chart.totalBeats, note.beat); } }
        if (root.has("effects") && root.get("effects").isJsonArray()) for (JsonElement effect : root.getAsJsonArray("effects")) if (effect.isJsonObject()) chart.effects.add(effect.getAsJsonObject().deepCopy());
        create(server, chart, stagedAudio); return chart;
    }
    private static String yamlValue(Path file, String key, String fallback) throws IOException { for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) if (line.startsWith(key + ":")) return line.substring(key.length() + 1).trim().replace("\"", ""); return fallback; }
    private static String difficultyFromFile(Path file) { String name = file.getFileName().toString().toLowerCase(); return name.startsWith("nether") ? "NR" : name.startsWith("end") ? "ED" : name.startsWith("void") ? "VO" : "WD"; }
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

    public static ChartManifest findByEditorSlot(MinecraftServer server, int editorSlot) throws IOException {
        if (editorSlot < 1) return null;
        for (ChartManifest chart : list(server)) {
            if (chart.editorSlot == editorSlot) return chart;
        }
        return null;
    }

    public static ChartManifest findByDimensionId(MinecraftServer server, String dimensionId) throws IOException {
        if (dimensionId == null || dimensionId.isBlank()) return null;
        for (ChartManifest chart : list(server)) {
            if (dimensionId.equals(chart.dimensionId)) return chart;
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

