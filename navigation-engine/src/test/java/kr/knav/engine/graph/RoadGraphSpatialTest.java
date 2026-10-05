package kr.knav.engine.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import kr.knav.common.Coordinate;
import org.junit.jupiter.api.Test;

class RoadGraphSpatialTest {
    @Test void boundedNearestNodeCrossesGridBoundaryAndRejectsRemoteCoordinates() {
        RoadNode west = new RoadNode(1, 37.0099, 127.0099);
        RoadNode east = new RoadNode(2, 37.0101, 127.0101);
        RoadGraph graph = new RoadGraph(List.of(west, east), List.of());

        assertEquals(east, graph.findNearestNodeWithin(new Coordinate(37.01008, 127.01008), 1000));
        assertEquals(west, graph.findNearestNodeWithin(new Coordinate(37.00992, 127.00992), 1000));
        assertNull(graph.findNearestNodeWithin(new Coordinate(38.0, 128.0), 1000));
    }
}
