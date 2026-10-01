package cn.frkovo.rhythmcv2.cv2.ops;

import com.google.gson.JsonObject;

/** 操作 payload ↔ Operation 映射（日志重放/持久化用）。 */
public final class OpsJson {

    private OpsJson() {
    }

    public static Operation fromJson(JsonObject p, String command) {
        return switch (command) {
            case "BPM_ADD" -> new Operation.BpmAdd(p.get("beat").getAsString(), p.get("bpm").getAsString());
            case "BPM_REMOVE" -> new Operation.BpmRemove(p.get("beat").getAsString(),
                    p.has("removedBpm") ? p.get("removedBpm").getAsString() : "");
            case "BPM_SET" -> new Operation.BpmSet(p.get("beat").getAsString(),
                    p.get("from").getAsString(), p.get("to").getAsString());
            case "OFFSET_SET" -> new Operation.OffsetSet(p.get("from").getAsString(), p.get("to").getAsString());
            case "NOTE_CREATE" -> new Operation.NoteCreate(p.get("level").getAsString(),
                    p.get("trackId").getAsString(), p.getAsJsonObject("note"));
            case "NOTE_DELETE" -> new Operation.NoteDelete(p.get("level").getAsString(),
                    p.get("trackId").getAsString(), p.getAsJsonObject("note"));
            case "NOTE_SET_BEAT" -> new Operation.NoteSetBeat(p.get("level").getAsString(),
                    p.get("trackId").getAsString(), p.get("id").getAsString(),
                    p.get("from").getAsString(), p.get("to").getAsString());
            case "NOTE_SET_TRANSFORM" -> new Operation.NoteSetTransform(p.get("level").getAsString(),
                    p.get("trackId").getAsString(), p.get("id").getAsString(),
                    p.getAsJsonObject("from"), p.getAsJsonObject("to"));
            case "TRACK_CREATE" -> new Operation.TrackCreate(p.get("level").getAsString(),
                    p.get("trackId").getAsString());
            case "TRACK_DELETE" -> new Operation.TrackDelete(p.get("level").getAsString(),
                    p.get("trackId").getAsString());
            case "EVENT_ADD" -> new Operation.EventAdd(p.get("level").getAsString(),
                    p.get("trackId").getAsString(), p.get("channel").getAsString(), p.getAsJsonObject("event"));
            case "EVENT_REMOVE" -> new Operation.EventRemove(p.get("level").getAsString(),
                    p.get("trackId").getAsString(), p.get("channel").getAsString(), p.getAsJsonObject("event"));
            default -> null;
        };
    }
}
