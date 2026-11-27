# route, safetyspot 서비스 함수 목록 - 추상화 레벨 검토용

route 모듈(`core/core-route`)과 안심시설 저장 모듈(`storage/db-safety-spot`)의 서비스 영역 클래스와 함수를 파일 단위로
정리한 목록이다.

각 함수가 어떤 동작 유형인지 표시해서, 한 클래스 안에 서로 다른 유형이 섞여 있는지(추상화 레벨이 맞는지)를 검토하는 데
쓴다.

> 작성 기준 시점: 2026-11-25<br>
> 기준 브랜치: `feat/nearby-safety-spot-counts`

## 동작 유형 분류

CRUD처럼 함수 하나가 한 유형만 갖는 것을 목표로 한다. 이 문서에서 쓰는 유형은 아래 여덟 가지다.

| 유형 | 뜻                                                                  | 예                          |
|------|---------------------------------------------------------------------|-----------------------------|
| 조회 | 갖고 있는 것을 조건에 맞게 꺼낸다                                   | `index(type)`, `findByType` |
| 선별 | 여러 후보 중 조건에 맞는 것만 남기거나 고른다                       | `pick`, `withinCorridor`    |
| 계산 | 입력으로부터 수치나 판정 결과를 만든다                              | `score`, `isNight`          |
| 변환 | 한 형태의 데이터를 다른 형태로 바꾼다(내용은 그대로)                | `toGeoPoints`               |
| 조립 | 여러 재료를 모아 응답이나 모델 하나로 만든다                        | `assemble`, `build`         |
| 저장 | 상태를 바꾼다(파일 쓰기, 인덱스 교체)                               | `save`, `replaceRegion`     |
| 적재 | 부팅 때 디스크의 파일을 읽어 메모리 인덱스를 세운다                 | `load`, `readFiles`         |
| 흐름 | 다른 컴포넌트를 순서대로 불러 유스케이스를 완성한다(자기 판단 없음) | `RouteOptionService.find`   |

한 함수에 두 유형이 겹쳐 있으면 쪼갤 후보다. 한 클래스의 공개 함수들이 서로 다른 유형이면 클래스를 나눌 후보다.

## 1. route 모듈: domain/service

### RouteOptionService (`domain/service/RouteOptionService.kt`)

경로 비교 유스케이스(`POST /api/routes`)의 흐름 조립. 도메인 서비스 계층.

| 함수          | 공개 | 유형 | 역할                                                          |
|---------------|------|------|---------------------------------------------------------------|
| `find(route)` | 공개 | 흐름 | 아래 단계를 순서대로 부른다. 기본 경로가 없으면 예외를 던진다 |

`find`가 부르는 단계는 다음과 같다.

1. 기본 경로 검색(`RouteFinder.find`)
2. 재검색(`reroute`)이 아니면 시설 뭉치 계산, 경유지 고르기, 우회 경로 검색
3. 경로 계획 알림(`RoutePlannedHandler.onRoutePlanned`, 경로 주변 시설 채우기를 뒤에서 시작)
4. 중복 제거(`RouteCandidateMerger.merge`)
5. 유형 배정(`RouteTypeAssigner.assignOneRoutePerType`)
6. 응답 조립(`RouteOptionAssembler.assemble`)

이름 변경 이력은 다음과 같다.

- 원래 이름 `safeCompare`는 비교(내부 단계)를 말하고 반환(경로 옵션 목록)을 말하지 못해 `findRouteOptions`로 바꿨다
  (2026-09-26).
- 클래스 이름 `SafeRouteService`가 safe 하나만 돌려주는 것처럼 읽혀, 클래스를 `RouteOptionService`로 바꾸고 메서드는
  `find`로 줄였다(2026-11-21).
- 엔드포인트를 `POST /api/routes/safe-compare`에서 `POST /api/routes`로 바꿨다. 모바일이 동시에 배포되므로 옛 경로는
  남기지 않았다.
- 컨트롤러도 실제로 다루는 대상(경로 선택지)을 클래스 이름이 말하도록 `RouteController.findRouteOptions`를
  `RouteOptionController.find`로 바꿨다(2026-11-23). 경로 `POST /api/routes`는 그대로다.

