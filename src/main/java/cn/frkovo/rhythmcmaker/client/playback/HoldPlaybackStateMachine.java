package cn.frkovo.rhythmcmaker.client.playback;

import cn.frkovo.rhythmcmaker.chart.HoldInterval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HoldPlaybackStateMachine {
    public enum State { WAITING, ACTIVE, ENDED, CANCELLED }
    public enum TransitionType { STARTED, SUSTAINED, ENDED, CANCELLED, SEEKED }

    public record Transition(String intervalId, TransitionType type, double beat) {}

    private final Map<String, HoldInterval> intervals = new LinkedHashMap<>();
    private final Map<String, State> states = new LinkedHashMap<>();
    private double currentBeat;
    private boolean initialized;

    public void reset(Collection<HoldInterval> source, double beat) {
        intervals.clear();
        states.clear();
        for (HoldInterval interval : source) {
            intervals.put(interval.id(), interval);
            states.put(interval.id(), stateAt(interval, beat));
        }
        currentBeat = beat;
        initialized = true;
    }

    public List<Transition> update(double beat) {
        if (!Double.isFinite(beat)) return List.of();
        if (!initialized) reset(intervals.values(), beat);
        if (beat < currentBeat) return seek(beat);
        List<Transition> transitions = new ArrayList<>();
        for (HoldInterval interval : intervals.values()) {
            State previous = states.get(interval.id());
            if (previous == State.CANCELLED) continue;
            State next = stateAt(interval, beat);
            if (previous != next) {
                if (next == State.ACTIVE) transitions.add(new Transition(interval.id(), TransitionType.STARTED, interval.startBeat()));
                else if (next == State.ENDED) {
                    if (previous == State.WAITING && currentBeat < interval.startBeat()) {
                        transitions.add(new Transition(interval.id(), TransitionType.STARTED, interval.startBeat()));
                    }
                    transitions.add(new Transition(interval.id(), TransitionType.ENDED, interval.endBeat()));
                }
                states.put(interval.id(), next);
            } else if (next == State.ACTIVE) {
                transitions.add(new Transition(interval.id(), TransitionType.SUSTAINED, beat));
            }
        }
        currentBeat = beat;
        return List.copyOf(transitions);
    }

    public List<Transition> seek(double beat) {
        if (!Double.isFinite(beat)) return List.of();
        List<Transition> transitions = new ArrayList<>();
        for (HoldInterval interval : intervals.values()) {
            State previous = states.get(interval.id());
            if (previous == State.ACTIVE) transitions.add(new Transition(interval.id(), TransitionType.CANCELLED, currentBeat));
            State next = stateAt(interval, beat);
            states.put(interval.id(), next);
            transitions.add(new Transition(interval.id(), TransitionType.SEEKED, beat));
            if (next == State.ACTIVE) transitions.add(new Transition(interval.id(), TransitionType.STARTED, interval.startBeat()));
        }
        currentBeat = beat;
        initialized = true;
        return List.copyOf(transitions);
    }

    public List<Transition> cancelAll() {
        List<Transition> transitions = new ArrayList<>();
        for (HoldInterval interval : intervals.values()) {
            if (states.get(interval.id()) == State.ACTIVE) {
                states.put(interval.id(), State.CANCELLED);
                transitions.add(new Transition(interval.id(), TransitionType.CANCELLED, currentBeat));
            }
        }
        return List.copyOf(transitions);
    }

    public Transition cancel(String intervalId) {
        HoldInterval interval = intervals.get(intervalId);
        if (interval == null || states.get(intervalId) != State.ACTIVE) return null;
        states.put(intervalId, State.CANCELLED);
        return new Transition(intervalId, TransitionType.CANCELLED, currentBeat);
    }

    public State state(String intervalId) {
        return states.getOrDefault(intervalId, State.WAITING);
    }

    private static State stateAt(HoldInterval interval, double beat) {
        if (beat < interval.startBeat()) return State.WAITING;
        if (beat < interval.endBeat()) return State.ACTIVE;
        return State.ENDED;
    }
}
