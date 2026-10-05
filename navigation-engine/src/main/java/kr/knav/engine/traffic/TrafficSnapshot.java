package kr.knav.engine.traffic;

import java.util.Map;

public record TrafficSnapshot(Map<String, TrafficState> edges, long timestamp) {
    public TrafficSnapshot { edges = Map.copyOf(edges); }
    public boolean hasObservedSpeed(String edgeId) {
        TrafficState state = edges.get(edgeId);
        return state != null && Double.isFinite(state.observedSpeedKmh()) && state.observedSpeedKmh() > 0;
    }
    public double speedOrBase(String edgeId, double baseSpeedKmh) {
        return hasObservedSpeed(edgeId) ? edges.get(edgeId).observedSpeedKmh() : baseSpeedKmh;
    }
}
