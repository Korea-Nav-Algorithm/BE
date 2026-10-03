package kr.knav.edge.global;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SqliteMigrationTest {
    @TempDir Path directory;

    @Test void oldTripAndGpsSurvivePointIdMigration() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("old.db"));
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE trip(id TEXT PRIMARY KEY,route_id TEXT NOT NULL,started_at INTEGER NOT NULL,"
                + "finished_at INTEGER,origin_lat REAL NOT NULL,origin_lng REAL NOT NULL,"
                + "destination_lat REAL NOT NULL,destination_lng REAL NOT NULL,our_eta_seconds INTEGER NOT NULL,"
                + "tmap_eta_seconds INTEGER,actual_duration_seconds INTEGER,created_at INTEGER NOT NULL)");
        jdbc.execute("CREATE TABLE gps_point(id INTEGER PRIMARY KEY AUTOINCREMENT,trip_id TEXT NOT NULL REFERENCES trip(id),"
                + "timestamp INTEGER NOT NULL,lat REAL NOT NULL,lng REAL NOT NULL,gps_speed REAL,heading REAL,"
                + "accuracy REAL,UNIQUE(trip_id, timestamp))");
        jdbc.execute("CREATE TABLE route_snapshot(route_id TEXT PRIMARY KEY,algorithm TEXT NOT NULL,"
                + "response_json TEXT NOT NULL,created_at INTEGER NOT NULL)");
        jdbc.update("INSERT INTO trip(id,route_id,started_at,origin_lat,origin_lng,destination_lat,destination_lng,"
                + "our_eta_seconds,created_at) VALUES('old','route',100,37,127,37,127,30,100)");
        jdbc.update("INSERT INTO gps_point(trip_id,timestamp,lat,lng) VALUES('old',101,37,127)");

        new SqliteMigration().migrateNavigationDatabase(jdbc, new DataSourceTransactionManager(dataSource)).run(null);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM gps_point WHERE trip_id='old'", Integer.class));
        assertEquals(null, jdbc.queryForObject("SELECT client_trip_id FROM trip WHERE id='old'", String.class));
        assertEquals(null, jdbc.queryForObject("SELECT point_id FROM gps_point WHERE trip_id='old'", String.class));
        jdbc.update("INSERT INTO gps_point(trip_id,point_id,timestamp,lat,lng) VALUES('old','point-1',101,37,127)");
        jdbc.update("INSERT OR IGNORE INTO gps_point(trip_id,point_id,timestamp,lat,lng) "
                + "VALUES('old','point-1',102,37,127)");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM gps_point WHERE trip_id='old'", Integer.class));
        new SqliteMigration().migrateNavigationDatabase(jdbc, new DataSourceTransactionManager(dataSource)).run(null);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM gps_point WHERE trip_id='old'", Integer.class));
    }
}
