package kr.knav.edge.places.service;

import kr.knav.edge.places.dto.PlaceSearchResponse;
import org.springframework.stereotype.Service;

/** Normalizes user input before delegating a bounded lookup to the configured provider. */
@Service
public class PlaceSearchService {
    private final PlaceSearchProvider provider;

    public PlaceSearchService(PlaceSearchProvider provider) { this.provider = provider; }

    public PlaceSearchResponse search(String query) {
        if (query == null) throw new IllegalArgumentException("Place query is required");
        String normalized = query.trim();
        if (normalized.length() < 2 || normalized.length() > 80)
            throw new IllegalArgumentException("Place query must have 2..80 characters");
        return new PlaceSearchResponse(provider.search(normalized), provider.source());
    }
}
