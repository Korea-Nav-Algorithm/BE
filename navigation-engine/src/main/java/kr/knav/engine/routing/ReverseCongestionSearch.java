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

/** Discounts a shared upstream queue only when an observed destination branch is distinctly freer. */
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
        queue.add(new Trace(destination, 0, null, false, 1));
        while (!queue.isEmpty()) {
            Trace trace = queue.poll();
            if (trace.distance() > maxDistance || trace.distance() > bestDistance.getOrDefault(trace.node(), Double.POSITIVE_INFINITY)) continue;
            bestDistance.put(trace.node(), trace.distance());
            for (RoadEdge incoming : graph.incoming(trace.node())) {
                double distance = trace.distance() + incoming.distanceMeters();
                if (distance > maxDistance) continue;
                if (!traffic.hasObservedSpeed(incoming.id())) continue;
                double ownCongestion = congestion(incoming, traffic);
                boolean branchKnown = trace.branchKnown();
                double branchAttribution = trace.branchAttribution();
                if (!branchKnown && trace.chosenEdge() != null
                        && traffic.hasObservedSpeed(trace.chosenEdge().id())) {
                    double leastAlternativeCongestion = Double.POSITIVE_INFINITY;
                    boolean hasAlternative = false;
                    boolean completeAlternatives = true;
                    for (RoadEdge alternative : graph.outgoing(trace.node())) {
                        if (alternative.id().equals(trace.chosenEdge().id()) || alternative.to() == incoming.from())
                            continue;
                        hasAlternative = true;
                        if (!traffic.hasObservedSpeed(alternative.id())) {
                            completeAlternatives = false;
                            break;
                        }
                        leastAlternativeCongestion = Math.min(leastAlternativeCongestion,
                                congestion(alternative, traffic));
                    }
                    if (hasAlternative && completeAlternatives) {
                        double advantage = leastAlternativeCongestion - congestion(trace.chosenEdge(), traffic);
                        // Similar congestion on every branch gives no evidence for a directional discount.
                        branchAttribution = Math.max(0, Math.min(1, 1 - Math.max(0, advantage - 0.15) / 0.7));
                        branchKnown = true;
                    }
                }
                double propagation = 1 - Math.pow(distance / maxDistance, 2);
                double continuity = Math.min(1, ownCongestion / 0.5);
                double attribution = branchKnown
                        ? 1 - (1 - branchAttribution) * continuity * propagation : 1;
                result.merge(incoming.id(), attribution, Math::max);
                if ((trace.chosenEdge() == null || ownCongestion >= 0.05)
                        && distance < bestDistance.getOrDefault(incoming.from(), Double.POSITIVE_INFINITY)) {
                    queue.add(new Trace(incoming.from(), distance, incoming, branchKnown, branchAttribution));
                }
            }
        }
        return result;
    }
    private double congestion(RoadEdge edge, TrafficSnapshot traffic) {
        return Math.max(0, Math.min(1, 1 - traffic.speedOrBase(edge.id(), edge.baseSpeedKmh()) / edge.baseSpeedKmh()));
    }
    private record Trace(long node, double distance, RoadEdge chosenEdge,
                         boolean branchKnown, double branchAttribution) {}
}
