package cn.frkovo.rhythmcmaker.client.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;

public final class EffectEditorCameraPreferences {
    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public EffectEditorCameraPreferences(Path path) {
        this.path = Objects.requireNonNull(path);
    }

    public static EffectEditorCameraPreferences getInstance() {
        return Holder.INSTANCE;
    }

    public EffectEditorCameraState load() throws IOException {
        if (!Files.exists(path)) return new EffectEditorCameraState(0.0, 74.0, 0.0);
        return Objects.requireNonNull(gson.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                EffectEditorCameraState.class), "Missing effect editor camera state");
    }

    public void save(EffectEditorCameraState state) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, gson.toJson(Objects.requireNonNull(state)), StandardCharsets.UTF_8);
    }

    private static final class Holder {
        private static final EffectEditorCameraPreferences INSTANCE = new EffectEditorCameraPreferences(
                FabricLoader.getInstance().getConfigDir().resolve("rhythmc-maker-effect-camera.json"));
    }
}
