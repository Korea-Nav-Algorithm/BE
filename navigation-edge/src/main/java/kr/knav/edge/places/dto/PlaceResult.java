package kr.knav.edge.places.dto;

import kr.knav.common.Coordinate;

/** Search result selected as a routing destination; provider credentials never cross this DTO. */
public record PlaceResult(String id, String name, String address, Coordinate coordinate) {}
