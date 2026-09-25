package cn.frkovo.rhythmcmaker.chart;

import cn.frkovo.rhythmcmaker.ChartDimensionManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Writes one Maker chart as a RhythMC 3.0-compatible song folder. */
public final class Rhythmc3Exporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCENE_MIN_X = 136;
    private static final int SCENE_MIN_Y = 1;
    private static final int SCENE_MIN_Z = -64;
    private static final int SCENE_SIZE = 128;

    private Rhythmc3Exporter() {
    }

    public static Path export(MinecraftServer server, ChartManifest chart) throws IOException {
        if (chart == null || chart.id == null || chart.id.isBlank()) throw new IOException("谱面不存在");
        ServerWorld world = server.getWorld(ChartDimensionManager.key(chart));
        if (world == null) throw new IOException("请先进入该谱面制谱维度，再进行导出");

        Path exportRoot = ChartStorage.root(server).resolve("exports").toAbsolutePath().normalize();
        String songFolderName = safeFolderName(chart);
        String arenaKey = safeArenaKey(chart);
        Path folder = exportRoot.resolve(songFolderName).normalize();
        if (!folder.startsWith(exportRoot)) throw new IOException("导出目录无效");
        replaceDirectory(folder);
        Files.createDirectories(folder);

        Path chartFolder = folder.resolve("Data").resolve("Charts").resolve(songFolderName).normalize();
        Path arenaFolder = folder.resolve("Data").resolve("Arenas").resolve(arenaKey).normalize();
        if (!chartFolder.startsWith(folder) || !arenaFolder.startsWith(folder)) throw new IOException("导出目录无效");
        Files.createDirectories(chartFolder);
        Files.createDirectories(arenaFolder);
        writeManifest(chartFolder, chart);
        writeChart(chartFolder, chart, arenaKey);
        writeScene(arenaFolder, world, chart, arenaKey);
        copyAudio(server, folder, chart);
        return folder;
    }

    private static void writeManifest(Path folder, ChartManifest chart) throws IOException {
        String icon = bukkitMaterial(chart.coverItemId);
        String yaml = "name: " + yaml(chart.title) + "\n"
            + "composer: " + yaml(chart.artist) + "\n"
            + "icon: " + icon + "\n"
            + "alias: \"\"\n"
            + "length: " + Math.max(0, Math.round(chart.durationSeconds * 1000.0)) + "\n"
            + "base_bpm: " + number(chart.bpm) + "\n"
            + "respack_sha1: \"\"\n"
            + "description: \"\"\n"
            + "song_id: " + positiveId(chart.id) + "\n"
            + "version: \"1.0\"\n"
            + "comments: []\n"
            + "player-alias: []\n"
            + "tags: []\n"
            + "unlockSong: []\n"
            + "unlockWorld: []\n"
            + "unlockNether: []\n"
            + "unlockVoid: []\n";
        Files.writeString(folder.resolve("manifest.yml"), yaml, StandardCharsets.UTF_8);
    }

    private static void writeChart(Path folder, ChartManifest chart, String arenaKey) throws IOException {
        double endBeat = Math.max(8.0, chart.totalBeats);
        if (chart.notes != null) for (ChartManifest.Note note : chart.notes) if (note != null) endBeat = Math.max(endBeat, note.beat + 8.0);
        JsonObject root = new JsonObject();
        JsonObject meta = new JsonObject();
        JsonArray charters = new JsonArray();
        charters.add(blank(chart.charter, "Unknown"));
        meta.add("charters", charters);
        meta.addProperty("level", chart.level);
        meta.addProperty("offset", chart.offsetMillis);
        meta.addProperty("uid", positiveId(chart.id) * 10 + difficultyIndex(chart.difficulty));
        meta.addProperty("initialArena", arenaKey);
        meta.add("comments", new JsonArray());
        JsonArray bpms = new JsonArray();
        JsonObject bpm = new JsonObject();
        bpm.addProperty("beat", 0.0);
        bpm.addProperty("bpm", chart.bpm);
        bpms.add(bpm);
        meta.add("bpms", bpms);
        root.add("meta", meta);

        JsonArray tracks = new JsonArray();
        JsonObject track = new JsonObject();
        track.addProperty("id", 0);
        track.add("speedEvents", speedEventList(chart, endBeat));
        track.add("xTransformEvents", eventList(0.0, endBeat, 0.0));
        track.add("yTransformEvents", eventList(0.0, endBeat, 0.0));
        track.add("zTransformEvents", eventList(0.0, endBeat, 0.0));
        track.add("xRotateEvents", eventList(0.0, endBeat, 0.0));
        track.add("yRotateEvents", eventList(0.0, endBeat, 0.0));
        track.add("zRotateEvents", eventList(0.0, endBeat, 0.0));
        track.add("xScaleEvents", eventList(0.0, endBeat, 1.0));
        track.add("yScaleEvents", eventList(0.0, endBeat, 1.0));
        track.add("zScaleEvents", eventList(0.0, endBeat, 1.0));
        JsonArray notes = new JsonArray();
        if (chart.notes != null) for (ChartManifest.Note note : chart.notes) {
            if (note == null) continue;
            JsonObject value = new JsonObject();
            value.addProperty("noteType", Math.max(0, Math.min(3, note.type)));
            value.addProperty("beat", note.beat);
            JsonArray position = new JsonArray(); position.add(note.x); position.add(note.y - 66.0); position.add(0.0); value.add("pos", position);
            JsonArray scale = new JsonArray(); scale.add(1.0); scale.add(1.0); scale.add(1.0); value.add("scale", scale);
            JsonArray rotation = new JsonArray(); rotation.add(0.0); rotation.add(0.0); rotation.add(0.0); value.add("rotation", rotation);
            if (note.type == 2) value.addProperty("holdGroup", -1);
            notes.add(value);
        }
        track.add("notes", notes);
        tracks.add(track);
        root.add("tracks", tracks);
        JsonArray effects = new JsonArray();
        if (chart.effects != null) for (JsonObject effect : chart.effects) if (effect != null) effects.add(effect.deepCopy());
        root.add("effects", effects);
        Files.writeString(folder.resolve(difficultyFile(chart.difficulty) + ".rmcc"), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static JsonArray speedEventList(ChartManifest chart, double endBeat) {
        JsonArray events = new JsonArray();
        if (chart.speedEvents == null || chart.speedEvents.isEmpty()) return eventList(0.0, endBeat, 1.0);
        for (ChartManifest.SpeedEvent event : PlaybackDistance.sortedEvents(chart.speedEvents)) {
            if (event == null) continue;
            JsonObject value = new JsonObject();
            value.addProperty("startBeat", event.startBeat);
            value.addProperty("endBeat", event.endBeat);
            value.addProperty("startValue", event.startValue);
            value.addProperty("endValue", event.endValue);
            value.addProperty("easing", event.easing);
            events.add(value);
        }
        return events.isEmpty() ? eventList(0.0, endBeat, 1.0) : events;
    }
    private static JsonArray eventList(double startBeat, double endBeat, double value) {
        JsonObject event = new JsonObject();
        event.addProperty("startBeat", startBeat); event.addProperty("endBeat", endBeat);
        event.addProperty("startValue", value); event.addProperty("endValue", value); event.addProperty("easing", 0);
        JsonArray events = new JsonArray(); events.add(event); return events;
    }

    private static void writeScene(Path sceneFolder, ServerWorld world, ChartManifest chart, String arenaKey) throws IOException {
        Files.createDirectories(sceneFolder);
        String yaml = "name: " + yaml(arenaKey) + "\n"
            + "display-name: " + yaml(chart.title + " 场景") + "\n"
            + "author: " + yaml(blank(chart.charter, "Unknown")) + "\n"
            + "description: \"\"\n"
            + "icon: " + bukkitMaterial(chart.coverItemId) + "\n"
            + "border: CYAN_CONCRETE\n"
            + "success: GREEN_CONCRETE\n"
            + "schematic: arena.schem\n"
            + "hide: false\n";
        Files.writeString(sceneFolder.resolve("metadata.yml"), yaml, StandardCharsets.UTF_8);

        Map<String, Integer> palette = new LinkedHashMap<>();
        ByteArrayOutputStream blockData = new ByteArrayOutputStream(SCENE_SIZE * SCENE_SIZE);
        for (int y = 0; y < SCENE_SIZE; y++) for (int z = 0; z < SCENE_SIZE; z++) for (int x = 0; x < SCENE_SIZE; x++) {
            BlockState state = world.getBlockState(new BlockPos(SCENE_MIN_X + x, SCENE_MIN_Y + y, SCENE_MIN_Z + z));
            String stateId = stateId(state);
            int paletteIndex = palette.computeIfAbsent(stateId, ignored -> palette.size());
            writeVarInt(blockData, paletteIndex);
        }
        NbtCompound paletteTag = new NbtCompound();
        for (Map.Entry<String, Integer> entry : palette.entrySet()) paletteTag.putInt(entry.getKey(), entry.getValue());
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 2);
        root.putInt("DataVersion", 0);
        root.putShort("Width", (short) SCENE_SIZE);
        root.putShort("Height", (short) SCENE_SIZE);
        root.putShort("Length", (short) SCENE_SIZE);
        root.putIntArray("Offset", new int[]{-64, -65, -64});
        root.put("Palette", paletteTag);
        root.putInt("PaletteMax", palette.size());
        root.putByteArray("BlockData", blockData.toByteArray());
        NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", safeFolderName(chart) + " scene");
        metadata.putString("Author", blank(chart.charter, "Unknown"));
        metadata.putString("Date", Long.toString(System.currentTimeMillis()));
        root.put("Metadata", metadata);
        NbtIo.writeCompressed(root, sceneFolder.resolve("arena.schem"));
    }

    private static void copyAudio(MinecraftServer server, Path folder, ChartManifest chart) throws IOException {
        Path source = ChartStorage.audioPath(server, chart);
        String filename = source.getFileName().toString();
        Files.copy(source, folder.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String stateId(BlockState state) {
        StringBuilder value = new StringBuilder(Registries.BLOCK.getId(state.getBlock()).toString());
        Collection<Property<?>> properties = state.getProperties();
        if (!properties.isEmpty()) {
            value.append('[');
            int index = 0;
            for (Property<?> property : properties) {
                if (index++ > 0) value.append(',');
                value.append(property.getName()).append('=').append(propertyValue(state, property));
            }
            value.append(']');
        }
        return value.toString();
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.name(state.get(property));
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7F) != 0) { output.write((value & 0x7F) | 0x80); value >>>= 7; }
        output.write(value);
    }

    private static void replaceDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.delete(path); } catch (IOException exception) { throw new ExportDeleteException(exception); } });
        } catch (ExportDeleteException exception) { throw exception.cause; }
    }

    private static String difficultyFile(String difficulty) { return switch (difficulty == null ? "" : difficulty) { case "NR" -> "nether"; case "ED" -> "end"; case "VO" -> "void"; default -> "world"; }; }
    private static int difficultyIndex(String difficulty) { return switch (difficulty == null ? "" : difficulty) { case "NR" -> 2; case "ED" -> 3; case "VO" -> 4; default -> 1; }; }
    private static int positiveId(String value) { return Math.max(1, value.hashCode() & 0x7FFFFFFF); }
    private static String safeFolderName(ChartManifest chart) { return (blank(chart.title, "untitled") + "-" + chart.id).replaceAll("[^a-zA-Z0-9 _.-]", "_").trim(); }
    private static String safeArenaKey(ChartManifest chart) { return safeFolderName(chart).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_"); }
    private static String bukkitMaterial(String itemId) { try { Identifier id = Identifier.of(blank(itemId, "minecraft:note_block")); return id.getPath().toUpperCase(Locale.ROOT); } catch (RuntimeException ignored) { return "NOTE_BLOCK"; } }
    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String yaml(String value) { return "\"" + blank(value, "Unknown").replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.3f", value) : "0.000"; }

    private static final class ExportDeleteException extends RuntimeException {
        private final IOException cause;
        private ExportDeleteException(IOException cause) { this.cause = cause; }
    }
}
