package cn.frkovo.rhythmcmaker.chart;

import com.google.gson.JsonObject;

public final class ChartManifest {
    public String id;
    public String title;
    public String artist;
    public String charter;
    public double bpm;
    /** RhythMC offset in milliseconds; positive values move notes later. */
    public long offsetMillis = 0;
    public boolean sceneInitialized = false;
    public String difficulty;
    public double level;
    public int divisionsPerChunk = 4;
    /** Odd number of editable horizontal lanes in the Maker grid. */
    public int laneCount = 3;
    public int beatsPerMeasure = 0;
    public String coverItemId;
    public String audioFile;
    public String lastEdited;
    public double durationSeconds;
    public double totalBeats;
    public int chunkCount;
    public int trackLength;
    /** Legacy static editor-dimension slot retained for old manifests. */
    public int editorSlot;
    /** Persisted stable dynamic editor-dimension path. */
    public String dimensionId;
    /** Persisted playback start chunk for this chart. */
    public int selectedStartChunk = 1;
    public java.util.List<SpeedEvent> speedEvents = new java.util.ArrayList<>();
    /** Independent visual effect-track events, kept in RhythMC 3.0 JSON form. */
    public java.util.List<JsonObject> effects = new java.util.ArrayList<>();
    public java.util.List<Note> notes = new java.util.ArrayList<>();

    public static final class SpeedEvent {
        public double startBeat;
        public double endBeat;
        public double startValue;
        public double endValue;
        public int easing;

        public SpeedEvent() {}

        public SpeedEvent(double startBeat, double endBeat, double startValue, double endValue, int easing) {
            this.startBeat = startBeat;
            this.endBeat = endBeat;
            this.startValue = startValue;
            this.endValue = endValue;
            this.easing = easing;
        }
    }

    public static final class Note {
        public String id;
        public int type;
        public double beat;
        public double time;
        public double x;
        public double y;
        public double z;

        public Note() {
        }
    }

    public ChartManifest() {
    }
}
