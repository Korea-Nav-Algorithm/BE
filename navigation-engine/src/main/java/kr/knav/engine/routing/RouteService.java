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
import kr.knav.common.TrafficLevel;
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
        RoadNode origin = graph.findNearestNodeWithin(request.origin(), 1000);
        RoadNode destination = graph.findNearestNodeWithin(request.destination(), 1000);
        if (origin == null || destination == null) throw new RouteNotFoundException();
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
        // Only edges explicitly attributed by the reverse search may discount observed delay.
        // An edge outside its propagation window must retain its measured travel time.
        double defaultAttribution = 1.0;
        long attributionEnd = System.nanoTime();
        List<RoadEdge> edges = router.route(graph, origin.id(), destination.id(), traffic, attribution, defaultAttribution);
        long routingEnd = System.nanoTime();
        RouteResponse response = response(edges, traffic, attribution, defaultAttribution, origin, request.algorithm());
        log.info("event=route routeId={} algorithm={} algorithmVersion={} origin={},{} destination={},{} distance={} duration={} segments={} observedSegments={} trafficSource={} "
                        + "graphLookupMs={} attributionMs={} routingTimeMs={} totalMs={}", response.routeId(),
                request.algorithm(), algorithmVersion, request.origin().lat(), request.origin().lng(), request.destination().lat(),
                request.destination().lng(), response.distanceMeters(), response.durationSeconds(), response.segments().size(),
                response.segments().stream().filter(segment -> segment.trafficLevel() != TrafficLevel.UNKNOWN).count(),
                response.trafficSource(),
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
        List<Integer> edgeStartIndices = new ArrayList<>();
        if (edges.isEmpty()) geometry.add(new Coordinate(origin.lat(), origin.lng()));
        for (RoadEdge edge : edges) {
            int startIndex = geometry.isEmpty() ? 0 : geometry.getLast().equals(edge.geometry().getFirst())
                    ? geometry.size() - 1 : geometry.size();
            edgeStartIndices.add(startIndex);
            EdgeCost cost = router.cost(edge, traffic,
                    attribution.getOrDefault(edge.id(), defaultAttribution));
            distance += edge.distanceMeters();
            duration += cost.effectiveSeconds();
            double effectiveSpeed = edge.distanceMeters() * 3.6 / cost.effectiveSeconds();
            for (Coordinate coordinate : edge.geometry()) {
                if (geometry.isEmpty() || !geometry.getLast().equals(coordinate)) geometry.add(coordinate);
            }
            segments.add(new RouteSegment(edge.id(), cost.observedSpeedKmh(), edge.baseSpeedKmh(),
                    cost.attribution(), effectiveSpeed, trafficLevel(edge, traffic),
                    startIndex, geometry.size() - 1));
        }
        String trafficSource = segments.stream().anyMatch(segment -> segment.trafficLevel() != TrafficLevel.UNKNOWN)
                ? trafficProvider.source() : "UNKNOWN";
        return new RouteResponse(UUID.randomUUID().toString(), algorithm, algorithmVersion,
                Math.round(distance), Math.round(duration), geometry, segments,
                ManeuverBuilder.build(edges, edgeStartIndices, geometry), trafficSource);
    }
    private TrafficLevel trafficLevel(RoadEdge edge, TrafficSnapshot traffic) {
        if (!traffic.hasObservedSpeed(edge.id())) return TrafficLevel.UNKNOWN;
        double ratio = traffic.speedOrBase(edge.id(), edge.baseSpeedKmh()) / edge.baseSpeedKmh();
        if (ratio <= 0.4) return TrafficLevel.CONGESTED;
        if (ratio <= 0.7) return TrafficLevel.SLOW;
        return TrafficLevel.FREE;
    }
    private long millis(long nanos) { return nanos / 1_000_000; }
}
