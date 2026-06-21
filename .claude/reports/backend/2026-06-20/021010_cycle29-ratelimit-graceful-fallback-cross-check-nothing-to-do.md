# cycle29 — 외부 API rate-limit/throttle graceful 처리 교차검증 (nothing-to-do)

- 일시: 2026-06-20 02:10 (KST) / 역할: backend / 엔진: Claude(opus)
- 결과: **nothing-to-do** (코드/테스트/문서 변경 0건, 순수 검증)

## 요청
Dorothy 자동 루프 backend 사이클. consensus(28)·handoff(28)·에이전트 정의 복원 후 "다음 한 사이클" 수행.
깨진 빌드/테스트 최우선 수정 + 최소 1개 검증 가능 산출물.

## 진행
1. 상태 복원: `consensus.md`(28회차)·`handoff.md`(28회차)·`bueongi-backend.md` 읽음.
   (절차가 가리킨 `.claude/agents/backend.md`·`.claude/skills/backlog-discovery/` 는 여전히 부재 → 실존 파일 기준.)
2. 베이스라인: `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, **tests=150/0/0/0**(클린 5 executed). 깨진 빌드/테스트 없음 — 28회차와 일치.
3. 칸반 점검(`list_tasks` **28건**): 내 영역 `cdb5314d` planned 100% done. bueongi backend 신규 할당 0건.
   `556ffb65`(`/v1/` torn-classpath 500) backlog 유지 = triplan. `6a85b153`(QG-2)는 이제 **done**. 둘 다 경계 밖.
4. **점검 각도(27회차 패턴 — 활성 형제 프로젝트(triplan) 발굴을 내 경계에 코드로 교차 적용, 28회차까지 미점검 신규 각도)**:
   triplan 이 활발히 다룬 **외부 API rate-limit/throttle graceful 처리** 위험 클래스
   (`07d1808f` Mobility throttle cooldown / `55deb5fc` rate-limited exception subtype caller-layer 가드)가
   bueongi 백엔드에 봉인됐는가를 코드로 독립 검증.

## 산출물 (검증 결과 — drift 0, 갭 0)
**bueongi 는 rate-limit/throttle 클래스에 이미 안전 + 회귀 가드 봉인됨.**

1. **외부 호출 표면 전수(grep `RestClient|WebClient|RestTemplate|HttpClient`)**: 유일한 실제 외부 호출처 = `places/KakaoPlaceProvider.kt`.
   `routes/RouteService.kt` 의 "Kakao" 매치는 **주석뿐**(순수 haversine 결정론 MVP, 외부 호출 0건) → 라우팅 경로는 rate-limit 노출 자체가 없음.
2. **장소 검색 폴백(`PlaceSearchService.search`)**: 외부 키 설정 시 `remoteProvider.search()` 를 `try { } catch (e: Exception)` 로 감싸
   **모든 예외(5xx·429/throttle·timeout·파싱오류 무관) → 시드 폴백 + log.warn**. 즉 rate-limit/429 도 catch-all 로 graceful 강등.
   triplan 이 retrofit 하려는 "예외 서브타입별 graceful 처리"(`55deb5fc`)를 **catch-all 로 전 실패 모드 일괄 커버** → 구조적으로 더 강함.
3. **회귀 가드 존재**: `PlaceSearchServiceSourceTest.외부 검색 실패(예외)면 시드로 폴백` 이 remote 가 예외를 던질 때
   ① 외부가 실제 호출됐고 ② 결과가 시드 카탈로그임을 단언 → 누가 catch 를 제거/약화하면 즉시 실패.
4. 따라서 외부 API 한도/장애 시 "검색 불능"이 아니라 "근사(시드) 결과 제공" — 안심 귀가 앱 가용성 계약이 코드+테스트로 봉인.

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL, tests=150/0/0/0(클린).
- 소스/테스트/문서 변경 0건(순수 검증). 시크릿 비노출(키 값 미기재). escalate/report-issue 미호출(새 막힘·버그 없음).

## 잔여·인계
- **rate-limit/throttle(29) + 부팅 무결성(28) + share 계약(22~25) + 500 정보노출(27) 모두 미고정 갭 0** → 다음 사이클도 베이스라인 그린(150) 확인 후 nothing-to-do 가 정상.
- Frontend `78eb6275`(보호자 웹 지도) 지금 연동 가능: `docs/API.md` §`/share` 4종. 함정: 경로 `/share`(not `/api`), POST(not PUT), DELETE 없음, 조회 항상 200+`expired`, TTL 1~3h.
- 신규 bueongi backend 태스크 등장 시에만 착수. 칸반·베이스라인·신규 소스↔테스트 매핑 매 사이클 코드로 교차 확인.
