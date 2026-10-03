package kr.knav.edge.trips.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.knav.edge.trips.dto.FinishTripRequest;
import kr.knav.edge.trips.dto.GpsBatchRequest;
import kr.knav.edge.trips.dto.GpsBatchResponse;
import kr.knav.edge.trips.dto.GpsPointRequest;
import kr.knav.edge.trips.dto.StartTripRequest;
import kr.knav.edge.trips.dto.StartTripResponse;
import kr.knav.edge.trips.dto.TripResponse;
import kr.knav.edge.trips.entity.TripRecord;
import kr.knav.edge.trips.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each GPS batch commits atomically; a repeat batch stores zero duplicates. */
@Service
public class TripService {
    public record StartResult(StartTripResponse response, boolean created) {}
    private static final Logger log = LoggerFactory.getLogger(TripService.class);
    private final TripRepository repository;
    public TripService(TripRepository repository) { this.repository = repository; }
    @Transactional public StartResult start(StartTripRequest request) {
        UUID.fromString(request.clientTripId());
        TripRecord existing = repository.findByClientTripId(request.clientTripId()).orElse(null);
        if (existing != null) {
            if (!sameStart(existing, request)) throw new IllegalArgumentException("Conflicting clientTripId");
            return new StartResult(new StartTripResponse(existing.id(), existing.clientTripId()), false);
        }
        String id = UUID.randomUUID().toString();
        repository.create(id, request);
        log.info("event=trip_start tripId={} clientTripId={}", id, request.clientTripId());
        return new StartResult(new StartTripResponse(id, request.clientTripId()), true);
    }
    @Transactional public GpsBatchResponse savePoints(String tripId, GpsBatchRequest request) {
        requireTrip(tripId);
        int stored = 0;
        for (GpsPointRequest point : request.points()) {
            UUID.fromString(point.pointId());
            stored += repository.insertPoint(tripId, point);
        }
        log.info("event=gps_batch tripId={} pointsReceived={} pointsStored={}", tripId, request.points().size(), stored);
        return new GpsBatchResponse(request.points().size(), stored);
    }
    @Transactional public void finish(String tripId, FinishTripRequest request) {
        TripRecord trip = requireTrip(tripId);
        if (request.finishedAt() < trip.startedAt()) throw new IllegalArgumentException("Finish precedes start");
        if (trip.finishedAt() != null && (!trip.finishedAt().equals(request.finishedAt())
                || !trip.actualDurationSeconds().equals(request.actualDurationSeconds())))
            throw new IllegalArgumentException("Trip already finished with different values");
        repository.finish(tripId, request.finishedAt(), request.actualDurationSeconds());
        log.info("event=trip_finish tripId={} clientTripId={} pointCount={}",
                tripId, trip.clientTripId(), repository.find(tripId).orElseThrow().pointCount());
    }
    public TripResponse get(String tripId) {
        return response(requireTrip(tripId), repository.points(tripId));
    }
    public List<TripResponse> recent() {
        return repository.recent().stream().map(trip -> response(trip, List.of())).toList();
    }
    private TripRecord requireTrip(String id) { return repository.find(id).orElseThrow(TripNotFoundException::new); }
    private TripResponse response(TripRecord trip, List<GpsPointRequest> points) {
        return new TripResponse(trip.id(), trip.clientTripId(), trip.routeId(), trip.startedAt(), trip.finishedAt(),
                trip.ourEtaSeconds(), trip.tmapEtaSeconds(), trip.tmapDistanceMeters(), trip.actualDurationSeconds(),
                trip.origin(), trip.destination(), trip.pointCount(), points);
    }
    private boolean sameStart(TripRecord existing, StartTripRequest request) {
        return existing.routeId().equals(request.routeId()) && existing.startedAt() == request.startedAt()
                && existing.origin().equals(request.origin()) && existing.destination().equals(request.destination())
                && existing.ourEtaSeconds() == request.ourEtaSeconds()
                && Objects.equals(existing.tmapEtaSeconds(), request.tmapEtaSeconds())
                && Objects.equals(existing.tmapDistanceMeters(), request.tmapDistanceMeters());
    }
}
