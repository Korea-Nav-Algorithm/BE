package kr.knav.common;

public record RouteSegment(String edgeId, double observedSpeed, double baseSpeed,
                           double attribution, double effectiveSpeed, TrafficLevel trafficLevel,
                           Integer geometryStartIndex, Integer geometryEndIndex) {
    public RouteSegment {
        // Older route snapshots predate traffic levels and geometry ranges.
        if (trafficLevel == null) trafficLevel = TrafficLevel.UNKNOWN;
    }

    public RouteSegment(String edgeId, double observedSpeed, double baseSpeed,
                        double attribution, double effectiveSpeed) {
        this(edgeId, observedSpeed, baseSpeed, attribution, effectiveSpeed,
                TrafficLevel.UNKNOWN, null, null);
    }
}
