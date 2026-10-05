package kr.knav.engine.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import kr.knav.engine.graph.JsonRoadGraphLoader;
import kr.knav.engine.graph.RoadEdge;
import kr.knav.engine.graph.RoadGraph;
import kr.knav.engine.graph.RoadNode;
import kr.knav.engine.graph.RoadClass;
import kr.knav.common.Coordinate;
import kr.knav.common.Algorithm;
import kr.knav.common.RouteRequest;
import kr.knav.common.TrafficLevel;
import kr.knav.engine.traffic.TrafficProvider;
import kr.knav.engine.traffic.TrafficSnapshot;
import kr.knav.engine.traffic.TrafficState;
import org.junit.jupiter.api.Test;

class RoutingTest {
    private final RoadGraph graph = new JsonRoadGraphLoader(new ObjectMapper().findAndRegisterModules(),
            "src/test/resources/test-graph.json").load();
    private final AStarRouter router = new AStarRouter();
    private final ReverseCongestionSearch search = new ReverseCongestionSearch(3000);
    private final TrafficSnapshot traffic = new TrafficSnapshot(Map.of(
            "A", new TrafficState("A", 25, 1), "B", new TrafficState("B", 20, 1),
            "C", new TrafficState("C", 18, 1), "Y", new TrafficState("Y", 5, 1),
            "S", new TrafficState("S", 48, 1)), 1);

