package cn.frkovo.rhythmcmaker.common.effect.editor;

import java.util.ArrayList;
import java.util.List;

public final class EffectEditorLayout {
    public List<Track> tracks = new ArrayList<>();
    public List<Position> positions = new ArrayList<>();
    public List<KeyframePosition> keyframes = new ArrayList<>();

    public static final class KeyframePosition {
        public int eventIndex;
        public int channelIndex;
        public int keyframeIndex;
        public double beat;
        public int denominator;

        public KeyframePosition() {
        }

        public KeyframePosition(int eventIndex, int channelIndex, int keyframeIndex, double beat, int denominator) {
            this.eventIndex = eventIndex;
            this.channelIndex = channelIndex;
            this.keyframeIndex = keyframeIndex;
            this.beat = beat;
            this.denominator = denominator;
        }
    }

    public static final class Track {
        public String id;
        public String type;

        public Track() {
        }

        public Track(String id, String type) {
            this.id = id;
            this.type = type;
        }
    }

    public static final class Position {
        public String trackId;
        public double beat;
        public int denominator;

        public Position() {
        }

        public Position(String trackId, double beat, int denominator) {
            this.trackId = trackId;
            this.beat = beat;
            this.denominator = denominator;
        }

        public String label() {
            int chunk = (int) Math.floor(beat);
            double numerator = (beat - chunk) * denominator;
            String text = Math.abs(numerator - Math.rint(numerator)) < 1.0e-8
                    ? Long.toString(Math.round(numerator)) : Double.toString(numerator);
            return "Chunk " + (chunk + 1) + " + " + text + "/" + denominator;
        }
    }
}
