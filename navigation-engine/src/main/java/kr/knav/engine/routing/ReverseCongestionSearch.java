package kr.knav.engine.routing;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import kr.knav.engine.graph.RoadEdge;
import kr.knav.engine.graph.RoadGraph;
import kr.knav.engine.traffic.TrafficSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Propagates a destination branch's congestion contrast upstream with a finite reach. */
@Component
public class ReverseCongestionSearch {
    private final double maxDistance;
    public ReverseCongestionSearch(@Value("${routing.max-propagation-distance-meters:3000}") double maxDistance) {
        if (maxDistance <= 0) throw new IllegalArgumentException("Propagation distance must be positive");
        this.maxDistance = maxDistance;
    }
    public Map<String, Double> attribute(RoadGraph graph, long destination, TrafficSnapshot traffic) {
        Map<String, Double> result = new HashMap<>();
        Map<Long, Double> bestDistance = new HashMap<>();
        PriorityQueue<Trace> queue = new PriorityQueue<>(Comparator.comparingDouble(Trace::distance));
        queue.add(new Trace(destination, 0, null, 0));
        while (!queue.isEmpty()) {
            Trace trace = queue.poll();
            if (trace.distance() > maxDistance || trace.distance() > bestDistance.getOrDefault(trace.node(), Double.POSITIVE_INFINITY)) continue;
            bestDistance.put(trace.node(), trace.distance());
            for (RoadEdge incoming : graph.incoming(trace.node())) {
                double distance = trace.distance() + incoming.distanceMeters();
                if (distance > maxDistance) continue;
                double ownCongestion = congestion(incoming, traffic);
                double branchSignal = trace.signal();
                if (trace.chosenEdge() == null) branchSignal = ownCongestion;
                else if (graph.outgoing(trace.node()).size() > 1) {
                    double alternative = graph.outgoing(trace.node()).stream()
                            .filter(edge -> !edge.id().equals(trace.chosenEdge().id()))
                            .mapToDouble(edge -> congestion(edge, traffic)).max().orElse(0);
                    branchSignal = Math.max(0, congestion(trace.chosenEdge(), traffic) - alternative);
                }
                // A free-flowing upstream edge breaks queue continuity even if its downstream branch is blocked.
                branchSignal *= Math.min(1, ownCongestion / 0.5);
                double attribution = Math.max(0, Math.min(1, branchSignal * (1 - distance / maxDistance)));
                result.merge(incoming.id(), attribution, Math::max);
                if (branchSignal >= 0.01
                        && distance < bestDistance.getOrDefault(incoming.from(), Double.POSITIVE_INFINITY)) {
                    queue.add(new Trace(incoming.from(), distance, incoming, branchSignal));
                }
            }
        }
        return result;
    }
    private double congestion(RoadEdge edge, TrafficSnapshot traffic) {
        return Math.max(0, Math.min(1, 1 - traffic.speedOrBase(edge.id(), edge.baseSpeedKmh()) / edge.baseSpeedKmh()));
    }
    private record Trace(long node, double distance, RoadEdge chosenEdge, double signal) {}
}
