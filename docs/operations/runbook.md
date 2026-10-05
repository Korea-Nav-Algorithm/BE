# Navigation 실차 준비 runbook

## 확인된 상태와 배포 경계

저장소에는 두 Spring Boot 서비스, Dockerfile, 개발용 Compose, SQLite 영속화, Nginx 설정 템플릿이 있다. Java 21 Docker Gradle 테스트와 실제 South Korea OSM PBF를 사용한 로컬 Edge→Engine→Trip HTTP smoke는 통과했다. 실제 주행 좌표·Pi/Engine SSH·도메인/TLS 정보가 아직 제공되지 않아 현장 배포와 모바일 HTTPS E2E는 **미검증**이다. `data/dev-graph.json`은 가상 도로이며 실차 길안내에 사용하지 않는다. 공식 TMAP 앱 결과는 수동 benchmark이며 Backend의 TMAP API 호출은 없다.

## 1. OSM PBF 배치

[Geofabrik South Korea](https://download.geofabrik.de/asia/south-korea.html)의 원본 PBF를 확보한다. 실제 출발지→분당중앙교회→다음 목적지와 우회 도로가 모두 포함되도록 `OSM_BBOX`를 설정하면 로더가 필요한 지역만 메모리에 올린다. 로컬 검증에서 공식 South Korea PBF와 `126.7,37.1,127.5,37.6` 경계로 977,628 nodes/1,933,018 directed edges를 로드했다. 경계 밖 목적지는 422가 될 수 있으므로 현장 좌표로 범위를 정한다. 필요하면 `osmium`이 있는 머신에서 [공식 extract 명령](https://docs.osmcode.org/osmium/latest/osmium-extract.html)의 `complete_ways` 방식으로 추출본을 만든다. `<...>`는 실제 좌표로 대체한다.

```bash
mkdir -p data/osm
osmium extract --bbox <min-lng>,<min-lat>,<max-lng>,<max-lat> \
  --strategy complete_ways --output data/osm/gyeonggi-latest.osm.pbf \
  south-korea-latest.osm.pbf
```

잘린 도로 way의 참조 node가 포함되도록 `complete_ways`를 사용한다. PBF는 Git에 넣지 않는다. 원본 또는 추출본을 `data/osm/`에 놓고 Engine이 읽는 파일명에 맞춰 `.env`를 수정한다. 아래 BBOX는 로컬 예시 경계이며 실제 주행 범위에 맞게 변경한다.

```env
GRAPH_SOURCE=osm-pbf
OSM_PBF_FILE=/data/osm/gyeonggi-latest.osm.pbf
OSM_BBOX=126.7,37.1,127.5,37.6
TRAFFIC_PROVIDER=json
```

Engine 시작 로그 `event=graph_loaded`에서 파일명, node/방향별 edge·회전 제한 수, build 시간, 메모리 사용량을 확인한다. 0 node/edge면 시작이 실패한다. `/health`의 `graphLoaded`, `nodeCount`, `edgeCount`, `turnRestrictionCount`, `trafficProvider`, `algorithmVersion`을 확인한다. 로컬 South Korea PBF 경계에서 via-node 회전 제한 6,431개를 로드했다. PBF 로더는 주요 자동차 도로, 고속도로 기본 일방통행·명시적 일방통행, 일부 자동차 접근 금지·maxspeed와 OSM의 via-node `no_*`/`only_*` 제한을 반영한다. via-way·조건부 제한, 모든 접근 규칙·edge 중간 스냅은 지원하지 않는다.

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

호스트 8080/8090을 다른 서비스가 사용하면 `.env`의 `EDGE_HOST_PORT`, `ENGINE_HOST_PORT`를 빈 포트로 지정한다. 이는 호스트의 loopback 바인딩만 바꾸며, 같은 Compose 내부의 `NAV_ENGINE_BASE_URL=http://navigation-engine:8090`은 유지한다. 실제 PBF 기동에는 `GRAPH_SOURCE=osm-pbf`, 존재하는 `OSM_PBF_FILE`, 해당 지역 `OSM_BBOX`가 필요하다.

Compose는 기본 host 8080/8090을 loopback에 바인딩한다. `data/`는 Engine에 read-only, Edge에 read-write로 마운트한다. SQLite 파일과 WAL은 `TRIP_DB_PATH=/data/navigation.db`에 남는다. `docker compose down`은 DB를 삭제하지 않는다. 별도 두 머신 배포에서는 Engine 컨테이너를 Compute 서버에, Edge 컨테이너를 Pi에 두고 `NAV_ENGINE_BASE_URL`을 Compute LAN/VPN 주소로 지정한다. Engine 8090은 공인망과 Nginx `/api/`에 노출하지 않는다.

2026-10-05 로컬 Docker Compose 검증: 기존 다른 서비스가 8080/8090을 사용해 `EDGE_HOST_PORT=28080`, `ENGINE_HOST_PORT=28090`으로 기동했다. Engine `/health`는 실제 PBF 977,628 nodes/1,933,018 directed edges, 회전 제한 6,431개를 반환했다. Docker Edge `127.0.0.1:28080`을 거친 예시 수원→분당중앙교회 경로는 미관측 속도 계수 적용 전 18,928m/1,224초였고 적용 후 18,928m/1,631초였다. geometry 383점·segment 382개·화면 안내 29개이며 교통 관측 없는 382개 구간은 모두 `UNKNOWN`이다. 교통 매핑 변경 뒤에도 `BASELINE`과 `DIRECTION_AWARE` 모두 200, 18,928m/1,631초, 관측 segment 0/382로 확인했다. `GET /api/routes/{routeId}` snapshot도 일치했다. 첫 cold 경로는 5초를 넘긴 적 있어 Edge read timeout을 9초로 조정했다. 이 수치는 Docker Desktop 로컬 결과이며 **실교통이 적용됐다는 증거가 아니다.** Pi·모바일 현장 성능도 미검증이다.

분리 배포 파일은 [Engine Compose](../../deploy/compose.engine.yml)와 [Edge Compose](../../deploy/compose.edge.yml)다. **각 머신에 저장소와 `.env`가 준비된 뒤** 저장소 루트에서 다음 명령으로 구성 검증·기동한다. 먼저 Compute 서버에서 `ENGINE_BIND_ADDRESS`를 서버의 LAN/VPN 인터페이스 IP로 바꾸고, PBF가 `/data/osm/...`에 mount되는지 확인한다. Pi에서는 `NAV_ENGINE_BASE_URL`을 그 내부 IP의 `http://<IP>:8090`으로 바꾼다. 예시 `.env.example`의 DNS 값과 loopback bind 값은 분리 배포에서 그대로 사용하면 안 된다.

```bash
docker compose --env-file .env -f deploy/compose.engine.yml config --quiet
docker compose --env-file .env -f deploy/compose.engine.yml up --build -d
# 다른 머신인 Pi에서: Pi에서 직접 build할 때만 --build를 사용한다.
docker compose --env-file .env -f deploy/compose.edge.yml config --quiet
docker compose --env-file .env -f deploy/compose.edge.yml up --build -d
```

Pi에서 Gradle 빌드를 수행하지 않으려면 빌드 머신에서 아래처럼 ARM64 이미지를 만든다. `NAV_EDGE_IMAGE`는 `.env`와 이미지 태그가 정확히 같아야 한다. tar 파일은 이미지이므로 Git에 추가하지 않는다. Pi로 파일을 옮기는 방법과 경로는 현장 접속 환경에 맞춰 정한다.

```bash
# 빌드 머신, 저장소 루트
docker buildx build --platform linux/arm64 --load \
  -f navigation-edge/Dockerfile -t navigation-edge:local .
docker save -o data/navigation-edge-arm64.tar navigation-edge:local

# Pi, 저장소 루트와 .env 및 tar 파일 준비 후
docker load -i data/navigation-edge-arm64.tar
docker compose --env-file .env -f deploy/compose.edge.yml config --quiet
docker compose --env-file .env -f deploy/compose.edge.yml up -d --no-build
```

Pi의 `.env`에는 `NAV_EDGE_IMAGE=navigation-edge:local`, 실제 Compute 서버의 `NAV_ENGINE_BASE_URL`, 영속 DB용 `TRIP_DB_PATH`를 지정한다. 첫 기동 뒤 `docker compose --env-file .env -f deploy/compose.edge.yml ps`와 `curl http://127.0.0.1:8080/health`로 확인한다. 위 절차는 로컬에서 ARM64 이미지 빌드·에뮬레이션 검증까지만 수행했으며 실제 Pi의 `docker load`와 기동은 미검증이다.

## 3. 실제 도로 route 확인

기본 가상 좌표를 재사용하지 않고 실제 출발지·분당중앙교회·다음 목적지의 WGS84 좌표를 준비한다. 두 구간 각각 `/api/routes`에 `BASELINE`, `DIRECTION_AWARE`를 보낸다. 200, `routeId`, 2개 이상 geometry, 양수 거리·시간, 비어 있지 않은 segments와 화면 안내 `instructions`를 확인하고 지도에서 도로와 일방통행을 눈으로 검사한다. Engine `event=route_snap`에 node ID·snap 거리가, `event=route`에 algorithm/version·거리·시간·segment 수·각 단계 시간이 남는다. 최근접 node 1km 초과 또는 도달 불가면 422다. 정상 route 계산 목표는 5초 이하다. 예시 좌표 두 구간에서는 성공했지만 실제 사용자 출발지/후속 목적지와 지도 육안 검사는 미검증이다. 안내는 via-node 제한만 일부 반영하며 차로·조건부 제한을 검증하지 않은 시각 힌트이므로 운전자에게 검증된 지시로 제시하지 않는다.

실좌표를 받으면 저장소의 [경로 smoke 스크립트](../../scripts/smoke-routes.ps1)에 세 지점을 인자로 전달한다. 아래 `$originLat` 등 여섯 변수에는 확인한 실좌표를 먼저 넣는다. 스크립트는 두 구간×두 algorithm의 거리·ETA·좌표/segment 수·elapsedMs를 출력하고 빈 경로 또는 5초 초과 응답에서 실패한다. 좌표를 스크립트 내부에 하드코딩하지 않는다.

```powershell
.\scripts\smoke-routes.ps1 -BaseUrl http://127.0.0.1:8080 `
  -OriginLat $originLat -OriginLng $originLng `
  -WaypointLat $waypointLat -WaypointLng $waypointLng `
  -DestinationLat $destinationLat -DestinationLng $destinationLng
```

`GET /api/routes/{routeId}`로 SQLite에 저장된 `algorithm`, `algorithmVersion`, geometry, segment attribution을 재조회한다. 교통 관측이 없는 edge는 `ETA_UNOBSERVED_SPEED_FACTOR`(기본 0.75)만큼 계획 속도를 낮추며, 상태는 `UNKNOWN`이다. 실차 보정 전 임시 ETA이므로 TMAP·실제 소요시간과 편차를 기록한다. 지도에서는 관측 속도 비율에 따라 빨강/주황/초록을 사용하고 `UNKNOWN`은 회색이다. **실교통 최단 시간 경로를 주장하려면 아래의 경기 교통 연결과 경로별 관측 적용을 확인해야 한다.** 키를 로그나 Frontend에 넣지 않는다.

### 실교통 link 연결

1. [경기도 교통정보센터](https://openapigits.gg.go.kr/api/jsp/apiKey_requestInfo.jsp)의 공개키를 Engine의 `.env`에만 설정한다. [전체 소통정보 API](https://openapigits.gg.go.kr/api/jsp/manual_getRoadTrafficInfoList.jsp)는 `linkId`, `spd`, `collDate`를 제공하지만 link의 좌표는 제공하지 않는다.
2. [ITS 표준 노드·링크](https://www.its.go.kr/nodelink/nodelinkStatus?service=inquiryNodelink)의 같은 ID를 쓰는 **지역별 방향 있는 링크 도형**을 준비한다. 도형 원본의 `LINK_ID`와 진행 방향을 확인한 뒤 WGS84(EPSG:4326) GeoJSON `FeatureCollection`으로 변환해 `data/` 아래에 놓는다. 각 `LineString` 또는 `MultiLineString`의 `[lng,lat]` 점 순서는 차량 진행 방향이어야 한다. 데이터가 지역 범위보다 크면 해당 지역만 추출한다. 버전이나 ID 체계가 경기 API와 다르면 매핑하지 않는다.
3. Engine `.env`에 다음을 지정하고 재시작한다. `TRAFFIC_MAPPING_FILE`은 자동 매칭이 애매한 소수 link의 수동 예외용이며 값은 edge ID 문자열 또는 방향별 edge ID 배열이다.

```env
TRAFFIC_PROVIDER=gyeonggi
GG_TRAFFIC_API_KEY=<서버에만 보관하는 발급 키>
TRAFFIC_LINK_GEOMETRY_FILE=/data/traffic-links/region.geojson
TRAFFIC_MAPPING_FILE=/data/traffic-mapping.json
GG_TRAFFIC_ROUTE_IDS=
```

4. 경기 전체 소통 API는 데이터가 많으면 느릴 수 있다. 첫 실차 대상 도로가 정해져 있다면 `GG_TRAFFIC_ROUTE_IDS`에 공식 API의 도로 ID를 쉼표로 지정해 대상 도로만 갱신한다. 비워 두면 전체 feed를 요청한다. 시작 로그 `event=traffic_geometry_mapping`의 `mappedLinks`·`mappedDirectedEdges`가 0보다 큰지 확인한다. 첫 수집 뒤 `event=traffic_refresh status=ready`와 양수 `mappedDirectedEdges`를 확인한다. 실제 주행 좌표의 `/api/routes`에서 `trafficSource=GYEONGGI`와 `trafficLevel`이 `UNKNOWN`이 아닌 segment를 확인하고 지도에서 방향·도로가 맞는지 육안 검사한다. 키 누락·응답 실패·도형 불일치·관측값 노후화 시 `trafficSource=UNKNOWN`이고 ETA는 계획 속도 추정치다. 저장소에는 지역 링크 도형이 포함되지 않으며, 실제 계정의 성공 응답과 현장 매핑률은 별도로 검증해야 한다.

교통 갱신 로그의 `reason=upstream_code_7`은 경기 API가 **등록됐으나 현재 사용할 수 없는 키**라고 응답한 것이다. API 이용 신청의 승인·활성 상태를 제공자 사이트에서 확인해야 한다. `reason=no_mapped_edges`라면 활성 키의 응답을 받았어도 방향별 `linkId` 매핑, 관측 시각 또는 속도 조건을 통과한 edge가 없다는 뜻이다. `reason=timeout|tls|dns|connect`는 통신 문제다. Engine은 응답 본문·키·요청 URL을 로그에 남기지 않는다. **키가 활성화돼도 링크 도형 파일과 매핑이 없으면 경로는 전부 `UNKNOWN`이다.**

목적지 건물·지명 검색은 Edge의 `GET /api/places?query=...`를 사용한다. 개발 기본값 `PLACE_SEARCH_PROVIDER=json`에는 분당중앙교회 한 건만 있다. 일반 검색 전 Edge의 `.env`에 `PLACE_SEARCH_PROVIDER=naver`, `NAVER_SEARCH_CLIENT_ID`, `NAVER_SEARCH_CLIENT_SECRET`을 설정하고 Edge를 재시작한다. 인증 정보는 Frontend에 넣지 않는다. 실제 계정 호출과 검색 결과 품질은 미검증이다. 200 응답의 `source`가 `NAVER`인지, 검색 결과를 선택해 route 200을 받는지 확인한다. Naver 검색은 최대 5건이고 서버의 OSM graph 범위로 결과를 제한하지 않으므로 범위 밖 결과는 route 422가 될 수 있다. 503이면 지도 터치·좌표 입력으로 진행한다.

```bash
curl --get --data-urlencode 'query=분당중앙교회' http://127.0.0.1:8080/api/places
```

개발 호스트 포트를 변경했다면 URL의 8080을 `EDGE_HOST_PORT` 값으로 바꾼다. 2자 미만·누락 query는 `400 INVALID_REQUEST`여야 한다. [Naver 지역 검색 API](https://developers.naver.com/docs/serviceapi/search/local/local.md)에 따라 애플리케이션의 검색 API 사용 설정·호출 한도를 확인한다.

## 4. Trip·GPS·수동 TMAP benchmark

1. OUR route를 계산하고 `routeId`·ETA·거리를 기록한다.
2. 공식 TMAP 앱에서 같은 목적지를 설정해 **출발 직전** ETA(초)와 거리(m)를 확인한다.
3. Frontend가 만든 안정적인 UUID `clientTripId`와 32바이트 난수의 `accessKey`를 IndexedDB에 첫 요청 전에 보존한다. `POST /api/trips`에 둘 다 포함하고 수동 `tmapEtaSeconds`, `tmapDistanceMeters`를 선택적으로 보낸다. 처음은 201, 동일 본문·key 재전송은 200과 동일 `tripId`다. 다른 benchmark 재전송은 400, 다른 key는 404다.
4. 각 GPS 점에 안정적인 UUID `pointId`와 Unix epoch milliseconds `timestamp`를 부여한다. `gpsSpeed`는 m/s, `heading`은 도, `accuracy`는 m이다. `X-Trip-Key: <accessKey>` 헤더로 `POST /api/trips/{tripId}/points`에 1..1000점을 보낸다. 실제 모바일은 약 10점 batch를 사용한다. 동일 point ID 재전송은 `pointsStored=0`이다. 다른 point ID의 같은 timestamp는 둘 다 저장된다.
5. 모바일이 오프라인이면 Frontend IndexedDB에 같은 `clientTripId`·`accessKey`·`pointId`를 유지하고 재연결 뒤 다시 보낸다. 서버는 재전송을 허용하지만 모바일 오프라인 버퍼를 제공하지 않는다.
   주행 중 `/api/routes` 재탐색에 성공하면 새 route ID와 시각을 IndexedDB에 먼저 저장하고 같은 `X-Trip-Key`로 `POST /api/trips/{tripId}/routes`에 보낸다. 응답 유실 시 같은 ID/시각을 재전송하며 Backend는 204를 반환한다.
6. 같은 `X-Trip-Key`로 `POST /api/trips/{tripId}/finish`에 `finishedAt`과 `actualDurationSeconds`를 보낸다. 같은 값 재전송은 204, 다른 종료값은 400이다.
7. Edge 재시작 후 같은 `X-Trip-Key`로 `GET /api/trips/{tripId}`에서 ETA·수동 benchmark·실제 시간·point 수·GPS·`reroutes`를 확인한다. `GET /api/trips`는 Edge에 `TRIP_ADMIN_KEY`를 설정한 운영자만 `X-Trip-Admin-Key`로 최근 50건 요약을 조회한다.

기존 SQLite는 시작 시 새 열과 point ID index로 migration한다. 옛 GPS 점은 보존되지만 access key가 없는 이전 Trip은 API 조회가 잠긴다. 운영자는 DB 백업에서 확인한다. 기존 DB 파일을 삭제하지 않는다. 마이그레이션 전에는 DB/WAL 파일의 백업을 권장한다.

## 5. Raspberry Pi와 Nginx HTTPS

Pi에는 Frontend 정적 파일, Edge, Nginx를 둔다. Engine은 Compute 서버에 둔다. Edge·Engine 두 이미지는 실제 `linux/arm64` 빌드가 성공했다. 로컬 에뮬레이션 ARM64 Edge에서 Java 21 health, Engine 프록시 경로, SQLite GPS 저장·조회를 확인했다. Pi 실기기 실행은 아직 아니다. Pi에서 Edge 8080은 Nginx가 접근할 로컬 주소에만 바인딩한다. Engine URL은 Pi의 환경변수 `NAV_ENGINE_BASE_URL`에 둔다.
Dockerfile은 빌드 머신 아키텍처에서 Java JAR을 만들고 ARM64 Java 런타임에 복사한다. `sqlite-jdbc-3.50.3.0.jar`의 `Linux/aarch64/libsqlitejdbc.so`가 로컬 ARM64 에뮬레이션에서 실제 DB 연결에 사용됐다. [공식 Temurin `21-jre-jammy` 태그](https://hub.docker.com/_/eclipse-temurin/tags?name=21-jre&page=1)는 arm64 manifest를 제공한다.

[배포 템플릿](../../deploy/nginx/navigation.conf.template)은 정적 Frontend `location /`과 Edge `location /api/`를 같은 HTTPS origin으로 제공한다. `limit_req_zone` 지시어가 있으므로 템플릿은 Nginx의 `http` 컨텍스트에 include되는 파일로 설치한다. API는 IP별 10r/s, 순간 burst 20을 허용하며 초과 시 JSON이 보장되지 않는 429를 반환한다. 서버의 기존 유효 인증서를 먼저 확인한 뒤 도메인, 정적 파일 위치, 인증서·개인키 경로를 정한다. 이 네 값만 선택적으로 치환하여 Nginx 설정을 만들고 `nginx -t`를 통과한 뒤 reload한다. 인증서 발급을 반복 시도하지 않는다. 템플릿에는 Engine 프록시가 없다. 서버 정보가 없어 실제 설정 생성·reload·HTTPS 확인은 수행하지 못했다.

```bash
# Pi에서 실제 도메인·Frontend dist·기존 TLS 파일을 환경변수로 설정한 후:
envsubst '${NAV_PUBLIC_HOST} ${FRONTEND_DIST} ${TLS_CERT_FILE} ${TLS_KEY_FILE}' \
  < deploy/nginx/navigation.conf.template > /tmp/k-nav-navigation.conf
```

위 임시 파일은 서버의 기존 Nginx 설정 방식에 맞춰 `sites-available` 등에 설치하고, 기본 `sudo nginx -t` 통과 후 `sudo systemctl reload nginx`를 수행한다. 템플릿 단독으로는 Nginx 최상위 설정을 대신할 수 없다.

## 6. 출발 전 확인

- [ ] 지역 PBF가 양쪽 주행 구간을 포함하고 graph count가 정상이다.
- [ ] 실제 좌표의 두 algorithm 경로가 200, 양수 거리·시간, 실제 도로 geometry를 낸다. one-way와 5초 목표를 확인한다.
- [ ] 목적지 검색의 실제 공급자 결과에서 건물명·주소·좌표를 확인하고 선택한 좌표로 route를 계산한다. 개발 JSON 목록을 실서비스 검색으로 취급하지 않는다.
- [ ] ETA를 별도 TMAP 앱 및 실제 소요시간과 비교하고, `UNKNOWN` 교통 구간을 회색으로 표시한다. 실제 교통 연결 전에 빨강/주황/초록의 현장 정확성을 주장하지 않는다.
- [ ] Edge→Engine LAN/VPN이 연결되고 Engine 8090이 외부에 노출되지 않는다.
- [ ] Trip 생성 201→동일 key·본문 재전송 200, benchmark 충돌 400, key 불일치 404를 확인한다.
- [ ] GPS 같은 point ID 중복 0건, 같은 timestamp의 다른 point ID 2건 저장을 확인한다.
- [ ] `X-Trip-Key` 없는 GPS/조회가 거부되고, finish 동일 재전송 204, Edge 재시작 후 route snapshot·Trip·GPS가 남는다.
- [ ] Nginx HTTPS `/`는 Frontend, `/api/routes`는 Edge→Engine이며 모바일에서 mixed content 없이 동작한다.
- [ ] 모바일 위치 권한, IndexedDB 오프라인 보존·재연결 동기화를 확인한다.
- [ ] 공식 TMAP 앱 ETA·거리를 출발 직전에 수동 기록한다. Backend TMAP 키/API는 없다.

로컬 PBF 경로·Trip E2E는 확인했다. 접근 정보 부재로 Pi·Nginx·모바일 E2E 항목은 체크하지 않았다.
