package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.common.effect.editor.EffectEditorLayout;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

public final class EffectTrackLayoutModel {
    private final List<EffectEditorLayout.Track> tracks = new ArrayList<>();
    private final List<EffectEditorLayout.Position> positions = new ArrayList<>();
    private final Map<String, List<EffectEditorLayout.Track>> tracksByType = new HashMap<>();
    private final Map<String, EffectEditorLayout.Track> tracksById = new HashMap<>();
    private final Map<String, NavigableMap<Double, Integer>> beatsByTrack = new HashMap<>();
    private final Map<String, Integer> trackIndexesById = new HashMap<>();
    private final Map<String, NavigableMap<Double, BitSet>> occupiedTracksByTypeBeat = new HashMap<>();
    private final BitSet occupiedTracksScratch = new BitSet();
    private long revision;

    public EffectTrackLayoutModel(EffectEditorLayout saved) {
        if (saved != null && saved.tracks != null) {
            for (EffectEditorLayout.Track track : saved.tracks) addSavedTrack(track);
        }
    }

    public long revision() {
        return revision;
    }

    public List<EffectEditorLayout.Track> tracks() {
        return List.copyOf(tracks);
    }

    public EffectEditorLayout.Track addTrack(String type) {
        EffectEditorLayout.Track track = addIndexedTrack(type);
        revision++;
        return track;
    }

    public void addEvent(String type, double beat, int denominator, String preferredTrack) {
        validatePosition(beat, denominator);
        String target = availableTrack(type, beat, preferredTrack, -1);
        positions.add(new EffectEditorLayout.Position(target, beat, denominator));
        addBeat(target, beat);
        revision++;
    }

    public EffectEditorLayout.Position position(int index) {
        return positions.get(index);
    }

    public boolean hasOverlappingEventsAtPosition(int index) {
        EffectEditorLayout.Position position = positions.get(index);
        NavigableMap<Double, Integer> beats = beatsByTrack.get(position.trackId);
        return beats != null && beats.getOrDefault(position.beat, 0) > 1;
    }

    public boolean hasTrack(String id, String type) {
        EffectEditorLayout.Track track = tracksById.get(id);
        return track != null && track.type.equals(type);
    }

    public void move(int index, String type, double beat) {
        EffectEditorLayout.Position position = positions.get(index);
        if (!Double.isFinite(beat) || beat < 0.0) throw new IllegalArgumentException("Invalid effect time");
        String target = availableTrack(type, beat, position.trackId, index);
        removeBeat(position.trackId, position.beat);
        position.trackId = target;
        position.beat = beat;
        addBeat(target, beat);
        revision++;
    }

    public void removeEvent(int index) {
        EffectEditorLayout.Position position = positions.remove(index);
        removeBeat(position.trackId, position.beat);
        revision++;
    }

    public void previewMove(int index, String type, double beat, String trackId, int denominator) {
        validatePosition(beat, denominator);
        EffectEditorLayout.Track track = tracksById.get(trackId);
        if (track == null || !track.type.equals(type)) throw new IllegalArgumentException("Effect track type mismatch");
        EffectEditorLayout.Position position = positions.get(index);
        removeBeat(position.trackId, position.beat);
        position.trackId = trackId;
        position.beat = beat;
        position.denominator = denominator;
        addBeat(trackId, beat);
        revision++;
    }

    public void removeTrack(String id) {
        if (positions.stream().anyMatch(position -> id.equals(position.trackId))) {
            throw new IllegalStateException("Track still contains events");
        }
        EffectEditorLayout.Track removed = tracksById.remove(id);
        if (removed == null) return;
        tracks.removeIf(track -> id.equals(track.id));
        tracksByType.get(removed.type).removeIf(track -> id.equals(track.id));
        beatsByTrack.remove(id);
        rebuildTrackIndexes(removed.type);
        revision++;
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
        tracksByType.clear();
        tracksById.clear();
        beatsByTrack.clear();
        trackIndexesById.clear();
        occupiedTracksByTypeBeat.clear();
        revision++;
        if (saved == null) return;
        if (saved.tracks != null) for (EffectEditorLayout.Track track : saved.tracks) addSavedTrack(track);
        if (saved.positions != null) {
            for (EffectEditorLayout.Position position : saved.positions) {
                positions.add(new EffectEditorLayout.Position(position.trackId, position.beat, position.denominator));
                addBeat(position.trackId, position.beat);
            }
        }
    }

    private String availableTrack(String type, double beat, String preferred, int excluded) {
        EffectEditorLayout.Track preferredTrack = tracksById.get(preferred);
        if (preferredTrack != null && preferredTrack.type.equals(type) && free(preferredTrack.id, beat, excluded)) {
            return preferredTrack.id;
        }
        List<EffectEditorLayout.Track> tracksForType = tracksByType.get(type);
        if (tracksForType == null) return addIndexedTrack(type).id;
        BitSet occupied = occupiedTracksScratch;
        occupied.clear();
        NavigableMap<Double, BitSet> occupiedBeats = occupiedTracksByTypeBeat.get(type);
        if (occupiedBeats != null) {
            for (var entry : occupiedBeats.subMap(beat - 1.0e-9, true, beat + 1.0e-9, true).entrySet()) {
                if (Math.abs(entry.getKey() - beat) < 1.0e-9) occupied.or(entry.getValue());
            }
        }
        EffectEditorLayout.Position excludedPosition = excluded < 0 ? null : positions.get(excluded);
        EffectEditorLayout.Track excludedTrack = excludedPosition == null ? null : tracksById.get(excludedPosition.trackId);
        if (excludedTrack != null && type.equals(excludedTrack.type)
                && free(excludedPosition.trackId, beat, excluded)) {
            Integer excludedTrackIndex = trackIndexesById.get(excludedPosition.trackId);
            if (excludedTrackIndex != null) occupied.clear(excludedTrackIndex);
        }
        int availableIndex = occupied.nextClearBit(0);
        if (availableIndex < tracksForType.size()) return tracksForType.get(availableIndex).id;
        return addIndexedTrack(type).id;
    }

