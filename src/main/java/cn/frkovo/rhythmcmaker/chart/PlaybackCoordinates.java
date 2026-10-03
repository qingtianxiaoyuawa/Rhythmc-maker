package cn.frkovo.rhythmcmaker.chart;

public final class PlaybackCoordinates {
    private static final double PLAYBACK_FLOW_MULTIPLIER = 5.0;
    public static final double CHART_ORIGIN_Z = -3.0;
    private PlaybackCoordinates() {}

    public static TrackPlayback.Prepared prepareDefaultTrack(ChartManifest chart) {
        if (chart != null && chart.tracks != null) {
            for (ChartManifest.Track track : chart.tracks) {
                if (track != null && track.id == 0) return TrackPlayback.prepare(track, chart.speedEvents);
            }
        }
        return TrackPlayback.prepare(null, chart == null ? null : chart.speedEvents);
    }

    public static double beatAtSongTime(ChartManifest chart, ChartTiming.Prepared timing, double songTime) {
        double offsetSeconds = chart == null ? 0.0 : chart.offsetMillis / 1000.0;
        return timing.secondsToBeat(songTime - offsetSeconds);
    }

    public static double songTimeAtBeat(ChartManifest chart, ChartTiming.Prepared timing, double beat) {
        if (chart == null || timing == null || !Double.isFinite(beat)) return 0.0;
        return timing.beatToSeconds(beat) + chart.offsetMillis / 1000.0;
    }

    public static double distanceAtBeat(ChartManifest chart, TrackPlayback.Prepared track, double beat, double playerSpeed) {
        return track.distanceAt(beat) * normalizedPlayerSpeed(playerSpeed) * PLAYBACK_FLOW_MULTIPLIER;
    }

    public static double distanceBetweenBeats(ChartManifest chart, TrackPlayback.Prepared track, double fromBeat, double toBeat, double playerSpeed) {
        return (track.distanceAt(toBeat) - track.distanceAt(fromBeat))
                * normalizedPlayerSpeed(playerSpeed) * PLAYBACK_FLOW_MULTIPLIER;
    }

    public static double beatAtWorldZ(ChartManifest chart, TrackPlayback.Prepared track, double worldZ, double playerSpeed) {
        double targetDistance = Math.max(0.0, CHART_ORIGIN_Z - worldZ);
        double factor = normalizedPlayerSpeed(playerSpeed) * PLAYBACK_FLOW_MULTIPLIER;
        double high = Math.max(1.0, chart == null ? 1.0 : Math.max(chart.totalBeats, chart.chunkCount * 32.0) + 1.0);
        while (track.distanceAt(high) * factor < targetDistance && high < 1_000_000.0) high *= 2.0;
        double low = 0.0;
        for (int iteration = 0; iteration < 64; iteration++) {
            double middle = (low + high) * 0.5;
            if (track.distanceAt(middle) * factor < targetDistance) low = middle; else high = middle;
        }
        return (low + high) * 0.5;
    }
    public static double worldZAtBeat(ChartManifest chart, TrackPlayback.Prepared track, double beat, double playerSpeed) {
        return CHART_ORIGIN_Z - distanceAtBeat(chart, track, beat, playerSpeed);
    }

    public static double editorWorldZAtBeat(ChartManifest chart, double beat) {
        double divisions = chart == null ? 1.0 : Math.max(1, Math.min(32, chart.divisionsPerChunk));
        return CHART_ORIGIN_Z - Math.max(0.0, beat) * divisions;
    }

    public static double editorWorldZAtSongTime(ChartManifest chart, ChartTiming.Prepared timing, double songTime) {
        if (chart == null || timing == null || !Double.isFinite(songTime)) return CHART_ORIGIN_Z;
        return editorWorldZAtBeat(chart, beatAtSongTime(chart, timing, songTime));
    }

    public static double editorBeatAtWorldZ(ChartManifest chart, double worldZ) {
        double divisions = divisionsPerChunk(chart);
        return Math.max(0.0, (CHART_ORIGIN_Z - worldZ) / divisions);
    }

    private static double divisionsPerChunk(ChartManifest chart) {
        return chart == null ? 1.0 : Math.max(1, Math.min(32, chart.divisionsPerChunk));
    }

    private static double normalizedPlayerSpeed(double playerSpeed) {
        return Double.isFinite(playerSpeed) ? Math.max(0.1, Math.min(5.0, playerSpeed)) : 1.0;
    }
}
