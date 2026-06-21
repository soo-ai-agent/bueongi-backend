# 작업 보고서 — 백엔드 도메인 로직 단위 테스트 추가

- 작성: 2026-06-17 06:37 (KST)
- 역할/에이전트: backend / bueongi-backend (engine: claude-opus)
- 칸반: ac4f2c70-ec29-47a3-a31b-14aa9791b9da (high, done)

## 1. 요청
자동 루프 backend 사이클. 직전 상태(consensus/handoff) 복원 후 "다음 한 사이클" 결정적 수행.
깨진 빌드/테스트 우선 수정, 검증 가능한 산출물 1개 이상. (메모리 파일 부재 = 최초 사이클)

## 2. 진행
- 메모리 파일 부재 확인 → 코드/빌드/칸반 직접 점검으로 상태 파악.
- `./gradlew test`는 성공하나 테스트 소스 0개(`:test NO-SOURCE`)임을 발견 → 회귀 안전망 부재를
  최우선 갭으로 판단(파괴적 빌드 오류는 없음).
- 결정론적·외부의존 없는 핵심 도메인 로직을 단위 테스트 대상으로 선정(가장 검증 가치 높고 안정적).
- JUnit5 + AssertJ(spring-boot-starter-test 내장) 사용, 프로덕션 코드 변경 없이 테스트만 추가.

## 3. 산출물
신규 테스트 5개 파일 / 31개 케이스 (전부 신규):
- `src/test/kotlin/io/soo/bueongi/routes/GeoTest.kt` — haversine·formatDistance·walkMinutes (7)
- `src/test/kotlin/io/soo/bueongi/routes/RouteTypeTest.kt` — from() 매핑·SAFE 기본·계수 순서 (5)
- `src/test/kotlin/io/soo/bueongi/places/PlaceSearchServiceTest.kt` — 부분일치/트림/빈검색어/좌표 (6)
- `src/test/kotlin/io/soo/bueongi/routes/RouteServiceTest.kt` — 3경로 계약/거리정렬/결정론/0거리 (5)
- `src/test/kotlin/io/soo/bueongi/routes/FacilityServiceTest.kt` — summary↔POI 일치/상한8/좌표범위/결정론 (8)
- 메모리 갱신: `.claude/memories/consensus.md`, `.claude/memories/handoff.md`

## 4. 검증
- `./gradlew test` → BUILD SUCCESSFUL.
- 결과 집계: tests=31, skipped=0, failures=0, errors=0.
- 프론트 계약 고정: 부분일치 검색, dist "850m"/"1.2km" 표기, summary가 렌더 POI와 항상 일치, SAFE 기본값.

## 5. 잔여·인계
- 컨트롤러 슬라이스 테스트(@WebMvcTest): JSON 직렬화 계약 + @Valid 누락 좌표 400 검증.
- RouteRequest 위경도 범위 검증(@Min/@Max) 추가 — **계약 변경이라 frontend 통지 필요**.
- Kakao Local 실연동 스켈레톤.
- git 커밋/push 미수행(승인 영역). projectPath 밖 수정 없음. 사용량 제한 미도달.

### 메타
- 변경 파일: 테스트 5 + 메모리 2 + 본 보고서 1. 프로덕션 코드 변경 0.
- fallback 전환: 없음.
