package kr.knav.edge.trips.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.knav.edge.trips.dto.FinishTripRequest;
import kr.knav.edge.trips.dto.GpsBatchRequest;
import kr.knav.edge.trips.dto.GpsBatchResponse;
import kr.knav.edge.trips.dto.StartTripRequest;
import kr.knav.edge.trips.dto.StartTripResponse;
import kr.knav.edge.trips.dto.TripResponse;
import kr.knav.edge.trips.service.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final TripService service;
    public TripController(TripService service) { this.service = service; }
    @PostMapping public ResponseEntity<StartTripResponse> start(@Valid @RequestBody StartTripRequest request) {
        TripService.StartResult result = service.start(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }
    @PostMapping("/{tripId}/points") public GpsBatchResponse points(@PathVariable String tripId,
                                                              @Valid @RequestBody GpsBatchRequest request) {
        return service.savePoints(tripId, request);
    }
    @PostMapping("/{tripId}/finish") public ResponseEntity<Void> finish(@PathVariable String tripId,
                                                          @Valid @RequestBody FinishTripRequest request) {
        service.finish(tripId, request); return ResponseEntity.noContent().build();
    }
    @GetMapping("/{tripId}") public TripResponse get(@PathVariable String tripId) { return service.get(tripId); }
    @GetMapping public List<TripResponse> recent() { return service.recent(); }
}
