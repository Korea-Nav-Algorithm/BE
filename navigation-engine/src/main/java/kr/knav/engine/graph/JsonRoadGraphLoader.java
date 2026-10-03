package kr.knav.engine.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Loads a bounded road fixture; invalid edges fail startup rather than producing silent routes. */
@Component
public class JsonRoadGraphLoader implements RoadGraphLoader {
    private final ObjectMapper mapper;
    private final String file;
    public JsonRoadGraphLoader(ObjectMapper mapper, @Value("${graph.file}") String file) {
        this.mapper = mapper; this.file = file;
    }
    @Override public RoadGraph load() {
        try {
            GraphFile graphFile = mapper.readValue(new File(file), GraphFile.class);
            return new RoadGraph(graphFile.nodes(), graphFile.edges());
        } catch (IOException exception) { throw new IllegalStateException("Cannot load road graph: " + file, exception); }
    }
    private record GraphFile(List<RoadNode> nodes, List<RoadEdge> edges) {}
}
