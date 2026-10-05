package kr.knav.engine.graph;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import de.topobyte.osm4j.pbf.seq.PbfIterator;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.knav.common.Coordinate;
import kr.knav.engine.routing.Geo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Converts a bounded OSM PBF extract into directed in-memory road segments. */
@Component
public class OsmPbfRoadGraphLoader implements RoadGraphLoader {
    private final String file;
    private final Bounds bounds;
    public OsmPbfRoadGraphLoader(@Value("${graph.osm-file}") String file,
                                 @Value("${graph.bounds:}") String bounds) {
        this.file = file;
        this.bounds = Bounds.parse(bounds);
    }
    @Override public RoadGraph load() {
        Map<Long, RoadNode> nodes = new HashMap<>();
        List<RoadEdge> edges = new ArrayList<>();
        List<TurnRestriction> restrictions = new ArrayList<>();
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            PbfIterator iterator = new PbfIterator(input, false);
            for (var container : iterator) {
                if (container.getType() == EntityType.Node) {
                    OsmNode node = (OsmNode) container.getEntity();
                    if (bounds.contains(node.getLatitude(), node.getLongitude()))
                        nodes.put(node.getId(), new RoadNode(node.getId(), node.getLatitude(), node.getLongitude()));
                } else if (container.getType() == EntityType.Way) {
                    addWay((OsmWay) container.getEntity(), nodes, edges);
                } else if (container.getType() == EntityType.Relation) {
                    TurnRestriction restriction = parseTurnRestriction((OsmRelation) container.getEntity());
                    if (restriction != null) restrictions.add(restriction);
                }
            }
        } catch (IOException exception) { throw new IllegalStateException("Cannot load OSM PBF: " + file, exception); }
        Map<Long, RoadNode> used = new HashMap<>();
        for (RoadEdge edge : edges) { used.put(edge.from(), nodes.get(edge.from())); used.put(edge.to(), nodes.get(edge.to())); }
        return new RoadGraph(new ArrayList<>(used.values()), edges, restrictions);
    }
    static TurnRestriction parseTurnRestriction(OsmRelation relation) {
        Map<String, String> tags = OsmModelUtil.getTagsAsMap(relation);
        if (!"restriction".equals(tags.get("type"))) return null;
        String rule = tags.getOrDefault("restriction:motorcar",
                tags.getOrDefault("restriction:motor_vehicle", tags.get("restriction")));
        if (rule == null || !(rule.startsWith("no_") || rule.startsWith("only_"))) return null;
        String exceptions = tags.getOrDefault("except", "");
        for (String exception : exceptions.split("[;,]")) {
            if (List.of("motorcar", "motor_vehicle", "vehicle").contains(exception.trim())) return null;
        }
        long from = -1, via = -1, to = -1;
        for (int index = 0; index < relation.getNumberOfMembers(); index++) {
            OsmRelationMember member = relation.getMember(index);
            switch (member.getRole() == null ? "" : member.getRole()) {
                case "from" -> { if (member.getType() != EntityType.Way || from >= 0) return null; from = member.getId(); }
                case "via" -> { if (member.getType() != EntityType.Node || via >= 0) return null; via = member.getId(); }
                case "to" -> { if (member.getType() != EntityType.Way || to >= 0) return null; to = member.getId(); }
                default -> { }
            }
        }
        if (from < 0 || via < 0 || to < 0) return null;
        return new TurnRestriction(from, via, to, rule.startsWith("only_"), rule.endsWith("u_turn"));
    }
    private void addWay(OsmWay way, Map<Long, RoadNode> nodes, List<RoadEdge> edges) {
        Map<String, String> tags = OsmModelUtil.getTagsAsMap(way);
        RoadClass roadClass = classify(tags.get("highway"));
        if (roadClass == null || blockedForCars(tags) || "reversible".equals(tags.get("oneway"))) return;
        String direction = tags.getOrDefault("oneway", "no");
        boolean oneWay = isOneWay(tags);
        boolean reverseOnly = "-1".equals(direction);
        double speed = speed(roadClass, tags.get("maxspeed"));
        for (int index = 1; index < way.getNumberOfNodes(); index++) {
            long from = way.getNodeId(index - 1);
            long to = way.getNodeId(index);
            RoadNode first = nodes.get(from);
            RoadNode second = nodes.get(to);
            if (first == null || second == null || from == to) continue;
            Coordinate start = new Coordinate(first.lat(), first.lng());
            Coordinate end = new Coordinate(second.lat(), second.lng());
            double distance = Geo.meters(start, end);
            if (distance <= 0) continue;
            String id = way.getId() + ":" + index;
            String name = tags.getOrDefault("name", "");
            if (!reverseOnly) edges.add(new RoadEdge(id + ":f", from, to, distance, speed, oneWay,
                    name, roadClass, List.of(start, end)));
            if (!oneWay || reverseOnly) edges.add(new RoadEdge(id + ":r", to, from, distance, speed, oneWay,
                    name, roadClass, List.of(end, start)));
        }
    }
    static boolean isOneWay(Map<String, String> tags) {
        String direction = tags.get("oneway");
        if ("no".equals(direction)) return false;
        return "yes".equals(direction) || "1".equals(direction) || "-1".equals(direction)
                || "roundabout".equals(tags.get("junction"))
                || "motorway".equals(tags.get("highway"))
                || "motorway_link".equals(tags.get("highway"));
    }
    static boolean blockedForCars(Map<String, String> tags) {
        for (String key : List.of("access", "vehicle", "motor_vehicle", "motorcar")) {
            String value = tags.get(key);
            if ("no".equals(value) || "private".equals(value)) return true;
        }
        return false;
    }
    private RoadClass classify(String highway) {
        if (highway == null) return null;
        return switch (highway) {
            case "motorway", "motorway_link" -> RoadClass.MOTORWAY;
            case "trunk", "trunk_link" -> RoadClass.TRUNK;
            case "primary", "primary_link" -> RoadClass.PRIMARY;
            case "secondary", "secondary_link" -> RoadClass.SECONDARY;
            case "tertiary", "tertiary_link" -> RoadClass.TERTIARY;
            case "residential", "living_street", "unclassified", "service" -> RoadClass.RESIDENTIAL;
            default -> null;
        };
    }
    private double speed(RoadClass roadClass, String maxSpeed) {
        if (maxSpeed != null && maxSpeed.matches("[0-9]{1,3}")) {
            double parsed = Double.parseDouble(maxSpeed);
            if (parsed > 0) return parsed;
        }
        return switch (roadClass) {
            case MOTORWAY -> 100;
            case TRUNK -> 80;
            case PRIMARY -> 60;
            case SECONDARY -> 50;
            case TERTIARY -> 40;
            default -> 30;
        };
    }

    /** Bounds reduce memory when using a country PBF; roads ending outside are intentionally cut. */
    record Bounds(double minLng, double minLat, double maxLng, double maxLat) {
        static Bounds parse(String value) {
            if (value == null || value.isBlank()) return new Bounds(-180, -90, 180, 90);
            String[] parts = value.split(",");
            if (parts.length != 4) throw new IllegalArgumentException("OSM_BBOX needs minLng,minLat,maxLng,maxLat");
            try {
                Bounds bounds = new Bounds(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
                if (!Double.isFinite(bounds.minLng) || !Double.isFinite(bounds.minLat)
                        || !Double.isFinite(bounds.maxLng) || !Double.isFinite(bounds.maxLat)
                        || bounds.minLng < -180 || bounds.maxLng > 180 || bounds.minLat < -90 || bounds.maxLat > 90
                        || bounds.minLng >= bounds.maxLng || bounds.minLat >= bounds.maxLat)
                    throw new IllegalArgumentException("Invalid OSM_BBOX");
                return bounds;
            } catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid OSM_BBOX", exception); }
        }
        boolean contains(double lat, double lng) {
            return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
        }
    }
}
