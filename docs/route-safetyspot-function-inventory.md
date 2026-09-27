# route·safetyspot 서비스 함수 목록 — 추상화 레벨 검토용

route 모듈(`core/core-route`)과 안심시설 모듈(`storage/db-core/.../safetyspot`)의 서비스 영역 클래스와 함수를 파일 단위로 정리한 목록이다.

각 함수가 어떤 동작 유형인지 표시해서, 한 클래스 안에 서로 다른 유형이 섞여 있는지(추상화 레벨이 맞는지)를 검토하는 데 쓴다.

작성 기준 시점: 2026-09-26, 브랜치 `claude/safe-route-share-map-auf1qy`.

## 동작 유형 분류

CRUD 처럼 함수 하나가 한 유형만 갖는 것을 목표로 한다. 이 문서에서 쓰는 유형은 아래 일곱 가지다.

| 유형 | 뜻 | 예 |
|---|---|---|
| 조회 | 갖고 있는 것을 조건에 맞게 꺼낸다 | `index(type)`, `findByType` |
| 선별 | 여러 후보 중 조건에 맞는 것만 남기거나 고른다 | `pickWaypoints`, `withinCorridor` |
| 계산 | 입력으로부터 수치·판정 결과를 만든다 | `score`, `isNight` |
| 변환 | 한 형태의 데이터를 다른 형태로 바꾼다 (내용은 그대로) | `toGeoPoints`, `toItemDto` |
| 조립 | 여러 재료를 모아 응답·모델 하나로 만든다 | `assemble`, `buildMarkers` |
| 저장 | 상태를 바꾼다 (파일 쓰기, 인덱스 교체) | `save`, `replace`, `append` |
| 흐름 | 다른 컴포넌트를 순서대로 불러 유스케이스를 완성한다 (자기 판단 없음) | `findRouteOptions` |

한 함수에 두 유형이 겹쳐 있으면 쪼갤 후보다. 한 클래스의 공개 함수들이 서로 다른 유형이면 클래스를 나눌 후보다.

---

## 1. route 모듈 — domain/service

### SafeRouteService (`domain/service/SafeRouteService.kt`)

경로 비교 유스케이스의 흐름 조립. 도메인 서비스 계층.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `findRouteOptions(query)` | 공개 | 흐름 | 기본 경로 검색 → 우회 후보 추가 → 지역 적재 알림 → 중복 제거 → 유형 배정 → 응답 조립을 순서대로 부른다 |

원래 이름 `safeCompare` 는 비교(내부 단계)를 말하고 반환(경로 옵션 목록)을 말하지 못해 `findRouteOptions` 로 리네임했다 (2026-09-26).
엔드포인트도 `POST /api/routes/safe-compare` 에서 `POST /api/routes` 로 바꿨다 — 모바일이 같은 저장소라 동시 배포되므로 옛 경로는 남기지 않았다.

자기 판단은 "reroute 면 우회 생략" 분기 하나뿐이고 나머지는 협력자 호출이다. 유형이 하나로 통일되어 있다.

### RouteFinder (`domain/service/RouteFinder.kt`)

Tmap 에서 경로를 받아 오는 조회 창구.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `findRoutes(query)` | 공개 | 조회 | 검색 옵션 3종(추천·대로·최단)으로 Tmap 경로를 받아 모은다 |
| `findSaferRoutes(query, routes, waypointPlans)` | 공개 | 조회 + 선별 | 경유지 계획마다 우회 경로를 받고, 거리 한도(선호별 +20~60%)를 넘는 것은 버린다 |
| `maxDetourDistanceM(query, routes)` | 비공개 | 계산 | 우회 허용 한도 — 최단거리의 +20%(균형)·+35%(안전)·+60%(최대안전) |
| `fetchRoute(query, searchOption, waypoints)` | 비공개 | 조회 | Tmap 클라이언트 호출 한 번을 감싼다 (좌표 변환 포함) |
| `RouteRequest.endName()` | 파일 내 | 변환 | 빈 목적지 이름을 "목적지" 로 채운다 |
| `List<TmapCoord>.toGeoPoints()` | internal | 변환 | Tmap 좌표 목록 → GeoPoint 목록 |
| `GeoPoint.toTmapCoord()` | 파일 내 | 변환 | GeoPoint → Tmap 좌표 |

`findSaferRoutes` 는 조회(우회 경로 받기)와 선별(거리 한도 거르기)이 한 함수에 있다. 이름은 조회(find)만 말한다.

### RouteCandidateMerger (`domain/service/RouteCandidateMerger.kt`)

