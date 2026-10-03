package kr.knav.edge.routes.controller;

import jakarta.validation.Valid;
import kr.knav.common.RouteRequest;
import kr.knav.common.RouteResponse;
import kr.knav.edge.routes.service.RouteProxyService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final RouteProxyService service;
    public RouteController(RouteProxyService service) { this.service = service; }
    @PostMapping public RouteResponse route(@Valid @RequestBody RouteRequest request) { return service.route(request); }
    @GetMapping("/{routeId}") public RouteResponse get(@PathVariable String routeId) { return service.get(routeId); }
}
