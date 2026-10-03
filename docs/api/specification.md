# API specification

애플리케이션 인증 없음. Edge는 Pi HTTPS Nginx의 동일 origin에서 접근하고 Engine은 LAN/VPN 내부에서만 접근한다. 모든 POST 요청은 JSON이다. 204에는 본문이 없고 413은 서블릿 오류 응답이므로 JSON이 보장되지 않는다. 좌표는 WGS84 `lat`(-90..90), `lng`(-180..180) 필수 유한 숫자다. 처리된 API 오류 본문은 `{"error":"CODE"}`이며 현재 `message` 필드는 없다. 아래 error code 설명이 사용자용 메시지를 대신한다. 별도 query parameter는 없다. `startedAt`, `finishedAt`, GPS `timestamp`는 Unix epoch milliseconds 정수다. `gpsSpeed`는 m/s, `accuracy`는 m, `heading`은 도(0..360)다.

## GET /health

Edge·Engine 공통. 인증 없음. Path/query/body 없음. 200: 필수 string, null 불가 `status="UP"`. Engine은 `graphLoaded` boolean, `nodeCount`/`edgeCount` integer, `trafficProvider`/`algorithmVersion` string도 반환한다. Edge health는 연결 상태를 검사하지 않는다. 정의된 애플리케이션 오류 없음.

## POST /api/routes

Edge 공개 API. 인증 없음. Path/query 없음. Request: 필수 객체 `origin`,`destination`(Coordinate), 필수 enum `algorithm=BASELINE|DIRECTION_AWARE`. 200: 필수 string UUID `routeId`, 필수 enum `algorithm`, 필수 string `algorithmVersion`, 필수 integer `distanceMeters`,`durationSeconds`, 필수 Coordinate 배열 `geometry`, 필수 `segments` 배열. 각 segment는 필수 string `edgeId`, 필수 number `observedSpeed`,`baseSpeed`,`attribution`(0..1),`effectiveSpeed`. `BASELINE` attribution은 1. 400 `INVALID_REQUEST`: 필드/범위/enum 오류. 422 `ROUTE_NOT_FOUND`: 최근접 노드가 1km 넘거나 도달 불가. 503 `ENGINE_UNAVAILABLE`: 연결·응답 오류. 성공 시 Edge SQLite에 응답 보존.

## GET /api/routes/{routeId}

Edge. 인증 없음. Path `routeId` 필수 string(생성된 ID는 UUID이나 경로 변수 형식은 검사하지 않음). Body/query 없음. 200: `/api/routes`와 같은 저장 응답. 404 `ROUTE_NOT_FOUND`: ID 없음.

## POST /api/trips

Edge. 인증 없음. Path/query 없음. Request: 필수 UUID string `clientTripId`, 필수 nonblank string `routeId`(존재 여부 검사 없음), 필수 0 이상 integer `startedAt`, 필수 Coordinate `origin`,`destination`, 필수 0 이상 integer `ourEtaSeconds`, 선택 0 이상 integer/null `tmapEtaSeconds`, `tmapDistanceMeters`. 두 TMAP 값은 **별도 공식 앱에서 사람이 확인한 수동 benchmark**이며 서버가 TMAP API를 호출하지 않는다. 201: 새 Trip 생성. 동일 `clientTripId`/동일 metadata 재전송은 200. 두 응답 모두 필수 UUID string `tripId` 및 `clientTripId`를 반환한다. 같은 client ID로 다른 값이 오면 400 `INVALID_REQUEST`; 잘못된 UUID·필드·음수도 400이다.

## POST /api/trips/{tripId}/points

Edge. 인증 없음. Path `tripId` 필수 string(형식 검사 없음). Query 없음. Request: 필수 `points` 배열(1..1000). 각 point: 필수 UUID string `pointId`, 필수 0 이상 integer `timestamp`, 필수 number `lat`,`lng`, 선택 0 이상 number/null `gpsSpeed`(m/s, 변환 없이 저장), 선택 0..360 number/null `heading`, 선택 0 이상 number/null `accuracy`(m). 200: 필수 integer `pointsReceived`,`pointsStored`. `(tripId,pointId)` 중복은 기존 point를 유지하며 저장 건수 0. 서로 다른 point ID는 timestamp가 같아도 각각 저장된다. 400 `INVALID_REQUEST`: 배열 크기/point 필드 오류. 404 `TRIP_NOT_FOUND`: ID 없음. 413: body 256 KiB 초과, JSON 본문 보장 없음. 한 batch는 transaction으로 저장되고 종료 후 지연 batch도 수락한다.