Tmap 이 중복해서 주는 비슷한 경로를 하나로 뭉친다.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `merge(routes)` | 공개 | 선별 + 변환 | 같은 경로(identityKey)·거의 같은 경로(90% 겹침)를 버리고 후보 모델로 바꿔 낸다 (원래 이름 `toCandidates` 에서 리네임) |
| `mergeNearDuplicates(candidates)` | 공개 | 선별 | 경로가 90% 이상 겹치는 후보를 버린다 |
| `isNearDuplicate(first, second)` | 비공개 | 계산 | 두 경로의 겹침 비율이 기준 이상인지 판정한다 |

`merge` 는 원래 이름이 `toCandidates`(변환)였는데 실제는 두 단계 중복 제거가 주제라, 클래스 이름과 같은 `merge` 로 리네임했다 (2026-09-26).

### RouteTypeAssigner (`domain/service/RouteTypeAssigner.kt`)

후보마다 안전 점수를 매기고 앱 표시 유형(안심·빠름·기본)마다 경로 하나씩 고른다.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `assignOneRoutePerType(candidates)` | internal | 계산 + 선별 | 점수 계산(scorer 호출) 후 유형별 고르기를 `pickIndexPerType` 에 맡긴다 |
| `pickIndexPerType(scored)` | 비공개 | 선별 | 안심=점수 최고 / 빠름=시간 최단 / 기본=남은 것 중 점수 최고 |

계산은 SafetyScorer 에 맡기고 있어서 실질은 선별이다. "점수 매기기"와 "유형별 고르기"가 한 본문에 있어 고르기를 `pickIndexPerType` 으로 분리했다 (2026-09-26).

### RouteTypeCatalog (`domain/service/RouteTypeCatalog.kt`)

유형별 표시 문구(이름·설명·태그)의 고정 사전.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `displayOf(type)` | 공개 | 조회 | RouteType → 표시 정보 lookup |

유형이 하나다.

### RouteOptionAssembler (`domain/service/RouteOptionAssembler.kt`)

점수 매긴 경로들을 앱 응답 모델(RouteOption)로 조립한다.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `assemble(scoredRoutes, origin, destination)` | internal | 조립 | 점수순 정렬 후 RouteOption 목록을 만든다 (마커·태그·출처 포함) |
| `describe(score, breakdown)` | 비공개 | 변환 | 점수 → 사용자 문구 ("안전 점수 N점 · CCTV n/km") |
| `buildTags(scoredRoute)` | 비공개 | 조립 | 유형 고정 태그 + 밀도 기준을 넘긴 태그를 모은다 |
| `buildProvenance()` | 비공개 | 조립 | 시설 자료 신선도에서 데이터 출처 표기를 만든다 |

전부 조립 계열로 통일되어 있다.

---

## 2. route 모듈 — domain/safety/service

### SafetySpotWaypointPlanner (`domain/safety/service/SafetySpotWaypointPlanner.kt`)

안심시설 뭉치로 우회 경로의 경유지를 계획한다. 원래 이름은 `SafeWaypointFinder` 였는데
조회(find)가 아니라 뭉치 계산·조합 선별이 주제이고, 재료가 안심시설(SafetySpot)임을 이름에 담아 리네임했다 (2026-09-26).

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `clusterSafetySpots(routePath)` | 공개 | 계산 | 경로 주변 시설을 격자로 묶어 뭉치(중심점+시설 수)를 만들고, 많이 뭉친 순 최대 6개를 낸다 |
| `pickWaypoints(origin, destination, clusters)` | 공개 | 선별 + 조립 | 경로 중간(진행률 20~80%)이고 옆이탈이 작은 뭉치만 남긴 뒤, 단일 3 + 쌍 1 경유지 조합을 만든다 |

`clusterSafetySpots` 는 원래 이름이 `findSafetySpotClusters`(조회)였는데 실제는 corridor 조회 → 격자 묶기 → 평균 중심 계산 → 정렬·절단까지 하는 계산이라, 유형에 맞게 리네임했다 (2026-09-26).

`pickWaypoints` 는 거르기(선별)와 계획 만들기(조립) 두 단계가 있어, 본문을 `filterMiddleClusters` → `buildPlans` 비공개 둘로 나눴다 (2026-09-26). 호출부가 한 곳뿐이라 공개 함수는 하나로 유지했다.
경유지 묶음의 낱말은 `combo`(줄임말)에서 `plan`(계획)으로 통일했다 — 계획 하나가 우회 경로 요청 한 번이 된다.

### SafetyScorer (`domain/safety/service/SafetyScorer.kt`)

