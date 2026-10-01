package cn.frkovo.rhythmcv2.cv2.draft;

import cn.frkovo.rhythmcv2.cv2.chart.Channel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.chart.EditorLevel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent;
import cn.frkovo.rhythmcv2.cv2.chart.EditorTrack;
import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import cn.frkovo.rhythmcv2.cv2.ops.OperationApplier;
import cn.frkovo.rhythmcv2.cv2.session.EditorState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.Map;

/**
 * draft.json 编解码：所有 beat 写规范化字符串分数（"12"、"n/d"），十进制量写字符串。
 * 禁止 JSON number beat（§7.5 硬规则）。
 */
public final class DraftCodec {

    public static final int FORMAT_VERSION = 1;
    public static final int SNAPSHOT_EVERY_OPS = 256;

    private DraftCodec() {
    }

    public static String modelJson(EditorChart chart) {
        return model(chart).toString();
    }

    public static JsonObject model(EditorChart chart) {
        JsonObject model = new JsonObject();
        model.addProperty("offsetMs", chart.offsetMs);
        JsonArray bpms = new JsonArray();
        for (BeatClock.BpmEvent e : chart.bpms) {
            JsonObject b = new JsonObject();
            b.addProperty("beat", e.beat().toString());
            b.addProperty("bpm", e.bpm().toPlainString());
            bpms.add(b);
        }
        model.add("bpms", bpms);
        JsonObject levels = new JsonObject();
        for (EditorLevel level : chart.levels.values()) {
            levels.add(level.name, levelJson(level));
        }
        model.add("levels", levels);
        return model;
    }

    private static JsonObject levelJson(EditorLevel level) {
        JsonObject o = new JsonObject();
        o.addProperty("levelValue", level.levelValue);
        o.addProperty("initialArena", level.initialArena);
        JsonArray tracks = new JsonArray();
        for (EditorTrack t : level.tracks.values()) {
            tracks.add(trackJson(t));
        }
        o.add("tracks", tracks);
        o.add("effects", level.effects.isEmpty() ? new JsonArray() : effectsJson(level));
        return o;
    }

    private static JsonArray effectsJson(EditorLevel level) {
        JsonArray a = new JsonArray();
        for (com.google.gson.JsonObject fx : level.effects) {
            a.add(fx);
        }
        return a;
    }

    private static JsonObject trackJson(EditorTrack t) {
        JsonObject o = new JsonObject();
        o.addProperty("id", t.id);
        JsonObject events = new JsonObject();
        for (Map.Entry<Channel, java.util.List<EditorNumEvent>> e : t.events.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            JsonArray arr = new JsonArray();
            for (EditorNumEvent ev : e.getValue()) {
                arr.add(OperationApplier.eventSnapshot(ev));
            }
            events.add(e.getKey().json, arr);
        }
        o.add("events", events);
        JsonArray notes = new JsonArray();
        for (EditorNote n : t.notes.values()) {
            notes.add(OperationApplier.noteSnapshot(n));
        }
        o.add("notes", notes);
        return o;
    }

    public static JsonObject draftJson(EditorChart chart, EditorState state, long snapshotIndex) {
        JsonObject root = new JsonObject();
        root.addProperty("format", "rmcd");
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.addProperty("draftId", chart.draftId);
        root.addProperty("songFolder", chart.songFolder);
        root.addProperty("activeLevel", chart.activeLevel);
        root.addProperty("revision", chart.revision);
        root.addProperty("baseSnapshot", snapshotIndex);
        root.add("model", model(chart));

        JsonObject manifest = new JsonObject();
        manifest.addProperty("name", chart.manifestName);
        manifest.addProperty("composer", chart.composer);
        manifest.add("charters", stringArray(chart.charters));
        manifest.addProperty("icon", chart.icon);
        manifest.addProperty("alias", chart.alias);
        manifest.addProperty("length", chart.length);
        manifest.addProperty("respack_sha1", chart.respackSha1);
        manifest.addProperty("description", chart.description);
        manifest.addProperty("song_id", chart.songId);
        manifest.addProperty("version", chart.version);
        manifest.addProperty("default_locale", chart.defaultLocale);
        manifest.add("comments", stringArray(chart.comments));
        manifest.add("albums", stringArray(chart.albums));
        manifest.add("i18n", chart.i18n);
        root.add("manifest", manifest);

        JsonObject editor = new JsonObject();
        editor.addProperty("cursorBeat", state.cursorBeat.toString());
        editor.addProperty("snap", state.snap);
        editor.addProperty("tool", state.tool);
        editor.add("selectedIds", stringArray(new java.util.ArrayList<>(state.selectedIds)));
        if (state.loopA != null && state.loopB != null) {
            JsonObject loop = new JsonObject();
            loop.addProperty("a", state.loopA.toString());
            loop.addProperty("b", state.loopB.toString());
            editor.add("loop", loop);
        }
        editor.addProperty("lastPos", state.lastPos);
        editor.addProperty("audioHint", state.audioHint);
        root.add("editorState", editor);

        JsonObject compiler = new JsonObject();
        compiler.addProperty("target", "rmcc-v1");
        compiler.addProperty("exportScale", 18);
        compiler.addProperty("rounding", "WARN");
        root.add("compiler", compiler);
        return root;
    }

    private static JsonArray stringArray(java.util.List<String> list) {
        JsonArray a = new JsonArray();
        for (String s : list) {
            a.add(s);
        }
        return a;
    }

    // ---- 反序列化 ----

