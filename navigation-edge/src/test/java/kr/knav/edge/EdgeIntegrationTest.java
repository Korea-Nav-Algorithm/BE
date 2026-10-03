package kr.knav.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.knav.common.Coordinate;
import kr.knav.common.Algorithm;
import kr.knav.common.RouteRequest;
import kr.knav.edge.trips.dto.GpsBatchRequest;
import kr.knav.edge.trips.dto.GpsPointRequest;
import kr.knav.edge.trips.dto.StartTripRequest;
import kr.knav.edge.trips.dto.FinishTripRequest;
import kr.knav.edge.trips.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite::memory:", "NAV_ENGINE_BASE_URL=http://127.0.0.1:1"})
class EdgeIntegrationTest {
    @Autowired TripService trips;
    @Autowired TestRestTemplate http;
    @Test void duplicateGpsBatchPersistsOnce() {
        Coordinate place = new Coordinate(37.0, 127.0);
        String id = trips.start(new StartTripRequest(UUID.randomUUID().toString(), "route-test",
                100L, place, place, 30L, null, null)).response().tripId();
        GpsBatchRequest batch = new GpsBatchRequest(List.of(new GpsPointRequest(
                UUID.randomUUID().toString(), 101L, 37.0, 127.0, 14.8, 132.4, 6.2)));
        assertEquals(1, trips.savePoints(id, batch).pointsStored());
        assertEquals(0, trips.savePoints(id, batch).pointsStored());
        GpsBatchRequest sameTimeDifferentId = new GpsBatchRequest(List.of(new GpsPointRequest(
                UUID.randomUUID().toString(), 101L, 37.0, 127.001, null, null, null)));
        assertEquals(1, trips.savePoints(id, sameTimeDifferentId).pointsStored());
        assertEquals(2, trips.get(id).pointCount());
    }
    @Test void tripCreateIsIdempotentAndManualBenchmarkPersists() {
        Coordinate place = new Coordinate(37.0, 127.0);
        String clientId = UUID.randomUUID().toString();
        StartTripRequest request = new StartTripRequest(clientId, "route-test", 100L,
                place, place, 30L, 35L, 500L);
        var first = http.postForEntity("/api/trips", request, String.class);
        var second = http.postForEntity("/api/trips", request, String.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(first.getBody(), second.getBody());
        String tripId = trips.start(request).response().tripId();
        assertEquals(35L, trips.get(tripId).tmapEtaSeconds());
        assertEquals(500L, trips.get(tripId).tmapDistanceMeters());
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/api/trips",
                new StartTripRequest(clientId, "route-test", 100L, place, place, 30L, 36L, 500L),
                String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/api/trips",
                new StartTripRequest(UUID.randomUUID().toString(), "route-test", 100L, place, place, 30L, -1L, null),
                String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/api/trips",
                new StartTripRequest(UUID.randomUUID().toString(), "route-test", 100L, place, place, 30L, null, -1L),
                String.class).getStatusCode());
    }
    @Test void tripWithoutBenchmarkAndRepeatedFinish() {
        Coordinate place = new Coordinate(37.0, 127.0);
        String tripId = trips.start(new StartTripRequest(UUID.randomUUID().toString(), "route-test",
                100L, place, place, 30L, null, null)).response().tripId();
        assertEquals(null, trips.get(tripId).tmapEtaSeconds());
        assertEquals(null, trips.get(tripId).tmapDistanceMeters());
        trips.finish(tripId, new FinishTripRequest(130L, 30L));
        trips.finish(tripId, new FinishTripRequest(130L, 30L));
        assertThrows(IllegalArgumentException.class, () -> trips.finish(tripId, new FinishTripRequest(131L, 31L)));
        assertEquals(30L, trips.get(tripId).actualDurationSeconds());
    }
    @Test void engineUnavailableReturns503() {
        RouteRequest request = new RouteRequest(new Coordinate(37.0, 127.0), new Coordinate(37.1, 127.1), Algorithm.BASELINE);
        var response = http.postForEntity("/api/routes", request, String.class);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
    }
    @Test void missingRequiredTripNumbersReturn400() {
        Map<String, Object> body = Map.of("routeId", "route-test", "origin", Map.of("lat", 37, "lng", 127),
                "destination", Map.of("lat", 37, "lng", 127), "ourEtaSeconds", 30);
        var response = http.postForEntity("/api/trips", body, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
    @Test void invalidAlgorithmIsRejectedBeforeEngineCall() {
        Map<String, Object> request = Map.of("origin", Map.of("lat", 37, "lng", 127),
                "destination", Map.of("lat", 37.1, "lng", 127.1), "algorithm", "UNKNOWN");
        assertEquals(HttpStatus.BAD_REQUEST,
                http.postForEntity("/api/routes", request, String.class).getStatusCode());
    }
    @Test void tmapProxyIsAbsent() {
        Map<String, Object> request = Map.of("origin", Map.of("lat", 37, "lng", 127),
                "destination", Map.of("lat", 37.1, "lng", 127.1));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED,
                http.postForEntity("/api/routes/tmap", request, String.class).getStatusCode());
    }
}