    @Test void directionAwareAttributionDependsOnDestination() {
        Map<String, Double> yangjae = search.attribute(graph, 5, traffic);
        Map<String, Double> sadang = search.attribute(graph, 6, traffic);
        for (String upstream : List.of("A", "B", "C")) {
            assertTrue(yangjae.get(upstream) > 0.5);
            assertTrue(sadang.getOrDefault(upstream, 0.0) < 0.1);
            RoadEdge edge = graph.outgoing(switch (upstream) { case "A" -> 1; case "B" -> 2; default -> 3; }).getFirst();
            assertTrue(EdgeCost.calculate(edge, traffic, yangjae.get(upstream)).effectiveSeconds()
                    > EdgeCost.calculate(edge, traffic, sadang.getOrDefault(upstream, 0.0)).effectiveSeconds());
        }
    }
    @Test void missingOrEquallyCongestedAlternativeNeverErasesObservedDelay() {
        TrafficSnapshot missingBranch = new TrafficSnapshot(Map.of(
                "A", new TrafficState("A", 25, 1), "B", new TrafficState("B", 20, 1),
                "C", new TrafficState("C", 18, 1), "S", new TrafficState("S", 48, 1)), 1);
        assertEquals(1.0, search.attribute(graph, 6, missingBranch).get("A"));
        TrafficSnapshot equalBranches = new TrafficSnapshot(Map.of(
                "A", new TrafficState("A", 25, 1), "B", new TrafficState("B", 20, 1),
                "C", new TrafficState("C", 18, 1), "Y", new TrafficState("Y", 10, 1),
                "S", new TrafficState("S", 10, 1)), 1);
        assertEquals(1.0, search.attribute(graph, 6, equalBranches).get("A"));
    }
    @Test void baselineUsesObservedTravelTimeAndCorrectBranch() {
        List<RoadEdge> route = router.route(graph, 1, 6, traffic, Map.of(), 1);
        assertEquals(List.of("A", "B", "C", "S"), route.stream().map(RoadEdge::id).toList());
        assertEquals(120 * 3.6 / 25, EdgeCost.calculate(route.getFirst(), traffic, 1).effectiveSeconds(), 0.0001);
    }
    @Test void directionAwareRetainsObservedDelayOutsideReverseSearch() {
        RoadGraph remote = new RoadGraph(List.of(new RoadNode(10, 37, 127),
                new RoadNode(11, 37.01, 127), new RoadNode(12, 37.02, 127)), List.of(
                new RoadEdge("jam", 10, 11, 1100, 60, true, "", RoadClass.PRIMARY,
                        List.of(new Coordinate(37.0, 127.0), new Coordinate(37.01, 127.0))),
                new RoadEdge("last", 11, 12, 1100, 60, true, "", RoadClass.PRIMARY,
                        List.of(new Coordinate(37.01, 127.0), new Coordinate(37.02, 127.0)))));
        TrafficProvider provider = () -> new TrafficSnapshot(Map.of("jam", new TrafficState("jam", 15, 1)), 1);
        RouteService service = new RouteService(remote, provider, router, new ReverseCongestionSearch(500), "test");
        var response = service.calculate(new RouteRequest(new Coordinate(37.0, 127.0),
                new Coordinate(37.02, 127.0), Algorithm.DIRECTION_AWARE));
        assertEquals(1.0, response.segments().getFirst().attribution());
        assertEquals(Math.round(1100 * 3.6 / 15 + 1100 * 3.6 / 60), response.durationSeconds());
    }
    @Test void baselineChoosesMinimumTravelTimeRatherThanFewestEdges() {
        RoadGraph alternatives = new RoadGraph(List.of(
                new RoadNode(10, 37.0, 127.0), new RoadNode(11, 37.001, 127.0),
                new RoadNode(12, 37.002, 127.0)), List.of(
                new RoadEdge("direct", 10, 12, 240, 20, true, "direct", RoadClass.PRIMARY,
                        List.of(new Coordinate(37.0, 127.0), new Coordinate(37.002, 127.0))),
                new RoadEdge("first", 10, 11, 120, 60, true, "first", RoadClass.PRIMARY,
                        List.of(new Coordinate(37.0, 127.0), new Coordinate(37.001, 127.0))),
                new RoadEdge("second", 11, 12, 120, 60, true, "second", RoadClass.PRIMARY,
                        List.of(new Coordinate(37.001, 127.0), new Coordinate(37.002, 127.0)))));
        assertEquals(List.of("first", "second"), router.route(alternatives, 10, 12,
                new TrafficSnapshot(Map.of(), 1), Map.of(), 1).stream().map(RoadEdge::id).toList());
    }
    @Test void measuredCongestionChangesFastestRouteForBothAlgorithms() {
        Coordinate start = new Coordinate(37.0, 127.0);
        Coordinate middle = new Coordinate(37.001, 127.0);
        Coordinate finish = new Coordinate(37.002, 127.0);
        RoadGraph alternatives = new RoadGraph(List.of(
                new RoadNode(10, start.lat(), start.lng()),
                new RoadNode(11, middle.lat(), middle.lng()),
                new RoadNode(12, finish.lat(), finish.lng())), List.of(
                new RoadEdge("direct", 10, 12, 230, 60, true, "direct", RoadClass.PRIMARY,
                        List.of(start, finish)),
                new RoadEdge("detour-a", 10, 11, 140, 60, true, "detour", RoadClass.PRIMARY,
                        List.of(start, middle)),
                new RoadEdge("detour-b", 11, 12, 140, 60, true, "detour", RoadClass.PRIMARY,
                        List.of(middle, finish))));
        TrafficProvider noTraffic = () -> new TrafficSnapshot(Map.of(), 1);
        TrafficProvider congestion = () -> new TrafficSnapshot(Map.of(
                "direct", new TrafficState("direct", 5, 1)), 1);
        for (Algorithm algorithm : Algorithm.values()) {
            RouteRequest request = new RouteRequest(start, finish, algorithm);
            RouteService unobserved = new RouteService(alternatives, noTraffic,
                    new AStarRouter(1), search, "test");
            RouteService observed = new RouteService(alternatives, congestion,
                    new AStarRouter(1), search, "test");
            assertEquals(List.of("direct"), unobserved.calculate(request).segments().stream()
                    .map(segment -> segment.edgeId()).toList());
            assertEquals(List.of("detour-a", "detour-b"), observed.calculate(request).segments().stream()
                    .map(segment -> segment.edgeId()).toList());
        }
    }
    @Test void absentTrafficUsesBaseSpeed() {
        TrafficSnapshot empty = new TrafficSnapshot(Map.of(), 1);
        List<RoadEdge> route = router.route(graph, 1, 5, empty, Map.of(), 1);
        assertEquals(List.of("A", "B", "C", "Y"), route.stream().map(RoadEdge::id).toList());
        assertEquals(120 * 3.6 / 60, EdgeCost.calculate(route.getFirst(), empty, 1).effectiveSeconds(), 0.0001);
    }
    @Test void unobservedSpeedFactorChangesEtaWithoutInventingTraffic() {
        TrafficSnapshot empty = new TrafficSnapshot(Map.of(), 1);
        RoadEdge edge = graph.outgoing(1).getFirst();
        EdgeCost estimated = EdgeCost.calculate(edge, empty, 1, 0.75);
        assertEquals(estimated.baseSeconds() / 0.75, estimated.effectiveSeconds(), 0.0001);
        assertEquals(edge.baseSpeedKmh(), estimated.observedSpeedKmh(), 0.0001);
        assertEquals(0, estimated.excessSeconds(), 0.0001);
        assertTrue(new AStarRouter(0.75).route(graph, 1, 5, empty, Map.of(), 1).size() > 0);
        assertThrows(IllegalArgumentException.class, () -> new AStarRouter(0));
    }
    @Test void observedTrafficClassifiesSlowCongestedAndFree() {
        TrafficProvider provider = () -> traffic;
        RouteService service = new RouteService(graph, provider, router, search, "test");
        var response = service.calculate(new RouteRequest(new Coordinate(37.0, 127.0),
                new Coordinate(37.004, 127.001), Algorithm.BASELINE));
        assertTrue(response.segments().stream().anyMatch(segment -> segment.trafficLevel() == TrafficLevel.SLOW));
        assertTrue(response.segments().stream().anyMatch(segment -> segment.trafficLevel() == TrafficLevel.CONGESTED));
        assertTrue(response.segments().stream().anyMatch(segment -> segment.trafficLevel() == TrafficLevel.FREE));
    }
    @Test void segmentGeometryAndTrafficLevelAreAvailableForMapColoring() {
        Coordinate first = new Coordinate(37.0, 127.0);
        Coordinate middle = new Coordinate(37.0005, 127.0);
        Coordinate second = new Coordinate(37.001, 127.0);
        Coordinate third = new Coordinate(37.002, 127.0);
        RoadGraph shaped = new RoadGraph(List.of(new RoadNode(1, first.lat(), first.lng()),
                new RoadNode(2, second.lat(), second.lng()), new RoadNode(3, third.lat(), third.lng())),
                List.of(new RoadEdge("first", 1, 2, 110, 50, true, "", RoadClass.PRIMARY,
                                List.of(first, middle, second)),
                        new RoadEdge("second", 2, 3, 110, 50, true, "", RoadClass.PRIMARY,
                                List.of(second, third))));
        TrafficProvider observedFirst = () -> new TrafficSnapshot(Map.of(
                "first", new TrafficState("first", 15, 1)), 1);
        RouteService service = new RouteService(shaped, observedFirst, new AStarRouter(0.75), search, "test");
        var response = service.calculate(new RouteRequest(first, third, Algorithm.BASELINE));
        assertEquals(4, response.geometry().size());
        assertEquals(TrafficLevel.CONGESTED, response.segments().get(0).trafficLevel());
        assertEquals(0, response.segments().get(0).geometryStartIndex());
        assertEquals(2, response.segments().get(0).geometryEndIndex());
        assertEquals(TrafficLevel.UNKNOWN, response.segments().get(1).trafficLevel());
        assertEquals(2, response.segments().get(1).geometryStartIndex());
        assertEquals(3, response.segments().get(1).geometryEndIndex());
    }
    @Test void oneWayReverseTraversalIsRejected() {
        RoadGraph oneWay = new RoadGraph(List.of(new RoadNode(1, 37, 127),
                new RoadNode(2, 37.001, 127)), List.of(new RoadEdge("forward", 1, 2, 120, 50,
                true, "one-way", RoadClass.PRIMARY,
                List.of(new Coordinate(37.0, 127.0), new Coordinate(37.001, 127.0)))));
        assertEquals(1, router.route(oneWay, 1, 2, traffic, Map.of(), 1).size());
        assertThrows(RouteNotFoundException.class,
                () -> router.route(oneWay, 2, 1, traffic, Map.of(), 1));
    }
    @Test void zeroDistanceProducesValidRouteAndAlgorithmVersion() {
        TrafficProvider noTraffic = () -> new TrafficSnapshot(Map.of(), 1);
        RouteService service = new RouteService(graph, noTraffic, router, search, "test-version");
        for (Algorithm algorithm : Algorithm.values()) {
            var response = service.calculate(new RouteRequest(new Coordinate(37.0, 127.0),
                    new Coordinate(37.0, 127.0), algorithm));
            assertEquals(0, response.distanceMeters());
            assertEquals(0, response.durationSeconds());
            assertEquals(1, response.geometry().size());
            assertTrue(response.segments().isEmpty());
            assertEquals(algorithm, response.algorithm());
            assertEquals("test-version", response.algorithmVersion());
        }
    }
}
