# Components

| Module | 역할 | 주요 경계 |
| --- | --- | --- |
| navigation-common | 공유 경로 HTTP 계약 | Coordinate, Algorithm, RouteRequest/Response/Segment |
| navigation-edge/routes | 공개 경로·snapshot | RouteController → RouteProxyService → EngineClient/RouteSnapshotRepository |
| navigation-edge/trips | 실차 기록 | TripController → TripService → TripRepository(SQLite), 요청 DTO와 TripRecord 분리 |
| navigation-edge/global | 공통 HTTP·DB 기동 | health, 오류, body 제한, 개발 CORS, SqliteMigration |
| navigation-engine/graph | 방향별 도로망 | JSON/PBF loader, RoadGraph, nearest node |
| navigation-engine/traffic | 교통 입력 | JSON 또는 경기 provider, 수동 link ID→edge ID 매핑 |
| navigation-engine/routing | 비용·경로 계산 | RouteService, AStarRouter, ReverseCongestionSearch, EdgeCost |

Controller는 HTTP 연결만, Service는 유스케이스와 상태 전이, Repository/Client는 I/O만 맡는다. `RoadGraph`는 기동 시 메모리에 생성한다. 현재 nearest node는 선형 탐색이다. 외부 link ID는 OSM edge ID와 같다고 가정하지 않는다. TMAP API 컴포넌트는 없다. 사용자 측의 수동 benchmark는 Trip DTO/row에만 저장한다. 공통 모듈에 DB나 domain routing 로직은 없다.
