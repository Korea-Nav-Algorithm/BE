PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;
CREATE TABLE IF NOT EXISTS trip (
    id TEXT PRIMARY KEY,
    client_trip_id TEXT UNIQUE NOT NULL,
    access_key_hash TEXT NOT NULL,
    route_id TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    finished_at INTEGER,
    origin_lat REAL NOT NULL,
    origin_lng REAL NOT NULL,
    destination_lat REAL NOT NULL,
    destination_lng REAL NOT NULL,
    our_eta_seconds INTEGER NOT NULL,
    tmap_eta_seconds INTEGER,
    tmap_distance_meters INTEGER,
    actual_duration_seconds INTEGER,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS trip_created_at_idx ON trip(created_at DESC);
CREATE TABLE IF NOT EXISTS gps_point (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trip_id TEXT NOT NULL REFERENCES trip(id),
    point_id TEXT,
    timestamp INTEGER NOT NULL,
    lat REAL NOT NULL,
    lng REAL NOT NULL,
    gps_speed REAL,
    heading REAL,
    accuracy REAL,
    UNIQUE(trip_id, point_id)
);
CREATE INDEX IF NOT EXISTS gps_point_trip_idx ON gps_point(trip_id, timestamp);
CREATE TABLE IF NOT EXISTS trip_route (
    trip_id TEXT NOT NULL REFERENCES trip(id),
    route_id TEXT NOT NULL,
    occurred_at INTEGER NOT NULL,
    PRIMARY KEY (trip_id, route_id)
);
CREATE INDEX IF NOT EXISTS trip_route_time_idx ON trip_route(trip_id, occurred_at);
CREATE TABLE IF NOT EXISTS route_snapshot (
    route_id TEXT PRIMARY KEY,
    algorithm TEXT NOT NULL,
    algorithm_version TEXT,
    origin_lat REAL,
    origin_lng REAL,
    destination_lat REAL,
    destination_lng REAL,
    response_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
