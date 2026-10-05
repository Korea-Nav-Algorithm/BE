# API specification

운영 Nginx의 IP별 요청 제한에 걸리면 429가 발생할 수 있으며 JSON 본문은 보장되지 않는다.

계정 로그인은 없다. Edge는 Pi HTTPS Nginx의 동일 origin에서 접근하고 Engine은 LAN/VPN 내부에서만 접근한다. Trip별 GPS 데이터는 기기에서 만든 256비트 `accessKey`로 보호한다. 모든 POST 요청은 JSON이다. 204에는 본문이 없고 413은 서블릿 오류 응답이므로 JSON이 보장되지 않는다. 좌표는 WGS84 `lat`(-90..90), `lng`(-180..180) 필수 유한 숫자다. 처리된 API 오류 본문은 `{"error":"CODE"}`이며 현재 `message` 필드는 없다. 아래 error code 설명이 사용자용 메시지를 대신한다. 장소 검색 외에는 별도 query parameter가 없다. `startedAt`, `finishedAt`, GPS `timestamp`는 Unix epoch milliseconds 정수다. `gpsSpeed`는 m/s, `accuracy`는 m, `heading`은 도(0..360)다.

## GET /health

Engine 응답에는 OSM via-node 제한 수 `turnRestrictionCount`(integer)도 포함한다.

Edge·Engine 공통. 인증 없음. Path/query/body 없음. 200: 필수 string, null 불가 `status="UP"`. Engine은 `graphLoaded` boolean, `nodeCount`/`edgeCount` integer, `trafficProvider`/`algorithmVersion` string, 현재 snapshot의 `trafficSource` string(`JSON|GYEONGGI|UNKNOWN`), `trafficObservedEdges` integer, `trafficReady` boolean도 반환한다. `trafficReady`는 최근 경기 snapshot에 매핑된 edge가 있을 때만 true이며 개별 경로의 적용 여부는 route `trafficSource`와 segment 상태를 확인한다. Edge health는 연결 상태를 검사하지 않는다. 정의된 애플리케이션 오류 없음.

## GET /api/places

Edge 공개 API. 인증 없음. Path/body 없음. 필수 query `query`는 trim 후 2..80자 string이다. 200: `places`(0..5개 배열)와 `source=JSON|NAVER`를 반환한다. 각 place는 필수 string `id`,`name`,`address`(주소가 없으면 빈 문자열)와 필수 WGS84 Coordinate `coordinate`를 가진다. 결과를 선택하면 Frontend가 좌표로 기존 `/api/routes`를 호출한다. 400 `INVALID_REQUEST`: 누락·짧거나 긴 검색어. 503 `PLACE_SEARCH_UNAVAILABLE`: 제공자 인증 정보 없음·응답 오류·시간 초과·개발 JSON 파일 오류. 기본 `PLACE_SEARCH_PROVIDER=json`은 한정된 개발 장소 목록이며 일반 건물 검색이 아니다. `naver`는 Edge 전용 Search API Client ID·Secret을 헤더로 전송하며 connect 2초/전체 3초 timeout이다. Naver의 WGS84 정수 좌표는 1e7로 나눠 반환한다. 검색 결과는 OSM graph 범위로 제한하지 않으며 범위 밖 좌표의 route 요청은 422가 될 수 있다. 인증 정보와 upstream 오류 본문은 사용자에게 전달하지 않는다.

## POST /api/routes

