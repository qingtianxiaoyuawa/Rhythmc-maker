package cn.frkovo.rhythmcmaker.chart;

import java.util.List;

public enum TrackEventChannel {
    SPEED("speedEvents", 0.0),
    X_TRANSFORM("xTransformEvents", 0.0),
    Y_TRANSFORM("yTransformEvents", 0.0),
    Z_TRANSFORM("zTransformEvents", 0.0),
    X_ROTATE("xRotateEvents", 0.0),
    Y_ROTATE("yRotateEvents", 0.0),
    Z_ROTATE("zRotateEvents", 0.0),
    X_SCALE("xScaleEvents", 1.0),
    Y_SCALE("yScaleEvents", 1.0),
    Z_SCALE("zScaleEvents", 1.0);

    private final String jsonKey;
    private final double defaultValue;

    TrackEventChannel(String jsonKey, double defaultValue) {
        this.jsonKey = jsonKey;
        this.defaultValue = defaultValue;
    }

    public String jsonKey() {
        return jsonKey;
    }

    public double defaultValue() {
        return defaultValue;
    }

    public List<ChartManifest.NumEvent> events(ChartManifest.Track track) {
        return switch (this) {
            case SPEED -> track.speedEvents;
            case X_TRANSFORM -> track.xTransformEvents;
            case Y_TRANSFORM -> track.yTransformEvents;
            case Z_TRANSFORM -> track.zTransformEvents;
            case X_ROTATE -> track.xRotateEvents;
            case Y_ROTATE -> track.yRotateEvents;
            case Z_ROTATE -> track.zRotateEvents;
            case X_SCALE -> track.xScaleEvents;
            case Y_SCALE -> track.yScaleEvents;
            case Z_SCALE -> track.zScaleEvents;
        };
    }

    public void setEvents(ChartManifest.Track track, List<ChartManifest.NumEvent> events) {
        switch (this) {
            case SPEED -> track.speedEvents = events;
            case X_TRANSFORM -> track.xTransformEvents = events;
            case Y_TRANSFORM -> track.yTransformEvents = events;
            case Z_TRANSFORM -> track.zTransformEvents = events;
            case X_ROTATE -> track.xRotateEvents = events;
            case Y_ROTATE -> track.yRotateEvents = events;
            case Z_ROTATE -> track.zRotateEvents = events;
            case X_SCALE -> track.xScaleEvents = events;
            case Y_SCALE -> track.yScaleEvents = events;
            case Z_SCALE -> track.zScaleEvents = events;
        }
    }
}
