package cn.frkovo.rhythmcv2.cv2.draft;

import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.ops.ModelHash;
import cn.frkovo.rhythmcv2.cv2.ops.Operation;
import cn.frkovo.rhythmcv2.cv2.ops.OperationApplier;
import cn.frkovo.rhythmcv2.cv2.ops.OperationLog;
import cn.frkovo.rhythmcv2.cv2.ops.OpsJson;
import cn.frkovo.rhythmcv2.cv2.session.EditorState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * .rmcd = ZIP{ draft.json, operations.ndjson, snapshots/NNNNNN.json, assets/index.json }。
 * 保存：先写 *.tmp 再原子替换（崩溃恢复基础）。
 * 权威恢复路径：draft.json.model；operations.ndjson 用于审计与 OperationReplayer 验证。
 */
public final class RmcdStore {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private RmcdStore() {
    }

    public record SaveInput(EditorChart chart, EditorState state, String ndjsonContent) {
    }

    public static void save(Path file, SaveInput input) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.createDirectories(file.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp),
                java.nio.charset.StandardCharsets.UTF_8)) {
            put(zip, "draft.json", GSON.toJson(DraftCodec.draftJson(input.chart(), input.state(), 0))
                    .getBytes(StandardCharsets.UTF_8));
            put(zip, "operations.ndjson",
                    (input.ndjsonContent() == null ? "" : input.ndjsonContent()).getBytes(StandardCharsets.UTF_8));
            put(zip, "snapshots/000000.json",
                    GSON_COMPACT.toJson(DraftCodec.model(input.chart())).getBytes(StandardCharsets.UTF_8));
            put(zip, "assets/index.json", assetsIndex(input.chart(), input.state()));
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] assetsIndex(EditorChart chart, EditorState state) {
        JsonObject o = new JsonObject();
        o.addProperty("audioHint", state.audioHint);
        // SHA-1/时长由 mod 经 CHART_STATUS 上报后由会话写入（MVP：占位结构）
        o.add("audio", new JsonObject());
        return GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    public static DraftCodec.DraftData load(Path file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("draft.json")) {
                    JsonObject root = GSON.fromJson(new InputStreamReader(zip, StandardCharsets.UTF_8),
                            JsonObject.class);
                    return DraftCodec.fromDraftJson(root);
                }
            }
        }
        throw new IOException("draft.json 缺失: " + file);
    }

    /** 读取草稿 JSON 文本（编译器 / 调试用）。 */
    public static String readEntry(Path file, String entryName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    /** OperationReplayer：快照模型 + 操作序列重放，结果 hash 必须等于模型 hash。 */
    public static String replayHash(EditorChart snapshotModel, List<OperationLog.Entry> ops,
                                    OperationApplier applier) {
        EditorChart model = snapshotModel.deepCopy();
        for (OperationLog.Entry e : ops) {
            Operation op = OpsJson.fromJson(e.payload(), e.command());
            if (op == null) {
                throw new IllegalStateException("未知操作: " + e.command());
            }
            OperationApplier.Result r = applier.apply(op, model);
            if (!r.ok()) {
                throw new IllegalStateException("重放失败 seq=" + e.seq() + ": " + r.message());
            }
        }
        return ModelHash.of(model);
    }

    public static byte[] readAll(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    public static void writeBytes(OutputStream out, byte[] data) throws IOException {
        out.write(data);
    }
}
