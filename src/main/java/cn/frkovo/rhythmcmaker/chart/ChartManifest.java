package cn.frkovo.rhythmcmaker.chart;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public final class ChartManifest {
    public String id;
    public String title;
    public String artist;
    public String charter;
    public double bpm;
    public List<BpmEvent> bpms = new ArrayList<>();
    /** RhythMC offset in milliseconds; positive values move notes later. */
    public long offsetMillis = 0;
    public boolean sceneInitialized = false;
    public boolean sceneImportIncomplete = false;
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
    /** Persisted stable dynamic editor-dimension path. */
    public String dimensionId;
    /** Persisted playback start chunk for this chart. */
    public int selectedStartChunk = 1;
    /** Persisted revision for editor note display entities. */
    public long noteDisplayRevision;
    /** Furthest editor-track column that still needs stale display cleanup. */
    public int noteDisplayCleanupTrackLength;
    /** Widest editor lane count that still needs stale display cleanup. */
    public int noteDisplayCleanupLaneCount;
    public java.util.List<SpeedEvent> speedEvents = new java.util.ArrayList<>();
    /** Complete RhythMC 3.0 track data. Legacy charts use one implicit track with id 0. */
    public java.util.List<Track> tracks = new java.util.ArrayList<>();
    /** Independent visual effect-track events, kept in RhythMC 3.0 JSON form. */
    public java.util.List<JsonObject> effects = new java.util.ArrayList<>();
    public String initialArena;
    public java.util.Map<String, String> arenaBindings = new java.util.LinkedHashMap<>();
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

    public static final class NumEvent {
        public double startBeat;
        public double endBeat;
        public double startValue;
        public double endValue;
        public int easingType;

        public NumEvent() {}

        public NumEvent(double startBeat, double endBeat, double startValue, double endValue, int easingType) {
            this.startBeat = startBeat;
            this.endBeat = endBeat;
            this.startValue = startValue;
            this.endValue = endValue;
            this.easingType = easingType;
        }
    }

    public static final class Track {
        public int id;
        public java.util.List<NumEvent> speedEvents = new java.util.ArrayList<>();
        public java.util.List<NumEvent> xTransformEvents = new java.util.ArrayList<>();
        public java.util.List<NumEvent> yTransformEvents = new java.util.ArrayList<>();
        public java.util.List<NumEvent> zTransformEvents = new java.util.ArrayList<>();
        public java.util.List<NumEvent> xRotateEvents = new java.util.ArrayList<>();
        public java.util.List<NumEvent> yRotateEvents = new java.util.ArrayList<>();
        public java.util.List<NumEvent> zRotateEvents = new java.util.ArrayList<>();
        public java.util.List<NumEvent> xScaleEvents = new java.util.ArrayList<>();
        public java.util.List<NumEvent> yScaleEvents = new java.util.ArrayList<>();
        public java.util.List<NumEvent> zScaleEvents = new java.util.ArrayList<>();

        public Track() {}
        public Track(int id) { this.id = id; }
    }

    public static final class Note {
        public String id;
        public int type;
        public int trackId;
        public double beat;
        public double time;
        public double x;
        public double y;
        public double z;
        /** Original RhythMC 3.0 spatial position; Maker z remains the editor time axis. */
        public Double sourceX;
        public Double sourceY;
        public Double sourceZ;
        public double scaleX = 1.0;
        public double scaleY = 1.0;
        public double scaleZ = 1.0;
        public double rotationX;
        public double rotationY;
        public double rotationZ;
        public int holdGroup = -1;

        public Note() {
        }
    }

    public static final class BpmEvent {
        public double beat;
        public double bpm;

        public BpmEvent() {}

        public BpmEvent(double beat, double bpm) {
            this.beat = beat;
            this.bpm = bpm;
        }
    }

    public ChartManifest() {
    }
}
