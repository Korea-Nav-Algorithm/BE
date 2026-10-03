package kr.knav.edge.trips.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record GpsPointRequest(@NotBlank String pointId, @NotNull @Min(0) Long timestamp,
                              @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                              @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
                              @DecimalMin("0") Double gpsSpeed,
                              @DecimalMin("0") @DecimalMax("360") Double heading,
                              @DecimalMin("0") Double accuracy) {
    public GpsPointRequest {
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180
                || gpsSpeed != null && !Double.isFinite(gpsSpeed)
                || heading != null && !Double.isFinite(heading)
                || accuracy != null && !Double.isFinite(accuracy)) throw new IllegalArgumentException("Invalid GPS point");
    }
}
