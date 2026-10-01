package cn.frkovo.rhythmcv2.cv2.chart;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 草稿轨道：稳定字符串 id；音符与事件按插入序保存，排序由 Normalizer/编译期完成。 */
public final class EditorTrack {
    public final String id;
    public final Map<Channel, List<EditorNumEvent>> events = new EnumMap<>(Channel.class);
    public final Map<String, EditorNote> notes = new LinkedHashMap<>();

    public EditorTrack(String id) {
        this.id = id;
        for (Channel c : Channel.values()) {
            events.put(c, new ArrayList<>());
        }
    }

    public EditorNote note(String noteId) {
        return notes.get(noteId);
    }

    public static EditorTrack copyOf(EditorTrack t) {
        EditorTrack c = new EditorTrack(t.id);
        for (Map.Entry<Channel, List<EditorNumEvent>> e : t.events.entrySet()) {
            List<EditorNumEvent> copy = new ArrayList<>();
            for (EditorNumEvent ev : e.getValue()) {
                copy.add(EditorNumEvent.copyOf(ev));
            }
            c.events.put(e.getKey(), copy);
        }
        for (EditorNote n : t.notes.values()) {
            c.notes.put(n.id, EditorNote.copyOf(n));
        }
        return c;
    }
}
