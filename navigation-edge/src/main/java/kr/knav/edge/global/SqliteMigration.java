package kr.knav.edge.global;

import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Preserves existing driving logs while changing retry keys on first startup. */
@Configuration
public class SqliteMigration {
    @Bean ApplicationRunner migrateNavigationDatabase(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return args -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            if (!columns(jdbc, "trip").contains("client_trip_id"))
                jdbc.execute("ALTER TABLE trip ADD COLUMN client_trip_id TEXT");
            if (!columns(jdbc, "trip").contains("access_key_hash"))
                jdbc.execute("ALTER TABLE trip ADD COLUMN access_key_hash TEXT");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS trip_client_trip_id_idx ON trip(client_trip_id)");
            if (!columns(jdbc, "trip").contains("tmap_distance_meters"))
                jdbc.execute("ALTER TABLE trip ADD COLUMN tmap_distance_meters INTEGER");

            String gpsDefinition = jdbc.queryForObject(
                    "SELECT sql FROM sqlite_master WHERE type='table' AND name='gps_point'", String.class);
            if (gpsDefinition != null && gpsDefinition.contains("UNIQUE(trip_id, timestamp)")) {
                jdbc.execute("CREATE TABLE gps_point_migrated (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "trip_id TEXT NOT NULL REFERENCES trip(id), point_id TEXT, timestamp INTEGER NOT NULL, "
                        + "lat REAL NOT NULL, lng REAL NOT NULL, gps_speed REAL, heading REAL, accuracy REAL, "
                        + "UNIQUE(trip_id, point_id))");
                jdbc.execute("INSERT INTO gps_point_migrated(id,trip_id,timestamp,lat,lng,gps_speed,heading,accuracy) "
                        + "SELECT id,trip_id,timestamp,lat,lng,gps_speed,heading,accuracy FROM gps_point");
                jdbc.execute("DROP TABLE gps_point");
                jdbc.execute("ALTER TABLE gps_point_migrated RENAME TO gps_point");
                jdbc.execute("CREATE INDEX IF NOT EXISTS gps_point_trip_idx ON gps_point(trip_id,timestamp)");
            }
            if (!columns(jdbc, "gps_point").contains("point_id"))
                jdbc.execute("ALTER TABLE gps_point ADD COLUMN point_id TEXT");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS gps_point_trip_point_idx ON gps_point(trip_id,point_id)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS trip_route (trip_id TEXT NOT NULL REFERENCES trip(id), "
                    + "route_id TEXT NOT NULL, occurred_at INTEGER NOT NULL, PRIMARY KEY(trip_id,route_id))");
            jdbc.execute("CREATE INDEX IF NOT EXISTS trip_route_time_idx ON trip_route(trip_id,occurred_at)");

            if (!columns(jdbc, "route_snapshot").contains("algorithm_version"))
                jdbc.execute("ALTER TABLE route_snapshot ADD COLUMN algorithm_version TEXT");
            for (String column : List.of("origin_lat", "origin_lng", "destination_lat", "destination_lng")) {
                if (!columns(jdbc, "route_snapshot").contains(column))
                    jdbc.execute("ALTER TABLE route_snapshot ADD COLUMN " + column + " REAL");
            }
        });
    }
    private List<String> columns(JdbcTemplate jdbc, String table) {
        return jdbc.query("PRAGMA table_info(" + table + ")", (result, row) -> result.getString("name"));
    }
}
