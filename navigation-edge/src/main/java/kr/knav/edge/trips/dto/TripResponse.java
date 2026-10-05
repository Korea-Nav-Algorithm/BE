package kr.knav.edge.trips.dto;

import java.util.List;
import kr.knav.common.Coordinate;

public record TripResponse(String tripId, String clientTripId, String routeId, long startedAt, Long finishedAt,
                           long ourEtaSeconds, Long tmapEtaSeconds, Long tmapDistanceMeters, Long actualDurationSeconds,
                           Coordinate origin, Coordinate destination, long pointCount,
                           List<GpsPointRequest> points, List<TripReroute> reroutes) {}
