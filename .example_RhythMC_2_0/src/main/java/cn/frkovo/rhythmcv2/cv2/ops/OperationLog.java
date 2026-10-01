package cn.frkovo.rhythmcv2.cv2.ops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * operations.ndjson：追加写；每行 {seq, timestamp, command, payload, beforeHash, afterHash}。
 * undo/redo 产生的反向操作同样追加（审计可复现）。
 */
public final class OperationLog {

    public record Entry(long seq, String timestamp, String command, JsonObject payload,
                        String beforeHash, String afterHash) {
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final List<Entry> pending = new ArrayList<>();
    private long nextSeq = 0;

    public void setNextSeq(long seq) {
        nextSeq = seq;
    }

    public long nextSeq() {
        return nextSeq;
    }

    public int pendingCount() {
        return pending.size();
    }

    public List<Entry> pendingEntries() {
        return List.copyOf(pending);
    }

    public void append(Operation op, String beforeHash, String afterHash) {
        Entry e = new Entry(nextSeq++, java.time.Instant.now().toString(),
                op.command(), op.payload(), beforeHash, afterHash);
        pending.add(e);
    }

    public void clear() {
        pending.clear();
        nextSeq = 0;
    }

    /** 追加写入 ndjson 文件（调用方决定同步/异步线程）。 */
    public synchronized void flushTo(Path ndjson) throws IOException {
        if (pending.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Entry e : pending) {
            JsonObject o = new JsonObject();
            o.addProperty("seq", e.seq());
            o.addProperty("timestamp", e.timestamp());
            o.addProperty("command", e.command());
            o.add("payload", e.payload());
            o.addProperty("beforeHash", e.beforeHash());
            o.addProperty("afterHash", e.afterHash());
            sb.append(GSON.toJson(o)).append('\n');
        }
        Files.writeString(ndjson, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        pending.clear();
    }

    public static List<Entry> readAll(Path ndjson) throws IOException {
        List<Entry> out = new ArrayList<>();
        if (!Files.exists(ndjson)) {
            return out;
        }
        for (String line : Files.readAllLines(ndjson, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            JsonObject o = GSON.fromJson(line, JsonObject.class);
            out.add(new Entry(o.get("seq").getAsLong(), o.get("timestamp").getAsString(),
                    o.get("command").getAsString(), o.getAsJsonObject("payload"),
                    o.get("beforeHash").getAsString(), o.get("afterHash").getAsString()));
        }
        return out;
    }
}