Edge 공개 API. 인증 없음. Path/query 없음. Request: 필수 객체 `origin`,`destination`(Coordinate), 필수 enum `algorithm=BASELINE|DIRECTION_AWARE`. 200: 필수 string UUID `routeId`, 필수 enum `algorithm`, 필수 string `algorithmVersion`, 필수 integer `distanceMeters`,`durationSeconds`, 필수 Coordinate 배열 `geometry`, 필수 `segments` 배열과 `instructions` 배열, 필수 string `trafficSource=JSON|GYEONGGI|UNKNOWN`. 각 segment는 필수 string `edgeId`, 필수 number `observedSpeed`,`baseSpeed`,`attribution`(0..1),`effectiveSpeed`, 필수 `trafficLevel=UNKNOWN|FREE|SLOW|CONGESTED`, 필수 0 기반 integer `geometryStartIndex`,`geometryEndIndex`(`start < end`, geometry 범위 안). `BASELINE` attribution은 1. 각 instruction은 `type`, `geometryIndex`, `distanceFromStartMeters`, `roadName`, `coordinate`를 포함한다. 400 `INVALID_REQUEST`: 필드/범위/enum 오류. 422 `ROUTE_NOT_FOUND`: 최근접 노드가 1km 넘거나 도달 불가. 503 `ENGINE_UNAVAILABLE`: 연결·응답 오류. 성공 시 Edge SQLite에 응답 보존. 이전 route snapshot의 geometry index는 null, trafficLevel/trafficSource는 UNKNOWN일 수 있다.

## GET /api/routes/{routeId}

Edge. 인증 없음. Path `routeId` 필수 string(생성된 ID는 UUID이나 경로 변수 형식은 검사하지 않음). Body/query 없음. 200: `/api/routes`와 같은 저장 응답. 404 `ROUTE_NOT_FOUND`: ID 없음.

## POST /api/trips

Edge. 계정 로그인 없음. Path/query 없음. Request: 필수 UUID string `clientTripId`, 필수 43자 Base64url string `accessKey`(기기 생성 256비트), 필수 nonblank string `routeId`(존재 여부 검사 없음), 필수 0 이상 integer `startedAt`, 필수 Coordinate `origin`,`destination`, 필수 0 이상 integer `ourEtaSeconds`, 선택 0 이상 integer/null `tmapEtaSeconds`, `tmapDistanceMeters`. 두 TMAP 값은 **별도 공식 앱에서 사람이 확인한 수동 benchmark**이며 서버가 TMAP API를 호출하지 않는다. 201: 새 Trip 생성. 동일 `clientTripId`/동일 metadata·key 재전송은 200. 두 응답 모두 필수 UUID string `tripId` 및 `clientTripId`를 반환한다. 같은 client ID로 다른 값이 오면 400 `INVALID_REQUEST`; key 불일치는 404 `TRIP_NOT_FOUND`; 잘못된 UUID·필드·음수도 400이다. 서버는 key의 SHA-256 해시만 저장하며 응답에 key를 넣지 않는다.

## POST /api/trips/{tripId}/points

Edge. `X-Trip-Key` 헤더 필수. Path `tripId` 필수 string(형식 검사 없음). Query 없음. Request: 필수 `points` 배열(1..1000). 각 point: 필수 UUID string `pointId`, 필수 0 이상 integer `timestamp`, 필수 number `lat`,`lng`, 선택 0..360 number/null `heading`, 선택 0 이상 number/null `gpsSpeed`(m/s, 변환 없이 저장), 선택 0 이상 number/null `accuracy`(m). 200: 필수 integer `pointsReceived`,`pointsStored`. `(tripId,pointId)` 중복은 기존 point를 유지하며 저장 건수 0. 서로 다른 point ID는 timestamp가 같아도 각각 저장된다. 400 `INVALID_REQUEST`: 배열 크기/point 필드 오류. 404 `TRIP_NOT_FOUND`: ID 없음 또는 key 불일치. 413: body 256 KiB 초과, JSON 본문 보장 없음. 한 batch는 transaction으로 저장되고 종료 후 지연 batch도 수락한다.

## POST /api/trips/{tripId}/finish

Edge. `X-Trip-Key` 헤더 필수. Path `tripId` 필수 string(형식 검사 없음). Query 없음. Request: 필수 0 이상 integer `finishedAt`,`actualDurationSeconds`; 종료시각은 시작시각 이상. 204 본문 없음. 같은 종료값 재전송은 멱등. 400 `INVALID_REQUEST`: 다른 값으로 이미 종료됐거나 입력 오류. 404 `TRIP_NOT_FOUND`: ID 없음 또는 key 불일치.

## POST /api/trips/{tripId}/routes

