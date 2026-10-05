# K-Nav Navigation MVP Backend

Java 21, Spring Boot 3, Gradle multi-project. 브라우저는 Raspberry Pi의 `navigation-edge` `/api/*`만 호출한다. `navigation-engine`은 LAN/VPN 내부에서 별도 실행하며 LLM을 호출하지 않는다. 공식 TMAP 앱 ETA·거리는 사용자가 확인한 값을 Trip에 수동 기록한다. Backend는 TMAP API를 호출하지 않는다.

## 현재 상태

- JSON 가상 그래프와 JSON 교통으로 Edge→Engine 경로, Trip/GPS 흐름을 개발·검증할 수 있다.
- South Korea OSM PBF를 `OSM_BBOX=126.7,37.1,127.5,37.6`으로 제한한 로컬 경로 검증을 수행했다. 원본 PBF는 Git에 포함하지 않는다. 현장 주행 좌표와 도로 연결성은 별도 검증이 필요하다.
- 경로 응답은 회전 방향의 화면 안내 `instructions`를 포함한다. OSM의 via-node `no_*`/`only_*` 회전 제한은 경로 탐색에 반영한다. via-way·조건부 제한, 차로, 음성 안내는 구현되지 않았다.
- 목적지 검색은 Edge `/api/places`에서 제공한다. 기본 JSON 목록은 개발용 장소 1건이며, 일반 건물·지명 검색에는 서버 전용 Naver Search API Client ID·Secret과 `PLACE_SEARCH_PROVIDER=naver`가 필요하다. 브라우저에는 키를 전달하지 않는다.
- 관측 교통이 없는 도로의 ETA는 `ETA_UNOBSERVED_SPEED_FACTOR=0.75`를 적용한 초기 계획치다. 실측 보정 전이며, 관측 없는 구간은 화면에서 회색으로 표시한다.
- Pi SSH, 공개 도메인/TLS, Engine 서버 접근 정보가 없어 실제 배포는 아직 검증되지 않았다.
- 상세 진행 현황: [Frontend handoff](docs/frontend-handoff.md). 현장 순서: [runbook](docs/operations/runbook.md).

## 로컬 실행

Java 21 설치 후 저장소 루트에서 `./gradlew test`를 실행한다. Windows는 `.\gradlew.bat test`를 사용한다. 터미널 1에서 `./gradlew :navigation-engine:bootRun`, 터미널 2에서 `./gradlew :navigation-edge:bootRun`을 실행한다. 각각 `http://localhost:8090/health`, `http://localhost:8080/health`를 확인한다.

```bash
curl -X POST http://127.0.0.1:8080/api/routes -H 'Content-Type: application/json' -d '{"origin":{"lat":37.263,"lng":127.028},"destination":{"lat":37.271,"lng":127.026},"algorithm":"DIRECTION_AWARE"}'
```

`data/dev-graph.json`은 **가상 도로**다. 실제 운전에는 사용하지 않는다.

```bash
curl 'http://127.0.0.1:8080/api/places?query=%EB%B6%84%EB%8B%B9%EC%A4%91%EC%95%99%EA%B5%90%ED%9A%8C'
```

개발 목록 외의 건물·지명을 찾으려면 Edge `.env`에 `PLACE_SEARCH_PROVIDER=naver`, `NAVER_SEARCH_CLIENT_ID`, `NAVER_SEARCH_CLIENT_SECRET`을 설정하고 Edge를 다시 시작한다. Naver API 인증 정보는 Frontend `.env`나 번들에 넣지 않는다. 실제 키·계정으로 한 호출은 아직 검증하지 않았다. Naver 지역 검색은 최대 5건을 반환하며, 선택한 좌표가 OSM graph 밖이면 route 요청은 422가 될 수 있다.

## Docker 개발 실행

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build -d
docker compose ps
curl http://127.0.0.1:8080/health
```

기본 호스트 포트는 loopback `8080`(Edge), `8090`(Engine)이다. 이미 사용 중이면 `.env`에서 `EDGE_HOST_PORT`, `ENGINE_HOST_PORT`를 다른 빈 포트로 지정한다. 컨테이너 내부 `NAV_ENGINE_BASE_URL=http://navigation-engine:8090`은 그대로 둔다.

