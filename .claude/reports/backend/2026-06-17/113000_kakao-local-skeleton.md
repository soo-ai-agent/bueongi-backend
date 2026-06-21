# 작업 보고서 — Kakao Local 실연동 스켈레톤

- 메타: roleId=backend / 엔진=Claude(opus) / 2026-06-17 11:30 KST
- 프로젝트: bueongi-backend (`/Users/soo/workspace/source-code/apps/bueongi/backend`)
- 사이클 기준: consensus.md Next Action #2

## 1. 요청
- `/api/places/search` 의 외부 지도 API(Kakao Local) 실연동 스켈레톤 구축.
  키가 있으면 실검색, 없으면(또는 실패 시) 내장 시드 카탈로그로 폴백. 응답 계약 `PlaceItem` 은 불변(프론트 무영향).

## 2. 진행
- 베이스라인 검증: `./gradlew test` SUCCESSFUL, 45개 통과(깨진 빌드 없음).
- 소스 추상화 설계: `PlaceProvider`(공통) / `RemotePlaceProvider`(외부, `isEnabled` 게이트) / `SeedPlaceProvider`(폴백) / `KakaoPlaceProvider`(실연동).
- `PlaceSearchService` 를 소스 선택·폴백 조정자로 재작성(트림/빈값 가드 + 예외 시 시드 폴백).
- 설계 판단: application.yml 이 키를 항상 빈 값으로 주입하므로 `@ConditionalOnProperty` 대신 런타임 `isEnabled` 게이트 채택.
- 테스트 슬라이스 수정 중 `@WebMvcTest` + nested `@Configuration` 의 컨트롤러 자동등록 대체(404) 함정 발견 → `@TestConfiguration` 으로 해결.

## 3. 산출물
- 신규: `places/PlaceProvider.kt`, `places/RemotePlaceProvider.kt`, `places/SeedPlaceProvider.kt`, `places/KakaoPlaceProvider.kt`(+Kakao 응답 DTO).
- 수정: `places/PlaceSearchService.kt`(재작성).
- 테스트 신규: `places/PlaceSearchServiceSourceTest.kt`(5), `places/KakaoPlaceProviderTest.kt`(6), `BueongiContextLoadTest.kt`(2).
- 테스트 수정: `places/PlaceSearchServiceTest.kt`(생성자), `places/PlaceControllerTest.kt`(@TestConfiguration disabled remote).

## 4. 검증
- `./gradlew test` → BUILD SUCCESSFUL.
- 테스트 **58개 전부 통과**(failures=0, errors=0). 직전 45 → +13.
- 컨텍스트 로드 테스트로 실 프로덕션 DI 배선(자동구성 RestClient.Builder 포함) + 무키 시드폴백 경로 검증.
- 응답 계약 변경 없음 → 프론트 통지 불필요(consensus.md 기록).

## 5. 잔여·인계
- 잔여: Kakao 실 HTTP 통합테스트(MockWebServer/MockRestServiceServer 로 성공·5xx 폴백 e2e)는 미작성 — 단위 매핑·게이트만 커버.
- 인계(Next Action): ① Frontend `/api/routes/compare` 클라이언트(400/422 분기) ② Backend keyword 길이/공백 검증
  ③ origin==destination 등 도메인 예외 코드 확장 ④ Kakao HTTP 통합테스트.
- 경계: git 커밋·push 안 함(승인 영역). secret 미노출. 사용량 제한 미도달.
