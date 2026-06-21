# backend 34회차 — Kakao REST 키 클라이언트 노출(SEC-KAKAO-REST-KEY-IN-BUNDLE) 내 경계 교차검증

## 요청
Dorothy 자동 루프(backend 역할) 1 사이클. 깨진 빌드/테스트 최우선 수정 후 최소 1개 검증 가능한 산출물. nothing-to-do면 백로그 발굴.

## 진행
- 상태 복원: consensus.md(33회차)·handoff.md(33회차)·에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음. (`.claude/skills/backlog-discovery/`는 여전히 부재 → 실존 파일 기준.)
- 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, 5 executed. 깨진 빌드/테스트 없음 — 33회차(150 그린)와 일치.
- 칸반 점검(`list_tasks` **32건**, 33회차와 동일 건수): 내 영역 `cdb5314d` planned 100% done. bueongi backend 신규 할당 0건. 11:10 이후 변경 소스 0건.
- **점검 각도(27~33회차 패턴 — 활성 형제(triplan/soo-auth) 보안 발굴을 내 경계에 코드로 교차 적용, 34회차까지 미점검 신규 각도)**: `5f2bced5`(SEC-KAKAO-REST-KEY-IN-BUNDLE — "client 번들에 Kakao REST API 키 인라인 노출, code→token 서버 프록시 필요")는 직전 사이클들에서 "경계 밖"으로 분류만 하고 실제 코드 교차검증은 미수행. 그 위험 클래스("Kakao 키가 클라이언트로 노출되는가, 서버 프록시로 가려져 있는가")가 bueongi 백엔드에 있는지 코드로 독립 검증.

## 산출물
- **결과: 구조적 부재 + 회귀 가드 봉인(drift 0, 갭 0)**. 소스/테스트/문서 변경 0건(순수 검증).
  - ① **서버 프록시 패턴 이미 구조적 존재**: Kakao 키 사용처 전수(grep) = 서버 측 `places/KakaoPlaceProvider.kt`뿐. `@Value("\${bueongi.places.kakao-rest-api-key:}")`로 주입 → `defaultHeader(AUTHORIZATION, "KakaoAK ${apiKey}")`로 **서버→Kakao 호출에만** 사용. FE는 Kakao를 직접 치지 않고 backend `/api/places/search`(`PlaceController.kt:13 @RequestMapping("/api/places")`)를 프록시로 호출 → `5f2bced5`가 요구하는 "code→token 서버 프록시"가 bueongi엔 이미 구조적으로 존재(키는 서버에만, 클라이언트 번들에 키를 박는 구조 자체가 부재).
  - ② **키 값이 클라이언트로 새는 경로 0건**: guardian.html `kakao/key/appkey` grep=0. health 응답은 `placeSource="live"|"seed"` 불리언 파생 라벨만(`HealthController.kt:33` `if (remoteProvider.isEnabled) LIVE else SEED`), 키 문자열 미탑재. `docs/API.md:75`도 "키 값은 절대 노출하지 않는다" 명시. 응답 DTO(`PlaceItem` = name/address/lat/lng)에 키 필드 부재.
  - ③ **회귀 가드 이미 봉인**: `HealthControllerTest.kt:66 키 설정이면 placeSource live 이고 키 값은 응답에 노출되지 않는다`가 `SECRET_KEY="super-secret-kakao-key-value"` 센티넬을 주입하고 placeSource="live" 단언 + 키 값이 응답 어디에도 없음을 검증 → 누가 키를 응답에 싣는 회귀 시 즉시 실패. `application.yml:13 kakao-rest-api-key: ${KAKAO_REST_API_KEY:}` 빈값 기본값으로 키 없으면 시드 graceful 폴백(크래시 아님).

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL(5 executed) — 변경 없음, 150 그린 불변.
- grep 교차검증: Kakao 키 사용처=서버 1파일, 클라이언트/HTML/health 노출 경로 0건, 회귀 가드 1건 실존.

## 잔여·인계
- **`5f2bced5`(34, 구조적 부재) + runbook bootRun(33) + stored XSS(32) + 환경변수 드리프트(31) + 동시성(30) + rate-limit(29) + 부팅 무결성(28) + 500 정보노출(27) + share 계약(22~26) 모두 미고정 갭 0**: 다음 사이클도 베이스라인 그린(150) 확인 후 nothing-to-do 가 정상(억지 마이크로 가드/리팩터/문서 재작성 금지).
- Frontend `78eb6275` 지금 연동 가능: `docs/API.md` §`/share` 4종.
- 신규 태스크 전부 triplan/soo-auth/bueongi frontend-src — bueongi backend 신규 태스크 등장 시에만 착수.
- escalate/report-issue 미호출(새 막힘·버그 없음). 시크릿 비노출(센티넬·라벨만 기재, 실 키 값 미노출).

## 메타
- 엔진: Claude(opus, backend). 사이클: 34. 산출물 유형: nothing-to-do(코드 레벨 독립 due-diligence 교차검증).