`.env`와 `data/**/*.osm.pbf`는 Git에서 제외한다. Compose는 개발용으로 두 서비스를 한 머신에 띄우고 Edge SQLite를 `./data:/data`에 영속화한다. 실제 배포는 Engine 서버와 Pi를 분리한다. Edge의 `NAV_ENGINE_BASE_URL`을 Engine의 LAN/VPN URL로 설정한다. Engine 8090은 외부에 공개하지 않는다.

분리 배포용 [Engine Compose](deploy/compose.engine.yml)와 [Edge Compose](deploy/compose.edge.yml)를 제공한다. 실제 인터페이스 IP와 URL로 `.env`를 수정한 뒤 각 머신에서 사용한다. Pi/Nginx 현장 배포는 접근 정보가 없어 아직 수행되지 않았다.

## 실차 데이터와 설정

[Geofabrik South Korea PBF](https://download.geofabrik.de/asia/south-korea.html)에서 원본을 구해 `data/osm/`에 배치한다. 전체 파일을 사용할 때 `.env`에서 `GRAPH_SOURCE=osm-pbf`, `OSM_PBF_FILE=/data/osm/<파일명>.osm.pbf`, `OSM_BBOX=<min-lng>,<min-lat>,<max-lng>,<max-lat>`를 지정한다. BBOX는 실제 두 주행 구간과 주변 우회 도로를 모두 포함해야 한다. 사전 추출본을 사용해도 된다. 검증 순서는 [runbook](docs/operations/runbook.md)에 있다.

개발 기본값 `TRAFFIC_PROVIDER=json`과 `data/dev-traffic.json`의 가상 ID는 실제 OSM 도로의 교통 상황을 나타내지 않는다. 실교통 경로 검증에는 `TRAFFIC_PROVIDER=gyeonggi`, `GG_TRAFFIC_API_KEY`, 경기 link ID와 방향별 OSM edge의 매핑이 필요하다. `TRAFFIC_LINK_GEOMETRY_FILE`에 지역 표준 노드·링크의 WGS84 GeoJSON을 두면 Engine이 방향·근접도로 매핑하며 `TRAFFIC_MAPPING_FILE`의 수동 예외가 우선한다. 관측값이 하나도 매핑되지 않은 경로는 `trafficSource=UNKNOWN`이다. 현재 실제 키·도형 파일·현장 경로의 교통 적용은 미검증이다. `NAVIGATION_ALGORITHM_VERSION`은 Engine 응답과 Edge route snapshot에 저장된다. TMAP 키는 필요하지 않다. 설정 변수는 [`.env.example`](.env.example)에 있다.

## Frontend 공개 API

| Method | Path | 용도 |
| --- | --- | --- |
| GET | `/api/places?query=...` | 건물·지명 검색, 좌표 선택 |
| POST | `/api/routes` | 두 algorithm 경로·ETA·진단·화면 안내 |
| GET | `/api/routes/{routeId}` | 저장된 경로 재조회 |
| POST | `/api/trips` | `clientTripId`와 기기 `accessKey`로 멱등 생성, 수동 TMAP benchmark 저장 |
| POST | `/api/trips/{tripId}/points` | `X-Trip-Key`가 필요한 `pointId`별 GPS batch 멱등 저장 |
| POST | `/api/trips/{tripId}/routes` | `X-Trip-Key`로 재탐색 route ID를 Trip에 연결 |
| POST | `/api/trips/{tripId}/finish` | `X-Trip-Key`로 같은 값 재전송 가능 |
| GET | `/api/trips/{tripId}` | `X-Trip-Key`로 Trip·GPS·benchmark 조회 |
| GET | `/api/trips` | `X-Trip-Admin-Key`가 설정된 운영자만 최근 50건 조회 |

정확한 필드·상태 코드는 [API 계약](docs/api/specification.md)을 따른다. Engine의 `/internal/routes`는 Frontend에서 직접 호출하지 않는다. 운영 Nginx 설정 템플릿은 [deploy/nginx/navigation.conf.template](deploy/nginx/navigation.conf.template)에 있다.

기기별 `accessKey`는 Frontend가 32바이트 난수로 생성해 첫 Trip 요청 전 IndexedDB에 저장한다. 서버는 해시만 DB에 보관한다. `TRIP_ADMIN_KEY`는 서버 환경에만 설정하며 브라우저에 넣지 않는다. 일반 공개 운영은 HTTPS와 API 요청 제한, 기기에서의 실제 GPS/안내 검증 전에는 완료로 취급하지 않는다.
