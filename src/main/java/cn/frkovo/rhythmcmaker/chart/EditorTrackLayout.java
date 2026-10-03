package cn.frkovo.rhythmcmaker.chart;

/** 制谱器轨道布局：轨道宽度（奇数车道数）与每个 chunk 的分数。 */
public record EditorTrackLayout(int laneCount, int divisionsPerChunk) {
    public static final int MIN_LANE_COUNT = 1;
    public static final int MAX_LANE_COUNT = 9;
    public static final int DEFAULT_LANE_COUNT = 3;
    public static final int MIN_DIVISIONS_PER_CHUNK = 1;
    public static final int MAX_DIVISIONS_PER_CHUNK = 32;

    public static boolean isSupportedLaneCount(int laneCount) {
        return laneCount >= MIN_LANE_COUNT && laneCount <= MAX_LANE_COUNT && (laneCount & 1) == 1;
    }

    public static boolean isSupportedDivisionsPerChunk(int divisionsPerChunk) {
        return divisionsPerChunk >= MIN_DIVISIONS_PER_CHUNK && divisionsPerChunk <= MAX_DIVISIONS_PER_CHUNK;
    }

    /** 不支持的车道数一律回落到默认轨道宽度。 */
    public static int supportedLaneCount(int laneCount) {
        return isSupportedLaneCount(laneCount) ? laneCount : DEFAULT_LANE_COUNT;
    }

    public static int boundedDivisionsPerChunk(int divisionsPerChunk) {
        return Math.max(MIN_DIVISIONS_PER_CHUNK, Math.min(MAX_DIVISIONS_PER_CHUNK, divisionsPerChunk));
    }

    public static EditorTrackLayout of(int laneCount, int divisionsPerChunk) {
        return new EditorTrackLayout(supportedLaneCount(laneCount), boundedDivisionsPerChunk(divisionsPerChunk));
    }

    public static EditorTrackLayout of(ChartManifest chart) {
        return of(chart.laneCount, chart.divisionsPerChunk);
    }

    /** 写回谱面，并清空历史遗留的小节拍数字段。 */
    public void applyTo(ChartManifest chart) {
        chart.laneCount = laneCount;
        chart.divisionsPerChunk = divisionsPerChunk;
        chart.beatsPerMeasure = 0;
    }
}
