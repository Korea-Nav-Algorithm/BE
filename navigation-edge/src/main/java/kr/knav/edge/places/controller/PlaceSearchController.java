package kr.knav.edge.places.controller;

import kr.knav.edge.places.dto.PlaceSearchResponse;
import kr.knav.edge.places.service.PlaceSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaceSearchController {
    private final PlaceSearchService service;

    public PlaceSearchController(PlaceSearchService service) { this.service = service; }

    @GetMapping("/api/places")
    public PlaceSearchResponse search(@RequestParam String query) { return service.search(query); }
}
