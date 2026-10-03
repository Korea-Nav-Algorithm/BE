# Navigation 실차 준비 runbook

## 확인된 상태와 배포 경계

저장소에는 두 Spring Boot 서비스, Dockerfile, 개발용 Compose, SQLite 영속화, Nginx 설정 템플릿이 있다. Java 21 Gradle build·16개 테스트와 가상 그래프 Edge→Engine→Trip HTTP smoke는 통과했다. 실제 OSM PBF·주행 좌표·Pi/Engine SSH·도메인/TLS 정보가 아직 제공되지 않아 현장 배포와 모바일 HTTPS E2E는 **미검증**이다. `data/dev-graph.json`은 가상 도로이며 실차 길안내에 사용하지 않는다. 공식 TMAP 앱 결과는 수동 benchmark이며 Backend의 TMAP API 호출은 없다.

## 1. OSM PBF 배치

[Geofabrik South Korea](https://download.geofabrik.de/asia/south-korea.html)의 원본 PBF를 확보한다. 전체 국가 PBF를 바로 Engine 메모리에 싣기 전에 실제 출발지→분당중앙교회→다음 목적지가 모두 포함되는 경계를 정한다. `osmium`이 있는 준비 머신에서 [공식 extract 명령](https://docs.osmcode.org/osmium/latest/osmium-extract.html)의 `complete_ways` 방식으로 지역 추출본을 만든다. `<...>`는 실제 좌표로 대체한다.

```bash
mkdir -p data/osm
osmium extract --bbox <min-lng>,<min-lat>,<max-lng>,<max-lat> \
  --strategy complete_ways --output data/osm/gyeonggi-latest.osm.pbf \
  south-korea-latest.osm.pbf
```

잘린 도로 way의 참조 node가 포함되도록 `complete_ways`를 사용한다. PBF는 Git에 넣지 않는다. Engine이 읽는 host 경로를 `data/osm/gyeonggi-latest.osm.pbf`로 맞추고 `.env`에 아래를 넣는다. 실제 경계·파일·좌표가 없어 이 단계는 아직 실행하지 않았다.

```env
GRAPH_SOURCE=osm-pbf
OSM_PBF_FILE=/data/osm/gyeonggi-latest.osm.pbf
TRAFFIC_PROVIDER=json
```

Engine 시작 로그 `event=graph_loaded`에서 파일명, node/방향별 edge 수, build 시간, 메모리 사용량을 확인한다. 0 node/edge면 시작이 실패한다. `/health`의 `graphLoaded`, `nodeCount`, `edgeCount`, `trafficProvider`, `algorithmVersion`을 확인한다. PBF 로더는 주요 자동차 도로, 고속도로 기본 일방통행·명시적 일방통행, 일부 자동차 접근 금지·maxspeed를 반영한다. 회전 제한·모든 접근 규칙·edge 중간 스냅은 지원하지 않는다.

## 2. 로컬 build·실행

Java 21 환경에서 저장소 루트의 `./gradlew test`(Windows `.\gradlew.bat test`)를 실행한다. 두 터미널에서 `./gradlew :navigation-engine:bootRun`, `./gradlew :navigation-edge:bootRun`을 실행한다. 직접 실행 기본 데이터는 `data/dev-graph.json`이므로 실차 검증에는 위 설정을 프로세스 환경변수로 지정한다.

```bash
curl http://127.0.0.1:8090/health
curl http://127.0.0.1:8080/health
```

Compose는 `.env.example`을 `.env`로 복사해 값을 채운 뒤 사용한다.

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

Compose는 host 8080/8090을 loopback에 바인딩한다. `data/`는 Engine에 read-only, Edge에 read-write로 마운트한다. SQLite 파일과 WAL은 `TRIP_DB_PATH=/data/navigation.db`에 남는다. `docker compose down`은 DB를 삭제하지 않는다. 별도 두 머신 배포에서는 Engine 컨테이너를 Compute 서버에, Edge 컨테이너를 Pi에 두고 `NAV_ENGINE_BASE_URL`을 Compute LAN/VPN 주소로 지정한다. Engine 8090은 공인망과 Nginx `/api/`에 노출하지 않는다.

분리 배포 파일은 [Engine Compose](../../deploy/compose.engine.yml)와 [Edge Compose](../../deploy/compose.edge.yml)다. **각 머신에 저장소와 `.env`가 준비된 뒤** 저장소 루트에서 다음 명령으로 구성 검증·기동한다. 먼저 Compute 서버에서 `ENGINE_BIND_ADDRESS`를 서버의 LAN/VPN 인터페이스 IP로 바꾸고, PBF가 `/data/osm/...`에 mount되는지 확인한다. Pi에서는 `NAV_ENGINE_BASE_URL`을 그 내부 IP의 `http://<IP>:8090`으로 바꾼다. 예시 `.env.example`의 DNS 값과 loopback bind 값은 분리 배포에서 그대로 사용하면 안 된다.

```bash
docker compose --env-file .env -f deploy/compose.engine.yml config --quiet
docker compose --env-file .env -f deploy/compose.engine.yml up --build -d
# 다른 머신인 Pi에서:
docker compose --env-file .env -f deploy/compose.edge.yml config --quiet
docker compose --env-file .env -f deploy/compose.edge.yml up --build -d
```

## 3. 실제 도로 route 확인

기본 가상 좌표를 재사용하지 않고 실제 출발지·분당중앙교회·다음 목적지의 WGS84 좌표를 준비한다. 두 구간 각각 `/api/routes`에 `BASELINE`, `DIRECTION_AWARE`를 보낸다. 200, `routeId`, 2개 이상 geometry, 양수 거리·시간, 비어 있지 않은 segments를 확인하고 지도에서 도로와 일방통행을 눈으로 검사한다. Engine `event=route_snap`에 node ID·snap 거리가, `event=route`에 algorithm/version·거리·시간·segment 수·각 단계 시간이 남는다. 최근접 node 1km 초과 또는 도달 불가면 422다. 정상 route 계산 목표는 5초 이하다. 파일과 실좌표가 없어 이 검증은 아직 수행하지 못했다.

실좌표를 받으면 저장소의 [경로 smoke 스크립트](../../scripts/smoke-routes.ps1)에 세 지점을 인자로 전달한다. 아래 `$originLat` 등 여섯 변수에는 확인한 실좌표를 먼저 넣는다. 스크립트는 두 구간×두 algorithm의 거리·ETA·좌표/segment 수·elapsedMs를 출력하고 빈 경로 또는 5초 초과 응답에서 실패한다. 좌표를 스크립트 내부에 하드코딩하지 않는다.

```powershell
.\scripts\smoke-routes.ps1 -BaseUrl http://127.0.0.1:8080 `
  -OriginLat $originLat -OriginLng $originLng `
  -WaypointLat $waypointLat -WaypointLng $waypointLng `
  -DestinationLat $destinationLat -DestinationLng $destinationLng
```

`GET /api/routes/{routeId}`로 SQLite에 저장된 `algorithm`, `algorithmVersion`, geometry, segment attribution을 재조회한다. JSON traffic에서 관측값이 없는 edge는 기본 속도를 쓴다. 경기 교통은 선택 사항이다. 사용할 경우 `TRAFFIC_PROVIDER=gyeonggi`, `GG_TRAFFIC_API_KEY`, `GG_TRAFFIC_URL`, 수동 방향별 매핑 파일을 지정한다. 매핑과 실제 API는 아직 검증하지 않았다. 키를 로그나 Frontend에 넣지 않는다.

## 4. Trip·GPS·수동 TMAP benchmark

1. OUR route를 계산하고 `routeId`·ETA·거리를 기록한다.
2. 공식 TMAP 앱에서 같은 목적지를 설정해 **출발 직전** ETA(초)와 거리(m)를 확인한다.
3. Frontend가 만든 안정적인 UUID `clientTripId`를 IndexedDB에 보존한다. `POST /api/trips`에 수동 `tmapEtaSeconds`, `tmapDistanceMeters`를 선택적으로 보낸다. 처음은 201, 동일 본문 재전송은 200과 동일 `tripId`다. 다른 benchmark 재전송은 400이다.
4. 각 GPS 점에 안정적인 UUID `pointId`와 Unix epoch milliseconds `timestamp`를 부여한다. `gpsSpeed`는 m/s, `heading`은 도, `accuracy`는 m이다. `POST /api/trips/{tripId}/points`로 1..1000점을 보낸다. 실제 모바일은 약 10점 batch를 사용한다. 동일 point ID 재전송은 `pointsStored=0`이다. 다른 point ID의 같은 timestamp는 둘 다 저장된다.
5. 모바일이 오프라인이면 Frontend IndexedDB에 같은 `clientTripId`·`pointId`를 유지하고 재연결 뒤 다시 보낸다. 서버는 재전송을 허용하지만 모바일 오프라인 버퍼를 제공하지 않는다.
6. `POST /api/trips/{tripId}/finish`에 `finishedAt`과 `actualDurationSeconds`를 보낸다. 같은 값 재전송은 204, 다른 종료값은 400이다.
7. Edge 재시작 후 `GET /api/trips/{tripId}`에서 ETA·수동 benchmark·실제 시간·point 수와 좌표를 확인한다. `GET /api/trips`는 최근 50건 요약이다.

기존 SQLite는 시작 시 새 열과 point ID index로 migration한다. 옛 GPS 점은 보존되며 `pointId=null`, 옛 Trip은 `clientTripId=null`로 조회될 수 있다. 기존 DB 파일을 삭제하지 않는다. 마이그레이션 전에는 DB/WAL 파일의 백업을 권장한다.

## 5. Raspberry Pi와 Nginx HTTPS

Pi에는 Frontend 정적 파일, Edge, Nginx를 둔다. Engine은 Compute 서버에 둔다. Edge 이미지의 Java 21 런타임은 multi-arch base image를 사용하지만 대상 ARM64에서 실행한 결과는 아직 없다. Pi에서 Edge 8080은 Nginx가 접근할 로컬 주소에만 바인딩한다. Engine URL은 Pi의 환경변수 `NAV_ENGINE_BASE_URL`에 둔다.
빌드 의존성 `sqlite-jdbc-3.50.3.0.jar` 내부에 `Linux/aarch64/libsqlitejdbc.so`가 있음을 확인했고, [공식 Temurin `21-jre-jammy` 태그](https://hub.docker.com/_/eclipse-temurin/tags?name=21-jre&page=1)는 arm64 manifest를 제공한다. 이는 **호환 구성 확인**이며 실제 Pi 컨테이너 기동 확인은 아니다.

[배포 템플릿](../../deploy/nginx/navigation.conf.template)은 정적 Frontend `location /`과 Edge `location /api/`를 같은 HTTPS origin으로 제공한다. 서버의 기존 유효 인증서를 먼저 확인한 뒤 도메인, 정적 파일 위치, 인증서·개인키 경로를 정한다. 이 네 값만 선택적으로 치환하여 Nginx 설정을 만들고 `nginx -t`를 통과한 뒤 reload한다. 인증서 발급을 반복 시도하지 않는다. 템플릿에는 Engine 프록시가 없다. 서버 정보가 없어 실제 설정 생성·reload·HTTPS 확인은 수행하지 못했다.

```bash
# Pi에서 실제 도메인·Frontend dist·기존 TLS 파일을 환경변수로 설정한 후:
envsubst '${NAV_PUBLIC_HOST} ${FRONTEND_DIST} ${TLS_CERT_FILE} ${TLS_KEY_FILE}' \
  < deploy/nginx/navigation.conf.template > /tmp/k-nav-navigation.conf
```

위 임시 파일은 서버의 기존 Nginx 설정 방식에 맞춰 `sites-available` 등에 설치하고, 기본 `sudo nginx -t` 통과 후 `sudo systemctl reload nginx`를 수행한다. 템플릿 단독으로는 Nginx 최상위 설정을 대신할 수 없다.

## 6. 출발 전 확인

- [ ] 지역 PBF가 양쪽 주행 구간을 포함하고 graph count가 정상이다.
- [ ] 실제 좌표의 두 algorithm 경로가 200, 양수 거리·시간, 실제 도로 geometry를 낸다. one-way와 5초 목표를 확인한다.
- [ ] Edge→Engine LAN/VPN이 연결되고 Engine 8090이 외부에 노출되지 않는다.
- [ ] Trip 생성 201→동일 재전송 200, benchmark 충돌 400을 확인한다.
- [ ] GPS 같은 point ID 중복 0건, 같은 timestamp의 다른 point ID 2건 저장을 확인한다.
- [ ] finish 동일 재전송 204, Edge 재시작 후 route snapshot·Trip·GPS가 남는다.
- [ ] Nginx HTTPS `/`는 Frontend, `/api/routes`는 Edge→Engine이며 모바일에서 mixed content 없이 동작한다.
- [ ] 모바일 위치 권한, IndexedDB 오프라인 보존·재연결 동기화를 확인한다.
- [ ] 공식 TMAP 앱 ETA·거리를 출발 직전에 수동 기록한다. Backend TMAP 키/API는 없다.

현재 접근 정보 부재로 실제 PBF·Pi·Nginx·모바일 E2E 항목은 체크하지 않았다.
