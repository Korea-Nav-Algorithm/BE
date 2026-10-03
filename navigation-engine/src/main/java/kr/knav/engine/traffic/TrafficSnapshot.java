package kr.knav.engine.traffic;

import java.util.Map;

public record TrafficSnapshot(Map<String, TrafficState> edges, long timestamp) {
    public TrafficSnapshot { edges = Map.copyOf(edges); }
    public double speedOrBase(String edgeId, double baseSpeedKmh) {
        TrafficState state = edges.get(edgeId);
        return state == null || !Double.isFinite(state.observedSpeedKmh()) || state.observedSpeedKmh() <= 0
                ? baseSpeedKmh : state.observedSpeedKmh();
    }
}
