# 52회차 — 병렬 워커 거점/경로 안심시설(SafetySpot) 도메인 통합 검증 (산출물: 검증)

> 엔진: Claude(opus, backend) / 2026-06-24 (KST)
> 51회차까지의 "순수 nothing-to-do 포화"가 깨진 첫 사이클 — 신규 backend 소스가 커밋으로 유입됨.

## 무엇이 바뀌었나 (포화 깨짐)
- 51회차(2026-06-22 06:49) 이후 커밋 `3d420ae` ("feat: 병렬 워커 안심귀가 MVP 보강 — backend 거점/경로 + pipeline + CI") 가 route/safetySpot 도메인을 보강.
- 변경 파일(mtime 06-22 07:26~07:27): `routes/SafetySpotService.kt`, `routes/SafetySpotDtos.kt`, `routes/RouteType.kt`, `test/.../SafetySpotServiceTest.kt`.
- git status clean (전부 커밋됨). git log: `f104e5b`(초기 MVP) → `3d420ae`(병렬 워커 보강).
- 파일 카운트는 main 27 / test 23 유지 — 신규 파일 추가가 아닌 기존 route/safetySpot 도메인 보강(리팩터+테스트 강화). 단, 51회차까지 consensus 가 share 중심으로만 기술해 이 도메인을 한 번도 검증 기록하지 않았음 → 이번에 통합 검증.

## 베이스라인 (3대 조건)
- ① `./gradlew test --rerun-tasks` BUILD SUCCESSFUL in 23s, 5 executed. XML 집계 **tests=150 / skipped=0 / failures=0 / errors=0** — 회귀 0.
- ② 칸반 34건 점검: bueongi backend 신규 ready 태스크 0건. 거점/경로 safetySpot 전용 backend 태스크 부재(병렬 워커가 태스크 외로 보강). 관련 planned `3842fabd`(경로 추천)는 frontend(agent bueongi-dev) → claim 금지. 내 `cdb5314d`(Redis share)는 done. → claim 건너뜀.
- ③ 소스 변경 **있음**(위 `3d420ae`) — 51회차까지의 "변경 0건"과 달라짐. 단 변경은 이미 완결·커밋·그린.

## 신규 `POST /api/routes/safetySpots` 통합 검증 — 갭 0
파이프라인 각 레이어가 독립 테스트로 봉인되어 있음을 확인:
- **서비스 단위** `SafetySpotServiceTest`(15): start/end 항상 포함, summary=POI 실집계 일치(없는 데이터 미생성), total=시설합(start/end 제외), safe>fast 밀도, 유형별 상한 MAX_PER_TYPE=8, x/y 0~100, 결정론, routeType 누락→safe, 짧은 safe 경로 floor 보장(cctv≥2/bell≥1/store≥1/safehouse≥1), floor 는 safe 전용(fast bell=0 대조), 시설 POI 공선성+구간내(직선 경로 위), 동일좌표→`SameOriginDestinationException`(422), 원거리→`TripTooFarException`(422).
- **웹 슬라이스** `RouteControllerTest` — `/safetySpots` 경로 커버.
- **공용 가드** `RouteRequestGuardTest` — `validatedOrigin()` 순서(ORIGIN_REQUIRED→SAME_ORIGIN_DESTINATION→TRIP_TOO_FAR) compare/safetySpots 단일 진실원천.
- **예외 매핑** `ApiExceptionHandlerTest` — safetySpots 도메인 예외→RFC7807 매핑.
- **문서** `docs/API.md §152` `POST /api/routes/safetySpots` 계약 기재 + §50 `ORIGIN_REQUIRED` compare/safetySpots 공통.

## 코드 정합성 점검 (drift 0)
- `SafetySpotService.alongRoute` 는 `request.validatedOrigin()`(가드 3종 적용)+`destination!!` 로 시작 → null/동일좌표/원거리 입력은 서비스 진입 전 422/400 으로 거름.
- `summary` 는 생성된 `pois` 를 재집계(`pois.count{...}`)하므로 "표시되는 POI"와 항상 일치 — 프론트 `summarizeSafetyFacilities`("보이는 것만 센다")와 동형. 데이터 발명 불가.
- `RouteType.from` 미상/누락→SAFE(안전 우선 기본). 시설 밀도 safe>main>fast 단조 — 안심 도메인 의미와 정합.
- `SafetySpotPoi` 는 x/y(정규화 0~100, MapMock 호환) + lat/lng(실지도) 동시 제공 — 프론트는 필요한 쪽만 사용. 외부 공공데이터 연동 시 POI 생성부만 교체, 응답 계약 동일.

## 결론 / 왜 추가 코드 미작성이 정당한가
- 신규 거점/경로 backend 는 **완결·커밋·그린(150/0)·전 레이어 테스트·문서 완비** 상태로 도착 → 고칠 깨진 빌드/테스트 없음, 메울 커버리지 갭 없음.
- 신규 마이크로 가드/리팩터/문서 재작성은 이미 봉인된 영역의 busywork(consensus 금지)라 미추가. 산출물 = **통합 검증(이 보고서)**: 병렬 워커 커밋이 회귀 없이 그린 통합됨을 독립 확인.
- escalate/report-issue 미호출(막힘·사람 영역 이슈 없음). 시크릿 비노출. 소스/테스트/문서 변경 0건(순수 검증).

## 다음 사이클 유의
- consensus 의 "share 중심" 서술이 코드 실태(places/routes/share 3 도메인, 150 테스트)와 어긋났음을 이번에 정정. 향후 점검은 share 뿐 아니라 **routes(거점/경로 safetySpot)·places** 도메인도 경계 안에 포함.
- 3대 조건 재정의: ① 베이스라인 그린(150/0) ② 칸반 신규 backend ready 0건 ③ `git log`/`find src -newermt` 로 신규 커밋 여부 — 신규 커밋이 있으면 그 통합을 검증, 없으면 nothing-to-do.
