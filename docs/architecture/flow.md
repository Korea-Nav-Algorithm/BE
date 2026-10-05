# 주요 흐름

## Route

1. Frontend가 Edge `POST /api/routes`에 좌표·`BASELINE` 또는 `DIRECTION_AWARE`를 보낸다. 좌표·enum 오류는 400이다.
2. Engine은 origin/destination을 가장 가까운 graph node에 스냅하고 거리·node ID를 로그에 남긴다. 각 스냅이 1km를 넘거나 경로가 없으면 422다.
3. Engine은 traffic snapshot을 읽고 필요하면 destination 역방향 attribution을 계산한 뒤 A*로 경로를 찾는다. snapshot 실패 시 기본 속도를 사용할 수 있다.
4. Engine은 route ID·algorithm/version·geometry·ETA·segments를 반환하고 시간 로그를 남긴다. 같은 node 스냅은 거리/시간 0, 한 점 geometry, 빈 segments다.
5. Edge는 성공 응답을 SQLite에 저장한다. `GET /api/routes/{routeId}`로 재조회한다. Engine 장애·timeout은 503이며 Frontend가 다시 route를 요청할 수 있다. 별도 세션 상태는 없다.

## Trip과 수동 benchmark

1. 사용자가 공식 TMAP 앱에서 같은 목적지 ETA·거리 값을 읽는다. TMAP API 호출은 없다.
2. Frontend가 IndexedDB의 안정적인 `clientTripId`와 선택적 수동 benchmark로 `POST /api/trips`를 호출한다. 최초 201, 동일 metadata 재전송 200, 같은 ID의 다른 metadata 400이다.
3. GPS point마다 안정적인 `pointId`를 붙여 batch(1..1000, Edge body 256 KiB 이하)를 보낸다. 같은 `(tripId,pointId)`는 기존 row를 유지한다. 다른 ID의 같은 timestamp는 별도 저장한다. 빈/잘못된 batch는 400, body 초과는 413, 없는 Trip은 404다. 종료 후 늦은 GPS도 허용한다.
4. 종료 API는 처음과 동일 값 재전송에서 204, 충돌 시 400, 없는 Trip은 404다. 상세 GET은 GPS 전체와 수동 benchmark를, 최근 GET은 50건 요약과 빈 points를 반환한다. 빈 목록은 `[]`다.
5. Frontend는 오프라인 때 IndexedDB에 동일 UUID를 유지하고 재연결 시 같은 요청을 재전송한다. Backend에는 사용자 인증/권한 단계가 없다.

## 선택적 경기 교통

`TRAFFIC_PROVIDER=json`은 개발용 파일을 읽는다. `gyeonggi`는 지역 표준 link 도형과 진행 방향으로 `linkId`를 OSM edge에 매핑하고, 수동 매핑을 우선 적용한다. Engine 준비 후 별도 스레드가 첫 snapshot을 요청하며 이후 최소 60초 간격으로 갱신한다. 유효한 관측 속도가 있는 edge는 그 속도로 비용을 계산한다. 갱신 실패 시 마지막 정상 snapshot을 최대 5분 사용하고, 그 뒤에는 관측 없음으로 처리한다. 개별 경로에서 관측 edge가 없으면 `trafficSource=UNKNOWN`이다. 키는 로그·응답에 넣지 않는다.
