package kr.knav.engine.routing;

import jakarta.validation.Valid;
import kr.knav.common.RouteRequest;
import kr.knav.common.RouteResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/routes")
public class RouteController {
    private final RouteService service;
    public RouteController(RouteService service) { this.service = service; }
    @PostMapping public RouteResponse route(@Valid @RequestBody RouteRequest request) { return service.calculate(request); }
}
