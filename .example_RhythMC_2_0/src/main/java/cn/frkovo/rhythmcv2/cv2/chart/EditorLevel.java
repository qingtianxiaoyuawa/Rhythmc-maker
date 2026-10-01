package cn.frkovo.rhythmcv2.cv2.chart;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 草稿难度（world/nether/end/void）。effects 为导入透传的原始 JSON（MVP 不提供效果编辑）。 */
public final class EditorLevel {
    public final String name;
    public final Map<String, EditorTrack> tracks = new LinkedHashMap<>();
    public final List<JsonObject> effects = new ArrayList<>();
    /** 难度标称值（.rmcc meta.level）。 */
    public String levelValue = "1";
    public String initialArena = "";

    public EditorLevel(String name) {
        this.name = name;
    }

    public EditorTrack track(String trackId) {
        return tracks.get(trackId);
    }

    public EditorTrack ensureTrack(String trackId) {
        return tracks.computeIfAbsent(trackId, EditorTrack::new);
    }

    /** 全难度音符迭代（含轨道归属）。 */
    public record NoteRef(EditorTrack track, EditorNote note) {
    }

    public List<NoteRef> allNotes() {
        List<NoteRef> out = new ArrayList<>();
        for (EditorTrack t : tracks.values()) {
            for (EditorNote n : t.notes.values()) {
                out.add(new NoteRef(t, n));
            }
        }
        return out;
    }

    public static EditorLevel copyOf(EditorLevel l) {
        EditorLevel c = new EditorLevel(l.name);
        c.levelValue = l.levelValue;
        c.initialArena = l.initialArena;
        for (Map.Entry<String, EditorTrack> e : l.tracks.entrySet()) {
            c.tracks.put(e.getKey(), EditorTrack.copyOf(e.getValue()));
        }
        for (JsonObject fx : l.effects) {
            c.effects.add(fx.deepCopy());
        }
        return c;
    }
}
