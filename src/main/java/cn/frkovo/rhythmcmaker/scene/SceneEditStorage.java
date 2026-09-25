package cn.frkovo.rhythmcmaker.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SceneEditStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Scene>>() {}.getType();
    private SceneEditStorage() {}

    public static List<Scene> list(MinecraftServer server) throws IOException {
        Path path = path(server);
        if (!Files.exists(path)) return new ArrayList<>();
        List<Scene> scenes = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LIST_TYPE);
        if (scenes == null) scenes = new ArrayList<>();
        scenes.sort(Comparator.comparingInt(scene -> scene.index));
        return scenes;
    }

    public static void save(MinecraftServer server, List<Scene> scenes) throws IOException {
        Path path = path(server);
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(scenes, LIST_TYPE), StandardCharsets.UTF_8);
    }

    private static Path path(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("rhythmc maker").resolve("scene-editors.json");
    }

    public static final class Scene {
        public int index;
        public String name;
        public String icon;
        public int x;

        public Scene() {}
        public Scene(int index) {
            this.index = index;
            this.name = "场景" + index;
            this.icon = "minecraft:grass_block";
            this.x = 10000 + (index - 1) * 200;
        }
    }
}
