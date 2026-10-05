package kr.knav.engine.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import kr.knav.common.Coordinate;
import kr.knav.engine.graph.RoadClass;
import kr.knav.engine.graph.RoadEdge;
import kr.knav.engine.graph.RoadGraph;
import kr.knav.engine.graph.RoadNode;
import kr.knav.engine.graph.TurnRestriction;
import kr.knav.engine.traffic.TrafficSnapshot;
import org.junit.jupiter.api.Test;

class TurnRestrictionRoutingTest {
    private final AStarRouter router = new AStarRouter();
    private final TrafficSnapshot empty = new TrafficSnapshot(Map.of(), 1);
    private final List<RoadNode> nodes = List.of(
            new RoadNode(1, 37.0, 127.0), new RoadNode(2, 37.001, 127.0),
            new RoadNode(3, 37.002, 127.0), new RoadNode(4, 37.001, 127.001));
    private final List<RoadEdge> edges = List.of(
            edge("100:1:f", 1, 2), edge("200:1:f", 2, 3),
            edge("300:1:f", 2, 4), edge("400:1:f", 4, 3));

    @Test void noTurnForcesLegalDetour() {
        RoadGraph graph = new RoadGraph(nodes, edges, List.of(new TurnRestriction(100, 2, 200, false, false)));
        assertEquals(List.of("100:1:f", "300:1:f", "400:1:f"), ids(graph, 1, 3));
        assertEquals(List.of("200:1:f"), ids(graph, 2, 3));
    }

    @Test void onlyTurnForcesListedBranch() {
        RoadGraph graph = new RoadGraph(nodes, edges, List.of(new TurnRestriction(100, 2, 300, true, false)));
        assertEquals(List.of("100:1:f", "300:1:f", "400:1:f"), ids(graph, 1, 3));
    }

    @Test void noUTurnBlocksImmediateReverseButAllowsForwardContinuation() {
        RoadEdge reverse = edge("100:1:r", 2, 1);
        RoadGraph graph = new RoadGraph(nodes, List.of(edges.get(0), reverse, edges.get(1)),
                List.of(new TurnRestriction(100, 2, 100, false, true)));
        assertEquals(List.of("100:1:f", "200:1:f"), ids(graph, 1, 3));
        org.junit.jupiter.api.Assertions.assertFalse(graph.turnAllowed(2, 100, 1, reverse));
        org.junit.jupiter.api.Assertions.assertTrue(graph.turnAllowed(2, 100, 1, edges.get(1)));
    }

    private List<String> ids(RoadGraph graph, long from, long to) {
        return router.route(graph, from, to, empty, Map.of(), 1).stream().map(RoadEdge::id).toList();
    }

    private RoadEdge edge(String id, long from, long to) {
        RoadNode first = nodes.stream().filter(node -> node.id() == from).findFirst().orElseThrow();
        RoadNode second = nodes.stream().filter(node -> node.id() == to).findFirst().orElseThrow();
        return new RoadEdge(id, from, to, 100, 50, true, "", RoadClass.PRIMARY,
                List.of(new Coordinate(first.lat(), first.lng()), new Coordinate(second.lat(), second.lng())));
    }
}