Edge. 주행 중 재탐색으로 받은 route ID를 Trip에 연결한다. `X-Trip-Key` 헤더 필수. Path `tripId` 필수 string, query 없음. Request: 필수 nonblank string `routeId`, 필수 0 이상 integer `occurredAt`(Unix epoch milliseconds, Trip 시작 시각 이상). 최초 저장과 동일 route ID 재전송 모두 204 본문 없음. `(tripId,routeId)`가 중복 기준이고 최초 `occurredAt`을 유지한다. 400 `INVALID_REQUEST`: 입력 오류·시작 이전 시각. 404 `TRIP_NOT_FOUND`: Trip 없음 또는 key 불일치. 초기 경로는 Trip의 `routeId` 필드에 있으며 이 endpoint는 재탐색 경로만 보낸다.

## GET /api/trips/{tripId}

응답 `reroutes`는 `{routeId,occurredAt}` 배열이며 `occurredAt`/route ID 순으로 정렬한다. 초기 경로는 별도 `routeId`에 남는다.

Edge. `X-Trip-Key` 헤더 필수. Path `tripId` 필수 string(형식 검사 없음). Body/query 없음. 200: 필수 string `tripId`,`routeId`; `clientTripId`는 신규 Trip에서 UUID string, migration 전 Trip에서 null; 필수 integer `startedAt`,`ourEtaSeconds`,`pointCount`; 종료 전 null인 integer `finishedAt`,`actualDurationSeconds`; 미입력 시 null인 integer `tmapEtaSeconds`,`tmapDistanceMeters`; 필수 Coordinate `origin`,`destination`; 필수 GPS point 배열 `points`(timestamp, 내부 ID 순). 기존 migration 전 GPS는 `pointId=null`로 조회된다. 404 `TRIP_NOT_FOUND`: ID 없음 또는 key 불일치. 이전 DB 기록에서 access key가 없으면 API 조회가 잠기며 서버 운영자가 DB에서 확인해야 한다.

## GET /api/trips

Edge. `X-Trip-Admin-Key` 헤더 필수이며 Edge의 `TRIP_ADMIN_KEY` 환경변수가 32자 이상으로 설정돼 있어야 한다. Path/query/body 없음. 200: 최근 50개 TripResponse 배열(최신 생성순). 각 항목의 `points=[]`, `pointCount`는 실제 건수. 빈 DB는 `[]`. 설정이 없거나 key 불일치는 403 `ACCESS_DENIED`이다. 일반 사용자 Frontend는 목록 API를 호출하지 않는다.

## POST /internal/routes

Engine 사설망 API. 애플리케이션 인증 없음. Path/query 없음. 공개 `/api/routes`와 동일한 request/200 response. 400 `INVALID_REQUEST`: 입력 오류; 422 `ROUTE_NOT_FOUND`: 도달 불가 또는 좌표에서 최근접 노드가 1km 초과. Edge는 422를 공개 `ROUTE_NOT_FOUND`로 전달하고 Engine 장애만 503으로 변환한다.

## 외부 연동