경로 하나의 안전 점수를 계산한다. 이 모듈에서 가장 큰 클래스(비공개 함수 12개).

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `coverageOf(paths)` | 공개 | 계산 | 경로 표본점 주변에 축(CCTV·조명 등)별 자료가 있는지 판정해 커버리지를 만든다 |
| `score(path)` | 공개 | 계산 | 커버리지를 스스로 구해 아래 score 를 부른다 (편의 오버로드) |
| `score(path, coverage)` | 공개 | 계산 | 시설 개수 → 축별 충족도 → 가중 평균 → 위험 감점 순으로 점수와 세부 내역을 낸다 |
| `hasDataNear(axis, scorePoints)` | 비공개 | 계산 | 한 축의 자료가 표본점 주변 1km 안에 있는지 |
| `hasSafePathNear(scorePoints)` | 비공개 | 계산 | 안심귀갓길 자료가 주변에 있는지 |
| `countNearby(path)` | 비공개 | 조회 | 종류별 corridor 내 시설 수를 센다 |
| `countWithinCorridor(type, path)` | 비공개 | 조회 | 한 종류의 corridor 내 시설 수 |
| `countRiskNear(path)` | 비공개 | 조회 | 범죄주의구간 수 (corridor 100m) |
| `safePathOverlapRatio(path, lengthM)` | 비공개 | 계산 | 안심귀갓길과 겹치는 비율 (최댓값 1개) |
| `fillsOf(counts, km, safePathOverlap)` | 비공개 | 계산 | 개수·밀도 → 축별 충족도(0~1) |
| `normalizedScore(fills, weights, coverage)` | 비공개 | 계산 | 자료 있는 축만 가중 평균 |
| `coveredWeight(weights, coverage)` | 비공개 | 계산 | 자료 있는 축의 가중치 합 |
| `spotTypesOf(axis)` | 비공개 | 조회 | 축 → 시설 종류 lookup |
| `applyRiskPenalty(normalized, riskCount, km)` | 비공개 | 계산 | 범죄주의구간 밀도만큼 감점 (최대 0.20) |
| `fill(value, halfPoint)` | 비공개 | 계산 | 포화 곡선 v/(v+half) |
| `saturate(value, ceiling)` | 비공개 | 계산 | 0~1 로 자르는 비율 |
| `round2(v)` / `round3(v)` | 비공개 | 계산 | 소수 자리 반올림 |

공개 함수는 계산으로 통일되어 있다. 다만 비공개 17개 > 공개 3개로, 점수 공식(fill·saturate·round)과
자료 존재 판정(hasDataNear 계열), 시설 세기(count 계열)라는 세 묶음이 한 클래스에 있다.

### SafetyWeightsCatalog (`domain/safety/service/SafetyWeightsCatalog.kt`)

시간대(주간·야간)에 따른 축별 가중치 사전.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `weightsAt(time)` | 공개 | 조회 | 밤이면 NIGHT, 낮이면 DAY 가중치 |
| `isNight(time)` | 공개 | 계산 | 서울 월별 일출·일몰표로 밤인지 판정 |

조회와 그 판정 재료가 같이 있다. `isNight` 는 `weightsAt` 만 쓰면 비공개 후보다 (외부 사용처 확인 필요).

---

## 3. route 모듈 — domain/marker/service

### RouteMarkerBuilder (`domain/marker/service/RouteMarkerBuilder.kt`)

미니맵에 찍을 마커(출발·도착 + 경로 주변 시설)를 만든다.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `buildMarkers(origin, destination, path)` | 공개 | 조립 | 출발·도착 마커 + 종류별 corridor 내 시설 마커를 모아 미니맵 좌표(%)로 바꿔 낸다 |
| `spreadAlongPath(alongPath, limit)` | 비공개 | 선별 | 시설이 한도(12개)를 넘으면 경로를 따라 고르게 뽑는다 |

조립 하나에 선별 보조 하나. 유형 구분이 명확하다.

---

## 4. safetyspot 모듈 — storage/db-core

### SafetySpotSpatialSource (`store/SafetySpotSpatialSource.kt`) — 인터페이스

읽기 창구. route 모듈이 보는 것은 이 인터페이스뿐이다.

| 멤버 | 유형 | 역할 |
|---|---|---|
| `index(type)` | 조회 | 종류별 공간 인덱스 |
| `safePaths` | 조회 | 안심귀갓길 목록 |
| `freshness` | 조회 | 자료 생성 시점·버전 |

### SafetySpotLiveStore (`store/SafetySpotLiveStore.kt`) — 인터페이스

쓰기 창구. 갱신 스케줄러·관리자 업로드가 쓴다.