자기 판단은 "기본 경로가 없으면 예외"와 "reroute면 우회 생략" 분기 둘뿐이고 나머지는 협력자 호출이다. 유형이 하나로
통일되어 있다.

### RouteFinder (`domain/service/RouteFinder.kt`)

Tmap에서 경로를 받아 오는 조회 창구.

| 함수                                         | 공개    | 유형        | 역할                                                              |
|----------------------------------------------|---------|-------------|-------------------------------------------------------------------|
| `find(query)`                                | 공개    | 조회        | 검색 옵션 3종(추천, 대로우선, 최단)으로 받은 경로를 후보로 모은다 |
| `findSafer(query, routeList, waypointPlans)` | 공개    | 조회 + 선별 | 경유지 계획마다 우회 경로를 받고, 거리 한도를 넘는 것은 버린다    |
| `maxDetourDistanceMeters(query, routeList)`  | 비공개  | 계산        | 우회 허용 한도(최단거리의 +20%, +35%, +60%)                       |
| `fetch(query, searchOption, waypoints)`      | 비공개  | 조회        | Tmap 클라이언트 호출 한 번을 감싼다(좌표, 후보 모델 변환 포함)    |
| `TmapRoute.toRouteCandidate()`               | 파일 내 | 변환        | Tmap 경로를 후보 모델(`RouteCandidate`)로                         |
| `List<NavStep>.toNavigationSteps()`          | 파일 내 | 변환        | Tmap 안내 단계를 `NavigationStep` 목록으로                        |
| `List<TmapCoord>.toGeoPoints()`              | 파일 내 | 변환        | Tmap 좌표 목록을 `GeoPoint` 목록으로                              |
| `GeoPoint.toTmapCoord()`                     | 파일 내 | 변환        | `GeoPoint`를 Tmap 좌표로                                          |

우회 허용 한도는 안전 선호(`SafetyPreference`)마다 다르다. `BALANCED`는 +20%, `SAFE`는 +35%, `SAFEST`는 +60%다.

`findSafer`는 조회(우회 경로 받기)와 선별(거리 한도 거르기)이 한 함수에 있다. 이름은 조회(find)만 말한다.

이름 변경 이력은 다음과 같다.

- 한 번만 쓰이던 `RouteRequest.endName()`은 호출부(`fetch`)로 합쳤다.
- 클래스 이름(`RouteFinder`)이 이미 경로를 말해, 메서드 이름의 `RouteList`를 지웠다. `findRouteList`는 `find`로,
  `findSaferRouteList`는 `findSafer`로, 비공개 `fetchRoute`는 `fetch`로 줄였다(2026-11-23).

### RouteCandidateMerger (`domain/service/RouteCandidateMerger.kt`)

Tmap이 중복해서 주는 비슷한 경로를 하나로 뭉친다.

| 함수               | 공개 | 유형 | 역할                                                              |
|--------------------|------|------|-------------------------------------------------------------------|
| `merge(routeList)` | 공개 | 선별 | 같은 경로(`identityKey`)와 거의 같은 경로(90% 이상 겹침)를 버린다 |

두 단계 중복 제거가 `merge` 한 함수에 들어 있다. 예전의 `mergeNearDuplicates`, `isNearDuplicate`는 지금 없다.

`merge`는 원래 이름이 `toCandidates`(변환)였는데 실제는 두 단계 중복 제거가 주제라, 클래스 이름과 같은 `merge`로
바꿨다(2026-09-26). 후보 모델 변환은 `RouteFinder`로 옮겼다.

### RouteTypeAssigner (`domain/service/RouteTypeAssigner.kt`)

후보마다 안전 점수를 매기고 앱 표시 유형(안심, 빠름, 기본)마다 경로 하나씩 고른다.

| 함수                                 | 공개     | 유형        | 역할                                                          |
|--------------------------------------|----------|-------------|---------------------------------------------------------------|
| `assignOneRoutePerType(candidates)`  | internal | 계산 + 선별 | 점수 계산(`SafetyScorer` 호출) 후 고르기를 아래 함수에 맡긴다 |
| `pickIndexPerType(scoredCandidates)` | 비공개   | 선별        | 유형마다 후보 하나를 고른다                                   |

