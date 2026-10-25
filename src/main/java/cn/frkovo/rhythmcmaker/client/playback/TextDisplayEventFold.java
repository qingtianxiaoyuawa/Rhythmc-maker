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

    private static final class MutableState {
        private int displayIndex = -1;
        private final List<Integer> transformIndexes = new ArrayList<>();
        private int synchronizationIndex = -1;
        private int visualStartIndex = -1;

        private State snapshot() {
            return new State(displayIndex, List.copyOf(transformIndexes), synchronizationIndex, visualStartIndex);
        }
    }

    private TextDisplayEventFold() {}

    public static Map<String, State> fold(List<Event> events) {
        Map<String, MutableState> states = new LinkedHashMap<>();
        for (Event event : events) {
            if (event.kind() == Kind.REMOVE) {
                states.remove(event.id());
                continue;
            }
            if (event.kind() == Kind.CLEAR_DISPLAY) {
                states.remove(event.id());
                continue;
            }
            MutableState state = states.computeIfAbsent(event.id(), ignored -> new MutableState());
            switch (event.kind()) {
                case DISPLAY -> {
                    if (state.visualStartIndex < 0) state.visualStartIndex = event.sourceIndex();
                    state.displayIndex = event.sourceIndex();
                    state.transformIndexes.clear();
                }
                case TRANSFORM -> {
                    if (state.displayIndex >= 0) state.transformIndexes.add(event.sourceIndex());
                }
                case SYNCHRONIZATION -> {
                    if (state.displayIndex >= 0) state.synchronizationIndex = event.sourceIndex();
                }
                case REMOVE, CLEAR_DISPLAY -> { }
            }
        }
        Map<String, State> snapshots = new LinkedHashMap<>();
        for (var entry : states.entrySet()) if (entry.getValue().displayIndex >= 0) snapshots.put(entry.getKey(), entry.getValue().snapshot());
        return snapshots;
    }
}
