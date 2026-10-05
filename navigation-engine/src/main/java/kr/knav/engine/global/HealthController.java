package kr.knav.engine.global;

import java.util.Map;
import kr.knav.engine.graph.RoadGraph;
import kr.knav.engine.traffic.TrafficProvider;
import kr.knav.engine.traffic.TrafficSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final RoadGraph graph;
    private final String trafficProvider;
    private final TrafficProvider traffic;
    private final String algorithmVersion;
    public HealthController(RoadGraph graph, TrafficProvider traffic,
                            @Value("${traffic.provider}") String trafficProvider,
                            @Value("${routing.algorithm-version}") String algorithmVersion) {
        this.graph = graph; this.traffic = traffic; this.trafficProvider = trafficProvider;
        this.algorithmVersion = algorithmVersion;
    }
    @GetMapping("/health") public Map<String, Object> health() {
        TrafficSnapshot snapshot = traffic.current();
        String source = traffic.source();
        return Map.of("status", "UP", "graphLoaded", true, "nodeCount", graph.nodeCount(),
                "edgeCount", graph.edgeCount(), "turnRestrictionCount", graph.turnRestrictionCount(), "trafficProvider", trafficProvider,
                "algorithmVersion", algorithmVersion, "trafficSource", source,
                "trafficObservedEdges", snapshot.edges().size(),
                "trafficReady", "GYEONGGI".equals(source) && !snapshot.edges().isEmpty());
    }
}