`pickIndexPerType`의 고르는 규칙은 다음과 같다. 동점이면 앞선 후보가 이긴다.

- 안심: 점수 최고
- 빠름: 남은 것 중 시간 최단
- 기본: 남은 비우회 후보 중 점수 최고

계산은 `SafetyScorer`에 맡기고 있어서 실질은 선별이다. "점수 매기기"와 "유형별 고르기"가 한 본문에 있어 고르기를
`pickIndexPerType`으로 분리했다(2026-09-26).

### RouteTypeCatalog (`domain/service/RouteTypeCatalog.kt`)

유형별 표시 문구(이름, 설명, 태그)의 고정 사전.

| 함수              | 공개 | 유형 | 역할                                  |
|-------------------|------|------|---------------------------------------|
| `displayOf(type)` | 공개 | 조회 | `RouteType`에 맞는 표시 정보를 꺼낸다 |

유형이 하나다.

### RouteOptionAssembler (`domain/service/RouteOptionAssembler.kt`)

점수 매긴 경로들을 앱 응답 모델(`RouteOption`)로 조립한다.

| 함수                                          | 공개     | 유형 | 역할                                                      |
|-----------------------------------------------|----------|------|-----------------------------------------------------------|
| `assemble(scoredRoutes, origin, destination)` | internal | 조립 | 점수순 정렬 후 마커, 태그, 출처를 붙여 응답 목록을 만든다 |
| `buildTags(scoredRoute)`                      | 비공개   | 조립 | 유형 고정 태그와 밀도 기준을 넘긴 태그를 모은다           |

예전에 이 클래스에 있던 함수 둘은 응답 DTO 쪽으로 옮겨졌다.

- 점수 문구(`describe`)는 `api/response/RouteOption.kt`의 비공개 함수다.
- 데이터 출처 표기(예전 `buildProvenance`)는 `api/response/DataSource.from(generatedAt)`이 만든다.

전부 조립 계열로 통일되어 있다.

## 2. route 모듈: domain/safety/service

### SafetySpotWaypointPlanner (`domain/safety/service/SafetySpotWaypointPlanner.kt`)

안심시설 뭉치로 우회 경로의 경유지를 계획한다. 원래 이름은 `SafeWaypointFinder`였는데 조회(find)가 아니라 뭉치 계산과
조합 선별이 주제이고, 재료가 안심시설(SafetySpot)임을 이름에 담아 바꿨다(2026-09-26).

| 함수                                                  | 공개   | 유형        | 역할                                                           |
|-------------------------------------------------------|--------|-------------|----------------------------------------------------------------|
| `cluster(routePath)`                                  | 공개   | 조회 + 계산 | 경로 주변 시설을 격자로 묶어 많이 뭉친 순 최대 6개 뭉치를 낸다 |
| `pick(origin, destination, clusters)`                 | 공개   | 선별 + 조립 | 경로 중간의 뭉치만 남긴 뒤 경유지 계획을 만든다                |
| `findNearRoute(routePath)`                            | 비공개 | 조회        | 경로 양옆 150m 안의 CCTV, 지킴이집, 비상벨, 치안시설을 모은다  |
| `filterMiddleClusters(origin, destination, clusters)` | 비공개 | 선별        | 진행률 20~80%이고 옆이탈이 직선거리의 25% 이하인 뭉치만 남긴다 |
| `buildPlans(placed)`                                  | 비공개 | 조립        | 상위 3곳 각각(단일)과 상위 2곳 묶음(쌍)으로 계획을 만든다      |

격자 묶기와 정렬은 결과 타입인 `SafetySpotCluster`(`domain/safety/model/SafetySpotCluster.kt`)가 갖는다.

| 함수                 | 공개            | 유형 | 역할                                                       |
|----------------------|-----------------|------|------------------------------------------------------------|
| `from(spots)`        | 공개(companion) | 계산 | 평균점에 가장 가까운 실제 시설을 대표 좌표로 뭉치를 만든다 |
| `groupByCell(spots)` | 공개(companion) | 계산 | 시설을 격자 칸(`CELL_DEG`)으로 묶고 많이 뭉친 순으로 낸다  |

