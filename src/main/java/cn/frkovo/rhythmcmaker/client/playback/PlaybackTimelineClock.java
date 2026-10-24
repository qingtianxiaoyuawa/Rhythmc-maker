package cn.frkovo.rhythmcmaker.client.playback;

public final class PlaybackTimelineClock {
    private PlaybackTimelineClock() {}

    public static double songTimeAfterWallMillis(double startSongTime, long durationMillis, double playbackRate) {
        if (durationMillis <= 0L || playbackRate <= 0.0) return startSongTime;
        return startSongTime + durationMillis * playbackRate / 1000.0;
    }
}
