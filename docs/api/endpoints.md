# Endpoint index

| Method | URL | Auth | 설명 |
| --- | --- | --- | --- |
| GET | `/health` | 없음 | Edge/Engine 각각 상태 확인 |
| GET | `/api/places` | 없음 | 건물·지명 검색 |
| POST | `/api/routes` | 없음 | 경로 계산 |
| GET | `/api/routes/{routeId}` | 없음 | 저장된 진단 조회 |
| POST | `/api/trips` | 없음 | 주행 시작 |
| POST | `/api/trips/{tripId}/points` | Trip key | GPS 배치 저장 |
| POST | `/api/trips/{tripId}/routes` | Trip key | 재탐색 route ID 멱등 저장 |
| POST | `/api/trips/{tripId}/finish` | Trip key | 주행 종료 |
| GET | `/api/trips/{tripId}` | Trip key | 주행 조회 |
| GET | `/api/trips` | 운영 key | 최근 주행 50건 |
| POST | `/internal/routes` | 사설망 전용 | Engine 계산 |
