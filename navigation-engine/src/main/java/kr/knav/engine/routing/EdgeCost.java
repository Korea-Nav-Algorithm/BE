package kr.knav.engine.routing;

import kr.knav.engine.graph.RoadEdge;
import kr.knav.engine.traffic.TrafficSnapshot;

/** Travel time retains the full observed delay for diagnostics before attribution. */
public record EdgeCost(double baseSeconds, double observedSeconds, double excessSeconds,
                       double attribution, double effectiveSeconds, double observedSpeedKmh) {
    public static EdgeCost calculate(RoadEdge edge, TrafficSnapshot traffic, double attribution) {
        double observedSpeed = traffic.speedOrBase(edge.id(), edge.baseSpeedKmh());
        double baseSeconds = edge.distanceMeters() * 3.6 / edge.baseSpeedKmh();
        double observedSeconds = edge.distanceMeters() * 3.6 / observedSpeed;
        double excess = Math.max(0, observedSeconds - baseSeconds);
        double effective = baseSeconds + Math.max(0, Math.min(1, attribution)) * excess;
        return new EdgeCost(baseSeconds, observedSeconds, excess, attribution, effective, observedSpeed);
    }
}
