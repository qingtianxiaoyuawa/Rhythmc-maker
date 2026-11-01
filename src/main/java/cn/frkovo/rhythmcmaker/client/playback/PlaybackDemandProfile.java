package cn.frkovo.rhythmcmaker.client.playback;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record PlaybackDemandProfile(int maxVisibleNotes, int maxActiveEffects,
                                    int recommendedNotePoolCapacity, int recommendedTextPoolCapacity) {
    private static final double NOTE_WINDOW_BEATS = 8.0;

    public static PlaybackDemandProfile from(ChartManifest chart) {
        List<Double> noteBeats = new ArrayList<>();
        if (chart != null && chart.notes != null) for (ChartManifest.Note note : chart.notes) {
            if (note != null && Double.isFinite(note.beat)) noteBeats.add(note.beat);
        }
        noteBeats.sort(Comparator.naturalOrder());
        int maxNotes = 0;
        int windowStart = 0;
        for (int windowEnd = 0; windowEnd < noteBeats.size(); windowEnd++) {
            while (noteBeats.get(windowEnd) - noteBeats.get(windowStart) > NOTE_WINDOW_BEATS) windowStart++;
            maxNotes = Math.max(maxNotes, windowEnd - windowStart + 1);
        }
        int maxEffects = 0;
        if (chart != null && chart.effects != null) {
            List<Double> effectBeats = new ArrayList<>();
            for (JsonObject effect : chart.effects) {
                if (effect != null && effect.has("beat") && effect.get("beat").isJsonPrimitive() && effect.getAsJsonPrimitive("beat").isNumber()) {
                    double beat = effect.get("beat").getAsDouble();
                    if (Double.isFinite(beat)) effectBeats.add(beat);
                }
            }
            effectBeats.sort(Comparator.naturalOrder());
            int start = 0;
            for (int end = 0; end < effectBeats.size(); end++) {
                while (effectBeats.get(end) - effectBeats.get(start) > NOTE_WINDOW_BEATS) start++;
                maxEffects = Math.max(maxEffects, end - start + 1);
            }
        }
        return new PlaybackDemandProfile(maxNotes, maxEffects,
                Math.max(8, maxNotes), Math.max(4, maxEffects));
    }
}