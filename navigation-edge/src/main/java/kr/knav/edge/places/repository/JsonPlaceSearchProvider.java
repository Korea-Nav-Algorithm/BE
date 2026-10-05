package kr.knav.edge.places.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import kr.knav.edge.places.dto.PlaceResult;
import kr.knav.edge.places.service.PlaceSearchProvider;
import kr.knav.edge.places.service.PlaceSearchUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Small development catalog keeps search, route selection, and offline tests usable without an API key. */
@Component
public class JsonPlaceSearchProvider implements PlaceSearchProvider {
    private final ObjectMapper mapper;
    private final String file;

    public JsonPlaceSearchProvider(ObjectMapper mapper, @Value("${places.file}") String file) {
        this.mapper = mapper; this.file = file;
    }

    @Override public List<PlaceResult> search(String query) {
        try {
            PlaceFile catalog = mapper.readValue(new File(file), PlaceFile.class);
            if (catalog.places() == null) throw new PlaceSearchUnavailableException();
            return catalog.places().stream().filter(place -> place.name() != null
                    && place.name().toLowerCase().contains(query.toLowerCase())).limit(5).toList();
        } catch (IOException exception) { throw new PlaceSearchUnavailableException(); }
    }

    @Override public String source() { return "JSON"; }

    private record PlaceFile(List<PlaceResult> places) {}
}
