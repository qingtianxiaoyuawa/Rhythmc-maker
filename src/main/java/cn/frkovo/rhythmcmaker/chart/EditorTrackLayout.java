package cn.frkovo.rhythmcmaker.chart;

/** 制谱器轨道布局：轨道宽度（奇数车道数）与每个 beat 的分数。 */
public record EditorTrackLayout(int laneCount, int divisionsPerBeat, int trackHeight) {
    public static final int MIN_LANE_COUNT = 1;
    public static final int MAX_LANE_COUNT = 9;
    public static final int DEFAULT_LANE_COUNT = 3;
    public static final int MIN_DIVISIONS_PER_BEAT = 1;
    public static final int MAX_DIVISIONS_PER_BEAT = 32;
    public static final int DEFAULT_HEIGHT = 3;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 5;

    public static boolean isSupportedHeight(int height) {
        return height >= MIN_HEIGHT && height <= MAX_HEIGHT;
    }

    public int halfWidth() { return (laneCount - 1) / 2; }
    public int maximumRelativeY() { return trackHeight - 1; }
    public int maximumWorldY() { return 65 + trackHeight; }

    public boolean hasNotesAbove(ChartManifest chart) {
        return chart.notes != null && chart.notes.stream().anyMatch(note -> note != null && note.type != 2
                && note.y > maximumWorldY() + 1.0e-6);
    }

    public static boolean isSupportedLaneCount(int laneCount) {
        return laneCount >= MIN_LANE_COUNT && laneCount <= MAX_LANE_COUNT && (laneCount & 1) == 1;
    }

    public static boolean isSupportedDivisionsPerBeat(int divisionsPerBeat) {
        return divisionsPerBeat >= MIN_DIVISIONS_PER_BEAT && divisionsPerBeat <= MAX_DIVISIONS_PER_BEAT;
    }

    /** 不支持的车道数一律回落到默认轨道宽度。 */
    public static int supportedLaneCount(int laneCount) {
        return isSupportedLaneCount(laneCount) ? laneCount : DEFAULT_LANE_COUNT;
    }

    public static int boundedDivisionsPerBeat(int divisionsPerBeat) {
        return Math.max(MIN_DIVISIONS_PER_BEAT, Math.min(MAX_DIVISIONS_PER_BEAT, divisionsPerBeat));
    }

    public static EditorTrackLayout of(int laneCount, int divisionsPerBeat) {
        return of(laneCount, divisionsPerBeat, DEFAULT_HEIGHT);
    }

    public static EditorTrackLayout of(int laneCount, int divisionsPerBeat, int height) {
        return new EditorTrackLayout(supportedLaneCount(laneCount), boundedDivisionsPerBeat(divisionsPerBeat),
                isSupportedHeight(height) ? height : DEFAULT_HEIGHT);
    }

    public static EditorTrackLayout of(ChartManifest chart) {
        return of(chart.laneCount, chart.divisionsPerBeat, chart.trackHeight);
    }

    /** 写回谱面，并清空历史遗留的小节拍数字段。 */
    public void applyTo(ChartManifest chart) {
        chart.laneCount = laneCount;
        chart.trackHeight = trackHeight;
        chart.divisionsPerBeat = divisionsPerBeat;
        chart.beatsPerMeasure = 0;
    }
}
