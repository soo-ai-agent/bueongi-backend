# 백엔드 안심 라우팅 기반 구축 (공공데이터 적재 + 실 Tmap + 안심 점수/우회 + safe-compare)

- 작성: 2026-06-28 19:11 KST / 역할: backend / 엔진: Claude(opus)
- 경계: `apps/bueongi/backend` 내부만 수정. 프론트 무수정(계약은 응답 JSON 형태로 문서화).

## 1) 요청
"진짜 공공데이터 기반 안심 라우팅"을 위해 시설 데이터 + 라우팅 연산을 백엔드로 이전:
인메모리 공간 인덱스, 안전 점수(프론트 safetyScore.ts 이식), 안심 경유지 우회, 서버사이드 Tmap 호출,
신규 `POST /api/routes/safe-compare`. 기존 compare/facilities 는 그대로 유지(프론트 폴백 호환).

## 2) 진행
- 새 패키지 `io.soo.bueongi.facility`: 모델/공간수학/그리드 인덱스/적재/점수/우회.
- `io.soo.bueongi.routes` 확장: TmapRouteClient(+GeoJSON 파서), SafeRouteService, 컨트롤러 엔드포인트, DTO 확장.
- 점수/우회는 `FacilitySpatialSource` 인터페이스로 분리해 파일 적재 없이 단위 테스트.
- 그리드 인덱스 회랑 질의는 정점만으로 후보를 잡으면 멀리 떨어진 두 정점 사이 구간 시설을 놓치므로,
  각 선분을 셀 간격으로 보간(densify)한 샘플점으로 후보 셀을 모으도록 보정.

## 3) 산출물 (신규/변경 파일)
신규(main):
- `facility/FacilityModels.kt` — GeoPoint, FacilityType, FacilityPoint, SafePath, `FacilitySpatialSource`.
- `facility/SpatialMath.kt` — pathLength/점-선분거리/투영(t,측방)/겹침(프론트 geo.ts 이식).
- `facility/GridSpatialIndex.kt` — ~100m 셀 버킷 인덱스, withinRadius/withinCorridor(densify+정밀필터).
- `facility/FacilityRepository.kt` — CDN 디렉터리 적재(@Component, 설정/폴백/회복력) + JSON DTO.
- `facility/SafetyScorer.kt` — 30m 회랑 밀도+안심귀갓길 겹침 → 0~100(가중치 cctv.35/lamp.25/bell.20/safehouse.05/safePath.15).
- `facility/SafeDetourPlanner.kt` — extractSafeAnchors(150m·80m그리드·상위6)/pickDetourWaypoints(중간구간·측방0.25·단일2~3+쌍1).
- `routes/TmapRouteClient.kt` — 서버사이드 Tmap(RestClient, appKey 헤더 전용·비노출) + `TmapGeoJson` 파서 + TmapRoute/NavStep.
- `routes/SafeRouteService.kt` — 후보 수집/채점/유형배정/거리예산 필터/마커(directRoute.ts 이식).
변경(main):
- `routes/RouteController.kt` — `POST /api/routes/safe-compare` 추가.
- `routes/RouteDtos.kt` — RouteRequest 에 `safetyPreference`; RouteOption 에 path/steps/score/markers(@JsonInclude NON_NULL → compare 계약 불변).
- `resources/application.yml` — `bueongi.facility.data-dir`(BUEONGI_FACILITY_DATA_DIR, 기본 ../cdn), `bueongi.tmap.app-key`(TMAP_APP_KEY).
신규(test, 42건): SpatialMath/GridSpatialIndex/SafetyScorer/SafeDetourPlanner/FacilityRepository(+FakeFacilitySource)/TmapGeoJson/SafeRouteService.
변경(test): RouteControllerTest·ApiExceptionHandlerTest 의 @Import 에 신규 빈 추가 + TmapRouteClient @MockBean(웹슬라이스 RestClient.Builder 부재 대응).

## 4) 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL. XML 집계 **tests=192 / failures=0 / errors=0**(기존 150 + 신규 42, 회귀 0).
- 실기동(bootRun) 확인: 실 CDN 적재 로그 `files=11, cctv=3, lamp=4, bell=3, safehouse=2, police=3, safepaths=2`.
- 키 없음 상태 `POST /api/routes/safe-compare` → compare 폴백, 응답 shape 기존과 동일(path/steps/score/markers 미출력 = NON_NULL 동작).
- 시크릿 비노출(appKey 헤더 전용·로그 미기록), 결정론(같은 입력+데이터→같은 결과), 외부호출 테스트는 목/주입 격리.

## 5) 잔여 · 인계
### safe-compare 계약(프론트 공유)
- 요청: compare 와 동일(origin/destination) + 옵션 `safetyPreference`: "balanced"|"safe"|"safest" → detourBudget 0.2/0.35/0.6(기본 "safe").
- 응답: `RouteOption[]` (안전점수 내림차순). 필드:
  `id, name, time("42분"), dist("2.8km"), desc("안전 점수 N점 · CCTV X/km"), tags[{text,variant}], type("safe"|"main"|"fast")`
  + Tmap 키 있을 때 추가: `path: [{lat,lng}]`, `steps: [{index,lat,lng,description,turnType,distanceM,timeS,pointType}]`,
  `score: 0~100`, `markers: [{type,x,y,lat,lng,name?}]`(type: start|end|cctv|safehouse|bell, x/y=0~100 폴백투영).
  - Tmap 키 없음/전부 실패: compare 결과로 폴백(path/steps/score/markers 없음).
- 운영: `TMAP_APP_KEY`(보행자 경로), 필요 시 `BUEONGI_FACILITY_DATA_DIR`(기본 ../cdn) 주입. 키는 코드 하드코딩 금지(env만).

### 알고리즘 요약
- 안전점수: 경로 30m 회랑 내 CCTV/조명/비상벨/안심집 밀도(개/km, 상한 20/40/5, 안심집 count 3)와 안심귀갓길 겹침비율을
  가중합(.35/.25/.20/.05/.15) → 0~100. 데이터 비면 0 수렴.
- 우회: 추천경로 150m 반경 CCTV/안심집/비상벨을 80m 그리드 클러스터링(weight=시설수, 상위6 앵커) →
  진행방향 중간(t 0.2~0.8)·측방이탈≤직선×0.25 앵커로 경유지 조합(단일 2~3 + 쌍 1) → Tmap passList 호출 →
  기본 최단거리×(1+budget) 이내만 채택.

### 인계
- 프론트(bueongi-dev): safe-compare 직접 호출로 전환 시 위 계약 사용 가능(현재 프론트는 Tmap 직접호출 구조 — 백엔드 경유로 옮길지 결정 필요).
- 미해결/주의: safepath 실 CDN 은 현재 단일 점(좌표열 아님)이라 겹침 보너스는 사실상 0 — 좌표열 데이터 들어오면 자동 반영.