`cluster`의 변천은 다음과 같다.

1. 원래 이름은 `findSafetySpotClusters`(조회)였는데 실제는 corridor 조회, 격자 묶기, 평균 중심 계산, 정렬과 절단까지
   하는 계산이라 `clusterSafetySpots`로 바꿨다(2026-09-26).
2. 뭉치 하나의 대표 좌표 고르기는 결과 타입이 갖도록 `SafetySpotCluster.from(spots)`으로 옮겼다(2026-11-21).
3. 조회(시설 저장소를 읽음)와 계산(받은 목록만 씀)이 한 함수에 있어, 비공개 `findNearRoute`와 `groupIntoClusters`
   둘로 나눴다(2026-11-21).
4. `groupIntoClusters`의 격자 묶기와 정렬은 `SafetySpotCluster.groupByCell(spots)`으로 옮기고(격자 크기 상수
   `CELL_DEG`도 함께), 최대 6개로 자르기(`MAX_CLUSTERS`)만 `cluster`에 남겼다(2026-11-23).
5. 클래스 이름이 이미 말하는 SafetySpot과 Waypoint를 메서드 이름에서 지웠다. `clusterSafetySpots`는 `cluster`로,
   `pickWaypoints`는 `pick`으로, 비공개 `findSpotsNearRoute`는 `findNearRoute`로 줄였다(2026-11-23).

`pick`은 거르기(선별)와 계획 만들기(조립) 두 단계가 있어, 본문을 비공개 `filterMiddleClusters`와 `buildPlans` 둘로
나눴다(2026-09-26). 호출부가 한 곳뿐이라 공개 함수는 하나로 유지했다. 경유지 묶음의 낱말은 `combo`(줄임말)에서
`plan`(계획)으로 통일했다. 계획 하나가 우회 경로 요청 한 번이 된다.

### SafetyScorer (`domain/safety/service/SafetyScorer.kt`)

경로 하나의 안전 점수를 계산한다. 이 모듈에서 가장 큰 클래스(비공개 함수 15개)다.

| 함수                                               | 공개   | 유형 | 역할                                                          |
|----------------------------------------------------|--------|------|---------------------------------------------------------------|
| `coverageOf(paths)`                                | 공개   | 계산 | 경로 표본점 주변에 축별 자료가 있는지 판정한다                |
| `score(path)`                                      | 공개   | 계산 | 커버리지를 스스로 구해 아래 `score`를 부른다(편의 오버로드)   |
| `score(path, coverage)`                            | 공개   | 계산 | 시설 수, 축별 충족도, 가중 평균, 위험 감점 순으로 점수를 낸다 |
| `hasDataNear(item, scorePoints)`                   | 비공개 | 계산 | 한 축의 자료가 표본점 주변 1km 안에 있는지                    |
| `hasSafePathNear(scorePoints)`                     | 비공개 | 계산 | 안심귀갓길 자료가 주변에 있는지                               |
| `countNearby(path)`                                | 비공개 | 조회 | 종류별 corridor 내 시설 수를 센다                             |
| `countWithinCorridor(type, path)`                  | 비공개 | 조회 | 한 종류의 corridor(30m) 내 시설 수                            |
| `countRiskNear(path)`                              | 비공개 | 조회 | 범죄주의구간 수(corridor 100m)                                |
| `safePathOverlapRatio(path, lengthMeters)`         | 비공개 | 계산 | 안심귀갓길과 겹치는 비율(가장 많이 겹치는 한 길 기준)         |
| `fillsOf(counts, km, safePathOverlap)`             | 비공개 | 계산 | 개수와 밀도로 축별 충족도(0~1)를 만든다                       |
| `densityAndCountFill(count, km, halfDensityPerKm)` | 비공개 | 계산 | 밀도 충족도와 개수 충족도를 반씩 섞는다                       |
| `normalizedScore(fills, weights, coverage)`        | 비공개 | 계산 | 자료 있는 축만 가중 평균                                      |
| `coveredWeight(weights, coverage)`                 | 비공개 | 계산 | 자료 있는 축의 가중치 합                                      |
| `applyRiskPenalty(normalized, riskCount, km)`      | 비공개 | 계산 | 범죄주의구간 밀도만큼 감점(최대 0.20)                         |
| `fill(value, halfPoint)`                           | 비공개 | 계산 | 포화 곡선 `v/(v+half)`                                        |
| `saturate(value, saturationCeiling)`               | 비공개 | 계산 | 0~1로 자르는 비율                                             |
| `round2(value)`                                    | 비공개 | 계산 | 소수 둘째 자리 반올림                                         |
| `round3(value)`                                    | 비공개 | 계산 | 소수 셋째 자리 반올림                                         |

