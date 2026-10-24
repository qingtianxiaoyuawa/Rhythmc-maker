package cn.frkovo.rhythmcmaker.client.playback;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class PersistentEffectStateFold {
    private enum Kind {
        TIME, WEATHER, ARENA, GLOW_COLOR, HIDE_NOTES;

        private static Kind from(String effectType) {
            return switch (effectType) {
                case "TIME" -> TIME;
                case "WEATHER" -> WEATHER;
                case "ARENA", "CHANGE_ARENA" -> ARENA;
                case "GLOW_COLOR" -> GLOW_COLOR;
                case "HIDE_NOTES" -> HIDE_NOTES;
                default -> null;
            };
        }
    }

    private PersistentEffectStateFold() {}

    public static boolean isArenaType(String effectType) {
        return Kind.from(effectType) == Kind.ARENA;
    }

    public static List<Integer> latestEventIndexes(List<String> effectTypes, List<Double> effectTimes, double targetSongTime) {
        if (effectTypes.size() != effectTimes.size()) throw new IllegalArgumentException("Effect types and times must align");
        EnumMap<Kind, Integer> latestIndexes = new EnumMap<>(Kind.class);
        for (int index = 0; index < effectTypes.size(); index++) {
            double effectTime = effectTimes.get(index);
            if (!Double.isFinite(effectTime) || effectTime > targetSongTime) continue;
            Kind kind = Kind.from(effectTypes.get(index));
            if (kind != null) latestIndexes.put(kind, index);
        }
        List<Integer> result = new ArrayList<>(latestIndexes.values());
        result.sort(Integer::compareTo);
        return result;
    }
}
