package cn.frkovo.rhythmcmaker.client.playback;

public final class HoldVisualPreloadRules {
    private HoldVisualPreloadRules() {
    }

    public static boolean shouldWaitForApproach(double distance, double approachDistance) {
        return Double.isFinite(distance) && distance > approachDistance;
    }
}