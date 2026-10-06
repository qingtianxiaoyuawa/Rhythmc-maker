package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.common.effect.editor.EffectEditorLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EffectTrackLayoutModel {
    private final List<EffectEditorLayout.Track> tracks = new ArrayList<>();
    private final List<EffectEditorLayout.Position> positions = new ArrayList<>();

    public EffectTrackLayoutModel(EffectEditorLayout saved) {
        if (saved != null && saved.tracks != null) {
            for (EffectEditorLayout.Track track : saved.tracks) {
                tracks.add(new EffectEditorLayout.Track(track.id, track.type));
            }
        }
    }

    public List<EffectEditorLayout.Track> tracks() {
        return List.copyOf(tracks);
    }

    public EffectEditorLayout.Track addTrack(String type) {
        EffectEditorLayout.Track track = new EffectEditorLayout.Track(UUID.randomUUID().toString(), type);
        tracks.add(track);
        return track;
    }

    public void addEvent(String type, double beat, int denominator, String preferredTrack) {
        if (!Double.isFinite(beat) || beat < 0.0 || denominator < 1 || denominator > 32) {
            throw new IllegalArgumentException("Invalid effect position");
        }
        String target = availableTrack(type, beat, preferredTrack, -1);
        positions.add(new EffectEditorLayout.Position(target, beat, denominator));
    }

    public EffectEditorLayout.Position position(int index) {
        return positions.get(index);
    }

    public void move(int index, String type, double beat) {
        EffectEditorLayout.Position position = positions.get(index);
        if (!Double.isFinite(beat) || beat < 0.0) throw new IllegalArgumentException("Invalid effect time");
        position.trackId = availableTrack(type, beat, position.trackId, index);
        position.beat = beat;
    }

    public void removeEvent(int index) {
        positions.remove(index);
    }

    public void removeTrack(String id) {
        if (positions.stream().anyMatch(position -> id.equals(position.trackId))) {
            throw new IllegalStateException("Track still contains events");
        }
        tracks.removeIf(track -> id.equals(track.id));
    }

    public EffectEditorLayout snapshot() {
        EffectEditorLayout saved = new EffectEditorLayout();
        for (EffectEditorLayout.Track track : tracks) saved.tracks.add(new EffectEditorLayout.Track(track.id, track.type));
        for (EffectEditorLayout.Position position : positions) {
            saved.positions.add(new EffectEditorLayout.Position(position.trackId, position.beat, position.denominator));
        }
        return saved;
    }

    public void restore(EffectEditorLayout saved) {
        tracks.clear();
        positions.clear();
        if (saved == null) return;
        if (saved.tracks != null) {
            for (EffectEditorLayout.Track track : saved.tracks) {
                tracks.add(new EffectEditorLayout.Track(track.id, track.type));
            }
        }
        if (saved.positions != null) {
            for (EffectEditorLayout.Position position : saved.positions) {
                positions.add(new EffectEditorLayout.Position(position.trackId, position.beat, position.denominator));
            }
        }
    }

    private String availableTrack(String type, double beat, String preferred, int excluded) {
        for (EffectEditorLayout.Track track : tracks) {
            if (track.id.equals(preferred) && track.type.equals(type) && free(track.id, beat, excluded)) return track.id;
        }
        for (EffectEditorLayout.Track track : tracks) {
            if (track.type.equals(type) && free(track.id, beat, excluded)) return track.id;
        }
        return addTrack(type).id;
    }

    private boolean free(String trackId, double beat, int excluded) {
        for (int index = 0; index < positions.size(); index++) {
            EffectEditorLayout.Position position = positions.get(index);
            if (index != excluded && trackId.equals(position.trackId) && Math.abs(position.beat - beat) < 1.0e-9) return false;
        }
        return true;
    }
}
