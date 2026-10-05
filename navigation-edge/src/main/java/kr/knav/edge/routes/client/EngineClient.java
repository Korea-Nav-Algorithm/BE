package kr.knav.edge.routes.client;

import java.time.Duration;
import kr.knav.common.RouteRequest;
import kr.knav.common.RouteResponse;
import kr.knav.edge.routes.service.EngineUnavailableException;
import kr.knav.edge.routes.service.EngineRouteNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Only the edge can call the internal engine; network failures become retryable 503s. */
@Component
public class EngineClient {
    private final RestClient restClient;
    public EngineClient(@Value("${navigation.engine-base-url}") String baseUrl,
                        @Value("${navigation.engine-read-timeout-seconds:9}") int readTimeoutSeconds) {
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    public RouteResponse route(RouteRequest request) {
        try {
            RouteResponse response = restClient.post().uri("/internal/routes").body(request)
                    .retrieve().onStatus(status -> status.value() == 422, (sent, received) -> {
                        throw new EngineRouteNotFoundException();
                    }).onStatus(HttpStatusCode::isError, (sent, received) -> {
                        throw new EngineUnavailableException();
                    }).body(RouteResponse.class);
            if (response == null || response.routeId() == null || response.algorithm() != request.algorithm()
                    || response.algorithmVersion() == null || response.algorithmVersion().isBlank()
                    || response.distanceMeters() < 0 || response.durationSeconds() < 0
                    || response.geometry() == null || response.segments() == null)
                throw new EngineUnavailableException();
            return response;
        } catch (RestClientException exception) { throw new EngineUnavailableException(); }
    }
}
