package cn.frkovo.rhythmcmaker.client.playback;

public final class TextDisplayDurationAtSeek {
    private TextDisplayDurationAtSeek() {}

    public static long remainingMillis(long durationMillis, double elapsedSongSeconds) {
        if (durationMillis <= 0L) return durationMillis;
        double remainingMillis = durationMillis - Math.max(0.0, elapsedSongSeconds) * 1000.0;
        if (remainingMillis <= 0.0) return 0L;
        return (long) Math.ceil(remainingMillis);
    }
}
