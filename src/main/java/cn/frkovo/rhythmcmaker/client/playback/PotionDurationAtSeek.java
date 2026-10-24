package cn.frkovo.rhythmcmaker.client.playback;

public final class PotionDurationAtSeek {
    private PotionDurationAtSeek() {}

    public static int remainingTicks(int durationTicks, double elapsedSongSeconds, double playbackRate) {
        if (durationTicks <= 0 || playbackRate <= 0.0) return 0;
        int elapsedTicks = (int) Math.ceil(Math.max(0.0, elapsedSongSeconds) * 20.0);
        int remainingSongTicks = durationTicks - elapsedTicks;
        if (remainingSongTicks <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(remainingSongTicks / playbackRate));
    }
}
