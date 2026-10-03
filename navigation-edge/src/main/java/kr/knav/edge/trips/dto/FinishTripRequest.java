package kr.knav.edge.trips.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FinishTripRequest(@NotNull @Min(0) Long finishedAt,
                                @NotNull @Min(0) Long actualDurationSeconds) {}
