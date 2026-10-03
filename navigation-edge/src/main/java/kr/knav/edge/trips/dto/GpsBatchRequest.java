package kr.knav.edge.trips.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GpsBatchRequest(@NotEmpty @Size(max = 1000) List<@Valid GpsPointRequest> points) {}
