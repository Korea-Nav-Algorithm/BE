package kr.knav.edge.trips.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.knav.common.Coordinate;

public record StartTripRequest(@NotBlank String clientTripId, @NotBlank String accessKey,
                               @NotBlank String routeId,
                               @NotNull @Min(0) Long startedAt,
                               @NotNull @Valid Coordinate origin, @NotNull @Valid Coordinate destination,
                               @NotNull @Min(0) Long ourEtaSeconds, @Min(0) Long tmapEtaSeconds,
                               @Min(0) Long tmapDistanceMeters) {}
