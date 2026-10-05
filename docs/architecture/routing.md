# 구현된 경로 계산

## 입력 데이터와 그래프

Engine은 시작할 때 `RoadGraphLoader`로 방향별 edge가 있는 `RoadGraph`를 메모리에 만든다. `GRAPH_SOURCE=json`은 `GRAPH_FILE`의 node/edge JSON을 읽으며, 기본 `data/dev-graph.json`은 분기 정체를 시험하는 **가상 도로**다. `GRAPH_SOURCE=osm-pbf`는 `OSM_PBF_FILE`의 지역 추출본 또는 `OSM_BBOX`로 제한한 국가 PBF에서 자동차 도로 `highway`의 인접 OSM node를 edge로 만든다. `oneway=yes/1/-1`, roundabout, 고속도로 기본 일방통행을 반영하고 명시적인 `oneway=no`는 우선한다. `access`/`vehicle`/`motor_vehicle`/`motorcar`가 `no` 또는 `private`인 way와 가변 방향 way는 제외한다. 숫자형 `maxspeed`를 일부 반영하며 반대 방향은 별도 edge ID로 만든다. OSM relation의 via-node `no_*`/`only_*`와 `restriction:motorcar`/`restriction:motor_vehicle`는 경로 탐색에 반영한다. via-way·조건부 회전 제한, 완전한 접근 규칙, 지도 매칭은 구현되지 않았다.

좌표를 경로에 연결할 때 0.01도 크기의 공간 격자에서 1km 안의 최근접 node를 구한다. 출발지나 목적지에 1km 안의 node가 없으면 경로가 없다고 반환한다. 도로 edge의 중간 지점 스냅은 하지 않는다. South Korea PBF의 예시 경계에서 977,628 node·1,933,018 directed edge·6,431 via-node 제한을 10.5초에 로드했고 메모리 로그는 약 1,415MiB였다. 해당 예시 경로의 후속 요청은 1초 미만이었으며 대상 서버·실차 좌표에서 다시 측정해야 한다.

## 교통 입력

`TrafficProvider.current()`가 요청 시점의 snapshot을 제공한다. JSON provider는 설정 파일을 읽는다. 경기 provider는 외부 `linkId`를 방향별 OSM edge ID로 변환한다. `TRAFFIC_MAPPING_FILE`의 수동 매핑(문자열 또는 edge ID 배열)이 우선하며, `TRAFFIC_LINK_GEOMETRY_FILE`에 WGS84 GeoJSON이 있으면 도형의 위치·진행 방향을 이용해 한 교통 link를 여러 OSM edge에 매핑한다. 근접한 평행 도로처럼 결과가 애매하면 매핑하지 않는다. link 도형은 원본 교통 link ID와 같은 `LINK_ID`를 가져야 하며 도형 점 순서는 차량 진행 방향이어야 한다. 매핑되지 않거나 최근 15분 이내의 유효한 양수 속도가 없는 edge는 관측값이 없는 `UNKNOWN`으로 취급하며 `ETA_UNOBSERVED_SPEED_FACTOR`(기본 0.75)를 곱한 계획 속도로 ETA를 계산한다. `observedSpeed` 진단 필드는 이전 계약과 호환되도록 기준 속도를 담지만 관측값이라는 뜻이 아니다. 경기 provider는 백그라운드에서 최소 60초 간격으로 갱신하고 마지막 정상 snapshot을 최대 5분 동안만 사용한다. 빈 snapshot에서는 모든 edge가 계획 속도로 계산된다.

## 공통 비용과 Baseline A*

각 edge의 시간 단위는 초다.

```text
baseSeconds     = distanceMeters * 3.6 / baseSpeedKmh
observedSeconds = distanceMeters * 3.6 / observedSpeedKmh
excessSeconds   = max(0, observedSeconds - baseSeconds)
effectiveSeconds = baseSeconds + attribution * excessSeconds
effectiveSpeedKmh = distanceMeters * 3.6 / effectiveSeconds
```

