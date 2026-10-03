# 구현된 경로 계산

## 입력 데이터와 그래프

Engine은 시작할 때 `RoadGraphLoader`로 방향별 edge가 있는 `RoadGraph`를 메모리에 만든다. `GRAPH_SOURCE=json`은 `GRAPH_FILE`의 node/edge JSON을 읽으며, 기본 `data/dev-graph.json`은 분기 정체를 시험하는 **가상 도로**다. `GRAPH_SOURCE=osm-pbf`는 `OSM_PBF_FILE`의 작은 지역 추출본에서 자동차 도로 `highway`의 인접 OSM node를 edge로 만든다. `oneway=yes/1/-1`, roundabout, 고속도로 기본 일방통행을 반영하고 명시적인 `oneway=no`는 우선한다. `access`/`vehicle`/`motor_vehicle`/`motorcar`가 `no` 또는 `private`인 way와 가변 방향 way는 제외한다. 숫자형 `maxspeed`를 일부 반영하며 반대 방향은 별도 edge ID로 만든다. 전국 PBF, 회전 제한, 완전한 접근 규칙, 지도 매칭은 구현되지 않았다.

좌표를 경로에 연결할 때 모든 node를 선형 탐색해 최근접 node를 구한다. 출발지나 목적지가 최근접 node에서 1km를 넘으면 경로가 없다고 반환한다. 도로 edge의 중간 지점 스냅은 하지 않는다. 큰 그래프에서는 이 선형 탐색과 전체 메모리 로드가 병목이 될 수 있어 실차 지역 추출본으로 확인해야 한다.

## 교통 입력

`TrafficProvider.current()`가 요청 시점의 snapshot을 제공한다. JSON provider는 설정 파일을 읽는다. 경기 provider는 외부 `linkId`를 `TRAFFIC_MAPPING_FILE`의 수동 방향별 edge ID로 변환한다. geometry 기반 자동 근접 매핑은 아직 없다. 매핑되지 않거나 유효한 양수 속도가 없는 edge는 `baseSpeedKmh`를 사용한다. 경기 provider는 성공 snapshot을 60초 캐시하고 실패 시 마지막 정상 snapshot 또는 빈 snapshot으로 돌아간다. 빈 snapshot에서는 모든 edge가 기본 속도로 계산된다.

## 공통 비용과 Baseline A*

각 edge의 시간 단위는 초다.

```text
baseSeconds     = distanceMeters * 3.6 / baseSpeedKmh
observedSeconds = distanceMeters * 3.6 / observedSpeedKmh
excessSeconds   = max(0, observedSeconds - baseSeconds)
effectiveSeconds = baseSeconds + attribution * excessSeconds
effectiveSpeedKmh = distanceMeters * 3.6 / effectiveSeconds
```

`BASELINE`은 모든 edge의 attribution을 1로 둔다. 따라서 관측된 **추가 지연**을 모두 반영한다. 관측 속도가 기본 속도보다 빠르면 `excessSeconds=0`이므로 기본 시간보다 낮아지지 않는다. A*는 `effectiveSeconds`를 비용으로 사용하며 직선거리와 그래프의 최대 속도를 이용한 시간 하한을 heuristic으로 사용한다. 비용은 음수가 아니다. 경로 ETA는 통과 edge의 유효 시간을 합산해 초로 반올림한다.

## 목적지별 역방향 혼잡 귀속

`DIRECTION_AWARE`에서는 목적지에서 incoming edge를 거슬러 올라가며 목적지 쪽 분기를 추적한다. 각 edge 혼잡도는 `clamp(1 - observedSpeed / baseSpeed, 0, 1)`이다. 선택된 downstream 분기가 다른 outgoing 분기보다 더 막힌 정도를 branch signal로 사용한다. 상류 edge 자신의 혼잡도와 연속성을 곱하고, 역방향 누적 거리의 `max(0, 1 - distance / MAX_PROPAGATION_DISTANCE_METERS)` 감쇠를 적용한다. 기본 최대 전파 거리는 3000m다. 신호가 거의 없거나 거리가 초과되면 전파를 멈춘다. 여러 역방향 경로가 한 edge에 도달하면 큰 attribution을 유지한다. 결과는 edge ID별 0..1 값이다.

이 값으로 동일한 forward A*를 다시 실행한다. 목적지 방향의 병목이 뚜렷한 공용 진입로는 높은 attribution을, 원활한 다른 분기로 향하는 경로는 낮은 attribution을 받을 수 있다. 이는 연구 가설을 시험하는 간단한 모델이며 실제 정체 원인의 확정 판정은 아니다.

테스트 그래프에는 A/B/C 공용 진입로 뒤에 YANGJAE(5/50 km/h)와 SADANG(48/50 km/h) 분기가 있다. A/B/C 관측 속도는 각각 25/60, 20/60, 18/60 km/h다. 방향별 테스트는 YANGJAE 목적지의 상류 attribution이 SADANG 목적지보다 높음을 확인한다.

## 출력과 추적

Engine은 edge geometry를 이어 붙이고 연속 중복 좌표를 제거한다. 응답 `segments`에는 `edgeId`, `baseSpeed`, `observedSpeed`, `attribution`, `effectiveSpeed`를 순서대로 담는다. 실제 비용의 base/observed/excess/effective seconds는 내부 `EdgeCost`에 보존하지만 공개 응답에는 넣지 않는다. Edge는 성공한 응답 전체를 SQLite `route_snapshot`에 저장하므로 `/api/routes/{routeId}`로 나중에 재조회할 수 있다. Engine 로그는 route ID, algorithm, 좌표, 거리, 시간과 `graphLookupMs`, `attributionMs`, `routingTimeMs`, `totalMs`를 남긴다. Traffic 로그에는 provider, snapshot 시각, edge 수를 남긴다.