| 멤버 | 유형 | 역할 |
|---|---|---|
| `replace(type, points)` | 저장 | 종류 전체를 원자적으로 교체 |
| `append(type, points)` | 저장 | 기존 인덱스에 원자적으로 추가 |

읽기(SpatialSource)와 쓰기(LiveStore)가 인터페이스 수준에서 분리되어 있다.

### SafetySpotRepository (`store/SafetySpotRepository.kt`)

두 인터페이스의 구현체. CDN JSON·CSV·지역 캐시·업로드분을 모아 메모리 인덱스를 만든다. 이 모듈에서 가장 큰 파일(451줄).

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `index(type)` | 공개(override) | 조회 | 종류별 인덱스, 없으면 빈 인덱스 |
| `append(type, points)` | 공개(override) | 저장 | 기존 점 + 새 점으로 인덱스 재생성 후 교체 |
| `replace(type, points)` | 공개(override) | 저장 | 새 점만으로 인덱스 재생성 후 교체 |
| `load()` | 공개(@PostConstruct) | 적재 | 부팅 시 CDN → CSV → 지역 캐시 → 업로드분 순으로 읽어 인덱스를 세운다 |
| `addRegionCache(byType)` | 비공개 | 적재 | 지역 캐시 파일을 종류별로 더한다 |
| `readSafetySpotFiles(dir, byType)` | 비공개 | 적재 | CDN 시설 JSON 을 읽는다 |
| `readSafePathFiles(dir, paths)` | 비공개 | 적재 | 안심귀갓길 JSON 을 읽는다 |
| `readFreshness(dir)` | 비공개 | 적재 | manifest.json 에서 신선도를 읽는다 |
| `overwriteWithUploads(byType)` | 비공개 | 적재 | 업로드분이 있으면 그 종류를 통째로 덮는다 |
| `logLoaded(...)` | 비공개 | 기록 | 적재 결과 로그 |
| `loadCctvCsv()` / `loadLampCsv()` / `loadLampCsvFile(file)` | 비공개 | 적재 | CSV 원천을 파싱해 점 목록으로 |
| `resolveCctvCsv()` / `resolveDataDir()` | 비공개 | 조회 | 후보 경로 중 실제 존재하는 파일·디렉터리 찾기 |
| `parseCollection(file)` | 비공개 | 변환 | JSON 파일 → DTO (실패 시 null) |
| `parseCctvCsvLine(line)` | 파일 내 internal | 변환 | CSV 한 줄 → 시설 점 |
| `LampCsvColumns.of(header)` / `parse(row)` | 파일 내 | 변환 | 보안등 CSV 헤더 해석과 행 파싱 |
| `openCsv(file)` / `detectCharset(file)` | 파일 내 | 적재 | gz 해제·인코딩(CP949/UTF-8) 감지 |

공개 3개(조회 1, 저장 2)는 인터페이스 계약 그대로다. 나머지 20여 개 비공개가 전부 부팅 적재의 하위 단계로,
"서빙(조회·저장)"과 "원천 파싱(CSV·JSON 읽기)"이라는 두 층이 한 파일에 있다.

### GridSpatialIndex (`store/GridSpatialIndex.kt`)

시설 점들을 격자에 담아 공간 질의에 답하는 불변 인덱스.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `withinRadius(center, radiusM)` | 공개 | 선별 | 중심점에서 반경 안 시설 |
| `withinCorridor(path, corridorM)` | 공개 | 선별 | 경로 양옆 띠 안 시설 |
| `fillPointsBetween(path)` | 비공개 | 계산 | 경로 점 사이가 격자 칸보다 넓으면 중간 점을 채운다 |
| `candidateCells(anchors, radiusM)` | 비공개 | 조회 | 기준점 주변 격자 칸의 시설을 후보로 모은다 |
| `cellIndex(value, cellDeg)` | 비공개 | 계산 | 좌표 → 칸 번호 |
| `cellKey(latCell, lngCell)` | 공개(companion) | 계산 | 칸 번호 둘 → Long 키 |

공개 질의 둘 다 선별로 통일. `cellKey` 만 공개인 이유는 SafetySpotWaypointPlanner 가 격자 묶기에 재사용하기 때문이다.

### SafetySpotUploadStore (`store/SafetySpotUploadStore.kt`)

관리자가 올린 시설 파일의 저장·조회.

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `save(type, points)` | 공개 | 저장 | 임시 파일 → 원자적 이동으로 종류별 JSON 저장 |
| `findByType(type)` | 공개 | 조회 | 저장된 파일을 점 목록으로 (없거나 깨지면 null) |
| `toItemDto()` / `cctvPropertiesOrNull()` | 파일 내 | 변환 | 점 → 저장용 DTO |

