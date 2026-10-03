package kr.knav.engine.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JsonTrafficProvider implements TrafficProvider {
    private final ObjectMapper mapper;
    private final String file;
    public JsonTrafficProvider(ObjectMapper mapper, @Value("${traffic.file}") String file) {
        this.mapper = mapper; this.file = file;
    }
    @Override public TrafficSnapshot current() {
        try {
            TrafficFile parsed = mapper.readValue(new File(file), TrafficFile.class);
            Map<String, TrafficState> states = new HashMap<>();
            for (TrafficState state : parsed.edges()) states.put(state.edgeId(), state);
            return new TrafficSnapshot(states, System.currentTimeMillis());
        } catch (IOException exception) { throw new IllegalStateException("Cannot load traffic JSON: " + file, exception); }
    }
    private record TrafficFile(List<TrafficState> edges) {}
}
