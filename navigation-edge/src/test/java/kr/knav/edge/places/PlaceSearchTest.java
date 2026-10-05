package kr.knav.edge.places;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import kr.knav.edge.places.client.NaverPlaceClient;
import kr.knav.edge.places.repository.JsonPlaceSearchProvider;
import kr.knav.edge.places.service.PlaceSearchService;
import kr.knav.edge.places.service.PlaceSearchUnavailableException;
import org.junit.jupiter.api.Test;

class PlaceSearchTest {
    @Test void developmentCatalogSearchesKoreanBuildingNames() {
        PlaceSearchService service = new PlaceSearchService(new JsonPlaceSearchProvider(
                new ObjectMapper(), "src/test/resources/test-places.json"));
        var response = service.search("  분당중앙교회  ");
        assertEquals("JSON", response.source());
        assertEquals(1, response.places().size());
        assertEquals(37.37709, response.places().getFirst().coordinate().lat());
        assertTrue(service.search("없는장소").places().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.search("a"));
    }

    @Test void naverAdapterKeepsCredentialsInHeadersAndConvertsCoordinates() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/local.json", exchange -> {
            assertEquals("test-id", exchange.getRequestHeaders().getFirst("X-Naver-Client-Id"));
            assertEquals("test-secret", exchange.getRequestHeaders().getFirst("X-Naver-Client-Secret"));
            assertTrue(exchange.getRequestURI().getRawQuery().contains("display=5"));
            assertTrue(!exchange.getRequestURI().getRawQuery().contains("test-secret"));
            String body = "{\"items\":[{\"title\":\"<b>분당</b>중앙교회\","
                    + "\"roadAddress\":\"효자길 39\",\"address\":\"성남시\","
                    + "\"mapx\":1271397300,\"mapy\":373770900}]}";
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, data.length);
            try (var output = exchange.getResponseBody()) { output.write(data); }
        });
        server.start();
        try {
            NaverPlaceClient client = new NaverPlaceClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/local.json",
                    "test-id", "test-secret");
            var results = client.search("분당중앙교회");
            assertEquals(1, results.size());
            assertEquals("분당중앙교회", results.getFirst().name());
            assertEquals(127.13973, results.getFirst().coordinate().lng());
            assertEquals(37.37709, results.getFirst().coordinate().lat());
            assertTrue(results.getFirst().id().startsWith("naver-"));
            assertEquals(results.getFirst().id(), client.search("분당중앙교회").getFirst().id());
            assertThrows(PlaceSearchUnavailableException.class, () -> new NaverPlaceClient(
                    new ObjectMapper(), "http://127.0.0.1/local.json", "", "test-secret")
                    .search("교회"));
        } finally { server.stop(0); }
    }
}
