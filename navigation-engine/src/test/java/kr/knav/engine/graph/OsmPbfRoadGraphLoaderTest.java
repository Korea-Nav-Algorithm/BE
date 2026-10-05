package kr.knav.engine.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.List;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.impl.Relation;
import de.topobyte.osm4j.core.model.impl.RelationMember;
import de.topobyte.osm4j.core.model.impl.Tag;
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
    @Test void countryPbfCanBeBoundedToTheDrivingRegion() {
        var bounds = OsmPbfRoadGraphLoader.Bounds.parse("126.6,36.8,127.6,37.8");
        assertTrue(bounds.contains(37.37709, 127.13973));
        assertFalse(bounds.contains(38.0, 127.13973));
        assertThrows(IllegalArgumentException.class,
                () -> OsmPbfRoadGraphLoader.Bounds.parse("127.6,36.8,126.6,37.8"));
    }
    @Test void parsesViaNodeTurnRestrictionAndMotorcarException() {
        List<RelationMember> members = List.of(
                new RelationMember(100, EntityType.Way, "from"),
                new RelationMember(2, EntityType.Node, "via"),
                new RelationMember(200, EntityType.Way, "to"));
        var restricted = new Relation(1, members,
                List.of(new Tag("type", "restriction"), new Tag("restriction", "no_left_turn")));
        var excepted = new Relation(2, members,
                List.of(new Tag("type", "restriction"), new Tag("restriction", "no_left_turn"),
                        new Tag("except", "motorcar")));
        assertTrue(OsmPbfRoadGraphLoader.parseTurnRestriction(restricted)
                .equals(new TurnRestriction(100, 2, 200, false, false)));
        assertTrue(OsmPbfRoadGraphLoader.parseTurnRestriction(excepted) == null);
    }
}
