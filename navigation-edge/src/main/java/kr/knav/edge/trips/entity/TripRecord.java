package kr.knav.edge.trips.entity;

import kr.knav.common.Coordinate;

public record TripRecord(String id, String clientTripId, String accessKeyHash,
                         String routeId, long startedAt, Long finishedAt,
                         Coordinate origin, Coordinate destination, long ourEtaSeconds,
                         Long tmapEtaSeconds, Long tmapDistanceMeters,
                         Long actualDurationSeconds, long pointCount) {}
