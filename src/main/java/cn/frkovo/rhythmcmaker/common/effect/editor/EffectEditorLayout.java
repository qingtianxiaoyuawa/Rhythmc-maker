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
            int safeDenominator = Math.max(1, Math.min(32, denominator));
            long numerator = Math.round((beat - chunk) * safeDenominator);
            if (numerator >= safeDenominator) {
                chunk++;
                numerator = 0;
            }
            return "Chunk " + (chunk + 1) + " + " + numerator + "/" + safeDenominator;
        }
    }
}
