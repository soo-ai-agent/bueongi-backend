# [backend] 18회차 — 위치 공유(보호자) 서버 백엔드 설계 제안서

- 메타: roleId=backend / engine=claude(opus) / 2026-06-19 KST / projectPath=apps/bueongi/backend

## 요청
- Dorothy 자동 루프 backend 역할 "다음 한 사이클". 깨진 빌드 우선 수정, 없으면 검증 가능한 산출물 1개. nothing-to-do 면 백로그 발굴.

## 진행
- 상태 복원: consensus.md(17회차)·handoff.md(17회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음.
  (절차가 가리킨 `.claude/agents/backend.md`·`.claude/skills/backlog-discovery/`는 여전히 부재 → 실존 파일 기준.)
- 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, **tests=108/skipped=0/failures=0/errors=0**. 깨진 빌드/테스트 없음.
- **칸반 점검(신규 가치 발견)**: `list_tasks` 24건(지난 18건 → +6). 신규 `safe-return-mvp` 에픽 6태스크 확인. 그 중
  **`cdb5314d [Backend/Server] Redis 기반 위치 공유 최소 서버 설계/구현`** 이 명백한 backend 신규 영역(label `role:Backend/Server`).
  17사이클 연속 포화였던 기존 경로/장소 stateless MVP 와 **본질이 다른 신규 기능**(stateful·실시간·TTL).
- 의사결정: 무작정 Redis 를 기존 stateless 앱에 붙이는 것은 (a) 신규 인프라 의존성=비용/아키텍처 결정(승인 영역),
  (b) 상위 PM 설계(`03b7bcf7`, high)가 시스템 구성/AC 미확정 — 선구현은 잘못된 가정 위험. 따라서 이번 사이클은
  **역할 핵심 출력물인 "API 계약 명세"를 신규 기능에 대해 설계 제안서로 산출**(설계가 구현에 선행, 코드/인프라 무변경, 승인 불요).

## 산출물
- **`docs/LOCATION_SHARING_DESIGN.md`(신규)** — 위치 공유 서버 백엔드 설계 제안서:
  - 배경/요구 + 기존 stateless 계약과의 본질 차이(TTL 필요) 명시.
  - **API 계약 4종 제안**: `POST /api/share/sessions`(세션 시작) / `PUT .../location`(주기 갱신) / `GET .../{shareId}`(보호자 조회, 204=위치 전·404=만료·`stale` 플래그) / `DELETE`(종료, 멱등).
  - 신규 에러 code `SHARE_SESSION_NOT_FOUND`(404) + 좌표 검증은 기존 `VALIDATION_FAILED`/`MALFORMED_REQUEST` 재사용.
  - Redis 데이터 모델(키·위치 TTL 90s·세션 TTL 6h·PII 미저장) + 코드 배치(`io/soo/bueongi/share/`, `LocationStore` 추상화로 Redis/인메모리 교체 — 장소 `PlaceProvider` 패턴 차용, CI 무중단).
  - env(`BUEONGI_REDIS_URL` 미설정→인메모리 폴백) + `/api/health.locationStore` 라벨(placeSource 패턴 차용, 자격증명 미노출).
  - 수용 기준 7항(PM 03b7bcf7 / QA c5f4dddb 입력) + **미결정/승인 게이트 §8**(Redis 인프라 승인·통합vs별서비스 스코프·토큰 인증 모델·폴링 vs 푸시).
- 일관성: `/api` 프리픽스·CORS·RFC7807+`code`·좌표 규약·isEnabled 런타임 게이트(빈 env 함정 회피, consensus 11:30 교훈) 전부 기존 컨벤션 재사용.
- 칸반 `cdb5314d` 진행 20% 기록(설계 제안 드래프트 — PM 비준·인프라 승인 대기 신호).

## 검증
- `./gradlew test --rerun-tasks` → BUILD SUCCESSFUL, tests=108/0/0/0(클린). **소스/테스트 무변경**(문서 1파일 추가 → 빌드 영향 0).
- 문서의 모든 계약 요소를 기존 컨벤션과 대조: `/api` 프리픽스·CORS env·RFC7807 code 스키마·좌표 범위·health 라벨 패턴 일치.
- 신규 파일 2개: 설계 제안서 1 + 본 보고서 1. 시크릿/키 값 미노출(env 이름만).

## 잔여·인계
- **구현은 다음 단계**(게이트): ① PM(03b7bcf7) 비준 + Redis 인프라/스코프(통합 vs 별서비스) 승인 → ② backend 사이클에서 인메모리 스토어 우선 구현(계약·테스트 그린) → Redis 구현 추가.
- frontend(78eb6275)는 본 설계 §3 계약으로 보호자 웹 폴링 연동을 인메모리 스토어 기동만으로 병행 가능.
- 기존 stateless MVP(경로/장소)는 여전히 포화 — 새 가치는 이 위치공유 에픽 또는 실 외부 API 키에서만.
- escalate 미호출: Redis 인프라 승인은 **설계 비준 후** 구체 사양과 함께 올리는 게 맞음(현 단계는 설계 제안이라 아직 막힘 아님). report-issue 미호출(새 버그/실패 없음).
