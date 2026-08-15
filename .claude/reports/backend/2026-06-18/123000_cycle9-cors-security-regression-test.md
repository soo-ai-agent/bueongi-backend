# 작업 보고서 — bueongi-backend 9회차 (CORS 보안 회귀 테스트 고정)

- 메타: roleId=backend / engine=claude(opus) / 2026-06-18 (KST) / 사이클 9회차
- 분류: 백로그 발굴(코드 직접 점검) → 미고정 보안 갭 봉인 (소스 무변경, 순수 가산적 테스트)

## 요청
- Dorothy 자동 루프 backend 역할: 직전 상태 복원 → 다음 한 사이클 결정적 수행 → 깨진 빌드/테스트 최우선 수정, 최소 1개 검증 가능 산출물 → 베이턴(consensus/handoff) 갱신.

## 진행
1. 상태 복원: `consensus.md`/`handoff.md`/에이전트 정의(`.claude/agents/bueongi-backend.md`) 읽음. 직전 8회차까지 연속 5회 nothing-to-do.
2. 베이스라인: `./gradlew test --rerun-tasks` → 99개 전부 통과(failures=0). 깨진 것 없음.
3. **점검 각도 전환**: 매 사이클 동일 nothing-to-do 결론을 기계적으로 반복하지 않고, main 소스 19개 중 **전용 테스트가 없는 파일**을 식별 → `config/WebCorsConfig.kt`, `places/PlaceCatalog.kt` 2개.
   - 4~8회차 "포화" 점검은 핵심 도메인 4종(RouteService/Geo/RouteType/SafetySpotService)·에러 계약만 봤고 **config/보안 설정은 미점검**이었음 = 실제 미고정 갭.
   - `WebCorsConfig`: `/api/**` 에 allowed-origins(기본 dev `http://localhost:3619`)만, 메서드 GET/POST/OPTIONS 만 허용. CORS 오설정은 안심앱의 대표적 실 보안 결함인데 회귀 테스트 전무.
   - `PlaceCatalog`: 순수 시드 데이터(로직 없음, 좌표 유효성은 KakaoPlaceProvider 경계가 이미 커버) → 테스트 대상 아님으로 판단, 추가 안 함.
4. CORS e2e 회귀 테스트 작성(`@SpringBootTest @AutoConfigureMockMvc` + MockMvc 프리플라이트). 컴파일 에러(allow-methods 헤더 매칭에 `stringValues` 대신 `string(name, Matcher<String>)` 필요) 1회 수정 후 통과.

## 산출물
- **신규**: `src/test/kotlin/io/soo/bueongi/config/WebCorsConfigTest.kt` (3건)
  - ① 허용 origin(`http://localhost:3619`) GET 프리플라이트 → 200 + `Access-Control-Allow-Origin` + allow-methods 가 GET/POST 포함·DELETE/PUT/PATCH 불포함.
  - ② 미허용 origin(`http://evil.example`) 프리플라이트 → 403 + allow-origin 헤더 없음 (`allowedOrigins("*")` 와일드카드 회귀 차단).
  - ③ 허용 origin + 미허용 메서드(DELETE) 프리플라이트 → 403 (메서드 목록 확장 회귀 차단).
- **소스 무변경**: 외부 동작/응답 계약 완전 불변(순수 가산적 가드).
- 베이턴 갱신: `consensus.md`(빌드상태·미해결/리스크·Next Action), `handoff.md`(한 일·검증·교훈·통지·경계).

## 검증
- `./gradlew test --tests 'io.soo.bueongi.config.WebCorsConfigTest'` → tests=3, failures=0, errors=0.
- `./gradlew test --rerun-tasks` → BUILD SUCCESSFUL. 전체 집계 **tests=102, skipped=0, failures=0, errors=0**. 99→102(+3).
- `grep TODO/FIXME/XXX src/main` → 0건. `src/main` 무변경.

## 잔여·인계
- **Frontend(최우선·유일하게 남은 명확한 병목)**: `/api/routes/compare` 클라이언트 + 4종 422/400 응답 분기. 백엔드 계약 전부 준비 완료, 프론트 분기만 남음(projectPath 밖).
- **Backend**: main 소스 19개 전부 커버됨(9회차에 마지막 미점검 config 봉인). 실제 미고정 갭 없으면 다음 사이클 nothing-to-do 정상. 새 가치는 Frontend 진행 또는 실 외부 API 키 주입(MVP 범위 밖, 합의된 보류 — 재에스컬레이션 금지)에서만 발생.
- 교훈: "포화" 단정 전 점검 범위에 config/resources/security 설정도 포함할 것. 매 사이클 점검 각도를 바꾸면 누락 갭이 드러난다.
- git 커밋·push 없음(승인 영역). secret 노출·사용량 우회·escalate 없음.
