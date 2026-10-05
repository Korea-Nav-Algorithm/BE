# Architecture

Mobile Browser → Pi Nginx HTTPS (`/`, `/api/*`) → Edge `:8080` → Engine `:8090` (LAN/VPN)이다. Engine은 별도 Compute 머신에서 실행하며 외부에 8090이나 `/internal/*`를 노출하지 않는다. Navigation 요청은 LLM 또는 TMAP API를 호출하지 않는다. 공식 TMAP 앱의 ETA·거리는 사용자가 Trip에 직접 기록한다.

`navigation-common`은 route HTTP DTO만 공유한다. Edge controller → service → repository/EngineClient, Engine controller → routing service → graph/traffic adapter 방향으로 의존한다. SQLite row 모델과 API DTO는 분리한다. 외부 HTTP는 Edge의 EngineClient·선택적 NaverPlaceClient 및 Engine의 선택적 GyeonggiTrafficProvider만 수행한다. 경기 link 도형의 방향별 OSM 매핑은 Engine 시작 시 `GeometryTrafficEdgeMapper`가 구성하며 수동 매핑이 우선한다. 목적지 검색은 Edge에서 수행하고 좌표만 route 요청에 넘긴다.

자체 route는 Edge가 입력을 검증하고 Engine에 2초 connect/9초 read timeout(설정 가능)으로 전달한다. Engine은 최근접 node, traffic snapshot, 목적지별 attribution, forward A*를 수행해 ETA·geometry·segments·algorithmVersion을 반환한다. Edge는 route_snapshot에 응답·algorithm·version·origin/destination을 저장한다. Engine 장애는 503, 그래프 밖/경로 부재는 422다. [routing](routing.md)과 [API 계약](../api/specification.md)을 참조한다.

Trip은 Edge SQLite에 영속화한다. clientTripId 재전송은 동일 Trip 반환, 충돌 metadata는 400이다. GPS batch는 `(trip_id,point_id)`로 중복을 제거하며 같은 timestamp의 다른 점을 저장한다. 종료 재전송은 동일 값이면 204다. 서버에는 인증·세션·오프라인 브라우저 상태가 없다. DB는 [schema](../database/schema.md), 배포는 [runbook](../operations/runbook.md)에 있다.

## 결정

| 결정 | 근거 |
| --- | --- |
| Java 21/Spring Boot 3/Gradle multi-project | Engine과 Pi Edge 분리, 공통 DTO 최소화 |
| SQLite WAL과 파일 볼륨 | Pi 재시작 후 로그 보존 |
| JSON graph 기본, 지역 PBF 선택 | 외부 데이터 없이 개발하고 실차에 실제 도로망 사용 |
| 방향별 지연 귀속 + 비음수 A* 비용 | 목적지별 정체 연구 가설 실증 |
| 수동 TMAP benchmark | 공식 앱 결과를 저장하며 API 키/실호출 제거 |
