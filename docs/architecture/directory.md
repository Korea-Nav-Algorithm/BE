# Directory map

| Path | 역할 |
| --- | --- |
| `settings.gradle`, `build.gradle`, `gradle/wrapper/` | Java 21 Gradle multi-project |
| `navigation-common/src/main/java/kr/knav/common/` | 공유 route DTO·enum |
| `navigation-edge/src/main/java/kr/knav/edge/routes/` | 공개 route, Engine proxy, snapshot |
| `navigation-edge/src/main/java/kr/knav/edge/places/` | 개발 JSON·Naver 장소 검색 adapter, service, 공개 API |
| `navigation-edge/src/main/java/kr/knav/edge/trips/` | Trip/GPS HTTP·service·SQLite repository |
| `navigation-edge/src/main/java/kr/knav/edge/global/` | health, 오류, 제한, CORS, migration |
| `navigation-edge/src/main/resources/schema.sql` | 새 DB 초기 schema |
| `navigation-engine/src/main/java/kr/knav/engine/graph/` | JSON/PBF loader, adjacency, nearest node |
| `navigation-engine/src/main/java/kr/knav/engine/traffic/` | JSON/경기 traffic, 수동·방향별 GeoJSON link mapping |
| `navigation-engine/src/main/java/kr/knav/engine/routing/` | A*, 목적지별 attribution, ETA, 시각 기동 안내, 내부 API |
| `navigation-engine/src/main/java/kr/knav/engine/global/` | health, 오류 |
| `navigation-edge/src/test/`, `navigation-engine/src/test/` | API/저장/migration/routing 테스트 |
| `data/` | 가상 graph·traffic·장소·mapping; 실제 PBF·SQLite는 Git 제외 |
| `navigation-edge/Dockerfile`, `navigation-engine/Dockerfile`, `docker-compose.yml` | 이미지와 로컬 개발 topology |
| `deploy/nginx/navigation.conf.template` | Pi same-origin HTTPS reverse proxy 템플릿 |
| `deploy/compose.edge.yml`, `deploy/compose.engine.yml` | Pi/Compute 분리 배포 Compose |
| `scripts/smoke-routes.ps1` | 실제 세 좌표의 두 구간·두 algorithm smoke |
| `.env.example`, `README.md`, `docs/` | 설정 안내·계약·운영 절차 |
| `docs/frontend-handoff.md` | Frontend 협업 현황 |
| `docs/architecture/routing.md` | 경로 비용과 방향별 혼잡 귀속 |
| `docs/operations/runbook.md` | 실행·실차 전 확인 |
