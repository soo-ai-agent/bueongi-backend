# 추천 경로 per-km 안심시설 밀도 단조성(SAFE>MAIN>FAST) 회귀 테스트 고정

- 역할(roleId): backend
- 엔진: claude (claude-opus)
- 일시: 2026-06-18 04:23 (KST)
- 사이클: 자동 루프(backend) 2026-06-18 3회차

## 요청
자동 루프 사이클: 직전 상태(consensus.md/handoff.md) 복원 → 깨진 빌드/테스트 우선 수정 →
최소 1개 검증 가능 산출물 생성 → nothing-to-do 시 백로그 발굴(코드 직접 점검).

## 진행
1. 메모리 복원: consensus.md / handoff.md / 에이전트 정의(`bueongi-backend.md`) 읽음.
   (절차가 가리킨 `.claude/agents/backend.md`·`.claude/skills/backlog-discovery/`는 부재 → 실제 존재 파일 기준 진행.)
2. 베이스라인: `./gradlew test` BUILD SUCCESSFUL, 97개 통과(깨진 것 없음).
3. 도메인 검증·에러 계약·가드 단일화·외부 데이터 경계(양방향)·SAFE floor·시설 경로 위 배치가 모두 포화 → 백로그 발굴(코드 직접 점검).
4. RouteService/RouteType/Geo/SafetySpotService 정독 중 **미고정 핵심 가치 약속 발견**: `RouteType` doc 은 "safe→main→fast 로
   갈수록 시설이 준다"를 명시하고, 안심시설(cctv/bell/police) per-km 밀도가 SAFE>MAIN>FAST 로 엄격히 단조 감소하도록 enum 상수가 잡혀 있다.
   이것이 곧 앱의 핵심 가치(추천 경로=가장 안심시설 많은 길)인데, `RouteTypeTest`는 distanceFactor 단조성만, `SafetySpotServiceTest`는
   total 기준 SAFE>FAST(MAIN 누락)만 고정 → 밀도 단조성이 상수 레벨에서 미고정.

## 산출물
- **소스 0 수정 / 테스트 2 신규(97→99)**: `src/test/kotlin/io/soo/bueongi/routes/RouteTypeTest.kt`
  - `추천 경로일수록 안심시설(CCTV-비상벨-경찰) 밀도가 엄격히 높다`: cctvPerKm/bellPerKm/policePerKm 가 각각 SAFE>MAIN>FAST 임을 상수 원천에서 고정.
  - `편의점 밀도는 안심시설과 달리 주요 도로(MAIN)에서 가장 높다`: 대조군 — storePerKm 은 의도적 비단조(MAIN 최대=상업/유동인구)임을 고정해 단조성 확장 실수 방지.
- 위험 근거: `total` 비교는 store(MAIN 최대)·distanceFactor 가 섞여 cctv/bell 밀도 역전을 가린다 → cctv 밀도가 역전돼도 SAFE>FAST total 은 통과 가능.
  밀도 상수 튜닝 시 '추천 경로가 per-km 안심시설 최다'라는 핵심 안전 순서가 조용히 깨질 수 있어 상수 원천에서 직접 봉인.
- 순수 가산적 가드 테스트 — 외부 동작/응답 계약 완전 불변.

## 검증
- `./gradlew test` → BUILD SUCCESSFUL.
- 테스트 **99개 전부 통과**(failures=0, errors=0). 직전 97 → +2. (`build/test-results/test/*.xml` tests 합산=99, failures=0, errors=0 확인.)
- RouteType 카운트 5→7, 그 외 클래스 무변동(거동 보존).

## 잔여·인계
- **Frontend(#1, 명확한 병목)**: `/api/routes/compare` 클라이언트 스켈레톤 + 응답 분기(400/422 4종). 백엔드 계약 전부 준비 완료. `GET /api/health` 로 가동/소스 상태 확인 가능.
- **Backend(실질 잔여 없음)**: 도메인 검증·운영 가시성·에러 계약·가드 단일화·외부 경계(양방향)·도메인 안전 불변식(floor + 경로 위 배치 + per-km 밀도 단조성) 전부 포화.
  잔여 실질 후보는 실 외부 API(Tmap/Kakao 라우팅·공공 시설데이터) 연동뿐 — 키/네트워크 의존, MVP 범위 밖. 다음 사이클 nothing-to-do 가능성 매우 큼.
- secret 노출 없음, 사용량 제한 미도달, fallback 전환 없음, git 커밋/push 없음(승인 영역).