## POST /api/trips/{tripId}/finish

Edge. 인증 없음. Path `tripId` 필수 string(형식 검사 없음). Query 없음. Request: 필수 0 이상 integer `finishedAt`,`actualDurationSeconds`; 종료시각은 시작시각 이상. 204 본문 없음. 같은 종료값 재전송은 멱등. 400 `INVALID_REQUEST`: 다른 값으로 이미 종료됐거나 입력 오류. 404 `TRIP_NOT_FOUND`: ID 없음.

## GET /api/trips/{tripId}

Edge. 인증 없음. Path `tripId` 필수 string(형식 검사 없음). Body/query 없음. 200: 필수 string `tripId`,`routeId`; `clientTripId`는 신규 Trip에서 UUID string, migration 전 Trip에서 null; 필수 integer `startedAt`,`ourEtaSeconds`,`pointCount`; 종료 전 null인 integer `finishedAt`,`actualDurationSeconds`; 미입력 시 null인 integer `tmapEtaSeconds`,`tmapDistanceMeters`; 필수 Coordinate `origin`,`destination`; 필수 GPS point 배열 `points`(timestamp, 내부 ID 순). 기존 migration 전 GPS는 `pointId=null`로 조회된다. 404 `TRIP_NOT_FOUND`: ID 없음.

## GET /api/trips

Edge. 인증 없음. Path/query/body 없음. 200: 최근 50개 TripResponse 배열(최신 생성순). 각 항목의 `points=[]`, `pointCount`는 실제 건수. 빈 DB는 `[]`. 정의된 애플리케이션 오류 없음.

## POST /internal/routes

Engine 사설망 API. 애플리케이션 인증 없음. Path/query 없음. 공개 `/api/routes`와 동일한 request/200 response. 400 `INVALID_REQUEST`: 입력 오류; 422 `ROUTE_NOT_FOUND`: 도달 불가 또는 좌표에서 최근접 노드가 1km 초과. Edge는 422를 공개 `ROUTE_NOT_FOUND`로 전달하고 Engine 장애만 503으로 변환한다.

## 외부 연동

