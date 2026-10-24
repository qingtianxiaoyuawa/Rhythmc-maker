package cn.frkovo.rhythmcmaker.client.playback;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TextDisplayEventFold {
    public enum Kind {
        DISPLAY, CLEAR_DISPLAY, TRANSFORM, SYNCHRONIZATION, REMOVE;

        public static Kind from(String effectType) {
            return switch (effectType) {
                case "TEXT_DISPLAY", "HOLOGRAM" -> DISPLAY;
                case "TEXT_DISPLAY_EFFECT" -> TRANSFORM;
                case "TEXT_DISPLAY_SYNC_TRACK", "TEXT_DISPLAY_DESYNC_TRACK" -> SYNCHRONIZATION;
                case "TEXT_DISPLAY_REMOVE", "REMOVE_HOLOGRAM" -> REMOVE;
                default -> null;
            };
        }
    }

    public record Event(String id, Kind kind, int sourceIndex) {}

    public record State(int displayIndex, List<Integer> transformIndexes, int synchronizationIndex, int visualStartIndex) {}

    private TextDisplayEventFold() {}

    public static Map<String, State> fold(List<Event> events) {
        Map<String, State> states = new LinkedHashMap<>();
        for (Event event : events) {
            if (event.kind() == Kind.REMOVE) {
                states.remove(event.id());
                continue;
            }
            if (event.kind() == Kind.CLEAR_DISPLAY) {
                states.remove(event.id());
                continue;
            }
            State previous = states.getOrDefault(event.id(), new State(-1, List.of(), -1, -1));
            State updated = switch (event.kind()) {
                case DISPLAY -> new State(event.sourceIndex(), List.of(), previous.synchronizationIndex(),
                        previous.visualStartIndex() < 0 ? event.sourceIndex() : previous.visualStartIndex());
                case TRANSFORM -> previous.displayIndex() < 0 ? previous
                        : new State(previous.displayIndex(), append(previous.transformIndexes(), event.sourceIndex()), previous.synchronizationIndex(), previous.visualStartIndex());
                case SYNCHRONIZATION -> previous.displayIndex() < 0 ? previous
                        : new State(previous.displayIndex(), previous.transformIndexes(), event.sourceIndex(), previous.visualStartIndex());
                case REMOVE -> previous;
                case CLEAR_DISPLAY -> previous;
            };
            if (updated.displayIndex() >= 0) states.put(event.id(), updated);
        }
        return states;
    }

    private static List<Integer> append(List<Integer> indexes, int sourceIndex) {
        List<Integer> updated = new ArrayList<>(indexes);
        updated.add(sourceIndex);
        return List.copyOf(updated);
    }
}
