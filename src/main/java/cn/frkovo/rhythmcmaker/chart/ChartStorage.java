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
import java.nio.file.AtomicMoveNotSupportedException;
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
        chart.divisionsPerChunk = RhythmcMaker.defaultDivisionsPerChunk();
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
                double frameBeat = snapImportedBeat(frameSeconds * bpm / 60.0);
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
                        note.beat = snapImportedBeat(noteSeconds * bpm / 60.0);
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
                        if (type == 2) {
                            note.sourceY = -1.0;
                            note.y = 65.0;
                        } else {
                            note.y = (note.sourceY == null ? 0.0 : note.sourceY) + 66.0;
                        }
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
                    if (Double.isFinite(startTick)) effect.addProperty("beat", snapImportedBeat(Math.max(0.0, startTick / 20.0) * bpm / 60.0));
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
    }

    public static ChartManifest importRhythmc3(MinecraftServer server, Path difficultyFile, Path manifestFile, Path stagedAudio, List<ImportedScene> scenes) throws IOException {
        return importRhythmc3(server, difficultyFile, manifestFile, null, stagedAudio, scenes);
    }

    public static ChartManifest importRhythmc3(MinecraftServer server, Path difficultyFile, Path manifestFile, Path layoutFile, Path stagedAudio, List<ImportedScene> scenes) throws IOException {
        ChartManifest chart = importRhythmc3(server, difficultyFile, manifestFile, layoutFile, stagedAudio);
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
        return importRhythmc3(server, difficultyFile, manifestFile, null, stagedAudio);
    }

    private static ChartManifest importRhythmc3(MinecraftServer server, Path difficultyFile, Path manifestFile, Path layoutFile, Path stagedAudio) throws IOException {
        JsonObject root = JsonParser.parseString(readChartText(difficultyFile)).getAsJsonObject();
        JsonObject meta = root.getAsJsonObject("meta");
        if (meta == null) throw new IOException("3.0 难度文件缺少 meta");
        ChartManifest chart = new ChartManifest();
        Map<String, Object> manifest = readYaml(manifestFile);
        chart.id = UUID.randomUUID().toString(); chart.title = yamlString(manifest, "name", "未命名谱面"); chart.artist = yamlString(manifest, "composer", "Unknown"); chart.charter = yamlString(manifest, "charter", yamlString(manifest, "author", "Unknown")); chart.coverItemId = coverItemId(yamlString(manifest, "icon", yamlString(manifest, "cover", "minecraft:note_block"))); chart.difficulty = difficultyFromFile(difficultyFile);
        readEditorTrackLayout(meta, difficultyFile, layoutFile).applyTo(chart);
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
    private static EditorTrackLayout readEditorTrackLayout(JsonObject meta, Path difficultyFile, Path selectedLayoutFile) throws IOException {
        if (selectedLayoutFile != null) {
            if (!Files.isRegularFile(selectedLayoutFile)) throw new IOException("制谱器布局文件不存在：" + selectedLayoutFile.getFileName());
            return EditorTrackLayoutFile.read(selectedLayoutFile);
        }
        Path siblingLayoutFile = difficultyFile.resolveSibling(EditorTrackLayoutFile.FILE_NAME);
        if (Files.isRegularFile(siblingLayoutFile)) return EditorTrackLayoutFile.read(siblingLayoutFile);
        int divisionsPerChunk = jsonNumber(meta, "divisionsPerChunk", jsonNumber(meta, "beatsPerChunk", jsonNumber(meta, "divisions", RhythmcMaker.defaultDivisionsPerChunk()))).intValue();
        return EditorTrackLayout.of(EditorTrackLayout.DEFAULT_LANE_COUNT, divisionsPerChunk);
    }
    private static void importRhythmc3Track(ChartManifest chart, ChartTiming.Prepared timing, JsonObject source) {
        ChartManifest.Track track = new ChartManifest.Track(jsonNumber(source, "id", 0).intValue());
        for (TrackEventChannel channel : TrackEventChannel.values()) {
            channel.setEvents(track, readNumEvents(source, channel.jsonKey()));
        }
        chart.tracks.add(track);
        if (!source.has("notes") || !source.get("notes").isJsonArray()) return;
        for (JsonElement sourceElement : source.getAsJsonArray("notes")) {
            if (!sourceElement.isJsonObject()) continue;
            JsonObject noteSource = sourceElement.getAsJsonObject();
            double beat = jsonNumber(noteSource, "beat", 0).doubleValue();
            if (!Double.isFinite(beat)) continue;
            beat = snapImportedBeat(beat);
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
        String legacy = firstJsonString(old, "effect-type", "eventType", firstJsonString(old, "effectType", "type", "")).toUpperCase(java.util.Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
        if (legacy.equals("SPEED") || legacy.equals("JUDGEDOT")) return null;
        String type = switch (legacy) { case "REMOVEHOLOGRAM" -> "REMOVE_HOLOGRAM"; case "INVERT", "POTION", "POTIONEFFECT", "STATUSEFFECT" -> "EFFECT"; case "CLREFFECT" -> "CLEAR_EFFECT"; case "COLOR" -> "GLOW_COLOR"; case "VISIBLE" -> "HIDE_NOTES"; case "TEXT" -> "TEXT_DISPLAY"; case "TRANSFORMATIONS" -> "TEXT_DISPLAY_EFFECT"; case "CLIENTTIME", "SETCLIENTTIME", "TIMECLIENT", "WORLDTIME", "SETWORLDTIME", "SETTIME" -> "TIME"; default -> legacy; };
        if (type.isBlank()) return null;
        JsonObject converted = new JsonObject(); converted.addProperty("eventType", type);
        double tick = jsonNumber(old, "start-tick", jsonNumber(old, "beat", 0)).doubleValue(); converted.addProperty("beat", snapImportedBeat(Math.max(0.0, tick / 20.0) * bpm / 60.0));
        JsonObject properties = old.has("properties") && old.get("properties").isJsonObject()
                ? old.getAsJsonObject("properties").deepCopy() : new JsonObject();
        for (var entry : old.entrySet()) if (!entry.getKey().equals("properties")) properties.add(entry.getKey(), entry.getValue().deepCopy());
        properties.remove("effect-type"); properties.remove("start-tick"); properties.remove("beat");
        properties.remove("eventType"); properties.remove("effectType");
        if (type.equals("HOLOGRAM")) convertRhythmc2Hologram(properties, old);
        if (type.equals("TEXT_DISPLAY")) convertRhythmc2TextDisplay(properties, old);
        if (type.equals("TEXT_DISPLAY_EFFECT")) {
            String transformation = firstJsonString(old, "type", "transformation", firstJsonString(properties, "type", "transformation", "LINEAR_TRANSFORMATION"));
            if (transformation.equalsIgnoreCase("REMOVE")) {
                converted.addProperty("eventType", "TEXT_DISPLAY_REMOVE");
            } else {
                convertRhythmc2TextDisplayTransformation(properties, old, transformation);
            }
        }
        if (legacy.equals("INVERT")) properties.addProperty("type", "BLINDNESS");
        if (type.equals("EFFECT") && !properties.has("type")) {
            String potion = firstJsonString(old, "effect", "potion", firstJsonString(old, "effect-id", "potion-effect", ""));
            if (potion.isBlank()) potion = firstJsonString(properties, "effect", "potion", firstJsonString(properties, "effect-id", "potion-effect", "UNKNOWN"));
            properties.addProperty("type", normalizePotionEffectId(potion));
        }
        if (type.equals("EFFECT") && properties.has("type")) properties.addProperty("type", normalizePotionEffectId(properties.get("type").getAsString()));
        if (type.equals("EFFECT")) {
            JsonElement duration = properties.has("durationTicks") ? properties.get("durationTicks") : properties.get("duration");
            if (duration != null && !duration.isJsonNull()) {
                properties.addProperty("durationTicks", jsonNumber(properties, properties.has("durationTicks") ? "durationTicks" : "duration", 100).intValue());
                properties.remove("duration");
            }
        }
        if (type.equals("CLEAR_EFFECT")) {
            String effectId = firstJsonString(old, "effect-id", "effect", firstJsonString(properties, "effect-id", "effect", ""));
            if (!effectId.isBlank()) properties.addProperty("type", normalizePotionEffectId(effectId));
        }
        if (type.equals("TIME") && (legacy.contains("CLIENTTIME") || legacy.contains("TIMECLIENT"))) properties.addProperty("client", true);
        if (type.equals("TIME") && (legacy.contains("WORLDTIME") || legacy.equals("SETTIME"))) properties.addProperty("client", false);
        if (type.equals("TIME") && properties.has("duration") && !properties.get("duration").isJsonNull())
            properties.addProperty("duration", jsonNumber(properties, "duration", 0).doubleValue() * 50.0);
        if (type.equals("TIME") && !properties.has("time")) {
            Number time = jsonNumber(properties, "time-of-day", jsonNumber(properties, "world-time", jsonNumber(properties, "client-time", jsonNumber(properties, "clientTime", jsonNumber(properties, "value", 1000)))));
            properties.addProperty("time", time);
        }
        if (legacy.equals("VISIBLE")) properties.addProperty("hidden", !old.has("visible") || !old.get("visible").getAsBoolean());
        converted.add("properties", properties); return converted;
    }
    private static void convertRhythmc2Hologram(JsonObject properties, JsonObject old) {
        JsonElement legacyLocation = firstJsonElement(old, properties, "hologram-loc");
        JsonElement sourceLocation = legacyLocation != null ? legacyLocation : firstJsonElement(old, properties, "location", "loc", "position");
        JsonArray location = vectorArray(sourceLocation, 0.0, 1.5, 0.0);
        if (legacyLocation != null) location.set(1, new com.google.gson.JsonPrimitive(location.get(1).getAsDouble() + 0.5));
        properties.add("location", location);
        String id = firstJsonString(old, "id", "displayId", firstJsonString(properties, "id", "displayId", ""));
        properties.addProperty("id", id.isBlank() ? "RhyMCGameHologram_" + UUID.randomUUID().toString().replace("-", "") : id);
        JsonElement sourceContents = firstJsonElement(old, properties, "hologram-contents", "contents");
        properties.add("contents", stringArray(sourceContents));
        double durationTicks = jsonNumber(old, "duration", jsonNumber(properties, "duration", 0)).doubleValue();
        properties.addProperty("duration", durationTicks > 0.0 ? durationTicks * 50.0 : 31_536_000_000L);
    }
    private static void convertRhythmc2TextDisplay(JsonObject properties, JsonObject old) {
        String id = firstJsonString(old, "id", "displayId", firstJsonString(properties, "id", "displayId", ""));
        properties.addProperty("id", id.isBlank() ? "text_" + UUID.randomUUID().toString().replace("-", "") : id);
        JsonElement text = firstJsonElement(old, properties, "text", "content");
        properties.add("text", text == null ? new com.google.gson.JsonPrimitive("") : text.deepCopy());
        properties.add("position", vectorArray(firstJsonElement(old, properties, "position", "loc", "location"), 0.0, 1.5, 0.0));
        properties.add("rotation", vectorArray(firstJsonElement(old, properties, "rotation"), 0.0, 0.0, 0.0));
        properties.add("scale", vectorArray(firstJsonElement(old, properties, "scale"), 1.0, 1.0, 1.0));
        if (!properties.has("color") || properties.get("color").isJsonNull()) properties.addProperty("color", "WHITE");
    }
    private static void convertRhythmc2TextDisplayTransformation(JsonObject properties, JsonObject old, String transformation) {
        String id = firstJsonString(old, "id", "displayId", firstJsonString(properties, "id", "displayId", ""));
        properties.addProperty("id", id);
        String normalized = transformation == null || transformation.isBlank() ? "LINEAR_TRANSFORMATION" : transformation.toUpperCase(java.util.Locale.ROOT);
        if (normalized.equals("TRANSFORMATION")) normalized = "LINEAR_TRANSFORMATION";
        properties.addProperty("type", normalized);
        switch (normalized) {
            case "BACKGROUND_COLOR" -> {
                JsonElement color = firstJsonElement(old, properties, "color", "backgroundColor");
                if (color != null) properties.add("color", color.deepCopy());
            }
            case "SHADOW" -> properties.addProperty("shadowed", jsonBoolean(old, properties, "shadowed", "shadow", true));
            case "GLOWING" -> properties.addProperty("glowing", jsonBoolean(old, properties, "glowing", "glow", true));
            case "TEXT" -> {
                JsonElement text = firstJsonElement(old, properties, "text", "content");
                if (text != null) properties.add("text", text.deepCopy());
                if (!properties.has("color") || properties.get("color").isJsonNull()) properties.addProperty("color", "WHITE");
            }
            case "OPACITY" -> {
                JsonElement opacity = firstJsonElement(old, properties, "targetOpacity", "opacity");
                if (opacity == null) opacity = firstArrayElement(firstJsonElement(old, properties, "to"), 0);
                if (opacity != null) properties.add("targetOpacity", opacity.deepCopy());
            }
            case "LINEAR_TRANSFORMATION" -> {
                properties.add("position", vectorArray(firstJsonElement(old, properties, "position", "to"), 0.0, 0.0, 0.0));
                properties.add("rotation", rotationArray(firstJsonElement(old, properties, "rotation"), firstJsonElement(old, properties, "rotate")));
                properties.add("scale", vectorArray(firstJsonElement(old, properties, "scale"), 1.0, 1.0, 1.0));
        if (!properties.has("color") || properties.get("color").isJsonNull()) properties.addProperty("color", "WHITE");
            }
            default -> properties.addProperty("type", "LINEAR_TRANSFORMATION");
        }
        double durationTicks = jsonNumber(old, "duration", jsonNumber(properties, "duration", 0)).doubleValue();
        if (durationTicks > 0.0) properties.addProperty("duration", durationTicks * 50.0);
    }
    private static JsonElement firstJsonElement(JsonObject first, JsonObject second, String... keys) {
        for (String key : keys) {
            if (first != null && first.has(key)) return first.get(key);
            if (second != null && second.has(key)) return second.get(key);
        }
        return null;
    }
    private static JsonElement firstArrayElement(JsonElement element, int index) {
        return element != null && element.isJsonArray() && element.getAsJsonArray().size() > index ? element.getAsJsonArray().get(index) : null;
    }
    private static JsonArray vectorArray(JsonElement source, double defaultX, double defaultY, double defaultZ) {
        JsonArray result = new JsonArray(); result.add(defaultX); result.add(defaultY); result.add(defaultZ);
        if (source == null) return result;
        if (source.isJsonPrimitive() && source.getAsJsonPrimitive().isNumber()) {
            double value = source.getAsDouble(); result.set(0, new com.google.gson.JsonPrimitive(value)); result.set(1, new com.google.gson.JsonPrimitive(value)); result.set(2, new com.google.gson.JsonPrimitive(value)); return result;
        }
        if (!source.isJsonArray()) return result;
        JsonArray sourceArray = source.getAsJsonArray();
        for (int index = 0; index < Math.min(3, sourceArray.size()); index++) if (sourceArray.get(index).isJsonPrimitive() && sourceArray.get(index).getAsJsonPrimitive().isNumber()) result.set(index, sourceArray.get(index).deepCopy());
        return result;
    }
    private static JsonArray rotationArray(JsonElement rotation, JsonElement rotate) {
        if (rotation != null) return vectorArray(rotation, 0.0, 0.0, 0.0);
        JsonArray result = new JsonArray(); result.add(0.0); result.add(0.0); result.add(rotate != null && rotate.isJsonPrimitive() && rotate.getAsJsonPrimitive().isNumber() ? rotate.getAsDouble() : 0.0); return result;
    }
    private static JsonArray stringArray(JsonElement source) {
        JsonArray result = new JsonArray();
        if (source == null) return result;
        if (source.isJsonPrimitive()) { result.add(source.getAsString()); return result; }
        if (!source.isJsonArray()) return result;
        for (JsonElement value : source.getAsJsonArray()) if (value.isJsonPrimitive()) result.add(value.getAsString());
        return result;
    }
    private static boolean jsonBoolean(JsonObject first, JsonObject second, String firstKey, String secondKey, boolean fallback) {
        JsonElement value = firstJsonElement(first, second, firstKey, secondKey);
        try { return value != null ? value.getAsBoolean() : fallback; } catch (RuntimeException ignored) { return fallback; }
    }
    private static String normalizePotionEffectId(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.contains(":")) normalized = normalized.substring(normalized.indexOf(':') + 1);
        return switch (normalized) {
            case "jump", "jump_boost", "jumpboost" -> "minecraft:jump_boost";
            case "slow", "slowness" -> "minecraft:slowness";
            case "nightvision", "night_vision" -> "minecraft:night_vision";
            case "invis", "invisible", "invisibility" -> "minecraft:invisibility";
            case "blind" -> "minecraft:blindness";
            case "dark" -> "minecraft:darkness";
            case "levitate" -> "minecraft:levitation";
            case "haste" -> "minecraft:haste";
            case "mining_fatigue", "miningfatigue" -> "minecraft:mining_fatigue";
            case "speed" -> "minecraft:speed";
            case "strength" -> "minecraft:strength";
            case "instant_health", "instanthealth" -> "minecraft:instant_health";
            case "instant_damage", "instantdamage" -> "minecraft:instant_damage";
            case "regeneration", "regen" -> "minecraft:regeneration";
            case "resistance" -> "minecraft:resistance";
            case "fire_resistance", "fireresistance" -> "minecraft:fire_resistance";
            case "water_breathing", "waterbreathing" -> "minecraft:water_breathing";
            case "absorption" -> "minecraft:absorption";
            case "saturation" -> "minecraft:saturation";
            case "health_boost", "healthboost" -> "minecraft:health_boost";
            case "glowing" -> "minecraft:glowing";
            case "hunger" -> "minecraft:hunger";
            case "weakness" -> "minecraft:weakness";
            case "poison" -> "minecraft:poison";
            case "wither" -> "minecraft:wither";
            case "conduit_power", "conduitpower" -> "minecraft:conduit_power";
            case "dolphins_grace", "dolphinsgrace" -> "minecraft:dolphins_grace";
            case "bad_omen", "badomen" -> "minecraft:bad_omen";
            case "hero_of_the_village", "hero_of_village", "heroofthevillage" -> "minecraft:hero_of_the_village";
            case "darkness" -> "minecraft:darkness";
            default -> value.contains(":") ? value.toLowerCase(java.util.Locale.ROOT) : "minecraft:" + normalized;
        };
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
    private static double snapImportedBeat(double beat) {
        if (!Double.isFinite(beat)) return 0.0;
        double target = Math.max(0.0, beat);
        double nearest = Math.rint(target);
        double smallestError = Math.abs(nearest - target);
        for (int denominator = 2; denominator <= 32; denominator++) {
            double candidate = Math.rint(target * denominator) / denominator;
            double error = Math.abs(candidate - target);
            if (error < smallestError) {
                nearest = candidate;
                smallestError = error;
            }
        }
        return nearest;
    }

    private static int calculateImportedTrackLength(ChartManifest chart) {
        double beats = chart.durationSeconds > 0 && chart.bpm > 0 ? ChartTiming.secondsToBeat(chart, chart.durationSeconds) : 8.0;
        chart.totalBeats = beats;
        chart.chunkCount = Math.max(1, (int) Math.ceil(beats));
        return Math.max(8, chart.chunkCount * Math.max(1, Math.min(32, chart.divisionsPerChunk)));
    }

    private static void ensureDefaultTrackSpeed(ChartManifest chart) {
        if (chart.tracks == null) chart.tracks = new ArrayList<>();
        ChartManifest.Track defaultTrack = chart.tracks.stream()
                .filter(track -> track != null && track.id == 0)
                .findFirst()
                .orElseGet(() -> {
                    ChartManifest.Track created = new ChartManifest.Track(0);
                    chart.tracks.add(created);
                    return created;
                });
        double endBeat = Math.max(1.0, Math.max(chart.totalBeats,
                chart.durationSeconds > 0.0 ? ChartTiming.secondsToBeat(chart, chart.durationSeconds) : 0.0));
        for (ChartManifest.Track track : chart.tracks) {
            if (track == null) continue;
            if (track.speedEvents == null) track.speedEvents = new ArrayList<>();
            if (track.speedEvents.isEmpty()) track.speedEvents.add(new ChartManifest.NumEvent(0.0, endBeat, 1.0, 1.0, 0));
        }
        if (defaultTrack.speedEvents == null) defaultTrack.speedEvents = new ArrayList<>();
        chart.speedEvents = defaultTrack.speedEvents.stream()
                .map(event -> new ChartManifest.SpeedEvent(event.startBeat, event.endBeat, event.startValue, event.endValue, event.easingType))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public static void create(MinecraftServer server, ChartManifest chart, Path stagedAudio) throws IOException {
        if (chart.id == null || chart.id.isBlank()) chart.id = UUID.randomUUID().toString();
        if (chart.title == null || chart.title.isBlank()) throw new IOException("歌曲名称不能为空");
        if (chart.coverItemId == null || chart.coverItemId.isBlank()) throw new IOException("必须选择曲绘物品");
        if (chart.difficulty == null || chart.difficulty.isBlank()) throw new IOException("必须选择难度");
        if (chart.bpm <= 0) throw new IOException("BPM 必须大于 0");
        if (chart.level < 0) throw new IOException("定数不能小于 0");
        if (chart.divisionsPerChunk <= 0) chart.divisionsPerChunk = RhythmcMaker.defaultDivisionsPerChunk();
        EditorTrackLayout.of(chart).applyTo(chart);
        if (stagedAudio == null || !Files.exists(stagedAudio)) throw new IOException("必须上传歌曲文件");

        String extension = extensionOf(stagedAudio.getFileName().toString());
        chart.durationSeconds = readAudioDuration(stagedAudio);
        ensureDefaultTrackSpeed(chart);
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
        if (chart == null || chart.id == null || chart.id.isBlank() || !chart.id.matches("[a-zA-Z0-9_-]+")) {
            throw new IOException("谱面编号无效");
        }
        writeManifest(manifestPath(server, chart.id), chart);
        writeChartDocument(chartDocumentPath(server, chart.id), chart);
        Files.createDirectories(effectScenesDirectory(server, chart.id));
    }

    public static ChartManifest find(MinecraftServer server, String chartId) throws IOException {
        Path path = manifestPath(server, chartId);
        if (!Files.exists(path)) {
            Path backup = backupPath(path);
            if (!Files.exists(backup)) return null;
            try {
                return parseManifest(backup);
            } catch (IOException | RuntimeException exception) {
                throw chartReadException(path, exception, null);
            }
        }
        try {
            return parseManifest(path);
        } catch (IOException | RuntimeException exception) {
            Path backup = backupPath(path);
            if (!Files.exists(backup)) throw chartReadException(path, exception, null);
            try {
                ChartManifest recovered = parseManifest(backup);
                if (recovered == null) throw new IOException("备份谱面为空");
                return recovered;
            } catch (IOException | RuntimeException backupException) {
                throw chartReadException(path, exception, backupException);
            }
        }
    }

    public static ChartManifest findByDimensionId(MinecraftServer server, String dimensionId) throws IOException {
        if (dimensionId == null || dimensionId.isBlank()) return null;
        Path manifests = manifestsDirectory(server);
        try (var files = Files.list(manifests)) {
            for (Path path : files.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                String chartId = stripExtension(path.getFileName().toString());
                if (cn.frkovo.rhythmcmaker.ChartDimensionManager.stableDimensionId(chartId).equals(dimensionId)) return find(server, chartId);
            }
        }
        return null;
    }

    public static java.nio.file.attribute.BasicFileAttributes manifestAttributes(MinecraftServer server, String chartId) throws IOException {
        return Files.readAttributes(manifestPath(server, chartId), java.nio.file.attribute.BasicFileAttributes.class);
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
            ChartManifest chart = parseManifest(path);
            if (chart != null && chart.id != null) charts.add(chart);
        } catch (IOException | RuntimeException exception) {
            try {
                ChartManifest backup = parseManifest(backupPath(path));
                if (backup != null && backup.id != null) charts.add(backup);
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }

    private static void writeManifest(Path path, ChartManifest chart) throws IOException {
        writeJsonAtomically(path, GSON.toJson(chart));
    }

    private static void writeChartDocument(Path path, ChartManifest chart) throws IOException {
        String document = GSON.toJson(new ChartDocument(chart));
        writeJsonAtomically(path, document);
    }

    private static ChartManifest parseManifest(Path path) throws IOException {
        return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), CHART_TYPE);
    }

    private static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }

    private static IOException chartReadException(Path path, Exception primary, Exception backup) {
        String message = "谱面文件解析失败：" + path;
        if (primary.getMessage() != null && !primary.getMessage().isBlank()) message += "（" + primary.getMessage() + "）";
        if (backup != null) message += "；备份也无法恢复：" + (backup.getMessage() == null ? backup.getClass().getSimpleName() : backup.getMessage());
        return new IOException(message, primary);
    }

    private static void writeJsonAtomically(Path path, String json) throws IOException {
        JsonParser.parseString(json);
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            JsonParser.parseString(Files.readString(temporary, StandardCharsets.UTF_8));
            if (Files.exists(path)) Files.copy(path, backupPath(path), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (RuntimeException exception) {
            throw new IOException("谱面 JSON 校验失败：" + path, exception);
        } finally {
            Files.deleteIfExists(temporary);
        }
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

    private record ChartDocument(ChartManifest meta, List<ChartManifest.Track> tracks, List<ChartManifest.Note> notes, List<com.google.gson.JsonObject> effects) {
        private ChartDocument(ChartManifest meta) {
            this(meta, meta.tracks == null ? List.of() : meta.tracks, meta.notes == null ? List.of() : meta.notes, meta.effects == null ? List.of() : meta.effects);
        }
    }
}




