package kr.knav.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RouteRequest(@NotNull @Valid Coordinate origin,
                           @NotNull @Valid Coordinate destination,
                           @NotNull Algorithm algorithm) {}
