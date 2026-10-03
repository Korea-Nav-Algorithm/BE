package kr.knav.engine.graph;

import java.util.List;
import kr.knav.common.Coordinate;

/** A directed traversable road segment; two-way roads are expanded by the loader. */
public record RoadEdge(String id, long from, long to, double distanceMeters,
                       double baseSpeedKmh, boolean oneWay, String roadName,
                       RoadClass roadClass, List<Coordinate> geometry) {}
