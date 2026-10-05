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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/** A* over nonnegative travel times; the speed bound keeps its heuristic admissible. */
@Component
public class AStarRouter {
    private final double unobservedSpeedFactor;

    @Autowired
    public AStarRouter(@Value("${routing.unobserved-speed-factor:0.75}") double unobservedSpeedFactor) {
        if (!Double.isFinite(unobservedSpeedFactor) || unobservedSpeedFactor <= 0 || unobservedSpeedFactor > 1)
            throw new IllegalArgumentException("routing.unobserved-speed-factor must be in (0,1]");
        this.unobservedSpeedFactor = unobservedSpeedFactor;
    }

    public AStarRouter() { this(1.0); }

    EdgeCost cost(RoadEdge edge, TrafficSnapshot traffic, double attribution) {
        return EdgeCost.calculate(edge, traffic, attribution, unobservedSpeedFactor);
    }

    public List<RoadEdge> route(RoadGraph graph, long origin, long destination,
                                TrafficSnapshot traffic, Map<String, Double> attribution,
                                double defaultAttribution) {
        if (origin == destination) return List.of();
        if (graph.hasTurnRestrictions())
            return routeWithRestrictions(graph, origin, destination, traffic, attribution, defaultAttribution);
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
                double seconds = cost(edge, traffic,
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

    /** Via-node OSM restrictions require the incoming way to be part of the search state. */
    private List<RoadEdge> routeWithRestrictions(RoadGraph graph, long origin, long destination,
                                                  TrafficSnapshot traffic, Map<String, Double> attribution,
                                                  double defaultAttribution) {
        double speedBound = Math.max(graph.maximumSpeedKmh(), traffic.edges().values().stream()
                .mapToDouble(state -> state.observedSpeedKmh()).max().orElse(1));
        TurnState start = new TurnState(origin, -1, -1);
        Map<TurnState, Double> best = new HashMap<>();
        Map<TurnState, TurnStep> previous = new HashMap<>();
        PriorityQueue<TurnVisit> frontier = new PriorityQueue<>(Comparator.comparingDouble(TurnVisit::estimatedTotal));
        best.put(start, 0.0);
        frontier.add(new TurnVisit(start, 0, heuristic(graph, origin, destination, speedBound)));
        while (!frontier.isEmpty()) {
            TurnVisit visit = frontier.poll();
            TurnState current = visit.state();
            if (visit.cost() > best.getOrDefault(current, Double.POSITIVE_INFINITY)) continue;
            if (current.node() == destination) return reconstructTurns(previous, start, current);
            for (RoadEdge edge : graph.outgoing(current.node())) {
                if (current.incomingWay() >= 0 && !graph.turnAllowed(current.node(), current.incomingWay(),
                        current.previousNode(), edge)) continue;
                double seconds = cost(edge, traffic,
                        attribution.getOrDefault(edge.id(), defaultAttribution)).effectiveSeconds();
                double nextCost = visit.cost() + seconds;
                TurnState next = new TurnState(edge.to(), edge.from(), RoadGraph.osmWayId(edge.id()));
                if (nextCost < best.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    best.put(next, nextCost);
                    previous.put(next, new TurnStep(current, edge));
                    frontier.add(new TurnVisit(next, nextCost,
                            nextCost + heuristic(graph, edge.to(), destination, speedBound)));
                }
            }
        }
        throw new RouteNotFoundException();
    }

    private List<RoadEdge> reconstructTurns(Map<TurnState, TurnStep> previous,
                                            TurnState start, TurnState destination) {
        List<RoadEdge> reversed = new ArrayList<>();
        TurnState cursor = destination;
        while (!cursor.equals(start)) {
            TurnStep step = previous.get(cursor);
            if (step == null) throw new RouteNotFoundException();
            reversed.add(step.edge());
            cursor = step.previous();
        }
        Collections.reverse(reversed);
        return reversed;
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
    private record TurnState(long node, long previousNode, long incomingWay) {}
    private record TurnStep(TurnState previous, RoadEdge edge) {}
    private record TurnVisit(TurnState state, double cost, double estimatedTotal) {}
}
