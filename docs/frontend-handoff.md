# Frontend 협업용 Backend 진행 현황

기준: 2026-10-05 현재 소스와 로컬 검증. **실제 OSM 도로망을 로드해 두 구간의 경로를 계산했지만 Pi/HTTPS·모바일 화면·현장 주행은 미검증**이다. 인접 Frontend는 목적지 검색, `clientTripId`, GPS `pointId`, Trip `accessKey` 및 화면 안내 `instructions` 계약에 맞춰 갱신됐다. 실제 서버 주소와 출발/후속 목적지 좌표는 제공되지 않았다. 상세 필드는 [API 계약](api/specification.md), 현장 절차는 [runbook](operations/runbook.md)을 따른다.

## 구현·검증 상태

| 영역 | 코드 상태 | 남은 현장 확인 |
| --- | --- | --- |
| Edge 공개 API | 자체 route, route snapshot, Trip/GPS 조회·저장 구현 | 모바일에서 Nginx HTTPS 동일 origin 호출 |
| Route | BASELINE/DIRECTION_AWARE, ETA·geometry·segments·algorithmVersion·화면 안내 구현. 로컬 South Korea PBF 두 구간 200 확인 | 실제 주행 지점·지도 시각·일방통행과 5초 지연 재확인 |
| Navigation UX | 인접 FE에서 건물·지명 검색, 지도 추적, 좌표 직접 입력, 경로 선분 진행률·구간별 남은 ETA, 예상 회전 표시, 이탈 재탐색·재접속 복원 구현 | 브라우저·모바일 시각 검증 없음. 실제 일반 장소 검색은 Naver 서버 인증 정보 필요; 차로·음성 안내 없음 |
| Trip 생성 | `clientTripId`/`accessKey`별 동일 요청 재전송 처리 구현 | 실제 브라우저 IndexedDB 재연결 흐름 |
| GPS | `pointId`별 중복 제거·같은 timestamp 다른 ID 저장 구현 | 모바일 오프라인 batch 재전송 |
| 재탐색 이력 | Trip에 route ID·시각을 멱등 저장하고 상세 조회 `reroutes`로 반환 | 모바일 경로 이탈→재탐색→오프라인 재전송 |
| 수동 TMAP 비교 | ETA·거리 nullable Trip 필드로 저장 | 사용자가 공식 앱에서 출발 직전 값 입력 |
| DB | SQLite migration·볼륨 구성·Trip key 해시 저장. 로컬 JAR 재시작 후 Trip/route 조회 확인 | 실제 Pi 재시작 뒤 데이터 확인 |
| 배포 | Dockerfile·Compose·Nginx HTTPS 템플릿 있음. AMD64·ARM64 두 서비스 이미지 build 성공, ARM64 Edge 에뮬레이션 health·route·SQLite GPS 확인 | Pi 실기기, Engine LAN/VPN, 인증서·공개 도메인 미확인 |

**Backend는 TMAP API를 호출하지 않는다.** `/api/routes/tmap`과 `TMAP_UNAVAILABLE`, TMAP AppKey 설정은 제거됐다. 기본 `TRAFFIC_PROVIDER=json`은 개발 모드이며 실제 OSM 도로에 실시간 교통을 제공하지 않는다. 경기 교통을 적용하려면 Engine의 키와 방향별 link 도형 매핑이 필요하다. `trafficSource=UNKNOWN`인 경로는 실교통 기반 최단 시간 경로라고 표시하지 않는다. Engine `:8090`/`/internal/routes`를 브라우저에서 호출하지 않는다.

**일반 사용자에게 공개할 완성 서비스 상태는 아니다.** 실도로 예시에서는 관측 교통 edge가 없어 BASELINE과 DIRECTION_AWARE가 같은 ETA를 냈다. 미관측 구간은 현재 기본 속도에 `ETA_UNOBSERVED_SPEED_FACTOR`(기본 0.75)를 곱한 계획 속도로 계산한다. 이는 TMAP과의 한 차례 비교를 맞추는 확정 모델이 아니라 실차 결과로 교정할 초기값이다. FE는 관측 구간의 정체/서행/원활을 빨강/주황/초록으로 표시하고 미관측 구간은 회색 ‘정보 없음’으로 표시한다. 현재 JSON provider는 실시간 소통 데이터가 아니다. 알고리즘의 실효성을 검증하려면 방향별 경기 링크 매핑과 실측 속도, 모바일 UX 테스트가 필요하다. 현재 화면 회전 문구는 예상 지점이며 실제 차로와 조건부 진입을 보증하지 않는다.

