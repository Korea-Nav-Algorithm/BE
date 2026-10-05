package kr.knav.engine.traffic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.knav.common.Coordinate;
import kr.knav.engine.graph.RoadEdge;
import kr.knav.engine.graph.RoadGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Matches directed OSM edges to a regional WGS84 standard-link GeoJSON at startup. */
@Component
public class GeometryTrafficEdgeMapper implements TrafficEdgeMapper {
    private static final Logger log = LoggerFactory.getLogger(GeometryTrafficEdgeMapper.class);
    private static final double CELL_DEGREES = 0.002;
    private static final double MAX_DISTANCE_METERS = 22;
    private static final double MIN_DIRECTION_DOT = 0.82;
    private final Map<String, List<String>> mapping;

    public GeometryTrafficEdgeMapper(ObjectMapper mapper, RoadGraph graph,
            @Value("${traffic.provider}") String provider,
            @Value("${traffic.link-geometry-file:}") String path) {
        if (!"gyeonggi".equals(provider) || path.isBlank()) {
            mapping = Map.of();
            return;
        }
        long started = System.nanoTime();
        try {
            JsonNode root = mapper.readTree(new File(path));
            JsonNode features = root.path("features");
            if (!features.isArray()) throw new IllegalStateException("Traffic link geometry must be GeoJSON");
            Map<Long, List<ReferenceSegment>> cells = new HashMap<>();
            Set<String> referenceLinks = new HashSet<>();
            for (JsonNode feature : features) {
                String linkId = linkId(feature.path("properties"));
                if (linkId.isBlank()) continue;
                JsonNode geometry = feature.path("geometry");
                if ("LineString".equals(geometry.path("type").asText())) {
                    if (indexLine(cells, linkId, geometry.path("coordinates"))) referenceLinks.add(linkId);
                } else if ("MultiLineString".equals(geometry.path("type").asText())) {
                    geometry.path("coordinates").forEach(line -> {
                        if (indexLine(cells, linkId, line)) referenceLinks.add(linkId);
                    });
                }
            }
            if (cells.isEmpty()) throw new IllegalStateException("No usable WGS84 traffic link geometries");
            Map<String, List<String>> matched = new HashMap<>();
            graph.forEachEdge(edge -> {
                String linkId = nearestLink(cells, edge);
                if (linkId != null) matched.computeIfAbsent(linkId, ignored -> new ArrayList<>()).add(edge.id());
            });
            Map<String, List<String>> immutable = new HashMap<>();
            matched.forEach((linkId, edges) -> immutable.put(linkId, List.copyOf(edges)));
            mapping = Map.copyOf(immutable);
            log.info("event=traffic_geometry_mapping referenceLinks={} mappedLinks={} mappedDirectedEdges={} buildMs={}",
                    referenceLinks.size(), mapping.size(), mapping.values().stream().mapToInt(List::size).sum(),
                    (System.nanoTime() - started) / 1_000_000);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load traffic link geometry", exception);
        }
    }

    @Override public List<String> mapExternalLinkToEdges(ExternalTrafficLink link) {
        return mapping.getOrDefault(link.linkId(), List.of());
    }

    private String linkId(JsonNode properties) {
        for (String key : List.of("LINK_ID", "linkId", "link_id")) {
            JsonNode value = properties.path(key);
            if (!value.isMissingNode() && !value.asText().isBlank()) return value.asText();
        }
        return "";
    }

    private boolean indexLine(Map<Long, List<ReferenceSegment>> cells, String linkId, JsonNode line) {
        if (!line.isArray()) return false;
        boolean indexed = false;
        Coordinate previous = null;
        for (JsonNode point : line) {
            Coordinate current = coordinate(point);
            if (previous != null && current != null && !previous.equals(current)) {
                ReferenceSegment segment = new ReferenceSegment(linkId, previous, current);
                int minLat = cell(Math.min(previous.lat(), current.lat()) - 0.0003);
                int maxLat = cell(Math.max(previous.lat(), current.lat()) + 0.0003);
                int minLng = cell(Math.min(previous.lng(), current.lng()) - 0.0003);
                int maxLng = cell(Math.max(previous.lng(), current.lng()) + 0.0003);
                if ((long) (maxLat - minLat + 1) * (maxLng - minLng + 1) <= 100) {
                    for (int lat = minLat; lat <= maxLat; lat++)
                        for (int lng = minLng; lng <= maxLng; lng++)
                            cells.computeIfAbsent(cellKey(lat, lng), ignored -> new ArrayList<>()).add(segment);
                    indexed = true;
                }
            }
            previous = current;
        }
        return indexed;
    }

