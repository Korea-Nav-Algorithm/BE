package kr.knav.edge.trips.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RerouteRequest(@NotBlank String routeId, @NotNull @Min(0) Long occurredAt) {}