한 번만 쓰이던 비공개 `spotTypesOf(axis)`는 `SPOT_TYPES_BY_ITEM.getValue(item)`으로 합쳤다.

공개 함수는 계산으로 통일되어 있다. 다만 비공개 15개가 공개 3개보다 많고, 점수 공식(`fill`, `saturate`, `round`
계열), 자료 존재 판정(`hasDataNear` 계열), 시설 세기(`count` 계열)라는 세 묶음이 한 클래스에 있다.

### SafetyWeightsCatalog (`domain/safety/service/SafetyWeightsCatalog.kt`)

시간대(주간, 야간)에 따른 축별 가중치 사전.

| 함수              | 공개 | 유형 | 역할                                     |
|-------------------|------|------|------------------------------------------|
| `weightsAt(time)` | 공개 | 조회 | 밤이면 `NIGHT`, 낮이면 `DAY` 가중치      |
| `isNight(time)`   | 공개 | 계산 | 서울 월별 일출, 일몰표로 밤인지 판정한다 |

조회와 그 판정 재료가 같이 있다. 프로덕션에서 `isNight`를 부르는 곳은 `weightsAt`뿐이고, 나머지 사용처는
`SafetyWeightsCatalogTest`다.

## 3. route 모듈: domain/marker/service

### RouteMarkerBuilder (`domain/marker/service/RouteMarkerBuilder.kt`)

미니맵에 찍을 마커(출발, 도착, 경로 주변 시설)를 만든다.

| 함수                               | 공개 | 유형 | 역할                                                    |
|------------------------------------|------|------|---------------------------------------------------------|
| `build(origin, destination, path)` | 공개 | 조립 | 출발, 도착 마커와 종류별 corridor 내 시설 마커를 모은다 |

`build`가 쓰는 보조 함수는 다른 파일에 있다.

- 종류마다 최대 12개를 경로를 따라 고르게 뽑기: `support/util`의 `List<T>.pickEvenly(count)`(`EvenPick.kt`)
- 시설을 마커로 바꾸기: `domain/marker/model/SafetySpotPoi.kt`의 `SafetySpotPoint.toPoi(markerType)`
- 미니맵 백분율 좌표로 바꾸기: 같은 파일의 `List<SafetySpotPoi>.toMiniMapPercent()`

예전의 비공개 `spreadAlongPath`는 `pickEvenly`로 옮겨져 지금은 없다. 클래스 안에는 조립 하나만 남았다.

`build`는 원래 `buildMarkers`였는데 클래스 이름(`RouteMarkerBuilder`)이 이미 마커를 말해 줄였다(2026-11-23).

## 4. safetyspot 모듈: storage/db-safety-spot

### SafetySpotSpatialSource (`store/SafetySpotSpatialSource.kt`)

읽기 창구 인터페이스. route 모듈이 보는 것은 이 인터페이스뿐이다.

| 멤버            | 유형 | 역할                                                     |
|-----------------|------|----------------------------------------------------------|
| `index(type)`   | 조회 | 종류별 공간 인덱스                                       |
| `safePaths`     | 조회 | 안심귀갓길 목록                                          |
| `freshness`     | 조회 | 자료 생성 시점과 버전(기본 구현은 null)                  |
| `sources(type)` | 조회 | 한 종류의 출처별 조각 스냅샷(기본 구현은 전부 파일 출처) |

### SafetySpotLiveStore (`store/SafetySpotLiveStore.kt`)