위 식은 **유효한 관측 속도가 있는 edge**에 적용한다. 관측이 없으면 `effectiveSeconds = baseSeconds / ETA_UNOBSERVED_SPEED_FACTOR`이고 `excessSeconds=0`이다. 이 기본 계수는 TMAP 한 건에 맞춘 확정 보정값이 아니라 실측 Trip으로 교정해야 할 초기 계획값이다. `BASELINE`은 관측된 edge의 attribution을 1로 둔다. 관측 속도가 기본 속도보다 빠르면 `excessSeconds=0`이므로 기본 시간보다 낮아지지 않는다. A*는 `effectiveSeconds`를 비용으로 사용하며 직선거리와 그래프의 최대 속도를 이용한 시간 하한을 heuristic으로 사용한다. OSM 회전 제한이 있는 graph에서는 이전 way와 node를 A* 상태에 포함해 금지된 전환을 건너뛴다. 비용은 음수가 아니다. 경로 ETA는 통과 edge의 유효 시간을 합산해 초로 반올림한다.

## 목적지별 역방향 혼잡 귀속

`DIRECTION_AWARE`에서는 목적지에서 incoming edge를 거슬러 올라가며 목적지 쪽 분기를 추적한다. 각 edge 혼잡도는 `clamp(1 - observedSpeed / baseSpeed, 0, 1)`이다. 선택된 downstream 분기가 **관측된 모든 비교 분기보다 뚜렷하게 원활할 때에만** 공유 상류의 관측 지연 일부를 할인한다. 비교 분기의 관측값이 없거나 모두 비슷하게 막혀 있으면 할인 근거가 없으므로 attribution은 1이다. 상류 edge 자신의 혼잡 연속성과 역방향 누적 거리에 따른 `1 - (distance / MAX_PROPAGATION_DISTANCE_METERS)^2` 감쇠를 적용한다. 기본 최대 전파 거리는 3000m다. 거리가 초과되면 전파를 멈춘다. 여러 역방향 경로가 한 edge에 도달하면 큰 attribution을 유지한다. 결과는 edge ID별 0..1 값이다. 탐색 범위 밖의 관측 edge도 attribution 1로 관측 지연을 온전히 반영한다.

이 값으로 동일한 forward A*를 다시 실행한다. 목적지 방향의 병목이 뚜렷한 공용 진입로는 높은 attribution을, 원활한 다른 분기로 향하는 경로는 낮은 attribution을 받을 수 있다. 이는 연구 가설을 시험하는 간단한 모델이며 실제 정체 원인의 확정 판정은 아니다.

테스트 그래프에는 A/B/C 공용 진입로 뒤에 YANGJAE(5/50 km/h)와 SADANG(48/50 km/h) 분기가 있다. A/B/C 관측 속도는 각각 25/60, 20/60, 18/60 km/h다. 방향별 테스트는 YANGJAE 목적지의 상류 attribution이 SADANG 목적지보다 높음을 확인한다.

## 출력과 추적

새 경로의 각 segment에는 `trafficLevel`(`CONGESTED` 관측/기준 속도 비율 ≤0.4, `SLOW` ≤0.7, `FREE` 그 외, `UNKNOWN` 미관측)과 `geometryStartIndex`/`geometryEndIndex`가 포함된다. 양 끝 인덱스를 포함한 좌표 범위이므로 여러 좌표로 구성된 OSM edge도 한 색으로 표시할 수 있다. 경로에 관측 edge가 하나도 없으면 `trafficSource=UNKNOWN`이며, 있으면 `JSON` 개발 데이터 또는 `GYEONGGI`다. 이전 snapshot은 geometry index가 null일 수 있다.

Engine은 edge geometry를 이어 붙이고 연속 중복 좌표를 제거한다. 응답 `segments`에는 `edgeId`, `baseSpeed`, `observedSpeed`, `attribution`, `effectiveSpeed`를 순서대로 담는다. 실제 비용의 base/observed/excess/effective seconds는 내부 `EdgeCost`에 보존하지만 공개 응답에는 넣지 않는다. Edge는 성공한 응답 전체를 SQLite `route_snapshot`에 저장하므로 `/api/routes/{routeId}`로 나중에 재조회할 수 있다. Engine 로그는 route ID, algorithm, 좌표, 거리, 시간과 `graphLookupMs`, `attributionMs`, `routingTimeMs`, `totalMs`를 남긴다. Traffic 로그에는 provider, snapshot 시각, edge 수를 남긴다.

`ManeuverBuilder`는 연속 edge의 진입·진출 방위각과 도로명 변화에서 `instructions`를 만든다. 각 항목은 지점의 geometry index와 누적 경로 거리를 포함한다. 한 도로의 직선 구간에서는 반복 안내를 생략한다. A*가 적용한 via-node 제한 외의 회전 조건이나 차로 수준 동작은 판별하지 못하므로 실도로 검증 전 신뢰 가능한 운전 지시로 취급하지 않는다.
