package kr.knav.edge.places.dto;

import java.util.List;

public record PlaceSearchResponse(List<PlaceResult> places, String source) {}