쓰기 창구 인터페이스. 두 교체 모두 원자적이다.

| 멤버                                      | 유형 | 역할                                                   |
|-------------------------------------------|------|--------------------------------------------------------|
| `replaceFiles(type, points)`              | 저장 | 파일 출처 조각만 바꾸고, API로 받은 지역 조각은 남긴다 |
| `replaceRegion(type, regionCode, points)` | 저장 | 그 지역의 API 조각만 바꾼다                            |

쓰는 곳은 둘이다.

- `replaceFiles`: 관리자 업로드(`core-admin-safety-spot`의 `SafetySpotUploadService`)
- `replaceRegion`: 경로 주변 채우기(`core-safety-spot`의 `*FillService` 4종)

읽기(`SpatialSource`)와 쓰기(`LiveStore`)가 인터페이스 수준에서 분리되어 있다.

### SafetySpotRepository (`store/SafetySpotRepository.kt`)

두 인터페이스의 구현체. CDN JSON, 업로드분(JSON), 지역 캐시를 모아 종류마다 출처별 조각(`SafetySpotSources`)을 만든다.
CSV 해석은 하지 않는다. 업로드가 변환해 둔 JSON만 읽는다(2026-10-11 일원화).

| 함수                                      | 공개                 | 유형 | 역할                                                       |
|-------------------------------------------|----------------------|------|------------------------------------------------------------|
| `index(type)`                             | 공개(override)       | 조회 | 종류별 인덱스, 없으면 빈 인덱스                            |
| `sources(type)`                           | 공개(override)       | 조회 | 종류별 출처 조각, 없으면 빈 조각                           |
| `replaceFiles(type, points)`              | 공개(override)       | 저장 | 파일 조각을 새 점으로 바꾸고 인덱스를 다시 세운다          |
| `replaceRegion(type, regionCode, points)` | 공개(override)       | 저장 | 지역 조각 하나를 새 점으로 바꾸고 인덱스를 다시 세운다     |
| `load()`                                  | 공개(@PostConstruct) | 적재 | 부팅 때 CDN, 업로드분, 지역 캐시 순으로 읽어 조각을 세운다 |
| `addRegionCache(sources)`                 | 비공개               | 적재 | 지역 캐시 파일을 종류별 지역 조각으로 더한다               |
| `readFiles(dir, byType)`                  | 비공개               | 적재 | CDN 시설 JSON을 읽는다                                     |
| `readSafePathFiles(dir, paths)`           | 비공개               | 적재 | 안심귀갓길 JSON을 읽는다                                   |
| `readFreshness(dir)`                      | 비공개               | 적재 | `manifest.json`에서 신선도를 읽는다                        |
| `overwriteWithUploads(byType)`            | 비공개               | 적재 | 업로드분이 있으면 그 종류의 CDN 점을 통째로 덮는다         |
| `logLoaded(...)`                          | 비공개               | 기록 | 적재 결과 로그                                             |
| `resolveDataDir()`                        | 비공개               | 조회 | 후보 경로 중 실제 존재하는 디렉터리 찾기                   |
| `parseCollection(file)`                   | 비공개               | 변환 | JSON 파일을 DTO로(실패 시 null)                            |

CSV 해석은 `core/core-admin-safety-spot`의 `domain/csv/<시설>/XxxCsvFormat`와
`domain/csv/service/SafetySpotCsvReader`가 맡는다. gz 해제, 인코딩 감지, 행 판정을 하며 업로드와 미리보기가 같은 형식을
쓴다.

공개 5개 중 4개(조회 2, 저장 2)는 인터페이스 계약 그대로다. 비공개 8개가 전부 부팅 적재의 하위 단계라, 서빙(조회,
저장)과 JSON 적재라는 두 층이 한 파일에 있다.

### GridSpatialIndex (`store/GridSpatialIndex.kt`)

시설 점들을 격자에 담아 공간 질의에 답하는 불변 인덱스. 좌표 5자리(약 1.1m)가 같은 점은 하나로 센다.

