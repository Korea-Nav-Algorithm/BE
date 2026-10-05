package kr.knav.common;

/** A maneuver at a route geometry point, measured from the start of the route. */
public record RouteInstruction(ManeuverType type, int geometryIndex, long distanceFromStartMeters,
                               String roadName, Coordinate coordinate) {}