save/find 짝으로 유형이 명확하다.

### SafetySpotRegionCache (`store/SafetySpotRegionCache.kt`)

지역 단위로 받아 온 시설의 파일 캐시 (TTL 90일).

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `isCached(type, regionCode)` | 공개 | 계산 | 저장 시각이 TTL 안인지 |
| `savedAtOf(type, regionCode)` | 공개 | 조회 | 저장 시각 (메모리 → 파일 순) |
| `save(type, regionCode, points)` | 공개 | 저장 | 지역별 JSON 원자적 저장 |
| `loadAll(type)` | 공개 | 조회 | 한 종류의 모든 지역 캐시를 점 목록으로 |
| `cachedRegions(type)` | 공개 | 조회 | 캐시된 지역 코드 집합 |

조회 3 + 판정 1 + 저장 1. 캐시라는 역할 안에서 자연스러운 구성이다.

### SafetySpotRefreshStatusStore (`store/SafetySpotRefreshStatusStore.kt`)

갱신 작업의 마지막 실행 기록 (관리자 화면용).

| 함수 | 공개 | 유형 | 역할 |
|---|---|---|---|
| `record(results, atEpochMs)` | 공개 | 저장 | 스케줄러 갱신 결과를 종류별 병합 |
| `recordUpload(type, replacedCount, atEpochMs)` | 공개 | 저장 | 업로드 한 건 기록 |
| `findByType(type)` | 공개 | 조회 | 종류별 마지막 기록 |

저장 2 + 조회 1. 유형이 명확하다.

---

## 5. 유형이 겹치거나 이름과 어긋난 함수 — 검토 후보

위 표에서 유형이 둘 이상이거나, 이름이 말하는 유형과 실제가 다른 함수만 모았다. 고치라는 판정이 아니라 검토 입구다.

| 함수 | 무엇이 겹치나 |
|---|---|
| `RouteFinder.findSaferRoutes` | ~~조회 + 선별(거리 한도)~~ → 한도 계산을 `maxDetourDistanceM` 비공개로 분리. 한도 거르기 자체는 "쓸만한 우회 경로 조회"라는 주제의 일부라 함수는 유지 |
| `RouteCandidateMerger.toCandidates` | ~~변환 + 선별. 이름은 to(변환)만 말한다~~ → `merge` 로 리네임 — 클래스 이름(Merger)이 말하는 주제와 일치 |
| `RouteTypeAssigner.assignOneRoutePerType` | ~~점수 계산 위임 + 유형별 선별이 한 본문에~~ → 유형별 고르기를 `pickIndexPerType` 비공개로 분리 |
| `SafetySpotWaypointPlanner.clusterSafetySpots` | ~~이름은 find(조회)인데 실제는 묶기·평균·정렬 계산~~ → `clusterSafetySpots` 로 리네임 완료. 클래스도 Finder → SafetySpotWaypointPlanner 로 리네임 |
| `SafetySpotWaypointPlanner.pickWaypoints` | ~~선별 + 조합 조립 두 단계~~ → 비공개 `filterMiddleClusters`·`buildPlans` 로 분리 완료. combo 낱말은 plan 으로 통일 |
| `SafetyScorer` (클래스) | 비공개 17 > 공개 3 — 점수 공식·자료 존재 판정·시설 세기 세 묶음이 한 클래스. **판정: 보류** — 공개 함수 유형은 계산 하나로 통일돼 있고, 나눠도 쓰는 곳이 이 클래스뿐이라 지금은 얻는 게 없다. 세기(count 계열)를 다른 곳이 또 쓰게 되면 그때 분리 |
| `SafetySpotRepository` (클래스) | 서빙(조회·저장)과 원천 파싱(CSV·JSON)이 한 파일 (451줄). **판정: 보류** — 파싱은 전부 `load()` 의 하위 단계이고 부팅 때 한 번만 돈다. CSV 원천이 늘거나 파싱을 갱신 스케줄러도 쓰게 되면 그때 분리 |
| `SafetyWeightsCatalog.isNight` | 프로덕션 사용처는 `weightsAt` 뿐. **판정: 공개 유지** — 일출·일몰 경계 판정 자체가 테스트로 검증할 규칙이라, 비공개로 내리고 weightsAt 결과로 우회 검증하면 테스트가 더 흐려진다 |

반대로 유형이 하나로 깔끔한 것: SafeRouteService(흐름), RouteTypeCatalog(조회), RouteOptionAssembler(조립),
RouteMarkerBuilder(조립), GridSpatialIndex(선별), UploadStore·RegionCache·RefreshStatusStore(저장/조회 짝).
