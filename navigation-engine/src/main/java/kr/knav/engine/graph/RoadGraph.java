package kr.knav.engine.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import kr.knav.common.Coordinate;
import kr.knav.engine.routing.Geo;

/** Immutable adjacency graph with a small geographic grid for bounded GPS lookups. */
public class RoadGraph {
    private static final double GRID_DEGREES = 0.01;
    private final Map<Long, RoadNode> nodes;
    private final Map<Long, List<RoadNode>> spatialCells;
    private final Map<Long, List<RoadEdge>> outgoingEdges;
    private final Map<Long, List<RoadEdge>> incomingEdges;
    private final Map<Long, List<TurnRestriction>> turnRestrictions;
    private final double maximumSpeedKmh;
    private double minimumLat = 90;
    private double maximumLat = -90;
    private double minimumLng = 180;
    private double maximumLng = -180;

    public RoadGraph(List<RoadNode> nodeList, List<RoadEdge> edgeList) {
        this(nodeList, edgeList, List.of());
    }

    public RoadGraph(List<RoadNode> nodeList, List<RoadEdge> edgeList,
                     List<TurnRestriction> restrictions) {
        nodes = new HashMap<>();
        spatialCells = new HashMap<>();
        outgoingEdges = new HashMap<>();
        incomingEdges = new HashMap<>();
        turnRestrictions = new HashMap<>();
        double fastest = 1;
        for (RoadNode node : nodeList) {
            if (nodes.putIfAbsent(node.id(), node) != null) throw new IllegalArgumentException("Duplicate node");
            spatialCells.computeIfAbsent(cellKey(cell(node.lat()), cell(node.lng())), ignored -> new ArrayList<>())
                    .add(node);
            minimumLat = Math.min(minimumLat, node.lat());
            maximumLat = Math.max(maximumLat, node.lat());
            minimumLng = Math.min(minimumLng, node.lng());
            maximumLng = Math.max(maximumLng, node.lng());
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
        for (TurnRestriction restriction : restrictions) {
            if (nodes.containsKey(restriction.viaNodeId()))
                turnRestrictions.computeIfAbsent(restriction.viaNodeId(), ignored -> new ArrayList<>())
                        .add(restriction);
        }
    }

    public RoadNode node(long id) { return nodes.get(id); }
    public List<RoadEdge> outgoing(long id) { return outgoingEdges.getOrDefault(id, List.of()); }
    public List<RoadEdge> incoming(long id) { return incomingEdges.getOrDefault(id, List.of()); }
    public double maximumSpeedKmh() { return maximumSpeedKmh; }
    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return outgoingEdges.values().stream().mapToInt(List::size).sum(); }
    public void forEachEdge(Consumer<RoadEdge> visitor) {
        outgoingEdges.values().forEach(edges -> edges.forEach(visitor));
    }
    public int turnRestrictionCount() { return turnRestrictions.values().stream().mapToInt(List::size).sum(); }
    public boolean hasTurnRestrictions() { return !turnRestrictions.isEmpty(); }

    /** Checks an OSM via-node restriction when an incoming way is known. */
    public boolean turnAllowed(long viaNode, long fromWay, long previousNode, RoadEdge next) {
        List<TurnRestriction> restrictions = turnRestrictions.get(viaNode);
        if (restrictions == null) return true;
        long nextWay = osmWayId(next.id());
        if (nextWay < 0) return true;
        for (TurnRestriction restriction : restrictions) {
            if (restriction.fromWayId() != fromWay) continue;
            boolean matchingTurn = restriction.toWayId() == nextWay
                    && (restriction.uTurn() ? next.to() == previousNode
                            : !(restriction.only() && next.to() == previousNode));
            if (restriction.only() && !matchingTurn) return false;
            if (!restriction.only() && matchingTurn) return false;
        }
        return true;
    }

    public static long osmWayId(String edgeId) {
        int separator = edgeId.indexOf(':');
        if (separator < 1) return -1;
        try { return Long.parseLong(edgeId.substring(0, separator)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    public RoadNode findNearestNode(Coordinate coordinate) {
        RoadNode nearby = findNearestNodeWithin(coordinate, 1000);
        if (nearby != null) return nearby;
        RoadNode nearest = null;
        double minimumMeters = Double.POSITIVE_INFINITY;
        for (RoadNode node : nodes.values()) {
            double meters = Geo.meters(coordinate, new Coordinate(node.lat(), node.lng()));
            if (meters < minimumMeters) { minimumMeters = meters; nearest = node; }
        }
        if (nearest == null) throw new IllegalStateException("Road graph is empty");
        return nearest;
    }

    /** Returns null when no road node is close enough to connect a GPS position. */
    public RoadNode findNearestNodeWithin(Coordinate coordinate, double radiusMeters) {
        if (radiusMeters <= 0 || !Double.isFinite(radiusMeters)) throw new IllegalArgumentException("Invalid snap radius");
        double latitudeRange = radiusMeters / 110_574;
        double longitudeRange = radiusMeters / (111_320 * Math.max(0.01, Math.cos(Math.toRadians(coordinate.lat()))));
        if (coordinate.lat() < minimumLat - latitudeRange || coordinate.lat() > maximumLat + latitudeRange
                || coordinate.lng() < minimumLng - longitudeRange || coordinate.lng() > maximumLng + longitudeRange)
            return null;

        RoadNode nearest = null;
        double minimumMeters = radiusMeters;
        int firstLatCell = cell(coordinate.lat() - latitudeRange);
        int lastLatCell = cell(coordinate.lat() + latitudeRange);
        int firstLngCell = cell(coordinate.lng() - longitudeRange);
        int lastLngCell = cell(coordinate.lng() + longitudeRange);
        for (int latitudeCell = firstLatCell; latitudeCell <= lastLatCell; latitudeCell++) {
            for (int longitudeCell = firstLngCell; longitudeCell <= lastLngCell; longitudeCell++) {
                for (RoadNode node : spatialCells.getOrDefault(cellKey(latitudeCell, longitudeCell), List.of())) {
                    double meters = Geo.meters(coordinate, new Coordinate(node.lat(), node.lng()));
                    if (meters <= minimumMeters) { minimumMeters = meters; nearest = node; }
                }
            }
        }
        return nearest;
    }

    private static int cell(double degrees) { return (int) Math.floor(degrees / GRID_DEGREES); }
    private static long cellKey(int latitudeCell, int longitudeCell) {
        return ((long) latitudeCell << 32) | (longitudeCell & 0xffffffffL);
    }
}
