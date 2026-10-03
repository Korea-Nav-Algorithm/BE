package kr.knav.engine.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.knav.common.Coordinate;
import kr.knav.engine.routing.Geo;

/** Immutable adjacency graph with a deliberately simple nearest-node scan. */
public class RoadGraph {
    private final Map<Long, RoadNode> nodes;
    private final Map<Long, List<RoadEdge>> outgoingEdges;
    private final Map<Long, List<RoadEdge>> incomingEdges;
    private final double maximumSpeedKmh;

    public RoadGraph(List<RoadNode> nodeList, List<RoadEdge> edgeList) {
        nodes = new HashMap<>();
        outgoingEdges = new HashMap<>();
        incomingEdges = new HashMap<>();
        double fastest = 1;
        for (RoadNode node : nodeList) {
            if (nodes.putIfAbsent(node.id(), node) != null) throw new IllegalArgumentException("Duplicate node");
        }
        for (RoadEdge edge : edgeList) {
            if (!nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to()) || edge.distanceMeters() <= 0
                    || edge.baseSpeedKmh() <= 0 || edge.geometry() == null || edge.geometry().size() < 2) {
                throw new IllegalArgumentException("Invalid edge: " + edge.id());
            }
            outgoingEdges.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge);
            fastest = Math.max(fastest, edge.baseSpeedKmh());
        }
        maximumSpeedKmh = fastest;
    }

    public RoadNode node(long id) { return nodes.get(id); }
    public List<RoadEdge> outgoing(long id) { return outgoingEdges.getOrDefault(id, List.of()); }
    public List<RoadEdge> incoming(long id) { return incomingEdges.getOrDefault(id, List.of()); }
    public double maximumSpeedKmh() { return maximumSpeedKmh; }
    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return outgoingEdges.values().stream().mapToInt(List::size).sum(); }

    public RoadNode findNearestNode(Coordinate coordinate) {
        RoadNode nearest = null;
        double minimumMeters = Double.POSITIVE_INFINITY;
        for (RoadNode node : nodes.values()) {
            double meters = Geo.meters(coordinate, new Coordinate(node.lat(), node.lng()));
            if (meters < minimumMeters) { minimumMeters = meters; nearest = node; }
        }
        if (nearest == null) throw new IllegalStateException("Road graph is empty");
        return nearest;
    }
}
