package kr.knav.common;

import java.util.List;

public record RouteResponse(String routeId, Algorithm algorithm, String algorithmVersion,
                            long distanceMeters, long durationSeconds,
                            List<Coordinate> geometry, List<RouteSegment> segments) {}
