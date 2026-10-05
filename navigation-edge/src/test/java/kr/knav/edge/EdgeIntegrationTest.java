package kr.knav.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.net.URI;
import kr.knav.common.Coordinate;
import kr.knav.common.Algorithm;
import kr.knav.common.RouteRequest;
import kr.knav.edge.trips.dto.GpsBatchRequest;
import kr.knav.edge.trips.dto.GpsPointRequest;
import kr.knav.edge.trips.dto.StartTripRequest;
import kr.knav.edge.trips.dto.FinishTripRequest;
import kr.knav.edge.trips.dto.RerouteRequest;
import kr.knav.edge.trips.dto.TripReroute;
import kr.knav.edge.trips.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:sqlite::memory:", "NAV_ENGINE_BASE_URL=http://127.0.0.1:1",
        "PLACE_SEARCH_FILE=src/test/resources/test-places.json"})
class EdgeIntegrationTest {
    private static final String ACCESS_KEY = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    @Autowired TripService trips;
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;
    @Test void placeSearchReturnsDestinationAndRejectsMissingQuery() {
        var response = http.getForEntity(URI.create("http://localhost:" + port
                + "/api/places?query=%EB%B6%84%EB%8B%B9%EC%A4%91%EC%95%99%EA%B5%90%ED%9A%8C"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().contains("37.37709"));
        assertEquals(HttpStatus.BAD_REQUEST, http.getForEntity("/api/places", String.class).getStatusCode());
    }
    @Test void duplicateGpsBatchPersistsOnce() {
        Coordinate place = new Coordinate(37.0, 127.0);
        String id = trips.start(new StartTripRequest(UUID.randomUUID().toString(), ACCESS_KEY, "route-test",
                100L, place, place, 30L, null, null)).response().tripId();
        GpsBatchRequest batch = new GpsBatchRequest(List.of(new GpsPointRequest(
                UUID.randomUUID().toString(), 101L, 37.0, 127.0, 14.8, 132.4, 6.2)));
        assertEquals(1, trips.savePoints(id, ACCESS_KEY, batch).pointsStored());
        assertEquals(0, trips.savePoints(id, ACCESS_KEY, batch).pointsStored());
        GpsBatchRequest sameTimeDifferentId = new GpsBatchRequest(List.of(new GpsPointRequest(
                UUID.randomUUID().toString(), 101L, 37.0, 127.001, null, null, null)));
        assertEquals(1, trips.savePoints(id, ACCESS_KEY, sameTimeDifferentId).pointsStored());
        assertEquals(2, trips.get(id, ACCESS_KEY).pointCount());
    }
    @Test void tripCreateIsIdempotentAndManualBenchmarkPersists() {
        Coordinate place = new Coordinate(37.0, 127.0);
        String clientId = UUID.randomUUID().toString();
        StartTripRequest request = new StartTripRequest(clientId, ACCESS_KEY, "route-test", 100L,
                place, place, 30L, 35L, 500L);
        var first = http.postForEntity("/api/trips", request, String.class);
        var second = http.postForEntity("/api/trips", request, String.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(first.getBody(), second.getBody());
        String tripId = trips.start(request).response().tripId();
        assertEquals(35L, trips.get(tripId, ACCESS_KEY).tmapEtaSeconds());
        assertEquals(500L, trips.get(tripId, ACCESS_KEY).tmapDistanceMeters());
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/api/trips",
                new StartTripRequest(clientId, ACCESS_KEY, "route-test", 100L, place, place, 30L, 36L, 500L),
                String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/api/trips",
                new StartTripRequest(UUID.randomUUID().toString(), ACCESS_KEY, "route-test", 100L, place, place, 30L, -1L, null),
                String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/api/trips",
                new StartTripRequest(UUID.randomUUID().toString(), ACCESS_KEY, "route-test", 100L, place, place, 30L, null, -1L),
                String.class).getStatusCode());
    }
    @Test void tripWithoutBenchmarkAndRepeatedFinish() {
        Coordinate place = new Coordinate(37.0, 127.0);
        String tripId = trips.start(new StartTripRequest(UUID.randomUUID().toString(), ACCESS_KEY, "route-test",
                100L, place, place, 30L, null, null)).response().tripId();
        assertEquals(null, trips.get(tripId, ACCESS_KEY).tmapEtaSeconds());
        assertEquals(null, trips.get(tripId, ACCESS_KEY).tmapDistanceMeters());
        trips.finish(tripId, ACCESS_KEY, new FinishTripRequest(130L, 30L));
        trips.finish(tripId, ACCESS_KEY, new FinishTripRequest(130L, 30L));
        assertThrows(IllegalArgumentException.class, () -> trips.finish(tripId, ACCESS_KEY, new FinishTripRequest(131L, 31L)));
        assertEquals(30L, trips.get(tripId, ACCESS_KEY).actualDurationSeconds());
    }
    @Test void tripGpsCannotBeReadOrModifiedWithoutItsKey() {
        Coordinate place = new Coordinate(37.0, 127.0);
        String id = trips.start(new StartTripRequest(UUID.randomUUID().toString(), ACCESS_KEY, "route-test",
                100L, place, place, 30L, null, null)).response().tripId();
        assertEquals(HttpStatus.NOT_FOUND, http.getForEntity("/api/trips/" + id, String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, http.getForEntity("/api/trips", String.class).getStatusCode());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trip-Key", ACCESS_KEY);
        assertEquals(HttpStatus.OK, http.exchange("/api/trips/" + id, HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getStatusCode());
        GpsBatchRequest batch = new GpsBatchRequest(List.of(new GpsPointRequest(
                UUID.randomUUID().toString(), 101L, 37.0, 127.0, null, null, null)));
        assertEquals(HttpStatus.NOT_FOUND, http.postForEntity("/api/trips/" + id + "/points", batch,
                String.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.exchange("/api/trips/" + id + "/points", HttpMethod.POST,
                new HttpEntity<>(batch, headers), String.class).getStatusCode());
    }
    @Test void reroutedRouteIdsRemainLinkedToTripWithoutDuplicateRows() {
        Coordinate place = new Coordinate(37.0, 127.0);
        String id = trips.start(new StartTripRequest(UUID.randomUUID().toString(), ACCESS_KEY, "initial-route",
                100L, place, place, 30L, null, null)).response().tripId();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trip-Key", ACCESS_KEY);
        RerouteRequest reroute = new RerouteRequest("new-route", 110L);
        for (int retry = 0; retry < 2; retry++)
            assertEquals(HttpStatus.NO_CONTENT, http.exchange("/api/trips/" + id + "/routes", HttpMethod.POST,
                    new HttpEntity<>(reroute, headers), String.class).getStatusCode());
        assertEquals(List.of(new TripReroute("new-route", 110L)), trips.get(id, ACCESS_KEY).reroutes());
        assertEquals(HttpStatus.NOT_FOUND, http.postForEntity("/api/trips/" + id + "/routes", reroute,
                String.class).getStatusCode());
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
