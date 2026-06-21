# 작업 보고서 — 컨트롤러 슬라이스 테스트 + RouteRequest 좌표 범위 검증

- 메타: roleId=backend / engine=claude(opus) / 2026-06-17 06:50 KST
- 프로젝트: bueongi-backend (`/Users/soo/workspace/source-code/apps/bueongi/backend`)
- 사이클 입력: consensus.md `## Next Action` 1·2

## 요청
- (1) 컨트롤러 계층 슬라이스 테스트(@WebMvcTest) 작성 — places/routes 엔드포인트 JSON 계약 + @Valid 누락/범위초과 좌표 400 검증.
- (2) RouteRequest 위경도 범위 검증(lat -90~90 / lng -180~180) 추가. Frontend는 직전 사이클에 동일 범위 사전 가드 반영 완료.

## 진행
1. 상태 복원: consensus.md / handoff.md / 에이전트 정의(`bueongi-backend.md`) 읽음. 깨진 빌드/테스트 없음 확인.
2. DTO 검증 보강: `routes/RouteDtos.kt`의 `LatLng`·`NamedLatLng` lat/lng 에 `@DecimalMin/@DecimalMax` 추가. 컨트롤러는 기존 `@Valid` 사용 → 추가 코드 없이 범위 밖 좌표 400.
3. 테스트 작성: mock 대신 실 서비스 `@Import` 방식으로 진짜 JSON 직렬화 계약까지 검증하도록 설계.

## 산출물
- 수정: `src/main/kotlin/io/soo/bueongi/routes/RouteDtos.kt`
  - `LatLng.lat`/`NamedLatLng.lat`: `@DecimalMin("-90.0") @DecimalMax("90.0")`
  - `LatLng.lng`/`NamedLatLng.lng`: `@DecimalMin("-180.0") @DecimalMax("180.0")`
- 신규: `src/test/kotlin/io/soo/bueongi/places/PlaceControllerTest.kt` (3 케이스)
  - GET /api/places/search 200 + PlaceItem(name/address/lat/lng) JSON shape
  - keyword 없음 / 무일치 → 200 빈 배열
- 신규: `src/test/kotlin/io/soo/bueongi/routes/RouteControllerTest.kt` (6 케이스)
  - compare 200 + safe/main/fast 3건 + tags(text/variant) 계약
  - facilities 200 (pois 배열 / summary)
  - origin 누락 / destination 좌표 누락 → 400
  - lat 100 / lng 200(범위 초과) → 400

## 검증
- `./gradlew test` → BUILD SUCCESSFUL.
- 테스트 40개 전부 통과 (failures=0, errors=0). 직전 31개 → +9.
- 클래스별: Geo7 / RouteType5 / PlaceSearchService6 / RouteService5 / FacilityService8 / **PlaceController3 / RouteController6**.

## 잔여·인계
- 검증 실패 400 응답 body 가 Spring 기본 포맷 → @RestControllerAdvice 로 field/message 표준화 필요(다음 Next Action 2).
- compare 의 origin 미확보 정책(기본 origin or 422) 미정 — Frontend 위치권한 흐름과 연계(Next Action 3).
- Kakao Local 실연동 미구현(키 미주입, 시드 경로만 활성).
- git: master 커밋 0개 유지. 자동 커밋/푸시 안 함(승인 영역). projectPath 밖 수정 없음. 사용량 제한 미도달.
