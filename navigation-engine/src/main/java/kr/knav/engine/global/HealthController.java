package kr.knav.engine.global;

import java.util.Map;
import kr.knav.engine.graph.RoadGraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final RoadGraph graph;
    private final String trafficProvider;
    private final String algorithmVersion;
    public HealthController(RoadGraph graph, @Value("${traffic.provider}") String trafficProvider,
                            @Value("${routing.algorithm-version}") String algorithmVersion) {
        this.graph = graph; this.trafficProvider = trafficProvider; this.algorithmVersion = algorithmVersion;
    }
    @GetMapping("/health") public Map<String, Object> health() {
        return Map.of("status", "UP", "graphLoaded", true, "nodeCount", graph.nodeCount(),
                "edgeCount", graph.edgeCount(), "trafficProvider", trafficProvider,
                "algorithmVersion", algorithmVersion);
    }
}
