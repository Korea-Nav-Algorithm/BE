package kr.knav.engine.graph;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfiguration {
    private static final Logger log = LoggerFactory.getLogger(GraphConfiguration.class);
    @Bean RoadGraph roadGraph(JsonRoadGraphLoader jsonLoader, OsmPbfRoadGraphLoader osmLoader,
                              @Value("${graph.source}") String source,
                              @Value("${graph.osm-file}") String osmFile) {
        long started = System.nanoTime();
        RoadGraph graph = switch (source) {
            case "json" -> jsonLoader.load();
            case "osm-pbf" -> osmLoader.load();
            default -> throw new IllegalArgumentException("Unsupported GRAPH_SOURCE: " + source);
        };
        if (graph.nodeCount() == 0 || graph.edgeCount() == 0)
            throw new IllegalStateException("Road graph has no routable nodes or edges");
        String fileName = source.equals("osm-pbf") ? Path.of(osmFile).getFileName().toString() : "json";
        long usedMemoryMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576;
        log.info("event=graph_loaded source={} file={} nodesLoaded={} directedEdges={} turnRestrictions={} buildMs={} usedMemoryMb={}",
                source, fileName, graph.nodeCount(), graph.edgeCount(), graph.turnRestrictionCount(),
                (System.nanoTime() - started) / 1_000_000, usedMemoryMb);
        return graph;
    }
}
