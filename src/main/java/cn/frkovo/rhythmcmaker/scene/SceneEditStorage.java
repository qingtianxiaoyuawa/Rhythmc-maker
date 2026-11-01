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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneEditStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Scene>>() {}.getType();
    private static final Map<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private SceneEditStorage() {}

    public static List<Scene> list(MinecraftServer server, String chartId) throws IOException {
        Path path = path(server, chartId).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) && Files.isDirectory(localRoot(server, chartId))) return listLocal(server, chartId);
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
        migrateSceneNames(server, chartId, scenes);
        normalize(scenes);
        refreshSavedState(server, chartId, scenes);
        migrateLegacyScenes(server, chartId, scenes);
        refreshSavedState(server, chartId, scenes);
        CACHE.put(path, new CacheEntry(modified, size, copyScenes(scenes)));
        return scenes;
    }

    public static List<Scene> list(MinecraftServer server) throws IOException {
        return list(server, "global");
    }

    public static void save(MinecraftServer server, String chartId, List<Scene> scenes) throws IOException {
        normalize(scenes);
        migrateLegacyScenes(server, chartId, scenes);
        Path root = localRoot(server, chartId);
        if (!scenes.isEmpty()) Files.createDirectories(root);
        for (Scene scene : scenes) {
            Path directory = root.resolve(sceneDirectoryName(scene)).normalize();
            if (!directory.startsWith(root)) throw new IOException("场景目录无效");
            Files.createDirectories(directory);
            writeMetadataIfChanged(directory.resolve("metadata.yml"), metadata(scene));
        }
        CACHE.remove(path(server, chartId).toAbsolutePath().normalize());
    }

    private static void normalize(List<Scene> scenes) {
        scenes.removeIf(scene -> scene == null);
        scenes.sort(Comparator.comparingInt(scene -> scene.index));
        Scene initial = scenes.stream().filter(scene -> scene.initial).findFirst().orElseGet(() -> {
            Scene scene = new Scene();
            scene.initial = true;
            scene.name = UUID.randomUUID().toString();
            scene.displayName = "initial_arena";
            scene.icon = "minecraft:grass_block";
            return scene;
        });
        List<Scene> customScenes = new ArrayList<>();
        for (Scene scene : scenes) if (scene != initial) customScenes.add(scene);
        scenes.clear();
        if (initial.sceneId == null || initial.sceneId.isBlank()) initial.sceneId = UUID.randomUUID().toString();
        initial.index = 1;
        initial.x = 200;
        if (!validName(initial.name)) initial.name = UUID.randomUUID().toString();
        if (initial.displayName == null || initial.displayName.isBlank()) initial.displayName = "initial_arena";
        if (initial.icon == null || initial.icon.isBlank()) initial.icon = "minecraft:grass_block";
        scenes.add(initial);
        for (int position = 0; position < customScenes.size(); position++) {
            Scene scene = customScenes.get(position);
            scene.initial = false;
            if (scene.sceneId == null || scene.sceneId.isBlank()) scene.sceneId = UUID.randomUUID().toString();
            scene.index = position + 2;
            scene.x = 600 + position * 200;
            if (!validName(scene.name)) scene.name = UUID.randomUUID().toString();
            if (scene.displayName == null || scene.displayName.isBlank()) scene.displayName = "effect_arena_" + (position + 1);
            if (scene.icon == null || scene.icon.isBlank()) scene.icon = "minecraft:grass_block";
            scenes.add(scene);
        }
    }

    private static void migrateSceneNames(MinecraftServer server, String chartId, List<Scene> scenes) throws IOException {
        Path root = localRoot(server, chartId);
        for (Scene scene : scenes) {
            if (scene == null) continue;
            boolean needsMigration = scene.displayName == null || scene.displayName.isBlank();
            if (!needsMigration) continue;
            scene.displayName = scene.name;
            Path directory = localSceneDirectory(root, scene.index);
            Path metadata = directory == null ? null : directory.resolve("metadata.yml");
            if (metadata != null && Files.isRegularFile(metadata)) {
                String storedName = readLegacySceneValue(metadata, "name", "");
                if (validName(storedName)) scene.name = storedName;
                String storedDisplayName = readLegacySceneValue(metadata, "display-name", "");
                if (storedDisplayName != null && !storedDisplayName.isBlank()) scene.displayName = storedDisplayName;
            } else if (needsMigration && validName(scene.name)) scene.name = UUID.randomUUID().toString();
        }
    }

    private static void refreshSavedState(MinecraftServer server, String chartId, List<Scene> scenes) throws IOException {
        for (Scene scene : scenes) scene.saved = Files.isRegularFile(schematicPath(server, chartId, scene.index));
    }

    private static void migrateLegacyScenes(MinecraftServer server, String chartId, List<Scene> scenes) throws IOException {
        Path legacyIndex = path(server, chartId);
        if (!Files.isRegularFile(legacyIndex)) return;
        Path local = localRoot(server, chartId);
        Path staging = local.resolveSibling("scene-migration-" + UUID.randomUUID()).normalize();
        if (!staging.startsWith(local.getParent())) throw new IOException("scene migration directory invalid");
        Files.createDirectories(staging);
        try {
            for (Scene scene : scenes) {
                Path old = legacySchematicPath(server, chartId, scene.index);
                Path staged = staging.resolve(sceneDirectoryName(scene));
                Files.createDirectories(staged);
                Files.writeString(staged.resolve("metadata.yml"), metadata(scene), StandardCharsets.UTF_8);
                if (Files.isRegularFile(old)) Files.copy(old, staged.resolve("arena.schem"));
            }
            Files.createDirectories(local);
            for (Scene scene : scenes) {
                Path target = local.resolve(sceneDirectoryName(scene));
                Path staged = staging.resolve(sceneDirectoryName(scene));
                if (!Files.exists(target)) Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
                else {
                    if (!Files.isRegularFile(target.resolve("arena.schem")) && Files.isRegularFile(staged.resolve("arena.schem"))) Files.copy(staged.resolve("arena.schem"), target.resolve("arena.schem"));
                    writeMetadataIfChanged(target.resolve("metadata.yml"), metadata(scene));
                }
            }
            for (Scene scene : scenes) {
                Path old = legacySchematicPath(server, chartId, scene.index);
                Path current = local.resolve(sceneDirectoryName(scene)).resolve("arena.schem");
                if (Files.isRegularFile(old) && !Files.isRegularFile(current)) throw new IOException("scene migration validation failed");
            }
            for (Scene scene : scenes) {
                Path old = legacySchematicPath(server, chartId, scene.index);
                Files.deleteIfExists(old);
                Files.deleteIfExists(old.getParent());
            }
            Files.deleteIfExists(legacyIndex);
            deleteDirectoryIfEmpty(legacyIndex.getParent());
        } finally {
            deleteTree(staging);
        }
    }

    private static void deleteDirectoryIfEmpty(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isEmpty()) Files.deleteIfExists(directory);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
        }
    }

    private static List<Scene> listLocal(MinecraftServer server, String chartId) throws IOException {
        List<Scene> scenes = new ArrayList<>();
        Path root = localRoot(server, chartId);
        try (var stream = Files.list(root)) {
            for (Path directory : stream.filter(Files::isDirectory).toList()) {
                Path metadata = directory.resolve("metadata.yml");
                if (Files.isRegularFile(metadata)) scenes.add(parseMetadata(metadata, directory.getFileName().toString()));
            }
        }
        normalize(scenes);
        refreshSavedState(server, chartId, scenes);
        return scenes;
    }

    private static String sceneDirectoryName(Scene scene) {
        String id = scene.sceneId == null ? "scene" : scene.sceneId.replaceAll("[^a-zA-Z0-9_-]", "");
        return "scene-" + id;
    }

    private static String metadata(Scene scene) {
        return "name: \"" + yaml(scene.name) + "\"\ndisplay-name: \"" + yaml(scene.displayName) + "\"\nauthor: RhythMC Maker\n"
                + "description: \"\"\nicon: " + material(scene.icon) + "\nborder: CYAN_CONCRETE\nsuccess: GREEN_CONCRETE\n"
                + "schematic: arena.schem\nhide: false\nsceneId: " + scene.sceneId + "\nindex: " + scene.index
                + "\ntype: " + (scene.initial ? "initial" : "effect") + "\n";
    }

    private static void writeMetadataIfChanged(Path target, String content) throws IOException {
        if (Files.isRegularFile(target) && content.equals(Files.readString(target, StandardCharsets.UTF_8))) return;
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static boolean validName(String value) {
        return value != null && value.matches("[a-zA-Z0-9._-]+");
    }

    private static String material(String icon) {
        if (icon == null || icon.isBlank()) return "GRASS_BLOCK";
        int separator = icon.indexOf(':');
        String path = separator < 0 ? icon : icon.substring(separator + 1);
        return path.toUpperCase(java.util.Locale.ROOT);
    }

    private static String yaml(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static Scene parseMetadata(Path file, String fallbackName) throws IOException {
        Scene scene = new Scene();
        String importedDisplayName = null;
        String importedName = null;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int separator = line.indexOf(':');
            if (separator < 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim().replace("\"", "");
            if (key.equals("sceneId") && !value.isBlank()) scene.sceneId = value;
            else if (key.equals("index")) scene.index = Integer.parseInt(value);

            else if (key.equals("name")) importedName = value;
            else if (key.equals("display-name")) importedDisplayName = value;
            else if (key.equals("type")) scene.initial = value.equals("initial");
            else if (key.equals("icon")) scene.icon = value;
        }
        scene.name = importedName == null || importedName.isBlank() ? UUID.randomUUID().toString() : importedName;
        scene.displayName = importedDisplayName == null || importedDisplayName.isBlank() ? fallbackName : importedDisplayName;
        return scene;
    }

    private static List<Scene> copyScenes(List<Scene> source) {
        List<Scene> copy = new ArrayList<>(source.size());
        for (Scene scene : source) {
            Scene value = new Scene();
            value.index = scene.index;
            value.initial = scene.initial;
            value.name = scene.name;
            value.displayName = scene.displayName;
            value.icon = scene.icon;
            value.x = scene.x;
            value.saved = scene.saved;
            value.sceneId = scene.sceneId;
            copy.add(value);
        }
        return copy;
    }

    private record CacheEntry(long modified, long size, List<Scene> scenes) {}

    public static void save(MinecraftServer server, List<Scene> scenes) throws IOException {
        save(server, "global", scenes);
    }

    public static void migrateLegacySceneDirectory(MinecraftServer server, String chartId, Path legacyRoot) throws IOException {
        if (!Files.isDirectory(legacyRoot)) return;
        List<Path> folders;
        try (var stream = Files.list(legacyRoot)) {
            folders = stream.filter(Files::isDirectory).sorted().toList();
        }
        List<Scene> scenes = list(server, chartId);
        List<LegacyScene> imports = new ArrayList<>();
        java.util.Set<String> assignedSceneIds = new java.util.HashSet<>();
        int nextIndex = scenes.stream().mapToInt(scene -> scene.index).max().orElse(0) + 1;
        for (Path folder : folders) {
            Path source = folder.resolve("arena.schem");
            Path metadataFile = folder.resolve("metadata.yml");
            String name = readLegacySceneValue(metadataFile, "name", folder.getFileName().toString());
            String icon = readLegacySceneValue(metadataFile, "icon", "minecraft:grass_block");
            boolean initial = folder.getFileName().toString().equalsIgnoreCase("initial");
            Scene scene = initial ? scenes.stream().filter(value -> value.initial).findFirst().orElse(null) : scenes.stream().filter(value -> !value.initial && value.name.equals(name) && !assignedSceneIds.contains(value.sceneId)).findFirst().orElse(null);
            if (scene != null && !assignedSceneIds.add(scene.sceneId)) scene = null;
            if (scene == null) {
                scene = new Scene(nextIndex++);
                scene.initial = false;
                scenes.add(scene);
                assignedSceneIds.add(scene.sceneId);
            }
            if (!name.isBlank()) scene.name = name;
            if (!icon.isBlank()) scene.icon = icon;
            imports.add(new LegacyScene(scene, source, metadataFile));
        }
        normalize(scenes);
        save(server, chartId, scenes);
        for (LegacyScene legacy : imports) {
            Scene scene = scenes.stream().filter(value -> value.sceneId.equals(legacy.scene.sceneId)).findFirst().orElseThrow(() -> new IOException("legacy scene mapping missing"));
            if (Files.isRegularFile(legacy.schematic)) copySchematic(server, chartId, scene.index, legacy.schematic);
            if (Files.isRegularFile(legacy.schematic) && !Files.isRegularFile(schematicPath(server, chartId, scene.index))) throw new IOException("legacy scene copy validation failed");
        }
        for (LegacyScene legacy : imports) {
            Files.deleteIfExists(legacy.schematic);
            Files.deleteIfExists(legacy.metadata);
            deleteDirectoryIfEmpty(legacy.schematic.getParent());
        }
        deleteDirectoryIfEmpty(legacyRoot);
    }

    private static String readLegacySceneValue(Path metadata, String key, String fallback) throws IOException {
        if (!Files.isRegularFile(metadata)) return fallback;
        for (String line : Files.readAllLines(metadata, StandardCharsets.UTF_8)) {
            int separator = line.indexOf(':');
            if (separator < 0 || !line.substring(0, separator).trim().equals(key)) continue;
            return line.substring(separator + 1).trim().replace("\"", "");
        }
        return fallback;
    }

    private record LegacyScene(Scene scene, Path schematic, Path metadata) {}

    public static Path schematicPath(MinecraftServer server, String chartId, int sceneIndex) throws IOException {
        if (sceneIndex < 1) throw new IOException("场景编号无效");
        Path root = localRoot(server, chartId);
        Path directory = localSceneDirectory(root, sceneIndex);
        if (directory == null) {
            Scene scene = new Scene(sceneIndex);
            directory = root.resolve(sceneDirectoryName(scene)).normalize();
        }
        if (!directory.startsWith(root)) throw new IOException("场景存储路径无效");
        return directory.resolve("arena.schem");
    }

    public static Path schematicPath(MinecraftServer server, String chartId, String sceneName) throws IOException {
        if (sceneName == null || sceneName.isBlank()) throw new IOException("场景名称无效");
        Scene scene = list(server, chartId).stream()
                .filter(value -> sceneName.equals(value.name) || sceneName.equals(value.displayName))
                .findFirst()
                .orElseThrow(() -> new IOException("谱面中不存在场景：" + sceneName));
        return schematicPath(server, chartId, scene.index);
    }

    private static Path localSceneDirectory(Path root, int sceneIndex) throws IOException {
        if (!Files.isDirectory(root)) return null;
        try (var stream = Files.list(root)) {
            for (Path directory : stream.filter(Files::isDirectory).sorted().toList()) {
                Path metadata = directory.resolve("metadata.yml");
                if (!Files.isRegularFile(metadata)) continue;
                Scene scene = parseMetadata(metadata, directory.getFileName().toString());
                if (scene.index == sceneIndex) return directory;
            }
        }
        return null;
    }

    public static void moveSchematic(MinecraftServer server, String chartId, int fromIndex, int toIndex) throws IOException {
        Path source = schematicPath(server, chartId, fromIndex);
        if (!Files.isRegularFile(source)) return;
        Path destination = schematicPath(server, chartId, toIndex);
        Files.createDirectories(destination.getParent());
        Files.move(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        CACHE.remove(path(server, chartId).toAbsolutePath().normalize());
    }

    public static void deleteSceneFiles(MinecraftServer server, String chartId, Scene scene) throws IOException {
        Path root = localRoot(server, chartId).toAbsolutePath().normalize();
        Path directory = root.resolve(sceneDirectoryName(scene)).normalize();
        if (!directory.startsWith(root)) throw new IOException("场景目录无效");
        Files.deleteIfExists(directory.resolve("arena.schem").toAbsolutePath().normalize());
        Files.deleteIfExists(directory.resolve("metadata.yml").toAbsolutePath().normalize());
        deleteDirectoryIfEmpty(directory);
        CACHE.remove(path(server, chartId).toAbsolutePath().normalize());
    }

    public static void deleteSchematic(MinecraftServer server, String chartId, int sceneIndex) throws IOException {
        Scene scene = list(server, chartId).stream().filter(value -> value.index == sceneIndex).findFirst()
                .orElseThrow(() -> new IOException("场景不存在"));
        deleteSceneFiles(server, chartId, scene);
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

    private static Path legacySchematicPath(MinecraftServer server, String chartId, int sceneIndex) throws IOException {
        Path metadata = path(server, chartId).toAbsolutePath().normalize();
        Path directory = metadata.getParent().resolve(chartId).resolve(sceneIndex == 1 ? "initial" : "scene" + sceneIndex).normalize();
        if (!directory.startsWith(metadata.getParent())) throw new IOException("legacy scene path invalid");
        return directory.resolve("arena.schem");
    }

    private static Path localRoot(MinecraftServer server, String chartId) throws IOException {
        return server.getSavePath(WorldSavePath.ROOT).resolve("rhythmc maker").resolve(chartId).resolve("scene").toAbsolutePath().normalize();
    }

    public static final class Scene {
        public int index;
        public boolean initial;
        public String name;
        public String displayName;
        public String icon;
        public int x;
        public boolean saved;
        public String sceneId;

        public Scene() {}
        public Scene(int index) {
            this.index = index;
            this.initial = index == 1;
            this.name = UUID.randomUUID().toString();
            this.displayName = index == 1 ? "initial_arena" : "effect_arena_" + (index - 1);
            this.icon = "minecraft:grass_block";
            this.x = index == 1 ? 200 : 600 + (index - 2) * 200;
            this.sceneId = UUID.randomUUID().toString();
        }
    }
}
