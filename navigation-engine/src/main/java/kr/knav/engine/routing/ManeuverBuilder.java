package kr.knav.engine.routing;

import java.util.ArrayList;
import java.util.List;
import kr.knav.common.Coordinate;
import kr.knav.common.ManeuverType;
import kr.knav.common.RouteInstruction;
import kr.knav.engine.graph.RoadEdge;

/** Produces visual cues from route turns without claiming lane or intersection precision. */
public final class ManeuverBuilder {
    private ManeuverBuilder() {}

    /** Edge start indices must correspond to the deduplicated route geometry. */
    public static List<RouteInstruction> build(List<RoadEdge> edges, List<Integer> edgeStartIndices,
                                               List<Coordinate> geometry) {
        if (geometry.isEmpty()) throw new IllegalArgumentException("Route geometry is empty");
        if (edges.isEmpty()) return List.of(new RouteInstruction(ManeuverType.ARRIVE, 0, 0, "", geometry.getFirst()));
        if (edges.size() != edgeStartIndices.size()) throw new IllegalArgumentException("Invalid edge indices");

        List<RouteInstruction> instructions = new ArrayList<>();
        instructions.add(new RouteInstruction(ManeuverType.START, 0, 0,
                edges.getFirst().roadName(), geometry.getFirst()));
        double distance = 0;
        for (int index = 1; index < edges.size(); index++) {
            RoadEdge previous = edges.get(index - 1);
            RoadEdge next = edges.get(index);
            distance += previous.distanceMeters();
            ManeuverType type = maneuver(previous, next);
            if (type != null) instructions.add(new RouteInstruction(type, edgeStartIndices.get(index),
                    Math.round(distance), next.roadName(), next.geometry().getFirst()));
        }
        distance += edges.getLast().distanceMeters();
        instructions.add(new RouteInstruction(ManeuverType.ARRIVE, geometry.size() - 1,
                Math.round(distance), "", geometry.getLast()));
        return List.copyOf(instructions);
    }

    private static ManeuverType maneuver(RoadEdge previous, RoadEdge next) {
        List<Coordinate> incoming = previous.geometry();
        List<Coordinate> outgoing = next.geometry();
        double before = bearing(incoming.get(incoming.size() - 2), incoming.getLast());
        double after = bearing(outgoing.getFirst(), outgoing.get(1));
        double change = ((after - before + 540) % 360) - 180;
        double magnitude = Math.abs(change);
        if (magnitude < 30) {
            return previous.roadName().equals(next.roadName()) ? null : ManeuverType.CONTINUE;
        }
        if (magnitude >= 150) return ManeuverType.U_TURN;
        if (magnitude < 60) return change < 0 ? ManeuverType.SLIGHT_LEFT : ManeuverType.SLIGHT_RIGHT;
        return change < 0 ? ManeuverType.TURN_LEFT : ManeuverType.TURN_RIGHT;
    }

    private static double bearing(Coordinate first, Coordinate second) {
        double firstLat = Math.toRadians(first.lat());
        double secondLat = Math.toRadians(second.lat());
        double deltaLng = Math.toRadians(second.lng() - first.lng());
        double east = Math.sin(deltaLng) * Math.cos(secondLat);
        double north = Math.cos(firstLat) * Math.sin(secondLat)
                - Math.sin(firstLat) * Math.cos(secondLat) * Math.cos(deltaLng);
        return (Math.toDegrees(Math.atan2(east, north)) + 360) % 360;
    }
}