| 함수                               | 공개              | 유형 | 역할                                               |
|------------------------------------|-------------------|------|----------------------------------------------------|
| `withinRadius(center, radiusM)`    | 공개              | 선별 | 중심점에서 반경 안 시설                            |
| `withinCorridor(path, corridorM)`  | 공개              | 선별 | 경로 양옆 띠 안 시설                               |
| `fillPointsBetween(path)`          | 비공개            | 계산 | 경로 점 사이가 격자 칸보다 넓으면 중간 점을 채운다 |
| `candidateCells(anchors, radiusM)` | 비공개            | 조회 | 기준점 주변 격자 칸의 시설을 후보로 모은다         |
| `roundCoord(value)`                | 비공개(companion) | 계산 | 좌표를 소수 5자리 정수로(중복 판정용)              |
| `cellIndex(value)`                 | 비공개(companion) | 계산 | 좌표를 칸 번호로                                   |
| `cellKey(latCell, lngCell)`        | 공개(companion)   | 계산 | 칸 번호 둘을 `Long` 키로                           |

이 밖에 `size`, `all`, `regions` 프로퍼티가 있다.

공개 질의 둘 다 선별로 통일되어 있다. `cellKey`만 공개인 이유는 `SafetySpotCluster.groupByCell`이 격자 묶기에 재사용하기
때문이다.

### SafetySpotUploadStore (`store/SafetySpotUploadStore.kt`)

관리자가 올린 시설 파일의 저장과 조회. 변환 결과는 `{uploadDir}/{cdnType}.json`, CSV 원본은
`{uploadDir}/originals/{cdnType}.csv`에 둔다.

| 함수                        | 공개 | 유형 | 역할                                          |
|-----------------------------|------|------|-----------------------------------------------|
| `save(type, points)`        | 공개 | 저장 | 종류별 JSON을 원자적으로 쓴다                 |
| `saveOriginal(type, bytes)` | 공개 | 저장 | 올린 CSV 원본을 종류마다 최신 하나만 둔다     |
| `originalFiles()`           | 공개 | 조회 | 보관된 CSV 원본 목록                          |
| `hasUpload(type)`           | 공개 | 조회 | 그 종류의 업로드 JSON이 있는지                |
| `findByType(type)`          | 공개 | 조회 | 저장된 JSON을 점 목록으로(없거나 깨지면 null) |

점을 저장 형식(`StoredSafetySpotItem`)으로 바꾸는 `SafetySpotPoint.toStoredItem()`은
`model/StoredSafetySpotItem.kt`에 있고, 지역 캐시도 같이 쓴다.

저장 2, 조회 3으로 유형이 명확하다.

### SafetySpotRegionCache (`store/SafetySpotRegionCache.kt`)

지역 단위로 받아 온 시설의 파일 캐시(`{regionDir}/{종류}/{지역코드}.json`, TTL 90일).

| 함수                             | 공개   | 유형 | 역할                                                |
|----------------------------------|--------|------|-----------------------------------------------------|
| `isCached(type, regionCode)`     | 공개   | 계산 | 저장 시각이 TTL 안인지                              |
| `savedAtOf(type, regionCode)`    | 공개   | 조회 | 저장 시각(메모리, 파일 순)                          |
| `save(type, regionCode, points)` | 공개   | 저장 | 지역별 JSON 원자적 저장. 결과가 비면 기존 점을 유지 |
| `load(type, regionCode)`         | 공개   | 조회 | 한 지역의 캐시를 점 목록으로                        |
| `list(type)`                     | 공개   | 조회 | 캐시된 지역 코드 집합                               |
| `cacheKey(type, regionCode)`     | 비공개 | 계산 | 메모리 기억용 키                                    |
| `fileOf(type, regionCode)`       | 비공개 | 계산 | 지역 캐시 파일 경로                                 |

예전의 `loadAll(type)`은 지역 하나를 읽는 `load(type, regionCode)`로 바뀌었고, 모든 지역 순회는
`SafetySpotRepository.addRegionCache`가 한다.

조회 3, 판정 1, 저장 1. 캐시라는 역할 안에서 자연스러운 구성이다.

### SafetySpotRefreshStatusStore (`store/SafetySpotRefreshStatusStore.kt`)

