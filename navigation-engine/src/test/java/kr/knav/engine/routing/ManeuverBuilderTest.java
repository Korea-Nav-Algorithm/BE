package kr.knav.engine.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kr.knav.common.Coordinate;
import kr.knav.common.ManeuverType;
import kr.knav.engine.graph.RoadClass;
import kr.knav.engine.graph.RoadEdge;
import org.junit.jupiter.api.Test;

class ManeuverBuilderTest {
    @Test void detectsRightTurnAndKeepsGeometryPosition() {
        Coordinate start = new Coordinate(37.0, 127.0);
        Coordinate junction = new Coordinate(37.001, 127.0);
        Coordinate finish = new Coordinate(37.001, 127.001);
        List<RoadEdge> edges = List.of(
                new RoadEdge("north", 1, 2, 111, 50, true, "North Road", RoadClass.PRIMARY,
                        List.of(start, junction)),
                new RoadEdge("east", 2, 3, 89, 50, true, "East Road", RoadClass.PRIMARY,
                        List.of(junction, finish)));

        var instructions = ManeuverBuilder.build(edges, List.of(0, 1), List.of(start, junction, finish));

        assertEquals(List.of(ManeuverType.START, ManeuverType.TURN_RIGHT, ManeuverType.ARRIVE),
                instructions.stream().map(instruction -> instruction.type()).toList());
        assertEquals(1, instructions.get(1).geometryIndex());
        assertEquals(111, instructions.get(1).distanceFromStartMeters());
        assertEquals(finish, instructions.getLast().coordinate());
    }

    @Test void sameRoadStraightSegmentsDoNotProduceRepeatedCues() {
        Coordinate first = new Coordinate(37.0, 127.0);
        Coordinate second = new Coordinate(37.001, 127.0);
        Coordinate third = new Coordinate(37.002, 127.0);
        List<RoadEdge> edges = List.of(
                new RoadEdge("first", 1, 2, 111, 50, true, "Road", RoadClass.PRIMARY,
                        List.of(first, second)),
                new RoadEdge("second", 2, 3, 111, 50, true, "Road", RoadClass.PRIMARY,
                        List.of(second, third)));

        var instructions = ManeuverBuilder.build(edges, List.of(0, 1), List.of(first, second, third));

        assertEquals(List.of(ManeuverType.START, ManeuverType.ARRIVE),
                instructions.stream().map(instruction -> instruction.type()).toList());
    }
}
