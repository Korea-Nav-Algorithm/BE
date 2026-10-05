package kr.knav.engine.traffic;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Operator-reviewed mappings override geometry matches for known exceptions. */
@Configuration
public class TrafficMappingConfiguration {
    @Bean @Primary TrafficEdgeMapper trafficEdgeMapper(ManualTrafficEdgeMapper manual,
                                                       GeometryTrafficEdgeMapper geometry) {
        return link -> {
            List<String> explicit = manual.mapExternalLinkToEdges(link);
            return explicit.isEmpty() ? geometry.mapExternalLinkToEdges(link) : explicit;
        };
    }
}
