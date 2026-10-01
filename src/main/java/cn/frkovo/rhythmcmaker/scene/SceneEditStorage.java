package cn.frkovo.rhythmcmaker.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneEditStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Scene>>() {}.getType();
    private static final Map<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private SceneEditStorage() {}

    public static List<Scene> list(MinecraftServer server, String chartId) throws IOException {
        Path path = path(server, chartId).toAbsolutePath().normalize();
        long modified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
        long size = Files.exists(path) ? Files.size(path) : -1L;
        CacheEntry cached = CACHE.get(path);
        if (cached != null && cached.modified == modified && cached.size == size) {
            List<Scene> scenes = copyScenes(cached.scenes);
            refreshSavedState(server, chartId, scenes);
            return scenes;
        }
        List<Scene> scenes = Files.exists(path)
                ? GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LIST_TYPE)
                : new ArrayList<>();
        if (scenes == null) scenes = new ArrayList<>();
        normalize(scenes);
        refreshSavedState(server, chartId, scenes);
        CACHE.put(path, new CacheEntry(modified, size, copyScenes(scenes)));
        return scenes;
    }

    public static List<Scene> list(MinecraftServer server) throws IOException {
        return list(server, "global");
    }

    public static void save(MinecraftServer server, String chartId, List<Scene> scenes) throws IOException {
        Path path = path(server, chartId).toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        CACHE.remove(path);
        Files.writeString(path, GSON.toJson(scenes, LIST_TYPE), StandardCharsets.UTF_8);
    }

    private static void normalize(List<Scene> scenes) {
        scenes.removeIf(scene -> scene == null);
        scenes.sort(Comparator.comparingInt(scene -> scene.index));
        Scene initial = scenes.stream().filter(scene -> scene.initial).findFirst().orElseGet(() -> {
            Scene scene = new Scene();
            scene.initial = true;
            scene.name = "初始场景";
            scene.icon = "minecraft:grass_block";
            return scene;
        });
        List<Scene> customScenes = new ArrayList<>();
        for (Scene scene : scenes) if (scene != initial) customScenes.add(scene);
        scenes.clear();
        initial.index = 1;
        initial.x = 200;
        if (initial.name == null || initial.name.isBlank()) initial.name = "初始场景";
        if (initial.icon == null || initial.icon.isBlank()) initial.icon = "minecraft:grass_block";
        scenes.add(initial);
        for (int position = 0; position < customScenes.size(); position++) {
            Scene scene = customScenes.get(position);
            scene.initial = false;
            scene.index = position + 2;
            scene.x = 600 + position * 200;
            if (scene.name == null || scene.name.isBlank()) scene.name = "场景" + scene.index;
            if (scene.icon == null || scene.icon.isBlank()) scene.icon = "minecraft:grass_block";
            scenes.add(scene);
        }
    }

    private static void refreshSavedState(MinecraftServer server, String chartId, List<Scene> scenes) throws IOException {
        for (Scene scene : scenes) scene.saved = Files.isRegularFile(schematicPath(server, chartId, scene.index));
    }

    private static List<Scene> copyScenes(List<Scene> source) {
        List<Scene> copy = new ArrayList<>(source.size());
        for (Scene scene : source) {
            Scene value = new Scene();
            value.index = scene.index;
            value.initial = scene.initial;
            value.name = scene.name;
            value.icon = scene.icon;
            value.x = scene.x;
            value.saved = scene.saved;
            copy.add(value);
        }
        return copy;
    }

    private record CacheEntry(long modified, long size, List<Scene> scenes) {}

    public static void save(MinecraftServer server, List<Scene> scenes) throws IOException {
        save(server, "global", scenes);
    }

    public static Path schematicPath(MinecraftServer server, String chartId, int sceneIndex) throws IOException {
        if (sceneIndex < 1) throw new IOException("场景编号无效");
        Path metadata = path(server, chartId).toAbsolutePath().normalize();
        Path directory = metadata.getParent().resolve(chartId).resolve(sceneIndex == 1 ? "initial" : "scene" + sceneIndex).normalize();
        if (!directory.startsWith(metadata.getParent())) throw new IOException("场景存储路径无效");
        return directory.resolve("arena.schem");
    }

    public static void moveSchematic(MinecraftServer server, String chartId, int fromIndex, int toIndex) throws IOException {
        Path source = schematicPath(server, chartId, fromIndex);
        if (!Files.isRegularFile(source)) return;
        Path destination = schematicPath(server, chartId, toIndex);
        Files.createDirectories(destination.getParent());
        Files.move(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        CACHE.remove(path(server, chartId).toAbsolutePath().normalize());
    }

    public static void deleteSchematic(MinecraftServer server, String chartId, int sceneIndex) throws IOException {
        Files.deleteIfExists(schematicPath(server, chartId, sceneIndex));
        CACHE.remove(path(server, chartId).toAbsolutePath().normalize());
    }

    public static void copySchematic(MinecraftServer server, String chartId, int sceneIndex, Path source) throws IOException {
        Path destination = schematicPath(server, chartId, sceneIndex);
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        CACHE.remove(path(server, chartId).toAbsolutePath().normalize());
    }

    public static void writeSchematic(MinecraftServer server, String chartId, int sceneIndex, Map<String, Integer> palette, byte[] blockData) throws IOException {
        Path destination = schematicPath(server, chartId, sceneIndex);
        Files.createDirectories(destination.getParent());
        NbtCompound paletteTag = new NbtCompound();
        for (Map.Entry<String, Integer> entry : palette.entrySet()) paletteTag.putInt(entry.getKey(), entry.getValue());
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 2);
        root.putInt("DataVersion", 0);
        root.putShort("Width", (short) 128);
        root.putShort("Height", (short) 128);
        root.putShort("Length", (short) 128);
        root.putIntArray("Offset", new int[]{-64, -65, -64});
        root.put("Palette", paletteTag);
        root.putInt("PaletteMax", palette.size());
        root.putByteArray("BlockData", blockData);
        NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", chartId + " scene " + sceneIndex);
        metadata.putString("Author", "Rhythmc Maker");
        metadata.putString("Date", Long.toString(System.currentTimeMillis()));
        root.put("Metadata", metadata);
        Path temporary = destination.resolveSibling("arena.schem.tmp");
        NbtIo.writeCompressed(root, temporary);
        try {
            Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        CACHE.remove(path(server, chartId).toAbsolutePath().normalize());
    }

    private static Path path(MinecraftServer server, String chartId) throws IOException {
        if (chartId == null || chartId.isBlank() || !chartId.matches("[a-zA-Z0-9._-]+")) throw new IOException("谱面编号无效");
        return server.getSavePath(WorldSavePath.ROOT).resolve("rhythmc maker").resolve("scene-editors").resolve(chartId + ".json");
    }

    public static final class Scene {
        public int index;
        public boolean initial;
        public String name;
        public String icon;
        public int x;
        public boolean saved;

        public Scene() {}
        public Scene(int index) {
            this.index = index;
            this.initial = index == 1;
            this.name = "场景" + index;
            this.icon = "minecraft:grass_block";
            this.x = index == 1 ? 200 : 600 + (index - 2) * 200;
        }
    }
}