2026-10-05 로컬 Engine에서 경기 API를 서버 전용 키로 직접 확인한 결과 HTTP 200의 `headerCd=7`(등록됐지만 현재 사용할 수 없는 키)을 받았다. 별도로 `TRAFFIC_LINK_GEOMETRY_FILE`은 비어 있고 수동 매핑 파일도 `{}`다. 따라서 로컬 경로의 모든 segment가 `UNKNOWN`인 것은 실제 입력 데이터 부재이며, Frontend가 회색으로 표시하는 것이 맞다. 키 활성화와 지역 링크 도형 매핑을 모두 마친 뒤 `trafficReady=true`, 경로의 `trafficSource=GYEONGGI`, 실제 관측 segment를 다시 확인해야 한다.
`GET /api/places?query=...`는 Edge의 JSON 개발 목록 또는 Naver 지역 검색 결과를 `{id,name,address,coordinate}`로 반환한다. Frontend는 결과를 선택해 기존 `/api/routes`에 좌표를 보낸다. 기본 JSON 목록은 분당중앙교회 1건이며 범용 검색이 아니다. 실제 검색에는 Edge에만 `NAVER_SEARCH_CLIENT_ID`, `NAVER_SEARCH_CLIENT_SECRET`, `PLACE_SEARCH_PROVIDER=naver`를 설정한다. 2..80자 query, 최대 5건이며 Naver 결과를 OSM graph 경계로 필터하지 않는다. 인증 정보·실호출 검증은 아직 없다. 공급자 실패는 `503 PLACE_SEARCH_UNAVAILABLE`; 좌표 입력과 지도 선택은 계속 사용할 수 있다. [Naver 지역 검색 API](https://developers.naver.com/docs/serviceapi/search/local/local.md)의 이용 조건과 계정 쿼터는 공개 전 확인한다.

## Frontend가 호출하는 순서

운영에서는 상대 경로 `/api/...`를 사용한다. 로컬 개발에서는 Edge `http://localhost:8080`이고 개발 CORS는 `http://localhost:*`, `http://127.0.0.1:*`만 허용한다. 프로덕션은 Nginx가 Frontend와 API를 같은 HTTPS origin으로 제공한다.

1. `GET /api/places?query=...`로 목적지 후보를 찾고 선택된 `coordinate`를 사용한다. 개발 기본값은 JSON 장소 1건이며 일반 검색은 Edge에 Naver 인증 정보 설정 후 사용한다.
2. `POST /api/routes`: `origin`, `destination`, 필수 `algorithm`(`BASELINE` 또는 `DIRECTION_AWARE`). 응답은 `routeId`, `algorithm`, `algorithmVersion`, `geometry`(`{lat,lng}` 배열), `distanceMeters`, `durationSeconds`, `segments`, `instructions`. MapLibre에 넘길 때 geometry는 `[lng,lat]`로 변환한다. 같은 graph node로 스냅되면 거리·시간 0, geometry 한 점, segments 빈 배열도 정상이다. `instructions`는 via-node 제한 일부를 반영하지만 via-way·차로·조건부 제한이 빠진 시각 힌트다.
3. 공식 TMAP 앱에서 동일 목적지 ETA·거리를 확인해 Frontend가 선택 입력한다. 이 값은 API가 얻은 실시간 값이 아니라 **수동 benchmark snapshot**이다.
4. `POST /api/trips`: Frontend가 IndexedDB에 먼저 저장한 UUID `clientTripId`, 32바이트 난수의 Base64url `accessKey`, OUR `routeId`, 시작 시각/좌표, OUR ETA, 선택적 `tmapEtaSeconds`, `tmapDistanceMeters`를 보낸다. 최초 201, 같은 내용과 key 재전송 200이며 둘 다 동일 `tripId`·`clientTripId`를 반환한다. 같은 ID의 다른 metadata는 400, 다른 key는 404다.
5. `POST /api/trips/{tripId}/points`: `X-Trip-Key: <accessKey>`를 보내고, 각 점의 UUID `pointId`를 오프라인 재전송에도 유지한다. 응답 `pointsReceived`는 보낸 점 수, `pointsStored`는 신규 삽입 수다. `(tripId,pointId)`가 중복 기준이며 timestamp는 중복 기준이 아니다. 1..1000점, 본문 256 KiB 이하로 보낸다.
   주행 중 새 경로를 받으면 새 `routeId`와 `occurredAt`을 IndexedDB에 저장하고 같은 `X-Trip-Key`로 `POST /api/trips/{tripId}/routes`에 보낸다. 같은 ID 재전송은 204이며 최초 시각을 유지한다.
6. `POST /api/trips/{tripId}/finish`: 같은 `X-Trip-Key`와 `finishedAt`, `actualDurationSeconds`를 보낸다. 성공 204, 같은 본문 재전송도 204, 다른 종료값은 400이다.
7. `GET /api/trips/{tripId}`: 같은 `X-Trip-Key`로 종료 뒤 OUR ETA·수동 TMAP ETA/거리·실제 시간·`pointCount`·GPS 점·`reroutes`를 확인한다. `GET /api/routes/{routeId}`는 당시 route diagnostics를 보존한다. `GET /api/trips`는 서버에 `TRIP_ADMIN_KEY`를 설정한 운영자가 `X-Trip-Admin-Key`로 호출할 때만 최근 50건 요약을 반환한다. 비설정/불일치는 403이다.

```json
{
  "clientTripId": "7ec178cb-2f65-4f5b-8eea-8940c6dc6d64",
  "accessKey": "<기기에서 생성해 IndexedDB에 보존한 43자 Base64url 비밀키>",
  "routeId": "our-route-id",
  "startedAt": 1760000000000,
  "origin": {"lat": 37.263, "lng": 127.028},
  "destination": {"lat": 37.271, "lng": 127.026},
  "ourEtaSeconds": 1724,
  "tmapEtaSeconds": 1860,
  "tmapDistanceMeters": 18400
}
```

```json
{
  "points": [{
    "pointId": "fc821386-b844-4d90-859d-54f59b490179",
    "timestamp": 1760000001000,
    "lat": 37.263,
    "lng": 127.028,
    "gpsSpeed": 14.8,
    "heading": 132.4,
    "accuracy": 6.2
  }]
}
```

시각(`startedAt`, `finishedAt`, GPS `timestamp`)은 Unix epoch **milliseconds**, ETA·실제 소요시간은 **seconds**, `gpsSpeed`는 **m/s**, `accuracy`는 **m**, `heading`은 **degrees [0,360]**으로 보낸다. 서버는 GPS speed를 변환하지 않는다. `gpsSpeed`, `heading`, `accuracy`와 수동 TMAP benchmark는 null 가능하다. Frontend는 오프라인 동안 같은 UUID와 데이터를 IndexedDB에 보존해야 한다. Backend는 브라우저의 오프라인 버퍼를 제공하지 않는다. 기존 DB에서 migration된 예전 Trip/GPS는 새 ID가 null로 보일 수 있다.

## 오류 처리

| HTTP | `error` | Frontend 동작 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필드·좌표·UUID·중복 ID 충돌 확인 |
| 404 | `ROUTE_NOT_FOUND`/`TRIP_NOT_FOUND` | 저장된 ID 확인 |
| 422 | `ROUTE_NOT_FOUND` | 그래프 밖/경로 단절 안내 |
| 503 | `ENGINE_UNAVAILABLE` | 자체 route 잠시 후 재시도 |
| 503 | `PLACE_SEARCH_UNAVAILABLE` | 검색 공급자 장애 안내, 지도 터치·좌표 입력 허용 |
| 413 | JSON 보장 없음 | GPS batch를 더 작게 나눔 |

API 오류 본문은 대체로 `{"error":"CODE"}`이며 413 본문은 보장되지 않는다. 로그인·JWT·Engine 직접 호출은 없다. 별도 reroute API 없이 현재 GPS를 `origin`으로 `/api/routes`를 다시 호출한다. `accessKey`는 각 Trip의 기기 비밀키이며 서버 운영키 `TRIP_ADMIN_KEY`나 경기 교통 키를 Frontend에 넣지 않는다.

## 검증 기록과 남은 일

- Java 21 Docker Gradle `gradle test --no-daemon`에서 Edge·Engine 테스트가 통과했다. Trip key 권한, 재탐색 ID 재전송, 장소 검색, 공간 탐색, PBF 범위, 화면 안내와 via-node 회전 제한 테스트를 포함한다. 이는 Pi·모바일 검증은 아니다.
- 두 JAR을 직접 띄운 JSON 가상 그래프 HTTP smoke에서 BASELINE/DIRECTION_AWARE 모두 200, 경로 4 segments, route snapshot 재조회 성공을 확인했다. Trip 생성 201→동일 재전송 200, GPS 신규/중복/같은 timestamp 다른 ID 저장 1/0/1, finish 204/204, Edge 재시작 후 pointCount 2·수동 benchmark·route version 보존을 확인했다. 가상 그래프 6 node·5 directed edge에서 각 경로 응답은 563ms(첫 호출)/27ms(후속 호출)로 측정됐다. 이 수치는 실제 PBF 성능을 뜻하지 않는다.
- [실도로용 경로 smoke 스크립트](../scripts/smoke-routes.ps1)를 실제 PBF 로컬 Edge에 실행해 예시 좌표 두 구간×두 algorithm의 4경로가 통과했다. 현장 출발지·후속 목적지 좌표로는 아직 실행하지 못했다.
- 개발·분리 배포 Compose `config --quiet`, AMD64 두 서비스 이미지 build, ARM64 두 서비스 이미지 build가 통과했다. ARM64 Edge를 로컬 에뮬레이션으로 실행해 health, Edge→실제 PBF Engine route, SQLite GPS 1점 저장·조회까지 확인했다. 첫 cold 경로 요청 5,237ms, 다음 요청 1,096ms였다. 에뮬레이션 시간은 Pi 실기기 성능이 아니다.
- 2026-10-05 로컬 Docker Compose에서도 두 서비스를 healthy로 기동했다. 실제 PBF를 로드한 Engine과 Edge를 호스트 loopback `28090`/`28080`에 바인딩해 예시 실도로 `BASELINE`/`DIRECTION_AWARE` 요청 및 route snapshot 조회가 통과했다. 세부 수치와 포트 선택은 [runbook](operations/runbook.md)에 있다. Pi/Nginx/HTTPS 배포 결과는 아니다.
- 기존 Edge 이미지에서 `/api/places?query=분당중앙교회`는 200·JSON 목록 1건, 누락 query는 400이었다. Vite `localhost:5174/api/places` 개발 프록시도 200이었다. 검색 결과 좌표의 Docker 경로는 18,928m/1,631초였고 첫 요청 5,672ms, 후속 BASELINE 1,232ms·DIRECTION_AWARE 1,050ms였다. 첫 요청은 목표 5초를 넘으므로 현장 기기에서 재측정해야 한다. Naver 실제 인증 정보·호출 및 브라우저 화면은 검증하지 못했다.
- [Geofabrik South Korea](https://download.geofabrik.de/asia/south-korea.html) PBF(288,404,574바이트, MD5 일치)를 로컬에 내려받아 `OSM_BBOX=126.7,37.1,127.5,37.6`으로 제한해 977,628 nodes/1,933,018 directed edges를 로드했다. 예시 좌표 수원(37.263,127.028)→분당중앙교회 preset(37.37709,127.13973)에서 18,928m/1,224초였던 기존 ETA는 미관측 속도 계수 0.75 적용 후 1,631초로 바뀌었다. 두 알고리즘 모두 해당 예시에서 200이며 382개 구간은 모두 `UNKNOWN`이다. 실제 교통 관측값이 없어 두 algorithm ETA는 같다. 이 좌표는 사용자 주행 경로가 아니다.
- 실제 PBF Edge→Engine→Trip 로컬 HTTP에서는 경로 `instructions` 29개, Trip 201→200 동일 ID, GPS 신규 1/중복 0, finish 204/204, key 없는 조회 404, JAR 재시작 후 Trip/route snapshot 조회를 확인했다. 추가로 재탐색 경로 17,160m를 생성해 Trip `/routes` 204→204, 상세 조회 `reroutes` 1건을 확인했다. 사용자 GPS 기기/지도 화면 테스트는 아니다.
- Pi/Engine 접속 정보, 도메인·기존 인증서가 없어 Pi 실기기, HTTPS, 모바일 E2E는 미검증이다. 실제 운전에는 가상 `data/dev-graph.json`을 사용하면 안 된다.
