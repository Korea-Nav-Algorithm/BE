package kr.knav.engine.traffic;

import kr.knav.common.Coordinate;

public record ExternalTrafficLink(String linkId, double speedKmh, long timestamp,
                                  Coordinate start, Coordinate end) {}