관리자 업로드의 마지막 실행 기록(관리자 화면용). 메모리에만 둔다.

| 함수                                           | 공개 | 유형 | 역할               |
|------------------------------------------------|------|------|--------------------|
| `recordUpload(type, replacedCount, atEpochMs)` | 공개 | 저장 | 업로드 한 건 기록  |
| `findByType(type)`                             | 공개 | 조회 | 종류별 마지막 기록 |

예전의 `record(results, atEpochMs)`(스케줄러 갱신 결과 병합)는 지금 없다. 기록하는 곳은 관리자 업로드뿐이다.

저장 1, 조회 1. 유형이 명확하다.

## 5. 유형이 겹치거나 이름과 어긋난 함수: 검토 후보

위 표에서 유형이 둘 이상이거나, 이름이 말하는 유형과 실제가 다른 함수만 모았다. 고치라는 판정이 아니라 검토 입구다.

| 대상                                      | 무엇이 겹쳤나                            | 판정 |
|-------------------------------------------|------------------------------------------|------|
| `RouteFinder.findSafer`                   | 조회 + 선별(거리 한도)                   | 유지 |
| `RouteCandidateMerger.merge`              | 이름은 변환(`toCandidates`), 실제는 선별 | 해소 |
| `RouteTypeAssigner.assignOneRoutePerType` | 점수 계산 위임 + 유형별 선별             | 해소 |
| `SafetySpotWaypointPlanner.cluster`       | 이름은 조회(find), 실제는 계산           | 해소 |
| `SafetySpotWaypointPlanner.pick`          | 선별 + 조합 조립                         | 해소 |
| `SafetyScorer`(클래스)                    | 비공개 15개에 세 묶음                    | 보류 |
| `SafetySpotRepository`(클래스)            | CSV 파싱과 서빙                          | 해소 |
| `SafetyWeightsCatalog.isNight`            | 프로덕션 사용처는 `weightsAt`뿐          | 유지 |

판정마다 근거는 다음과 같다.

- `RouteFinder.findSafer`: 한도 계산은 비공개 `maxDetourDistanceMeters`로 분리했다. 한도 거르기 자체는 "쓸 만한 우회
  경로 조회"라는 주제의 일부라 함수는 유지한다.
- `RouteCandidateMerger.merge`: `toCandidates`를 `merge`로 바꿔 클래스 이름(Merger)이 말하는 주제와 맞췄다.
- `RouteTypeAssigner.assignOneRoutePerType`: 유형별 고르기를 비공개 `pickIndexPerType`으로 분리했다.
- `SafetySpotWaypointPlanner.cluster`: `clusterSafetySpots`를 거쳐 `cluster`로 바꿨다. 클래스도 `SafeWaypointFinder`에서
  `SafetySpotWaypointPlanner`로 바꿨다.
- `SafetySpotWaypointPlanner.pick`: 비공개 `filterMiddleClusters`와 `buildPlans`로 분리했다. combo 낱말은 plan으로
  통일했다.
- `SafetyScorer`: 공개 함수 유형은 계산 하나로 통일돼 있고, 나눠도 쓰는 곳이 이 클래스뿐이라 지금은 얻는 게 없다.
  세기(`count` 계열)를 다른 곳이 또 쓰게 되면 그때 분리한다.
- `SafetySpotRepository`: CSV 파싱이 core 계약으로 빠져(2026-10-11) JSON 적재와 서빙만 남았다.
- `SafetyWeightsCatalog.isNight`: 일출, 일몰 경계 판정 자체가 테스트로 검증할 규칙이라 공개로 둔다. 비공개로 내리고
  `weightsAt` 결과로 우회 검증하면 테스트가 더 흐려진다.

반대로 유형이 하나로 깔끔한 것은 다음과 같다.

- `RouteOptionService`(흐름)
- `RouteTypeCatalog`(조회)
- `RouteOptionAssembler`(조립)
- `RouteMarkerBuilder`(조립)
- `GridSpatialIndex`(선별)
- `SafetySpotUploadStore`, `SafetySpotRegionCache`, `SafetySpotRefreshStatusStore`(저장과 조회 짝)
