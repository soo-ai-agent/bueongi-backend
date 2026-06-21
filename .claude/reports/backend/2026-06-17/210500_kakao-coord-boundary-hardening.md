# 작업 보고서 — Kakao 외부 좌표 경계 강화

- 역할: backend (bueongi-backend)
- 엔진: Claude(opus)
- 일시: 2026-06-17 21:05 (KST)
- projectPath: /Users/soo/workspace/source-code/apps/bueongi/backend

## 요청
자동 루프(backend) 사이클 — consensus.md/handoff.md 복원 후 "다음 한 사이클" 수행.
깨진 빌드/테스트 우선 수정, 없으면 백로그 발굴로 다음 작업 발굴 + 최소 1개 검증 가능 산출물.

## 진행
- 상태 복원: consensus.md / handoff.md / 에이전트 정의(`bueongi-backend.md`) 읽음. (절차가 가리킨 `backend.md`·`skills/backlog-discovery/`는 부재 → 실제 존재 파일 기준 진행.)
- 베이스라인: `./gradlew test` BUILD SUCCESSFUL, 87 테스트 통과(깨진 것 없음).
- 도메인/에러 계약·가드 단일화가 포화 상태 → 백로그 발굴(코드 직접 점검) 수행.
- 외부 데이터 경계 갭 발견: `KakaoPlaceDocument.toPlaceItem()` 이 좌표를 `toDoubleOrNull()` 로만 파싱.
  `"NaN"`→Double.NaN, `"Infinity"`→∞, 범위 밖 숫자도 파싱 통과 → `PlaceItem` 으로 누출 → 프론트→`RouteRequest.destination`.
  haversine 이 NaN 거리를 내면 거리 가드(`<`/`>` 비교가 NaN 에 항상 false)를 우회 → garbage 경로/시설(안심앱 위험).

## 산출물
- 소스 1 수정: `places/KakaoPlaceProvider.kt`
  - `toPlaceItem()`: 좌표 파싱 뒤 `takeIf { it.isFinite() && it in <범위> }`(위도 -90~90 / 경도 -180~180). 비유효 시 null(제외).
    기존 "필수 누락/파싱 실패 건 제외" 정책과 동일하게 외부 경계에서 한 번에 거른다. KDoc 에 사유 명시.
- 테스트 3 신규: `places/KakaoPlaceProviderTest.kt`
  - 비유한 좌표(NaN/±Infinity)→제외 / 지리 범위 밖(위도>90·경도>180·위도<-90)→제외 / 범위 경계(±90,±180)→유효.
- 메모리 갱신: consensus.md(상태·해결됨·Next Action), handoff.md(이번 사이클/함정/인계).
- 순수 가산적 강화: 정상 Kakao 응답·시드 경로·`PlaceItem` 응답 계약 완전 불변(프론트 무영향).

## 검증
- `./gradlew test` → BUILD SUCCESSFUL.
- 테스트 90개 전부 통과(failures=0, errors=0). 직전 87 → +3. (`build/test-results/test/*.xml` tests 합산=90, failures=0 확인.)
- 기존 매핑/HTTP/서비스/컨트롤러 테스트 무변경 통과 → 거동 보존 증명.

## 잔여·인계
- Frontend(최우선·명확한 병목): `/api/routes/compare` 클라이언트 스켈레톤 + 응답 분기(400/422 4종). 백엔드 계약 전부 준비 완료.
- Backend 실질 잔여 없음(도메인 검증·운영 가시성·에러 계약·가드 단일화·외부 데이터 경계 모두 포화).
  다음 backend 사이클 nothing-to-do 가능성 큼 → 백로그 발굴 재수행. 잔여 후보는 실 외부 API 연동(키/네트워크 의존, MVP 범위 밖).
- git 커밋·push 안 함(승인 영역). secret 노출 없음. 사용량 제한 미도달, 우회 없음. fallback 전환 없음.
