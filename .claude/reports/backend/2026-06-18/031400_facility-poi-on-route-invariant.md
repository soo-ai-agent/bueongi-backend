# 안심시설 POI 경로 위 배치 불변식 회귀 테스트 고정

- 역할(roleId): backend
- 엔진: claude (claude-opus)
- 일시: 2026-06-18 03:14 (KST)
- 사이클: 자동 루프(backend) 2026-06-18 2회차

## 요청
자동 루프 사이클: 직전 상태(consensus.md/handoff.md) 복원 → 깨진 빌드/테스트 우선 수정 →
최소 1개 검증 가능 산출물 생성 → nothing-to-do 시 백로그 발굴(코드 직접 점검).

## 진행
1. 메모리 복원: consensus.md / handoff.md / 에이전트 정의(`bueongi-backend.md`) 읽음.
2. 베이스라인: `./gradlew test` BUILD SUCCESSFUL, 96개 통과(깨진 것 없음).
3. 도메인 검증·에러 계약·가드 단일화·외부 데이터 경계·SAFE floor 가 모두 포화 → 백로그 발굴(코드 직접 점검).
4. **미고정 핵심 도메인 불변식 발견**: `FacilityService.addFacilities` 가 시설 lat/lng 를 `o + (d-o)*t`
   선형 보간으로 출발→도착 선분 위에 배치하는데, 기존 테스트는 개수·x/y(정규화 표시 좌표) 범위·summary 일치·floor·결정론만
   고정할 뿐 시설 **지리 좌표(lat/lng)가 실제 경로(선분) 위에 있는지**는 미검증. 배치 리팩터로 경로 이탈해도 기존 테스트는 전부 통과.

## 산출물
- **소스 0 수정 / 테스트 1 신규(96→97)**: `src/test/kotlin/io/soo/bueongi/routes/FacilityServiceTest.kt`
  - `안심시설 POI 는 출발-도착 직선 경로 위에 놓인다(공선성 + 구간 내)`:
    11km safe 경로의 모든 시설 POI(cctv/bell/store/police)에 대해 ① 2D 외적 `(P−O)×(D−O) ≈ 0`(within 1e-9, 공선성)
    ② lat/lng 가 출발-도착 바운딩 박스 안(연장선 아닌 구간 내)을 고정.
  - `import org.assertj.core.api.Assertions.within` 추가. DTO 좌표가 `Double?`라 비교용 좌표는 로컬 non-null `val` 로 추출.
- 순수 가산적 가드 테스트 — 외부 동작/응답 계약 완전 불변.

## 검증
- `./gradlew test` → BUILD SUCCESSFUL.
- 테스트 **97개 전부 통과**(failures=0, errors=0). 직전 96 → +1. (`build/test-results/test/*.xml` 합산 확인.)
- FacilityService 카운트 12→13, 그 외 클래스 무변동(거동 보존).

## 잔여·인계
- **Frontend(#1, 명확한 병목)**: `/api/routes/compare` 클라이언트 스켈레톤 + 응답 분기(400/422 4종). 백엔드 계약 전부 준비 완료.
- **Backend(실질 잔여 없음)**: 도메인 검증·운영 가시성·에러 계약·가드 단일화·외부 경계·도메인 안전 불변식(floor + 경로 위 배치) 전부 포화.
  잔여 실질 후보는 실 외부 API(Tmap/Kakao 라우팅·공공 시설데이터) 연동뿐 — 키/네트워크 의존, MVP 범위 밖. 다음 사이클 nothing-to-do 가능성 큼.
- secret 노출 없음, 사용량 제한 미도달, fallback 전환 없음, git 커밋/push 없음(승인 영역).
