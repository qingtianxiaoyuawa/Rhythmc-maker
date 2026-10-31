package cn.frkovo.rhythmcmaker.chart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/** 与难度文件同目录的制谱器轨道布局旁挂文件；RhythMC 运行时只读固定文件名，不会读取它。 */
public final class EditorTrackLayoutFile {
    public static final String FILE_NAME = "layout.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EditorTrackLayoutFile() {
    }

    public static EditorTrackLayout read(Path file) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        String divisionsField = root.has("divisionsPerBeat") ? "divisionsPerBeat" : "divisionsPerChunk";
        if (!root.has("laneCount") || !root.has(divisionsField)) {
            throw new IOException("轨道布局文件缺少字段：" + file.getFileName());
        }
        EditorTrackLayout layout = EditorTrackLayout.of(root.get("laneCount").getAsInt(), root.get(divisionsField).getAsInt());
        if (root.has("divisionsPerChunk")) write(file, layout);
        return layout;
    }

    public static void write(Path file, EditorTrackLayout layout) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("laneCount", layout.laneCount());
        root.addProperty("divisionsPerBeat", layout.divisionsPerBeat());

    }
}
