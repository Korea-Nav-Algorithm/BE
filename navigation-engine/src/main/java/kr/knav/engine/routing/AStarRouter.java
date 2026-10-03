package kr.knav.engine.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import kr.knav.common.Coordinate;
import kr.knav.engine.graph.RoadEdge;
import kr.knav.engine.graph.RoadGraph;
import kr.knav.engine.graph.RoadNode;
import kr.knav.engine.traffic.TrafficSnapshot;
import org.springframework.stereotype.Component;

/** A* over nonnegative travel times; the speed bound keeps its heuristic admissible. */
@Component
public class AStarRouter {
    public List<RoadEdge> route(RoadGraph graph, long origin, long destination,
                                TrafficSnapshot traffic, Map<String, Double> attribution,
                                double defaultAttribution) {
        if (origin == destination) return List.of();
        Map<Long, Double> best = new HashMap<>();
        Map<Long, RoadEdge> previous = new HashMap<>();
        PriorityQueue<Visit> frontier = new PriorityQueue<>(Comparator.comparingDouble(Visit::estimatedTotal));
        double speedBound = Math.max(graph.maximumSpeedKmh(), traffic.edges().values().stream()
                .mapToDouble(state -> state.observedSpeedKmh()).max().orElse(1));
        best.put(origin, 0.0);
        frontier.add(new Visit(origin, 0, heuristic(graph, origin, destination, speedBound)));
        while (!frontier.isEmpty()) {
            Visit visit = frontier.poll();
            if (visit.cost() > best.getOrDefault(visit.node(), Double.POSITIVE_INFINITY)) continue;
            if (visit.node() == destination) return reconstruct(previous, origin, destination);
            for (RoadEdge edge : graph.outgoing(visit.node())) {
                double seconds = EdgeCost.calculate(edge, traffic,
                        attribution.getOrDefault(edge.id(), defaultAttribution)).effectiveSeconds();
                double nextCost = visit.cost() + seconds;
                if (nextCost < best.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    best.put(edge.to(), nextCost);
                    previous.put(edge.to(), edge);
                    frontier.add(new Visit(edge.to(), nextCost,
                            nextCost + heuristic(graph, edge.to(), destination, speedBound)));
                }
            }
        }
        throw new RouteNotFoundException();
    }
    private double heuristic(RoadGraph graph, long from, long to, double maximumSpeedKmh) {
        RoadNode first = graph.node(from);
        RoadNode second = graph.node(to);
        return Geo.meters(new Coordinate(first.lat(), first.lng()),
                new Coordinate(second.lat(), second.lng())) * 3.6 / maximumSpeedKmh;
    }
    private List<RoadEdge> reconstruct(Map<Long, RoadEdge> previous, long origin, long destination) {
        List<RoadEdge> reversed = new ArrayList<>();
        long cursor = destination;
        while (cursor != origin) {
            RoadEdge edge = previous.get(cursor);
            if (edge == null) throw new RouteNotFoundException();
            reversed.add(edge);
            cursor = edge.from();
        }
        Collections.reverse(reversed);
        return reversed;
    }
    private record Visit(long node, double cost, double estimatedTotal) {}
}