- [경기 전체 소통정보](https://openapigits.gg.go.kr/api/jsp/manual_getRoadTrafficInfoList.jsp): `GET /api/rest/getRoadTrafficInfoList?serviceKey=...`, XML `headerCd=0`,`linkId`,`spd`. Engine connect 2초/request 3초, 성공 시 60초 캐시. 실패 시 이전 스냅샷 또는 기본 속도. 키는 응답/로그에 출력하지 않음.

## 필드 계약표

아래의 필수 응답 필드는 별도 표시가 없으면 null 불가다. `Coordinate`는 `lat`(number, 필수, null 불가, -90..90)와 `lng`(number, 필수, null 불가, -180..180)로 구성된다. 요청 본문이 있는 모든 endpoint에서 동일하게 검증한다. 기존 DB에서 읽은 이전 기록의 일부 신규 필드는 null일 수 있다.

| 형태 | 필드 | 타입 | 필수 | null | 설명 |
| --- | --- | --- | --- | --- | --- |
| Engine health | `status` | string | 예 | 불가 | `UP` |
| Engine health | `graphLoaded` | boolean | 예 | 불가 | Graph 초기화 완료 |
| Engine health | `nodeCount`, `edgeCount` | integer | 예 | 불가 | 메모리 graph 크기 |
| Engine health | `trafficProvider`, `algorithmVersion` | string | 예 | 불가 | 설정값 |
| RouteRequest | `origin`, `destination` | Coordinate | 예 | 불가 | 출발·도착 |
| RouteRequest | `algorithm` | string enum | 예 | 불가 | `BASELINE`, `DIRECTION_AWARE` |
| RouteResponse | `routeId` | string | 예 | 불가 | 생성된 UUID |
| RouteResponse | `algorithm` | string enum | 예 | 기존 저장 route만 가능 | `BASELINE`, `DIRECTION_AWARE` |
| RouteResponse | `algorithmVersion` | string | 예 | 기존 저장 route만 가능 | Engine 계산 버전 |
| RouteResponse | `distanceMeters` | integer | 예 | 불가 | 전체 거리 m |
| RouteResponse | `durationSeconds` | integer | 예 | 불가 | 유효 통행시간 합계 초 |
| RouteResponse | `geometry` | Coordinate[] | 예 | 불가 | 이어 붙인 도로 좌표 |
| RouteResponse | `segments` | RouteSegment[] | 예 | 불가 | 통과한 edge 순서 |
| RouteSegment | `edgeId` | string | 예 | 불가 | 방향별 edge ID |
| RouteSegment | `observedSpeed` | number | 예 | 불가 | 관측 속도 km/h; 미관측은 기본 속도 |
| RouteSegment | `baseSpeed` | number | 예 | 불가 | 기본 속도 km/h |
| RouteSegment | `attribution` | number | 예 | 불가 | 귀속 비율 0..1, BASELINE은 1 |
| RouteSegment | `effectiveSpeed` | number | 예 | 불가 | 유효 통행시간에서 역산한 km/h |
| StartTripRequest | `clientTripId` | UUID string | 예 | 불가 | Frontend에서 만든 안정적인 재시도 키 |
| StartTripRequest | `routeId` | string | 예 | 불가 | 빈 문자열 아닌 경로 ID |
| StartTripRequest | `startedAt` | integer | 예 | 불가 | 0 이상 시각 |
| StartTripRequest | `origin`, `destination` | Coordinate | 예 | 불가 | 출발·도착 |
| StartTripRequest | `ourEtaSeconds` | integer | 예 | 불가 | 0 이상 자체 ETA |
| StartTripRequest | `tmapEtaSeconds` | integer | 아니오 | 가능 | 수동 benchmark ETA, 0 이상 초 |
| StartTripRequest | `tmapDistanceMeters` | integer | 아니오 | 가능 | 수동 benchmark 거리, 0 이상 m |
| StartTripResponse | `tripId`, `clientTripId` | string | 예 | 불가 | 서버·Frontend UUID |
| GpsBatchRequest | `points` | GpsPoint[] | 예 | 불가 | 1..1000개 |
| GpsPoint | `pointId` | UUID string | 예 | 불가 | Frontend에서 만든 안정적인 재시도 키; migration 전 조회값은 null |
| GpsPoint | `timestamp` | integer | 예 | 불가 | Unix epoch milliseconds, 0 이상 |
| GpsPoint | `lat`, `lng` | number | 예 | 불가 | Coordinate와 동일한 범위 |
| GpsPoint | `gpsSpeed` | number | 아니오 | 가능 | 0 이상 m/s, 서버 단위 변환 없음 |
| GpsPoint | `heading` | number | 아니오 | 가능 | 0..360도 |
| GpsPoint | `accuracy` | number | 아니오 | 가능 | 0 이상 m |
| GpsBatchResponse | `pointsReceived` | integer | 예 | 불가 | 요청 point 개수 |
| GpsBatchResponse | `pointsStored` | integer | 예 | 불가 | 신규 저장 개수 |
| FinishTripRequest | `finishedAt` | integer | 예 | 불가 | 0 이상, 시작 시각 이상 |
| FinishTripRequest | `actualDurationSeconds` | integer | 예 | 불가 | 0 이상 |
| TripResponse | `tripId`, `routeId` | string | 예 | 불가 | 주행·경로 ID |
| TripResponse | `clientTripId` | string | 예 | 가능 | 신규 UUID; migration 전 Trip은 null |
| TripResponse | `startedAt` | integer | 예 | 불가 | 시작 시각 |
| TripResponse | `finishedAt` | integer | 예 | 가능 | 종료 전 null |
| TripResponse | `origin`, `destination` | Coordinate | 예 | 불가 | 출발·도착 |
| TripResponse | `ourEtaSeconds` | integer | 예 | 불가 | 자체 ETA |
| TripResponse | `tmapEtaSeconds` | integer | 예 | 가능 | 수동 TMAP ETA; 미입력 시 null |
| TripResponse | `tmapDistanceMeters` | integer | 예 | 가능 | 수동 TMAP 거리; 미입력 시 null |
| TripResponse | `actualDurationSeconds` | integer | 예 | 가능 | 종료 전 null |
| TripResponse | `pointCount` | integer | 예 | 불가 | 고유 GPS 개수 |
| TripResponse | `points` | GpsPoint[] | 예 | 불가 | 상세는 시각 순, 목록은 빈 배열 |

같은 노드로 스냅된 출발·도착은 `segments=[]`, `geometry` 한 점, 거리·시간 0이 가능하다. 내부적으로 유지하는 edge 비용(`baseSeconds`, `observedSeconds`, `excessSeconds`, `effectiveSeconds`)은 응답 필드가 아니다. 오류의 `error`는 필수·null 불가 string이며 오류 코드는 각 endpoint 설명에 적었다.
