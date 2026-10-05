package kr.knav.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.knav.engine.global.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "graph.source=json",
        "graph.file=src/test/resources/test-graph.json",
        "traffic.provider=json",
        "traffic.file=../data/dev-traffic.json",
        "traffic.mapping-file=../data/traffic-mapping.json"
})
class EngineContextTest {
    @Autowired private HealthController health;

    @Test void startsWithExactlyOneActiveTrafficProvider() {
        var status = health.health();
        assertEquals("UP", status.get("status"));
        assertEquals("json", status.get("trafficProvider"));
        assertTrue((Integer) status.get("edgeCount") > 0);
    }
}
