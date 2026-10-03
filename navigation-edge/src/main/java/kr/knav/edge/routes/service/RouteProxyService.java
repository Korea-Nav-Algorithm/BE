package kr.knav.edge.routes.service;

import kr.knav.common.RouteRequest;
import kr.knav.common.RouteResponse;
import kr.knav.edge.routes.client.EngineClient;
import kr.knav.edge.routes.repository.RouteSnapshotRepository;
import org.springframework.stereotype.Service;

@Service
public class RouteProxyService {
    private final EngineClient client;
    private final RouteSnapshotRepository snapshots;
    public RouteProxyService(EngineClient client, RouteSnapshotRepository snapshots) {
        this.client = client; this.snapshots = snapshots;
    }
    public RouteResponse route(RouteRequest request) {
        RouteResponse response = client.route(request);
        snapshots.save(request, response);
        return response;
    }
    public RouteResponse get(String routeId) {
        return snapshots.find(routeId).orElseThrow(RouteNotFoundException::new);
    }
}