- [경기 전체 소통정보](https://openapigits.gg.go.kr/api/jsp/manual_getRoadTrafficInfoList.jsp): `GET /api/rest/getRoadTrafficInfoList?serviceKey=...`, XML `headerCd=0`,`linkId`,`spd`,`collDate`. Engine connect 2초/request 8초, 백그라운드에서 최소 60초 간격으로 갱신한다. `GG_TRAFFIC_ROUTE_IDS`가 설정되면 쉼표로 구분한 도로 ID마다 `routeId` query를 넣어 조회하고, 비어 있으면 전체 feed를 조회한다. 유효한 수집 시각(15분 이내)과 양수 속도만 방향별 OSM edge에 매핑하고 마지막 정상 snapshot은 최대 5분 보존한다. 갱신 실패·매핑 실패 시 관측값 없는 edge는 계획 속도 추정치로 계산한다. `TRAFFIC_LINK_GEOMETRY_FILE`의 방향별 표준 링크 도형 또는 `TRAFFIC_MAPPING_FILE`의 수동 ID 매핑이 없으면 실제 OSM graph에 속도를 적용할 수 없다. 키는 응답/로그에 출력하지 않는다.

## 필드 계약표

아래의 필수 응답 필드는 별도 표시가 없으면 null 불가다. `Coordinate`는 `lat`(number, 필수, null 불가, -90..90)와 `lng`(number, 필수, null 불가, -180..180)로 구성된다. 요청 본문이 있는 모든 endpoint에서 동일하게 검증한다. 기존 DB에서 읽은 이전 기록의 일부 신규 필드는 null일 수 있다.

| 형태 | 필드 | 타입 | 필수 | null | 설명 |
| --- | --- | --- | --- | --- | --- |
| Engine health | `status` | string | 예 | 불가 | `UP` |
| Engine health | `graphLoaded` | boolean | 예 | 불가 | Graph 초기화 완료 |
| Engine health | `nodeCount`, `edgeCount` | integer | 예 | 불가 | 메모리 graph 크기 |
| Engine health | `turnRestrictionCount` | integer | 예 | 불가 | 적용 가능한 OSM via-node 제한 수 |
| Engine health | `trafficProvider`, `algorithmVersion` | string | 예 | 불가 | 설정값 |
| Engine health | `trafficSource` | string enum | 예 | 불가 | `JSON`, `GYEONGGI`, `UNKNOWN` |
| Engine health | `trafficObservedEdges` | integer | 예 | 불가 | 현재 snapshot에 매핑된 방향별 edge 수 |
| Engine health | `trafficReady` | boolean | 예 | 불가 | 유효한 경기 snapshot에 매핑된 edge가 있음 |
| PlaceSearchResponse | `places` | PlaceResult[] | 예 | 불가 | 최대 5건, 결과 없음은 빈 배열 |
| PlaceSearchResponse | `source` | string enum | 예 | 불가 | `JSON` 개발 목록, `NAVER` 장소 검색 |
| PlaceResult | `id`, `name`, `address` | string | 예 | 불가 | 제공자 장소 ID·표시명·주소; 주소 미제공은 빈 문자열 |
| PlaceResult | `coordinate` | Coordinate | 예 | 불가 | 목적지 WGS84 좌표 |
| RouteRequest | `origin`, `destination` | Coordinate | 예 | 불가 | 출발·도착 |
| RouteRequest | `algorithm` | string enum | 예 | 불가 | `BASELINE`, `DIRECTION_AWARE` |
| RouteResponse | `routeId` | string | 예 | 불가 | 생성된 UUID |
| RouteResponse | `algorithm` | string enum | 예 | 기존 저장 route만 가능 | `BASELINE`, `DIRECTION_AWARE` |
| RouteResponse | `algorithmVersion` | string | 예 | 기존 저장 route만 가능 | Engine 계산 버전 |
| RouteResponse | `distanceMeters` | integer | 예 | 불가 | 전체 거리 m |
| RouteResponse | `durationSeconds` | integer | 예 | 불가 | 유효 통행시간 합계 초 |
| RouteResponse | `geometry` | Coordinate[] | 예 | 불가 | 이어 붙인 도로 좌표 |
| RouteResponse | `segments` | RouteSegment[] | 예 | 불가 | 통과한 edge 순서 |
| RouteResponse | `instructions` | RouteInstruction[] | 예 | 불가 | 기하와 도로명에서 생성한 시각 안내. 이전 snapshot은 빈 배열 |
| RouteResponse | `trafficSource` | string enum | 예 | 불가 | 경로에 관측 속도 edge가 있을 때 `JSON` 또는 `GYEONGGI`; 없거나 이전 snapshot이면 `UNKNOWN` |
| RouteInstruction | `type` | string enum | 예 | 불가 | `START`, `CONTINUE`, `SLIGHT_LEFT`, `TURN_LEFT`, `SLIGHT_RIGHT`, `TURN_RIGHT`, `U_TURN`, `ARRIVE` |
| RouteInstruction | `geometryIndex` | integer | 예 | 불가 | `geometry` 배열에서 안내 지점의 0 기반 인덱스 |
| RouteInstruction | `distanceFromStartMeters` | integer | 예 | 불가 | 출발점부터 안내 지점까지 경로 거리 m |
| RouteInstruction | `roadName` | string | 예 | 불가 | 다음 도로명. OSM 이름이 없으면 빈 문자열 |
| RouteInstruction | `coordinate` | Coordinate | 예 | 불가 | 안내 지점 좌표 |
| RouteSegment | `edgeId` | string | 예 | 불가 | 방향별 edge ID |
| RouteSegment | `observedSpeed` | number | 예 | 불가 | 관측 속도 km/h; 미관측은 기본 속도 |
| RouteSegment | `baseSpeed` | number | 예 | 불가 | 기본 속도 km/h |
| RouteSegment | `attribution` | number | 예 | 불가 | 귀속 비율 0..1, BASELINE은 1 |
| RouteSegment | `effectiveSpeed` | number | 예 | 불가 | 유효 통행시간에서 역산한 km/h |
| RouteSegment | `trafficLevel` | string enum | 예 | 불가 | 관측 속도/기준 속도 비율로 `CONGESTED` ≤0.4, `SLOW` ≤0.7, 그 외 `FREE`; 미관측 또는 이전 snapshot은 `UNKNOWN` |
| RouteSegment | `geometryStartIndex`, `geometryEndIndex` | integer | 예 | 이전 snapshot만 가능 | 해당 edge가 차지하는 `geometry` 시작·끝 인덱스(양 끝 포함) |
| StartTripRequest | `clientTripId` | UUID string | 예 | 불가 | Frontend에서 만든 안정적인 재시도 키 |
| StartTripRequest | `accessKey` | Base64url string | 예 | 불가 | 기기에서 생성한 32바이트 비밀키의 패딩 없는 43자 인코딩. 서버에는 SHA-256만 저장 |
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
| RerouteRequest | `routeId` | string | 예 | 불가 | 재탐색으로 생성된 route ID |
| RerouteRequest | `occurredAt` | integer | 예 | 불가 | 최초 재탐색 시각, Unix epoch milliseconds |
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
| TripResponse | `reroutes` | TripReroute[] | 예 | 불가 | 재탐색 route ID·시각 배열, 시각 순 |
| TripReroute | `routeId` | string | 예 | 불가 | 저장된 재탐색 route ID |
| TripReroute | `occurredAt` | integer | 예 | 불가 | 최초 전송한 시각 |

같은 노드로 스냅된 출발·도착은 `segments=[]`, `geometry` 한 점, 거리·시간 0이 가능하다. 내부적으로 유지하는 edge 비용(`baseSeconds`, `observedSeconds`, `excessSeconds`, `effectiveSeconds`)은 응답 필드가 아니다. 오류의 `error`는 필수·null 불가 string이며 오류 코드는 각 endpoint 설명에 적었다.

매핑된 교통 속도가 없는 edge는 `observedSpeed`에 기존 계약대로 기준 속도를 표시하지만 `trafficLevel=UNKNOWN`으로 구분한다. 이 edge의 비용과 ETA는 `baseSeconds / ETA_UNOBSERVED_SPEED_FACTOR`(기본 0.75)를 사용한다. 관측값이 있는 edge의 기존 direction-aware 비용식은 유지한다. 이 계수는 신호·교차로 지연 등을 거칠게 반영하는 **검증 전 계획값**이며 TMAP ETA와 정확히 일치한다는 보장이 없다. JSON provider는 개발용 데이터이며 실시간 소통 상태가 아니다.

`instructions`는 선택 경로의 연속 edge 방향과 도로명으로 생성하는 화면 안내다. OSM via-node `no_*`/`only_*` 제한을 경로 탐색에 적용하지만 via-way·조건부 제한, 차로·신호 정보는 반영하지 않는다. 현장 검증 전에는 운전 지시로 사용하지 않는다. 동일 노드 zero-distance route에는 `ARRIVE` 하나가 포함된다.
