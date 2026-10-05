# Edge SQLite schema

Edge의 `TRIP_DB_PATH` 파일은 route snapshot과 실차 로그를 보존한다. `navigation-edge/src/main/resources/schema.sql`은 새 DB를 만든다. 기존 DB는 `SqliteMigration`이 시작 시 **트랜잭션으로** 열과 index를 추가하고 옛 GPS table을 복사·교체한다. DB를 삭제하지 않는다. 기존 파일을 옮기기 전 DB와 WAL 백업을 권장한다. WAL·FK pragma를 설정하고 Hikari 연결 pool은 1개다. 삭제 API·soft delete·사용자/세션 테이블은 없다.

## trip

Owner: `navigation-edge/trips`. Trip 시작 metadata, 수동 TMAP benchmark와 종료 결과. Primary key `id`, 별도 `UNIQUE(client_trip_id)`는 재전송 키다. 이전 DB의 기존 row는 `client_trip_id`와 `access_key_hash`가 NULL일 수 있으며 신규 API로 만든 row는 둘 다 필수다. Key가 없는 이전 row는 공개 API에서 접근할 수 없고 운영자가 DB에서 확인한다. `trip_created_at_idx(created_at DESC)`로 운영자 전용 최근 50건을 조회한다.

| Column | Type | Required new row | Default | Index/unique | Meaning |
| --- | --- | --- | --- | --- | --- |
| id | TEXT | Y | none | PK | 서버 Trip UUID |
| client_trip_id | TEXT | Y | none; legacy NULL | unique | Frontend UUID 재전송 키 |
| access_key_hash | TEXT | Y | none; legacy NULL | no | 기기 생성 256비트 비밀키의 SHA-256 hex; 원본 key 저장 금지 |
| route_id | TEXT | Y | none | no | OUR route ID; FK 아님 |
| started_at | INTEGER | Y | none | no | Unix epoch milliseconds |
| finished_at | INTEGER | N | NULL | no | 종료 시각 milliseconds |
| origin_lat | REAL | Y | none | no | 출발 위도 |
| origin_lng | REAL | Y | none | no | 출발 경도 |
| destination_lat | REAL | Y | none | no | 목적지 위도 |
| destination_lng | REAL | Y | none | no | 목적지 경도 |
| our_eta_seconds | INTEGER | Y | none | no | OUR ETA seconds |
| tmap_eta_seconds | INTEGER | N | NULL | no | 공식 앱에서 사람이 기록한 ETA seconds |
| tmap_distance_meters | INTEGER | N | NULL | no | 공식 앱에서 사람이 기록한 거리 meters |
| actual_duration_seconds | INTEGER | N | NULL | no | 실제 소요시간 seconds |
| created_at | INTEGER | Y | none | DESC index | 서버 생성 시각 milliseconds |

## gps_point

Owner: `navigation-edge/trips`. GPS raw point 보존. Primary key `id`, FK `trip_id → trip.id`(N:1, 기본 제한 삭제), `UNIQUE(trip_id,point_id)`가 신규 중복 기준이다. `gps_point_trip_idx(trip_id,timestamp)`는 시각순 조회용이며 **timestamp는 unique가 아니다**. `point_id`는 기존 DB migration row에서 NULL, 신규 API 입력에서는 필수 UUID다. 동일 point ID 재전송은 `INSERT OR IGNORE`로 이전 점을 유지한다.

| Column | Type | Required new row | Default | Index/unique | Meaning |
| --- | --- | --- | --- | --- | --- |
| id | INTEGER | Y | autoincrement | PK | 내부 row ID |
| trip_id | TEXT | Y | none | FK, unique 복합 | 소유 Trip |
| point_id | TEXT | Y | none; legacy NULL | `(trip_id,point_id)` unique | Frontend 점 UUID |
| timestamp | INTEGER | Y | none | 조회 index | Unix epoch milliseconds |
| lat | REAL | Y | none | no | 위도 |
| lng | REAL | Y | none | no | 경도 |
| gps_speed | REAL | N | NULL | no | m/s; 변환 없이 저장 |
| heading | REAL | N | NULL | no | 도 [0,360] |
| accuracy | REAL | N | NULL | no | m |

## trip_route

Owner: `navigation-edge/trips`. Trip 생성 시의 원래 경로는 `trip.route_id`에 남고, 주행 중 재탐색 경로는 이 테이블에 누적한다. `(trip_id,route_id)` 기본키로 같은 재전송을 한 번만 보존한다. `occurred_at`은 최초 전송된 Unix epoch milliseconds이며 이후 같은 route ID 재시도로 변경되지 않는다.

| Column | Type | Required | Index/unique | Meaning |
| --- | --- | --- | --- | --- |
| trip_id | TEXT | Y | PK, FK `trip.id` | 주행 |
| route_id | TEXT | Y | PK | 재탐색 route ID |
| occurred_at | INTEGER | Y | `(trip_id,occurred_at)` index | 재탐색 시각 |

## route_snapshot

Owner: `navigation-edge/routes`. 성공한 OUR route의 JSON 응답을 재조회한다. TMAP API 응답은 없다. Primary key `route_id`. `algorithm`은 `BASELINE`/`DIRECTION_AWARE` 중 하나다. 기존 route snapshot의 신규 열은 NULL이지만 기존 `response_json`은 보존한다.

| Column | Type | Required new row | Default | Index/unique | Meaning |
| --- | --- | --- | --- | --- | --- |
| route_id | TEXT | Y | none | PK | Engine route UUID |
| algorithm | TEXT | Y | none | no | algorithm enum |
| algorithm_version | TEXT | Y | none; legacy NULL | no | 계산 코드 버전 |
| origin_lat | REAL | Y | none; legacy NULL | no | 요청 출발 위도 |
| origin_lng | REAL | Y | none; legacy NULL | no | 요청 출발 경도 |
| destination_lat | REAL | Y | none; legacy NULL | no | 요청 목적지 위도 |
| destination_lng | REAL | Y | none; legacy NULL | no | 요청 목적지 경도 |
| response_json | TEXT | Y | none | no | geometry·ETA·segments·instructions 포함 전체 응답 |
| created_at | INTEGER | Y | none | no | 서버 생성 시각 milliseconds |

Migration은 old `gps_point`의 `(trip_id,timestamp)` 제약을 제거해야 하므로 기존 행의 ID와 값을 새 table로 복사한다. 완료 뒤 `(trip_id,point_id)` 고유 index가 존재한다. Migration 자체는 재실행 가능하게 작성되어 있으며 이전 데이터와 같은 timestamp의 새 point가 공존한다. 향후 열 타입 변경이 필요하면 별도 migration을 작성해야 한다.
