# K-Nav Navigation MVP Backend

Java 21, Spring Boot 3, Gradle multi-project. 브라우저는 Raspberry Pi의 `navigation-edge` `/api/*`만 호출한다. `navigation-engine`은 LAN/VPN 내부에서 별도 실행하며 LLM을 호출하지 않는다. 공식 TMAP 앱 ETA·거리는 사용자가 확인한 값을 Trip에 수동 기록한다. Backend는 TMAP API를 호출하지 않는다.

## 현재 상태

- JSON 가상 그래프와 JSON 교통으로 Edge→Engine 경로, Trip/GPS 흐름을 개발·검증할 수 있다.
- 실제 주행에는 지역 OSM PBF 파일과 실제 출발/목적지 좌표가 필요하다. 저장소에 PBF는 포함하지 않는다.
- Pi SSH, 공개 도메인/TLS, Engine 서버 접근 정보가 없어 실제 배포는 아직 검증되지 않았다.
- 상세 진행 현황: [Frontend handoff](docs/frontend-handoff.md). 현장 순서: [runbook](docs/operations/runbook.md).

## 로컬 실행

Java 21 설치 후 저장소 루트에서 `./gradlew test`를 실행한다. Windows는 `.\gradlew.bat test`를 사용한다. 터미널 1에서 `./gradlew :navigation-engine:bootRun`, 터미널 2에서 `./gradlew :navigation-edge:bootRun`을 실행한다. 각각 `http://localhost:8090/health`, `http://localhost:8080/health`를 확인한다.

```bash
curl -X POST http://127.0.0.1:8080/api/routes -H 'Content-Type: application/json' -d '{"origin":{"lat":37.263,"lng":127.028},"destination":{"lat":37.271,"lng":127.026},"algorithm":"DIRECTION_AWARE"}'
```

`data/dev-graph.json`은 **가상 도로**다. 실제 운전에는 사용하지 않는다.

## Docker 개발 실행

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build -d
docker compose ps
curl http://127.0.0.1:8080/health
```

`.env`와 `data/**/*.osm.pbf`는 Git에서 제외한다. Compose는 개발용으로 두 서비스를 한 머신에 띄우고 Edge SQLite를 `./data:/data`에 영속화한다. 실제 배포는 Engine 서버와 Pi를 분리한다. Edge의 `NAV_ENGINE_BASE_URL`을 Engine의 LAN/VPN URL로 설정한다. Engine 8090은 외부에 공개하지 않는다.

분리 배포용 [Engine Compose](deploy/compose.engine.yml)와 [Edge Compose](deploy/compose.edge.yml)를 제공한다. 실제 인터페이스 IP와 URL로 `.env`를 수정한 뒤 각 머신에서 사용한다. Pi/Nginx 현장 배포는 접근 정보가 없어 아직 수행되지 않았다.

## 실차 데이터와 설정

[Geofabrik South Korea PBF](https://download.geofabrik.de/asia/south-korea.html)에서 원본을 구해 주행 지역을 포함하는 추출본을 만든 뒤 `data/osm/gyeonggi-latest.osm.pbf`에 배치한다. 추출 명령과 검증 순서는 [runbook](docs/operations/runbook.md)에 있다. `.env`에서 `GRAPH_SOURCE=osm-pbf`, `OSM_PBF_FILE=/data/osm/gyeonggi-latest.osm.pbf`를 설정한다. 전체 국가 파일은 현재 메모리 로더에 부담이 클 수 있다. 실제 좌표 두 구간으로 경로와 지연 시간을 확인해야 한다.

경기 교통은 기본 `TRAFFIC_PROVIDER=json`이다. 실데이터를 사용하려면 `TRAFFIC_PROVIDER=gyeonggi`, `GG_TRAFFIC_API_KEY`, `TRAFFIC_MAPPING_FILE`의 방향별 link ID 매핑이 필요하다. 실패하면 마지막 정상 snapshot 또는 기본 속도를 사용한다. `NAVIGATION_ALGORITHM_VERSION`은 Engine 응답과 Edge route snapshot에 저장된다. TMAP 키는 필요하지 않다. 설정 전체는 [`.env.example`](.env.example)에 있다.

## Frontend 공개 API

| Method | Path | 용도 |
| --- | --- | --- |
| POST | `/api/routes` | 두 algorithm 경로·ETA·진단 |
| GET | `/api/routes/{routeId}` | 저장된 경로 재조회 |
| POST | `/api/trips` | `clientTripId`로 멱등 생성, 수동 TMAP benchmark 저장 |
| POST | `/api/trips/{tripId}/points` | `pointId`별 GPS batch 멱등 저장 |
| POST | `/api/trips/{tripId}/finish` | 같은 값 재전송 가능 |
| GET | `/api/trips/{tripId}` | Trip·GPS·benchmark 조회 |
| GET | `/api/trips` | 최근 50건 요약 |

정확한 필드·상태 코드는 [API 계약](docs/api/specification.md)을 따른다. Engine의 `/internal/routes`는 Frontend에서 직접 호출하지 않는다. 운영 Nginx 설정 템플릿은 [deploy/nginx/navigation.conf.template](deploy/nginx/navigation.conf.template)에 있다.
