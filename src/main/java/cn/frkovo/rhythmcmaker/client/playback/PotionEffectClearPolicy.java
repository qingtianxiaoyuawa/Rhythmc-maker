package cn.frkovo.rhythmcmaker.client.playback;

import java.util.HashSet;
import java.util.Set;

public final class PotionEffectClearPolicy {
    private PotionEffectClearPolicy() {}

    public static Set<String> matchingActiveEffects(Set<String> activeChartEffects, Set<String> requestedEffects, boolean clearAll, boolean explicitList) {
        if (clearAll && !explicitList) return new HashSet<>(activeChartEffects);
        if (!explicitList) return Set.of();
        Set<String> matchingEffects = new HashSet<>(activeChartEffects);
        matchingEffects.retainAll(requestedEffects);
        return matchingEffects;
    }
}
