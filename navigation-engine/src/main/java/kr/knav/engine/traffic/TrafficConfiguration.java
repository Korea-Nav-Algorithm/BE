package kr.knav.engine.traffic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrafficConfiguration {
    @Bean TrafficProvider trafficProvider(JsonTrafficProvider json, GyeonggiTrafficProvider gyeonggi,
                                          @Value("${traffic.provider}") String provider) {
        return switch (provider) {
            case "json" -> json;
            case "gyeonggi" -> gyeonggi;
            default -> throw new IllegalArgumentException("Unsupported TRAFFIC_PROVIDER: " + provider);
        };
    }
}
