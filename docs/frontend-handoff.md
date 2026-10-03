# Frontend 협업용 Backend 진행 현황

기준: 2026-10-04 현재 소스. **개발용 JSON 데이터로 연동은 가능하지만 실제 도로·Pi/HTTPS·모바일 E2E는 미검증**이다. 기존 Frontend가 `clientTripId` 또는 GPS `pointId`를 보내지 않으면 새 API는 400을 반환하므로 Frontend 요청을 함께 갱신해야 한다. 실제 서버 주소와 주행 좌표는 아직 제공되지 않았다. 상세 필드는 [API 계약](api/specification.md), 현장 절차는 [runbook](operations/runbook.md)을 따른다.

## 구현·검증 상태

| 영역 | 코드 상태 | 남은 현장 확인 |
| --- | --- | --- |
| Edge 공개 API | 자체 route, route snapshot, Trip/GPS 조회·저장 구현 | 모바일에서 Nginx HTTPS 동일 origin 호출 |
| Route | BASELINE/DIRECTION_AWARE, ETA·geometry·segments·algorithmVersion 구현 | 실제 PBF와 두 실좌표 구간에서 경로·5초 지연 확인 |
| Trip 생성 | `clientTripId`별 동일 요청 재전송 처리 구현 | 실제 IndexedDB 재연결 흐름 |
| GPS | `pointId`별 중복 제거·같은 timestamp 다른 ID 저장 구현 | 모바일 오프라인 batch 재전송 |
| 수동 TMAP 비교 | ETA·거리 nullable Trip 필드로 저장 | 사용자가 공식 앱에서 출발 직전 값 입력 |
| DB | SQLite migration·볼륨 구성 | 실제 Pi 재시작 뒤 데이터 확인 |
| 배포 | Dockerfile·Compose·Nginx HTTPS 템플릿 있음 | Pi ARM64, Engine LAN/VPN, 인증서·공개 도메인 미확인 |

**Backend는 TMAP API를 호출하지 않는다.** `/api/routes/tmap`과 `TMAP_UNAVAILABLE`, TMAP AppKey 설정은 제거됐다. 경기 교통은 선택 사항으로 기본 `TRAFFIC_PROVIDER=json`에서 키 없이 자체 경로가 동작한다. Engine `:8090`/`/internal/routes`를 브라우저에서 호출하지 않는다.

## Frontend가 호출하는 순서

운영에서는 상대 경로 `/api/...`를 사용한다. 로컬 개발에서는 Edge `http://localhost:8080`이고 개발 CORS는 `http://localhost:*`, `http://127.0.0.1:*`만 허용한다. 프로덕션은 Nginx가 Frontend와 API를 같은 HTTPS origin으로 제공한다.

1. `POST /api/routes`: `origin`, `destination`, 필수 `algorithm`(`BASELINE` 또는 `DIRECTION_AWARE`). 응답은 `routeId`, `algorithm`, `algorithmVersion`, `geometry`(`{lat,lng}` 배열), `distanceMeters`, `durationSeconds`, `segments`. MapLibre에 넘길 때 geometry는 `[lng,lat]`로 변환한다. 같은 graph node로 스냅되면 거리·시간 0, geometry 한 점, segments 빈 배열도 정상이다.
2. 공식 TMAP 앱에서 동일 목적지 ETA·거리를 확인해 Frontend가 선택 입력한다. 이 값은 API가 얻은 실시간 값이 아니라 **수동 benchmark snapshot**이다.
3. `POST /api/trips`: Frontend가 IndexedDB에 저장한 UUID `clientTripId`, OUR `routeId`, 시작 시각/좌표, OUR ETA, 선택적 `tmapEtaSeconds`, `tmapDistanceMeters`를 보낸다. 최초 201, 같은 내용 재전송 200이며 둘 다 동일 `tripId`·`clientTripId`를 반환한다. 같은 ID의 다른 metadata는 400이다.
4. `POST /api/trips/{tripId}/points`: 각 점의 UUID `pointId`를 오프라인 재전송에도 유지한다. 응답 `pointsReceived`는 보낸 점 수, `pointsStored`는 신규 삽입 수다. `(tripId,pointId)`가 중복 기준이며 timestamp는 중복 기준이 아니다. 1..1000점, 본문 256 KiB 이하로 보낸다.
5. `POST /api/trips/{tripId}/finish`: `finishedAt`, `actualDurationSeconds`. 성공 204, 같은 본문 재전송도 204, 다른 종료값은 400이다.
6. `GET /api/trips/{tripId}`: 종료 뒤 OUR ETA·수동 TMAP ETA/거리·실제 시간·`pointCount`·GPS 점을 확인한다. `GET /api/routes/{routeId}`는 당시 route diagnostics를 보존한다. `GET /api/trips`는 최근 50건 요약이며 points 배열은 비어 있다.

```json
{
  "clientTripId": "7ec178cb-2f65-4f5b-8eea-8940c6dc6d64",
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
| 413 | JSON 보장 없음 | GPS batch를 더 작게 나눔 |

API 오류 본문은 대체로 `{"error":"CODE"}`이며 413 본문은 보장되지 않는다. 로그인·JWT·Engine 직접 호출은 없다. 별도 reroute API 없이 현재 GPS를 `origin`으로 `/api/routes`를 다시 호출한다. API 키는 Frontend에 넣지 않는다. 경기 교통을 사용할 경우에만 Engine의 `GG_TRAFFIC_API_KEY`가 필요하다.

## 검증 기록과 남은 일

- `./gradlew.bat --no-daemon test bootJar`가 통과했다. Edge API 7건, 기존 DB migration 1건, Engine graph 방향·접근 제한 2건, routing 6건으로 총 16건 실패 0건이다. Gradle toolchain은 Java 21이며 로컬 smoke 실행 JVM은 Java 22였다.
- 두 JAR을 직접 띄운 JSON 가상 그래프 HTTP smoke에서 BASELINE/DIRECTION_AWARE 모두 200, 경로 4 segments, route snapshot 재조회 성공을 확인했다. Trip 생성 201→동일 재전송 200, GPS 신규/중복/같은 timestamp 다른 ID 저장 1/0/1, finish 204/204, Edge 재시작 후 pointCount 2·수동 benchmark·route version 보존을 확인했다. 가상 그래프 6 node·5 directed edge에서 각 경로 응답은 563ms(첫 호출)/27ms(후속 호출)로 측정됐다. 이 수치는 실제 PBF 성능을 뜻하지 않는다.
- [실도로용 경로 smoke 스크립트](../scripts/smoke-routes.ps1)를 개발 가상 그래프의 두 구간×두 algorithm으로 실행해 4경로 모두 통과했다. 실제 좌표와 PBF를 사용한 결과는 아직 없다.
- `docker compose config --quiet`는 통과했지만 Docker daemon이 없어 이미지 build/컨테이너 기동은 미검증이다.
- 실제 지역 PBF, 출발/도착 좌표, Pi/Engine 접속 정보, 도메인·기존 인증서가 없어 실도로 route, ARM64, HTTPS, 모바일 E2E는 미검증이다. 실제 운전에는 가상 `data/dev-graph.json`을 사용하면 안 된다.
