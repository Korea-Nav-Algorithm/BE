package kr.knav.common;

public record RouteSegment(String edgeId, double observedSpeed, double baseSpeed,
                           double attribution, double effectiveSpeed) {}