    public record DraftData(EditorChart chart, EditorState state, long baseSnapshot) {
    }

    public static DraftData fromDraftJson(JsonObject root) {
        String draftId = root.get("draftId").getAsString();
        String songFolder = root.get("songFolder").getAsString();
        EditorChart chart = new EditorChart(draftId, songFolder);
        chart.revision = root.get("revision").getAsLong();
        chart.activeLevel = root.has("activeLevel") ? root.get("activeLevel").getAsString() : "world";

        JsonObject model = root.getAsJsonObject("model");
        chart.offsetMs = model.has("offsetMs") ? model.get("offsetMs").getAsString() : "0";
        chart.bpms.clear();
        for (com.google.gson.JsonElement el : model.getAsJsonArray("bpms")) {
            JsonObject b = el.getAsJsonObject();
            chart.bpms.add(new BeatClock.BpmEvent(
                    BeatFraction.parse(b.get("beat").getAsString()),
                    new BigDecimal(b.get("bpm").getAsString())));
        }
        chart.levels.clear();
        for (Map.Entry<String, com.google.gson.JsonElement> e : model.getAsJsonObject("levels").entrySet()) {
            chart.levels.put(e.getKey(), levelFromJson(e.getKey(), e.getValue().getAsJsonObject()));
        }

        if (root.has("manifest")) {
            JsonObject m = root.getAsJsonObject("manifest");
            chart.manifestName = optString(m, "name", chart.manifestName);
            chart.composer = optString(m, "composer", chart.composer);
            chart.charters = stringList(m, "charters");
            chart.icon = optString(m, "icon", chart.icon);
            chart.alias = optString(m, "alias", chart.alias);
            chart.length = optString(m, "length", chart.length);
            chart.respackSha1 = optString(m, "respack_sha1", chart.respackSha1);
            chart.description = optString(m, "description", chart.description);
            chart.songId = optString(m, "song_id", chart.songId);
            chart.version = optString(m, "version", chart.version);
            chart.defaultLocale = optString(m, "default_locale", chart.defaultLocale);
            chart.comments = stringList(m, "comments");
            chart.albums = stringList(m, "albums");
            if (m.has("i18n") && m.get("i18n").isJsonObject()) {
                chart.i18n = m.getAsJsonObject("i18n");
            }
        }

        EditorState state = new EditorState();
        if (root.has("editorState")) {
            JsonObject es = root.getAsJsonObject("editorState");
            try {
                state.cursorBeat = BeatFraction.parse(es.get("cursorBeat").getAsString());
            } catch (RuntimeException ignored) {
                state.cursorBeat = BeatFraction.ZERO;
            }
            state.snap = es.has("snap") ? es.get("snap").getAsInt() : 4;
            state.tool = optString(es, "tool", "TAP");
            if (es.has("selectedIds")) {
                for (String id : stringList(es, "selectedIds")) {
                    state.selectedIds.add(id);
                }
            }
            if (es.has("loop") && es.getAsJsonObject("loop").has("a")) {
                JsonObject loop = es.getAsJsonObject("loop");
                try {
                    state.loopA = BeatFraction.parse(loop.get("a").getAsString());
                    state.loopB = BeatFraction.parse(loop.get("b").getAsString());
                } catch (RuntimeException ignored) {
                    state.clearLoop();
                }
            }
            state.lastPos = optString(es, "lastPos", "0,0,0");
            state.audioHint = optString(es, "audioHint", "");
        }
        long baseSnapshot = root.has("baseSnapshot") ? root.get("baseSnapshot").getAsLong() : 0;
        return new DraftData(chart, state, baseSnapshot);
    }

    private static EditorLevel levelFromJson(String levelName, JsonObject o) {
        EditorLevel level = new EditorLevel(levelName);
        level.levelValue = optString(o, "levelValue", "1");
        level.initialArena = optString(o, "initialArena", "");
        level.tracks.clear();
        for (com.google.gson.JsonElement tel : o.getAsJsonArray("tracks")) {
            JsonObject t = tel.getAsJsonObject();
            EditorTrack track = new EditorTrack(t.get("id").getAsString());
            if (t.has("events")) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : t.getAsJsonObject("events").entrySet()) {
                    Channel channel = channelByJson(e.getKey());
                    if (channel == null) {
                        continue;
                    }
                    java.util.List<EditorNumEvent> list = track.events.get(channel);
                    for (com.google.gson.JsonElement eel : e.getValue().getAsJsonArray()) {
                        list.add(OperationApplier.eventFromSnapshot(eel.getAsJsonObject()));
                    }
                }
            }
            if (t.has("notes")) {
                for (com.google.gson.JsonElement nel : t.getAsJsonArray("notes")) {
                    EditorNote n = OperationApplier.noteFromSnapshot(nel.getAsJsonObject());
                    track.notes.put(n.id, n);
                }
            }
            level.tracks.put(track.id, track);
        }
        if (o.has("effects") && o.get("effects").isJsonArray()) {
            for (com.google.gson.JsonElement fx : o.getAsJsonArray("effects")) {
                level.effects.add(fx.getAsJsonObject().deepCopy());
            }
        }
        return level;
    }

    private static Channel channelByJson(String json) {
        for (Channel c : Channel.values()) {
            if (c.json.equals(json)) {
                return c;
            }
        }
        return null;
    }

    private static String optString(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static java.util.List<String> stringList(JsonObject o, String key) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (com.google.gson.JsonElement e : o.getAsJsonArray(key)) {
                out.add(e.getAsString());
            }
        }
        return out;
    }
}
