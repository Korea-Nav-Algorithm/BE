package kr.knav.edge.trips.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import kr.knav.common.Coordinate;
import kr.knav.edge.trips.dto.GpsPointRequest;
import kr.knav.edge.trips.dto.StartTripRequest;
import kr.knav.edge.trips.dto.TripReroute;
import kr.knav.edge.trips.entity.TripRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQLite owns stable client trip and point IDs so replayed requests are safe. */
@Repository
public class TripRepository {
    private final JdbcTemplate jdbc;
    public TripRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void create(String id, StartTripRequest request, String accessKeyHash) {
        jdbc.update("INSERT INTO trip(id,client_trip_id,access_key_hash,route_id,started_at,origin_lat,origin_lng,destination_lat,destination_lng,"
                        + "our_eta_seconds,tmap_eta_seconds,tmap_distance_meters,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, request.clientTripId(), accessKeyHash, request.routeId(), request.startedAt(), request.origin().lat(), request.origin().lng(),
                request.destination().lat(), request.destination().lng(), request.ourEtaSeconds(),
                request.tmapEtaSeconds(), request.tmapDistanceMeters(), System.currentTimeMillis());
    }
    public Optional<TripRecord> findByClientTripId(String clientTripId) {
        return jdbc.query("SELECT t.*, (SELECT COUNT(*) FROM gps_point p WHERE p.trip_id=t.id) point_count "
                + "FROM trip t WHERE t.client_trip_id=?", (result, row) -> map(result), clientTripId)
                .stream().findFirst();
    }
    public boolean exists(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM trip WHERE id=?)", Boolean.class, id));
    }
    public int insertPoint(String tripId, GpsPointRequest point) {
        return jdbc.update("INSERT OR IGNORE INTO gps_point(trip_id,point_id,timestamp,lat,lng,gps_speed,heading,accuracy) "
                        + "VALUES(?,?,?,?,?,?,?,?)", tripId, point.pointId(), point.timestamp(), point.lat(), point.lng(),
                point.gpsSpeed(), point.heading(), point.accuracy());
    }
    public int insertReroute(String tripId, String routeId, long occurredAt) {
        return jdbc.update("INSERT OR IGNORE INTO trip_route(trip_id,route_id,occurred_at) VALUES(?,?,?)",
                tripId, routeId, occurredAt);
    }
    public List<TripReroute> reroutes(String tripId) {
        return jdbc.query("SELECT route_id,occurred_at FROM trip_route WHERE trip_id=? ORDER BY occurred_at,route_id",
                (result, row) -> new TripReroute(result.getString("route_id"), result.getLong("occurred_at")), tripId);
    }
    public void finish(String id, long finishedAt, long actualDurationSeconds) {
        jdbc.update("UPDATE trip SET finished_at=?,actual_duration_seconds=? WHERE id=? AND finished_at IS NULL",
                finishedAt, actualDurationSeconds, id);
    }
    public Optional<TripRecord> find(String id) {
        return jdbc.query("SELECT t.*, (SELECT COUNT(*) FROM gps_point p WHERE p.trip_id=t.id) point_count "
                + "FROM trip t WHERE t.id=?", (result, row) -> map(result), id).stream().findFirst();
    }
    public List<TripRecord> recent() {
        return jdbc.query("SELECT t.*, (SELECT COUNT(*) FROM gps_point p WHERE p.trip_id=t.id) point_count "
                + "FROM trip t ORDER BY created_at DESC LIMIT 50", (result, row) -> map(result));
    }
    public List<GpsPointRequest> points(String tripId) {
        return jdbc.query("SELECT * FROM gps_point WHERE trip_id=? ORDER BY timestamp,id", (result, row) ->
                new GpsPointRequest(result.getString("point_id"), result.getLong("timestamp"), result.getDouble("lat"), result.getDouble("lng"),
                        nullableDouble(result, "gps_speed"), nullableDouble(result, "heading"),
                        nullableDouble(result, "accuracy")), tripId);
    }
    private TripRecord map(ResultSet result) throws SQLException {
        return new TripRecord(result.getString("id"), result.getString("client_trip_id"),
                result.getString("access_key_hash"), result.getString("route_id"), result.getLong("started_at"),
                nullableLong(result, "finished_at"),
                new Coordinate(result.getDouble("origin_lat"), result.getDouble("origin_lng")),
                new Coordinate(result.getDouble("destination_lat"), result.getDouble("destination_lng")),
                result.getLong("our_eta_seconds"), nullableLong(result, "tmap_eta_seconds"),
                nullableLong(result, "tmap_distance_meters"),
                nullableLong(result, "actual_duration_seconds"), result.getLong("point_count"));
    }
    private Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column); return result.wasNull() ? null : value;
    }
    private Double nullableDouble(ResultSet result, String column) throws SQLException {
        double value = result.getDouble(column); return result.wasNull() ? null : value;
    }
}
