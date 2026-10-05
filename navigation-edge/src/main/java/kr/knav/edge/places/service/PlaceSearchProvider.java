package kr.knav.edge.places.service;

import java.util.List;
import kr.knav.edge.places.dto.PlaceResult;

public interface PlaceSearchProvider {
    List<PlaceResult> search(String query);
    String source();
}
