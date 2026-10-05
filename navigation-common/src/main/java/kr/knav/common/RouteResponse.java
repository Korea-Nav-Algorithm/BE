package kr.knav.common;

import java.util.List;

public record RouteResponse(String routeId, Algorithm algorithm, String algorithmVersion,
                            long distanceMeters, long durationSeconds,
                            List<Coordinate> geometry, List<RouteSegment> segments,
                            List<RouteInstruction> instructions, String trafficSource) {
    public RouteResponse {
        // Snapshots written before guidance was introduced have no instructions field.
        if (instructions == null) instructions = List.of();
        if (trafficSource == null) trafficSource = "UNKNOWN";
    }

    public RouteResponse(String routeId, Algorithm algorithm, String algorithmVersion,
                         long distanceMeters, long durationSeconds,
                         List<Coordinate> geometry, List<RouteSegment> segments,
                         List<RouteInstruction> instructions) {
        this(routeId, algorithm, algorithmVersion, distanceMeters, durationSeconds,
                geometry, segments, instructions, "UNKNOWN");
    }

    public RouteResponse(String routeId, Algorithm algorithm, String algorithmVersion,
                         long distanceMeters, long durationSeconds,
                         List<Coordinate> geometry, List<RouteSegment> segments) {
        this(routeId, algorithm, algorithmVersion, distanceMeters, durationSeconds,
                geometry, segments, List.of(), "UNKNOWN");
    }
}
