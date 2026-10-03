package kr.knav.edge.routes.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import kr.knav.common.RouteResponse;
import kr.knav.common.RouteRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Retains returned segment attribution so experiment routes remain inspectable. */
@Repository
public class RouteSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public RouteSnapshotRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    public void save(RouteRequest request, RouteResponse response) {
        try {
            jdbc.update("INSERT INTO route_snapshot(route_id,algorithm,algorithm_version,origin_lat,origin_lng,"
                            + "destination_lat,destination_lng,response_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    response.routeId(), request.algorithm().name(), response.algorithmVersion(),
                    request.origin().lat(), request.origin().lng(), request.destination().lat(), request.destination().lng(),
                    mapper.writeValueAsString(response), System.currentTimeMillis());
        } catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot store route diagnostics", exception); }
    }
    public Optional<RouteResponse> find(String routeId) {
        return jdbc.query("SELECT response_json FROM route_snapshot WHERE route_id=?", (result, row) -> {
            try { return mapper.readValue(result.getString(1), RouteResponse.class); }
            catch (JsonProcessingException exception) { throw new IllegalStateException("Corrupt route snapshot", exception); }
        }, routeId).stream().findFirst();
    }
}
