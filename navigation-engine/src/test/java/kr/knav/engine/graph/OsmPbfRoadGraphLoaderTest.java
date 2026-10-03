package kr.knav.engine.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OsmPbfRoadGraphLoaderTest {
    @Test void motorwayDefaultsToOneWayUnlessExplicitlyOverridden() {
        assertTrue(OsmPbfRoadGraphLoader.isOneWay(Map.of("highway", "motorway")));
        assertTrue(OsmPbfRoadGraphLoader.isOneWay(Map.of("highway", "motorway_link")));
        assertFalse(OsmPbfRoadGraphLoader.isOneWay(Map.of("highway", "motorway", "oneway", "no")));
        assertTrue(OsmPbfRoadGraphLoader.isOneWay(Map.of("highway", "residential", "oneway", "-1")));
        assertTrue(OsmPbfRoadGraphLoader.isOneWay(Map.of("highway", "residential", "junction", "roundabout")));
    }
    @Test void forbiddenCarAccessIsExcluded() {
        for (String key : new String[]{"access", "vehicle", "motor_vehicle", "motorcar"}) {
            assertTrue(OsmPbfRoadGraphLoader.blockedForCars(Map.of("highway", "service", key, "no")));
            assertTrue(OsmPbfRoadGraphLoader.blockedForCars(Map.of("highway", "service", key, "private")));
        }
        assertFalse(OsmPbfRoadGraphLoader.blockedForCars(Map.of("highway", "residential")));
    }
}
