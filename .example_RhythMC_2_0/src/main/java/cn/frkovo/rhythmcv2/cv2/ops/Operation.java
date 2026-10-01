package cn.frkovo.rhythmcv2.cv2.ops;

import com.google.gson.JsonObject;

/**
 * 领域操作（§7.6 子集，MVP 覆盖建谱全部高频操作）。
 * 实例不可变；Applier 在变更前由当前状态推导逆操作。
 */
public sealed interface Operation permits
        Operation.BpmAdd, Operation.BpmRemove, Operation.BpmSet, Operation.OffsetSet,
        Operation.NoteCreate, Operation.NoteDelete, Operation.NoteSetBeat, Operation.NoteSetTransform,
        Operation.TrackCreate, Operation.TrackDelete, Operation.EventAdd, Operation.EventRemove {

    String command();

    JsonObject payload();

    record BpmAdd(String beat, String bpm) implements Operation {
        @Override public String command() { return "BPM_ADD"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("beat", beat);
            o.addProperty("bpm", bpm);
            return o;
        }
    }

    record BpmRemove(String beat, String removedBpm) implements Operation {
        @Override public String command() { return "BPM_REMOVE"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("beat", beat);
            o.addProperty("removedBpm", removedBpm);
            return o;
        }
    }

    record BpmSet(String beat, String fromBpm, String toBpm) implements Operation {
        @Override public String command() { return "BPM_SET"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("beat", beat);
            o.addProperty("from", fromBpm);
            o.addProperty("to", toBpm);
            return o;
        }
    }

    record OffsetSet(String fromMs, String toMs) implements Operation {
        @Override public String command() { return "OFFSET_SET"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("from", fromMs);
            o.addProperty("to", toMs);
            return o;
        }
    }

    record NoteCreate(String level, String trackId, JsonObject note) implements Operation {
        @Override public String command() { return "NOTE_CREATE"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("level", level);
            o.addProperty("trackId", trackId);
            o.add("note", note);
            return o;
        }
    }

    record NoteDelete(String level, String trackId, JsonObject note) implements Operation {
        @Override public String command() { return "NOTE_DELETE"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("level", level);
            o.addProperty("trackId", trackId);
            o.add("note", note);
            return o;
        }
    }

    record NoteSetBeat(String level, String trackId, String noteId, String from, String to) implements Operation {
        @Override public String command() { return "NOTE_SET_BEAT"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("level", level);
            o.addProperty("trackId", trackId);
            o.addProperty("id", noteId);
            o.addProperty("from", from);
            o.addProperty("to", to);
            return o;
        }
    }

    /** pos/scale/rotation 三组 from/to 快照（字符串十进制）。 */
    record NoteSetTransform(String level, String trackId, String noteId,
                            JsonObject from, JsonObject to) implements Operation {
        @Override public String command() { return "NOTE_SET_TRANSFORM"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("level", level);
            o.addProperty("trackId", trackId);
            o.addProperty("id", noteId);
            o.add("from", from);
            o.add("to", to);
            return o;
        }
    }

    record TrackCreate(String level, String trackId) implements Operation {
        @Override public String command() { return "TRACK_CREATE"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("level", level);
            o.addProperty("trackId", trackId);
            return o;
        }
    }

    record TrackDelete(String level, String trackId) implements Operation {
        @Override public String command() { return "TRACK_DELETE"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("level", level);
            o.addProperty("trackId", trackId);
            return o;
        }
    }

    record EventAdd(String level, String trackId, String channel, JsonObject event) implements Operation {
        @Override public String command() { return "EVENT_ADD"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("level", level);
            o.addProperty("trackId", trackId);
            o.addProperty("channel", channel);
            o.add("event", event);
            return o;
        }
    }

    record EventRemove(String level, String trackId, String channel, JsonObject event) implements Operation {
        @Override public String command() { return "EVENT_REMOVE"; }
        @Override public JsonObject payload() {
            JsonObject o = new JsonObject();
            o.addProperty("level", level);
            o.addProperty("trackId", trackId);
            o.addProperty("channel", channel);
            o.add("event", event);
            return o;
        }
    }
}
