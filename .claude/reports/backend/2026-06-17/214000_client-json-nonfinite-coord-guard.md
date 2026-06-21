# 작업 보고서 — 클라이언트 JSON 좌표 비유한 값 차단 불변식 회귀 테스트 고정

- 일시: 2026-06-17 21:40 (KST)
- 역할/엔진: backend / Claude(opus)
- 프로젝트: bueongi-backend (`/Users/soo/workspace/source-code/apps/bueongi/backend`)

## 1. 요청
- Dorothy 자동 루프 backend 사이클. consensus/handoff 상태가 "도메인 검증·운영 가시성·에러 계약·가드 단일화·Kakao 외부 좌표 경계 모두 포화 → 백로그 발굴(코드 직접 점검) 재수행 권장".
- 깨진 빌드/테스트 없을 시 최소 1개의 검증 가능한 산출물 생성.

## 2. 진행
- 상태 복원(consensus.md / handoff.md / 에이전트 정의 `bueongi-backend.md`). `backlog-discovery` 스킬·`backend.md`는 부재 → 실제 존재 파일 기준 진행(직전 사이클들과 동일).
- 베이스라인: `./gradlew test` BUILD SUCCESSFUL, 90개 통과.
- 코드 직접 점검(Geo/RouteService/FacilityService/RouteDtos/RouteType/컨트롤러/application.yml).
  - **대칭 갭 발견**: 직전 사이클이 Kakao 외부 응답 매핑(`toPlaceItem`)의 비유한 좌표를 걸렀으나, 좌표의 1차 유입 경로(클라이언트 POST 본문 origin/destination)는 동일 위험 테스트가 없었다. 비유한 좌표는 haversine 에서 NaN 거리를 내고 거리 가드(`<`/`>` 가 NaN 에 항상 false)를 우회해 garbage 경로/시설을 만들 수 있음.
- 임시 ProbeTest(@WebMvcTest)로 4 입력 실제 거동 관찰 후 삭제 → 정식 회귀 테스트로 봉인.

## 3. 산출물
- 소스 **0건 수정**(경험적 확인 결과 이미 안전 — 변경 불요).
- 테스트 **4건 신규**(`src/test/kotlin/io/soo/bueongi/routes/RouteControllerTest.kt`):
  1. `lat 1e400`(→`+Infinity`) → 400 VALIDATION_FAILED, errors[0].field=origin.lat
  2. `lng -1e400`(→`-Infinity`) → 400 VALIDATION_FAILED, errors[0].field=origin.lng
  3. `NaN` 리터럴 → 400 MALFORMED_REQUEST
  4. `Infinity` 리터럴 → 400 MALFORMED_REQUEST
- 거부 분담 구조: 오버플로 숫자 토큰(`1e400`)은 `@DecimalMin`/`@DecimalMax`(범위 검증)가, 비숫자 리터럴(`NaN`/`Infinity`)은 Jackson `ALLOW_NON_NUMERIC_NUMBERS` 기본 비활성(파싱)가 막음.

## 4. 검증
- `./gradlew test` → BUILD SUCCESSFUL.
- 테스트 **94개 전부 통과**(failures=0, errors=0). 직전 90 → +4. (`build/test-results/test/*.xml` 합산 확인.)
- RouteController 7→11, 그 외 클래스 무변동(거동 보존). 임시 ProbeTest 잔존 없음.
- 외부 동작/응답 계약 완전 불변(순수 가산적 가드 테스트).

## 5. 잔여·인계
- Frontend(최우선 병목): `/api/routes/compare` 클라이언트 스켈레톤 + 응답 분기(400/422 4종). 백엔드 계약·프레임워크 에러·외부 데이터 경계 모두 완성, 프론트 분기만 남음.
- Backend: 실질 잔여 없음(도메인 검증·운영 가시성·에러 계약·가드 단일화·외부 데이터 경계 양방향 포화). 다음 사이클 nothing-to-do 가능성 매우 큼 → 백로그 발굴 재수행하되 억지 작업 금지.
- git 커밋·push 없음(승인 영역). projectPath 밖 수정 없음. secret 비노출. 사용량 제한 미도달, 우회·fallback 없음.
