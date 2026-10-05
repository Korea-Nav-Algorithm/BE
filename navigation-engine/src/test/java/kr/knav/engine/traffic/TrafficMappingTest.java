package kr.knav.engine.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import kr.knav.common.Coordinate;
import kr.knav.engine.graph.RoadClass;
import kr.knav.engine.graph.RoadEdge;
import kr.knav.engine.graph.RoadGraph;
import kr.knav.engine.graph.RoadNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

class TrafficMappingTest {
    @TempDir Path temporaryDirectory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void directedGeoJsonMapsOneMeasuredLinkToSeveralOsmEdges() throws Exception {
        Path file = temporaryDirectory.resolve("links.geojson");
        Files.writeString(file, """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"LINK_ID":"east"},"geometry":{"type":"LineString",
                    "coordinates":[[127.0,37.0],[127.002,37.0]]}},
                  {"type":"Feature","properties":{"LINK_ID":"west"},"geometry":{"type":"LineString",
                    "coordinates":[[127.002,37.0],[127.0,37.0]]}}
                ]}
                """);
        Coordinate west = new Coordinate(37.0, 127.0);
        Coordinate center = new Coordinate(37.0, 127.001);
        Coordinate east = new Coordinate(37.0, 127.002);
        RoadGraph graph = new RoadGraph(List.of(new RoadNode(1, west.lat(), west.lng()),
                new RoadNode(2, center.lat(), center.lng()), new RoadNode(3, east.lat(), east.lng())), List.of(
                edge("first-east", 1, 2, west, center), edge("second-east", 2, 3, center, east),
                edge("first-west", 3, 2, east, center), edge("second-west", 2, 1, center, west)));
        GeometryTrafficEdgeMapper geometry = new GeometryTrafficEdgeMapper(mapper, graph, "gyeonggi", file.toString());
        assertEquals(List.of("first-east", "second-east").stream().sorted().toList(),
                geometry.mapExternalLinkToEdges(link("east")).stream().sorted().toList());
        assertEquals(List.of("first-west", "second-west").stream().sorted().toList(),
                geometry.mapExternalLinkToEdges(link("west")).stream().sorted().toList());
        assertTrue(geometry.mapExternalLinkToEdges(link("unknown")).isEmpty());
    }

    @Test void manualMappingAcceptsLegacySingleEdgeAndDirectedEdgeArrays() throws Exception {
        Path file = temporaryDirectory.resolve("mapping.json");
        Files.writeString(file, "{\"old\":\"edge-1\",\"new\":[\"edge-2\",\"edge-3\"]}");
        ManualTrafficEdgeMapper manual = new ManualTrafficEdgeMapper(mapper, file.toString());
        assertEquals(List.of("edge-1"), manual.mapExternalLinkToEdges(link("old")));
        assertEquals(List.of("edge-2", "edge-3"), manual.mapExternalLinkToEdges(link("new")));
    }

    @Test void liveProviderFansOutMeasuredSpeedAndKeepsSecretOutOfResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/traffic", exchange -> {
            assertTrue(exchange.getRequestURI().getRawQuery().contains("serviceKey=test-key"));
            String routeId = exchange.getRequestURI().getRawQuery().contains("routeId=first")
                    ? "first" : "second";
            requests.incrementAndGet();
            String observedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                    .format(DateTimeFormatter.ofPattern(routeId.equals("first") ? "yyyyMMddHHmm" : "yyyyMMddHHmmss"));
            if (routeId.equals("second")) observedAt += ".0";
            byte[] body = ("<response><headerCd>0</headerCd><item><linkId>" + routeId + "</linkId><spd>18</spd>"
                    + "<collDate>" + observedAt + "</collDate></item></response>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        GyeonggiTrafficProvider provider = new GyeonggiTrafficProvider(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/traffic", "test-key",
                "gyeonggi", "first,second", link -> link.linkId().equals("first")
                        ? List.of("edge-1", "edge-2") : List.of("edge-3"));
        try {
            provider.warmUp();
            long deadline = System.currentTimeMillis() + 3_000;
            while (provider.current().edges().isEmpty() && System.currentTimeMillis() < deadline)
                Thread.sleep(20);
            assertEquals(3, provider.current().edges().size());
            assertEquals(18, provider.current().speedOrBase("edge-2", 60));
            assertEquals("GYEONGGI", provider.source());
            assertEquals(2, requests.get());
        } finally { provider.shutdown(); server.stop(0); }
    }

    @Test @ExtendWith(OutputCaptureExtension.class)
    void suspendedKeyIsDiagnosedWithoutLoggingTheKey(CapturedOutput output) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/traffic", exchange -> {
            byte[] body = "<response><headerCd>7</headerCd></response>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) { response.write(body); }
        });
        server.start();
        GyeonggiTrafficProvider provider = new GyeonggiTrafficProvider(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/traffic",
                "test-private-key", "gyeonggi", "", link -> List.of("edge-1"));
        try {
            provider.warmUp();
            long deadline = System.currentTimeMillis() + 3_000;
            while (!output.getAll().contains("reason=upstream_code_7") && System.currentTimeMillis() < deadline)
                Thread.sleep(20);
            assertTrue(output.getAll().contains("reason=upstream_code_7"));
            assertFalse(output.getAll().contains("test-private-key"));
            assertTrue(provider.current().edges().isEmpty());
        } finally { provider.shutdown(); server.stop(0); }
    }

    private RoadEdge edge(String id, long from, long to, Coordinate start, Coordinate end) {
        return new RoadEdge(id, from, to, 90, 60, false, "road", RoadClass.PRIMARY, List.of(start, end));
    }
    private ExternalTrafficLink link(String id) { return new ExternalTrafficLink(id, 20, 1, null, null); }
}
