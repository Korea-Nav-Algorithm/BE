package kr.knav.engine.traffic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Exact, operator-reviewed link mapping avoids accidentally assigning opposite-direction speeds. */
@Component
public class ManualTrafficEdgeMapper implements TrafficEdgeMapper {
    private final Map<String, String> mapping;
    public ManualTrafficEdgeMapper(ObjectMapper mapper, @Value("${traffic.mapping-file}") String path) {
        try { mapping = mapper.readValue(new File(path), new TypeReference<>() {}); }
        catch (IOException exception) { throw new IllegalStateException("Cannot load traffic mapping: " + path, exception); }
    }
    @Override public Optional<String> mapExternalLinkToEdge(ExternalTrafficLink link) {
        return Optional.ofNullable(mapping.get(link.linkId()));
    }
}
