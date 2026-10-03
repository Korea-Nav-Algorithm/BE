# Backend 상태

| 상태 | 소유자 | Source of truth | 전이·규칙 |
| --- | --- | --- | --- |
| Trip open/finished | Edge | SQLite `trip.finished_at` | null=open, 값 있음=finished; 같은 종료값은 멱등, 다른 값 400 |
| Trip 생성 키 | Edge | SQLite `trip.client_trip_id` | 신규 UUID 필수·unique; migration 전 row만 null 가능 |
| GPS point 집합 | Edge | SQLite `gps_point` | `(trip_id,point_id)` unique; 이전 row 유지, 기존 row는 point_id null |
| Route diagnostics | Edge | SQLite `route_snapshot` | 성공한 OUR route만 저장; algorithm/version/좌표/응답 JSON |
| RoadGraph | Engine | JSON/PBF 파일 | Engine 기동 시 로드, 0 node/edge면 기동 실패 |
| TrafficSnapshot | Engine | JSON 파일 또는 경기 API cache | 미관측 edge는 baseSpeed 사용 |

`Algorithm`: `BASELINE`은 관측 추가 지연을 전부 반영한다. `DIRECTION_AWARE`는 목적지 방향에 귀속된 지연을 반영한다. 둘 모두 비음수 A* 비용을 사용한다. `RoadClass`: `MOTORWAY` 고속도로, `TRUNK` 간선 고속 도로, `PRIMARY` 주요 도로, `SECONDARY` 보조 주요 도로, `TERTIARY` 연결 도로, `RESIDENTIAL` 주거지 도로, `OTHER` 기타(JSON graph용)다. PBF 로더는 OTHER를 생성하지 않는다. 사용자·인증·UI 상태는 Backend에 없다. DTO 검증과 TripService가 입력·상태 전이를 검사한다.
