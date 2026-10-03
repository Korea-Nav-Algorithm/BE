package kr.knav.engine.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.knav.common.Algorithm;
import kr.knav.common.Coordinate;
import kr.knav.common.RouteRequest;
import kr.knav.common.RouteResponse;
import kr.knav.common.RouteSegment;
import kr.knav.engine.graph.RoadEdge;
import kr.knav.engine.graph.RoadGraph;
import kr.knav.engine.graph.RoadNode;
import kr.knav.engine.traffic.TrafficProvider;
import kr.knav.engine.traffic.TrafficSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

/** Runs one immutable traffic snapshot through attribution, A*, ETA, and diagnostics. */
@Service
public class RouteService {
    private static final Logger log = LoggerFactory.getLogger(RouteService.class);
    private final RoadGraph graph;
    private final TrafficProvider trafficProvider;
    private final AStarRouter router;
    private final ReverseCongestionSearch reverseSearch;
    private final String algorithmVersion;
    public RouteService(RoadGraph graph, TrafficProvider trafficProvider, AStarRouter router,
                        ReverseCongestionSearch reverseSearch,
                        @Value("${routing.algorithm-version}") String algorithmVersion) {
        this.graph = graph; this.trafficProvider = trafficProvider; this.router = router;
        this.reverseSearch = reverseSearch; this.algorithmVersion = algorithmVersion;
    }
    public RouteResponse calculate(RouteRequest request) {
        long started = System.nanoTime();
        RoadNode origin = graph.findNearestNode(request.origin());
        RoadNode destination = graph.findNearestNode(request.destination());
        double originSnapMeters = Geo.meters(request.origin(), new Coordinate(origin.lat(), origin.lng()));
        double destinationSnapMeters = Geo.meters(request.destination(), new Coordinate(destination.lat(), destination.lng()));
        log.info("event=route_snap originNode={} destinationNode={} originSnapMeters={} destinationSnapMeters={}",
                origin.id(), destination.id(), originSnapMeters, destinationSnapMeters);
        if (originSnapMeters > 1000 || destinationSnapMeters > 1000)
            throw new RouteNotFoundException();
        long lookupEnd = System.nanoTime();
        TrafficSnapshot traffic = trafficProvider.current();
        log.info("event=traffic_snapshot provider={} snapshotTimestamp={} edgeCount={}",
                trafficProvider.getClass().getSimpleName(), traffic.timestamp(), traffic.edges().size());
        Map<String, Double> attribution = request.algorithm() == Algorithm.DIRECTION_AWARE
                ? reverseSearch.attribute(graph, destination.id(), traffic) : Map.of();
        double defaultAttribution = request.algorithm() == Algorithm.BASELINE ? 1.0 : 0.0;
        long attributionEnd = System.nanoTime();
        List<RoadEdge> edges = router.route(graph, origin.id(), destination.id(), traffic, attribution, defaultAttribution);
        long routingEnd = System.nanoTime();
        RouteResponse response = response(edges, traffic, attribution, defaultAttribution, origin, request.algorithm());
        log.info("event=route routeId={} algorithm={} algorithmVersion={} origin={},{} destination={},{} distance={} duration={} segments={} "
                        + "graphLookupMs={} attributionMs={} routingTimeMs={} totalMs={}", response.routeId(),
                request.algorithm(), algorithmVersion, request.origin().lat(), request.origin().lng(), request.destination().lat(),
                request.destination().lng(), response.distanceMeters(), response.durationSeconds(), response.segments().size(),
                millis(lookupEnd - started), millis(attributionEnd - lookupEnd), millis(routingEnd - attributionEnd),
                millis(System.nanoTime() - started));
        return response;
    }
    private RouteResponse response(List<RoadEdge> edges, TrafficSnapshot traffic,
                                   Map<String, Double> attribution, double defaultAttribution, RoadNode origin,
                                   Algorithm algorithm) {
        double distance = 0;
        double duration = 0;
        List<Coordinate> geometry = new ArrayList<>();
        List<RouteSegment> segments = new ArrayList<>();
        if (edges.isEmpty()) geometry.add(new Coordinate(origin.lat(), origin.lng()));
        for (RoadEdge edge : edges) {
            EdgeCost cost = EdgeCost.calculate(edge, traffic,
                    attribution.getOrDefault(edge.id(), defaultAttribution));
            distance += edge.distanceMeters();
            duration += cost.effectiveSeconds();
            double effectiveSpeed = edge.distanceMeters() * 3.6 / cost.effectiveSeconds();
            segments.add(new RouteSegment(edge.id(), cost.observedSpeedKmh(), edge.baseSpeedKmh(),
                    cost.attribution(), effectiveSpeed));
            for (Coordinate coordinate : edge.geometry()) {
                if (geometry.isEmpty() || !geometry.getLast().equals(coordinate)) geometry.add(coordinate);
            }
        }
        return new RouteResponse(UUID.randomUUID().toString(), algorithm, algorithmVersion,
                Math.round(distance), Math.round(duration), geometry, segments);
    }
    private long millis(long nanos) { return nanos / 1_000_000; }
}
