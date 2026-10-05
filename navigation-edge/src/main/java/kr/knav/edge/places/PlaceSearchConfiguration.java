package kr.knav.edge.places;

import kr.knav.edge.places.client.NaverPlaceClient;
import kr.knav.edge.places.repository.JsonPlaceSearchProvider;
import kr.knav.edge.places.service.PlaceSearchProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class PlaceSearchConfiguration {
    @Bean @Primary PlaceSearchProvider placeSearchProvider(JsonPlaceSearchProvider json, NaverPlaceClient naver,
                                                  @Value("${places.provider}") String provider) {
        return switch (provider) {
            case "json" -> json;
            case "naver" -> naver;
            default -> throw new IllegalArgumentException("Unsupported PLACE_SEARCH_PROVIDER");
        };
    }
}