    private Coordinate coordinate(JsonNode point) {
        if (!point.isArray() || point.size() < 2 || !point.get(0).isNumber() || !point.get(1).isNumber())
            throw new IllegalStateException("Invalid traffic link coordinate");
        double lng = point.get(0).asDouble();
        double lat = point.get(1).asDouble();
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180)
            throw new IllegalStateException("Traffic link geometry must use WGS84");
        return new Coordinate(lat, lng);
    }

    private String nearestLink(Map<Long, List<ReferenceSegment>> cells, RoadEdge edge) {
        Coordinate start = edge.geometry().getFirst();
        Coordinate end = edge.geometry().getLast();
        Coordinate midpoint = new Coordinate((start.lat() + end.lat()) / 2, (start.lng() + end.lng()) / 2);
        List<ReferenceSegment> candidates = cells.getOrDefault(cellKey(cell(midpoint.lat()), cell(midpoint.lng())), List.of());
        String bestLink = null;
        double best = Double.POSITIVE_INFINITY;
        double competing = Double.POSITIVE_INFINITY;
        for (ReferenceSegment candidate : candidates) {
            double direction = directionDot(start, end, candidate.start(), candidate.end());
            if (direction < MIN_DIRECTION_DOT) continue;
            double distance = distanceToSegment(midpoint, candidate.start(), candidate.end());
            if (distance > MAX_DISTANCE_METERS) continue;
            double score = distance + (1 - direction) * 20;
            if (score < best) {
                if (bestLink != null && !bestLink.equals(candidate.linkId())) competing = best;
                best = score;
                bestLink = candidate.linkId();
            } else if (bestLink != null && !bestLink.equals(candidate.linkId())) {
                competing = Math.min(competing, score);
            }
        }
        // Parallel carriageways or ramps with similar geometry are deliberately left unmapped.
        return competing - best >= 5 ? bestLink : null;
    }

    private double directionDot(Coordinate from, Coordinate to, Coordinate referenceFrom, Coordinate referenceTo) {
        double xScale = 111_320 * Math.cos(Math.toRadians((from.lat() + to.lat()) / 2));
        double ax = (to.lng() - from.lng()) * xScale;
        double ay = (to.lat() - from.lat()) * 110_574;
        double bx = (referenceTo.lng() - referenceFrom.lng()) * xScale;
        double by = (referenceTo.lat() - referenceFrom.lat()) * 110_574;
        double length = Math.hypot(ax, ay) * Math.hypot(bx, by);
        return length == 0 ? -1 : (ax * bx + ay * by) / length;
    }

    private double distanceToSegment(Coordinate point, Coordinate start, Coordinate end) {
        double xScale = 111_320 * Math.cos(Math.toRadians(point.lat()));
        double ax = (start.lng() - point.lng()) * xScale;
        double ay = (start.lat() - point.lat()) * 110_574;
        double bx = (end.lng() - point.lng()) * xScale;
        double by = (end.lat() - point.lat()) * 110_574;
        double dx = bx - ax;
        double dy = by - ay;
        double fraction = Math.max(0, Math.min(1, -(ax * dx + ay * dy) / (dx * dx + dy * dy)));
        return Math.hypot(ax + fraction * dx, ay + fraction * dy);
    }

    private static int cell(double coordinate) { return (int) Math.floor(coordinate / CELL_DEGREES); }
    private static long cellKey(int lat, int lng) { return ((long) lat << 32) | (lng & 0xffffffffL); }
    private record ReferenceSegment(String linkId, Coordinate start, Coordinate end) {}
}