    private boolean free(String trackId, double beat, int excluded) {
        NavigableMap<Double, Integer> beats = beatsByTrack.get(trackId);
        if (beats == null || beats.isEmpty()) return true;
        EffectEditorLayout.Position excludedPosition = excluded < 0 ? null : positions.get(excluded);
        for (var entry : beats.subMap(beat - 1.0e-9, true, beat + 1.0e-9, true).entrySet()) {
            if (Math.abs(entry.getKey() - beat) >= 1.0e-9) continue;
            int occupancy = entry.getValue();
            if (excludedPosition != null && trackId.equals(excludedPosition.trackId)
                    && Double.compare(entry.getKey(), excludedPosition.beat) == 0) occupancy--;
            if (occupancy > 0) return false;
        }
        return true;
    }

    private EffectEditorLayout.Track addIndexedTrack(String type) {
        EffectEditorLayout.Track track = new EffectEditorLayout.Track(UUID.randomUUID().toString(), type);
        tracks.add(track);
        tracksByType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(track);
        tracksById.put(track.id, track);
        beatsByTrack.put(track.id, new TreeMap<>());
        trackIndexesById.put(track.id, tracksByType.get(type).size() - 1);
        occupiedTracksByTypeBeat.computeIfAbsent(type, ignored -> new TreeMap<>());
        return track;
    }

    private void addSavedTrack(EffectEditorLayout.Track source) {
        EffectEditorLayout.Track track = new EffectEditorLayout.Track(source.id, source.type);
        tracks.add(track);
        tracksByType.computeIfAbsent(track.type, ignored -> new ArrayList<>()).add(track);
        tracksById.putIfAbsent(track.id, track);
        beatsByTrack.putIfAbsent(track.id, new TreeMap<>());
        trackIndexesById.putIfAbsent(track.id, tracksByType.get(track.type).size() - 1);
        occupiedTracksByTypeBeat.computeIfAbsent(track.type, ignored -> new TreeMap<>());
    }

    private void addBeat(String trackId, double beat) {
        NavigableMap<Double, Integer> beats = beatsByTrack.get(trackId);
        EffectEditorLayout.Track track = tracksById.get(trackId);
        Integer trackIndex = trackIndexesById.get(trackId);
        if (beats == null || track == null || trackIndex == null) throw new IllegalStateException("Effect track does not exist: " + trackId);
        beats.merge(beat, 1, Integer::sum);
        occupiedTracksByTypeBeat.get(track.type).computeIfAbsent(beat, ignored -> new BitSet()).set(trackIndex);
    }

    private void removeBeat(String trackId, double beat) {
        NavigableMap<Double, Integer> beats = beatsByTrack.get(trackId);
        if (beats == null) throw new IllegalStateException("Effect track does not exist: " + trackId);
        Integer occupancy = beats.get(beat);
        if (occupancy == null) throw new IllegalStateException("Effect position index is inconsistent");
        if (occupancy > 1) {
            beats.put(beat, occupancy - 1);
            return;
        }
        beats.remove(beat);
        EffectEditorLayout.Track track = tracksById.get(trackId);
        Integer trackIndex = trackIndexesById.get(trackId);
        if (track == null || trackIndex == null) throw new IllegalStateException("Effect track does not exist: " + trackId);
        NavigableMap<Double, BitSet> occupiedBeats = occupiedTracksByTypeBeat.get(track.type);
        BitSet occupiedTracks = occupiedBeats.get(beat);
        occupiedTracks.clear(trackIndex);
        if (occupiedTracks.isEmpty()) occupiedBeats.remove(beat);
    }

    private void rebuildTrackIndexes(String type) {
        List<EffectEditorLayout.Track> tracksForType = tracksByType.get(type);
        NavigableMap<Double, BitSet> occupiedBeats = new TreeMap<>();
        for (int trackIndex = 0; trackIndex < tracksForType.size(); trackIndex++) {
            EffectEditorLayout.Track track = tracksForType.get(trackIndex);
            trackIndexesById.put(track.id, trackIndex);
            for (Double beat : beatsByTrack.get(track.id).keySet()) {
                occupiedBeats.computeIfAbsent(beat, ignored -> new BitSet()).set(trackIndex);
            }
        }
        occupiedTracksByTypeBeat.put(type, occupiedBeats);
    }

    private static void validatePosition(double beat, int denominator) {
        if (!Double.isFinite(beat) || beat < 0.0 || denominator < 1 || denominator > 32) {
            throw new IllegalArgumentException("Invalid effect position");
        }
    }
}
