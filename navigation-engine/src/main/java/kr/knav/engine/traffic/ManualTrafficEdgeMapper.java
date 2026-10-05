package kr.knav.engine.traffic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Exact, operator-reviewed link mapping avoids accidentally assigning opposite-direction speeds. */
@Component
public class ManualTrafficEdgeMapper implements TrafficEdgeMapper {
    private final Map<String, List<String>> mapping;
    public ManualTrafficEdgeMapper(ObjectMapper mapper, @Value("${traffic.mapping-file}") String path) {
        try {
            JsonNode root = mapper.readTree(new File(path));
            if (!root.isObject()) throw new IllegalStateException("Invalid traffic mapping file");
            Map<String, List<String>> loaded = new HashMap<>();
            root.fields().forEachRemaining(entry -> {
                List<String> edges = new ArrayList<>();
                JsonNode value = entry.getValue();
                if (value.isTextual()) edges.add(value.asText());
                else if (value.isArray()) value.forEach(edge -> {
                    if (!edge.isTextual()) throw new IllegalStateException("Invalid traffic mapping edge");
                    edges.add(edge.asText());
                });
                else throw new IllegalStateException("Invalid traffic mapping value");
                if (edges.stream().anyMatch(String::isBlank)) throw new IllegalStateException("Empty traffic mapping edge");
                loaded.put(entry.getKey(), List.copyOf(edges));
            });
            mapping = Map.copyOf(loaded);
        } catch (IOException exception) { throw new IllegalStateException("Cannot load traffic mapping: " + path, exception); }
    }
    @Override public List<String> mapExternalLinkToEdges(ExternalTrafficLink link) {
        return mapping.getOrDefault(link.linkId(), List.of());
    }
}
