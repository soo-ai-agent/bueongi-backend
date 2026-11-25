---
paths:
  - "core/core-admin-auth/**"
  - "core/**/api/controller/**"
name: kotlin-auth
description: 인증과 인가 규칙. core-admin-auth 의 SecurityConfig(관리자 체인 /api/admin/** + 공개 체인), 관리자 JWT, 인증 정보를 컨트롤러에서만 꺼내 도메인에 값으로 넘기는 법, 소유자 스코프를 쿼리 조건으로 거는 법, 401·403·404 구분과 존재 여부 유출 방지, 비밀번호 해시, 토큰 취급을 다룬다. 로그인·권한·내 데이터만 보이는 기능을 만들 때 사용한다. "로그인", "권한", "관리자 API", "내 것만 조회" 요청에도 사용할 것.
---

# 인증과 인가

**자리:** 보안 설정은 `core/core-admin-auth/…/core/admin/auth/domain/config/SecurityConfig.kt` 한 곳이다.

## 이 저장소의 보안 구조 — 체인 둘

`spring-boot-starter-security` 는 의존성만 넣고 설정을 안 하면 **모든 경로에 인증을 요구한다**(헬스체크까지 401 → 컨테이너 재시작 반복).
그래서 `SecurityConfig` 가 필터 체인 둘을 명시한다.

| 순서 | 체인 | 경로 | 규칙 |
|---|---|---|---|
| `@Order(0)` | 관리자 | `securityMatcher("/api/admin/**")` | `anyRequest().hasRole("ADMIN")` + JWT 필터, 무상태 |
| `@Order(1)` | 공개 | 나머지 전부 | `anyRequest().permitAll()` — 앱 사용자는 로그인하지 않는다(위치 공유는 토큰 링크) |

- **관리자 기능은 `/api/admin/**` 아래에 만든다.** 그 경로는 체인이 통째로 닫으므로 새 엔드포인트가 열린 채 배포될 일이 없다.
  관리자 기능을 그 밖의 경로에 두면 공개 체인에 걸려 **누구나 부를 수 있다.**

- 공개 체인에 새 경로를 둘 때는 "로그인 없이 불러도 되는가"를 먼저 묻는다. 남의 데이터를 돌려주는 공개 경로는 추측할 수 없는 토큰
  (위치 공유의 `/share/{token}`)으로만 닿게 한다.

- 새 보호 영역이 생기면 체인을 하나 더 만들되, **보호할 경로를 `securityMatcher` 로 묶고 `anyRequest()` 로 통째로 닫는다.**
  경로를 하나씩 `authenticated()` 로 나열하면 빠뜨린 새 엔드포인트가 열린 채 나간다.

- `csrf` 를 끄는 것은 **쿠키 세션이 아닐 때만**이다. 지금은 Bearer 토큰이라 끈다. 쿠키로 인증하게 되면 끄지 않는다.

- 401·403 응답도 `ProblemDetail` + `code` 로 낸다 — `AuthenticationEntryPoint`·`AccessDeniedHandler` 가
  `MissingTokenException`·`AdminRoleRequiredException` 을 같은 모양으로 쓴다(**kotlin-error**).

## 인증(누구인가)과 인가(무엇을 할 수 있나)는 다른 자리에서 다룬다

| | 무엇을 묻나 | 어디서 |
|---|---|---|
| **인증** | 이 요청이 누구인가 | 필터·`SecurityConfig` — 컨트롤러에 오기 전에 끝난다 |
| **인가** | 이 사람이 이걸 해도 되나 | 기능 단위 역할(ADMIN)은 체인, 소유권 같은 업무 규칙은 **도메인** |

컨트롤러는 둘 다 하지 않는다. **인증 결과를 꺼내 도메인에 값으로 넘기는 것**이 전부다.

## 컨트롤러 — 꺼내서 넘기기만

```kotlin
@GetMapping
fun list(
    @AuthenticationPrincipal member: MemberPrincipal,
    pageable: Pageable,
): List<TodoResponse> {
    // 식별자만 넘긴다
    val results: List<TodoResult> = todoService.list(member.id, pageable)
    return TodoResponse.from(results)
}
```

- **`SecurityContextHolder` 를 도메인에서 부르지 않는다.** 도메인이 웹 요청 문맥을 알게 되면 테스트도 재사용도 어려워진다. 컨트롤러가 꺼내 **파라미터로** 내린다.

- 넘기는 것은 **식별자와 권한**이지 인증 객체 전체가 아니다. 도메인 서비스 시그니처에 `MemberPrincipal` 이 보이면 위반이다.

- 클라이언트가 보낸 값에서 사용자 식별자를 받지 않는다. `@RequestParam memberId` 는 남의 데이터를 달라고 하는 통로다 — **항상 인증 정보에서 가져온다.**

## 인가 — 쿼리 조건으로 건다

"내 것만 보인다"를 코드에서 `if` 로 확인하지 않는다. **처음부터 내 것만 조회한다.**

```kotlin
// ❌ 다 꺼내 놓고 나중에 확인 — 목록에서는 아예 걸러지지도 않는다
val todo: TodoEntity = todoRepository.findOneById(id)
if (todo.memberId != memberId) {
    throw TodoForbiddenException()
}

// ✅ 소유자를 쿼리 조건에 넣는다
fun findByIdAndMemberId(id: Long, memberId: Long): TodoEntity?
```

수정·삭제도 마찬가지다. **소유자 조건이 빠진 `deleteById(id)` 는 남의 데이터를 지운다.**

역할(`ADMIN` 등)은 체인의 `hasRole` 로 막고, 둘 이상 모듈이 역할 값을 쓰게 되면 `core-enum` 의 enum 으로 둔다.

## 상태코드 — 존재 여부를 흘리지 않는다

| 상황 | 상태 |
|---|---|
| 인증 자체가 없음·만료 | 401 |
| 인증은 됐지만 그 **기능**을 쓸 역할이 없음 (관리자 API 에 일반 토큰) | 403 |
| 남의 소유물이거나 없음 | **404 (둘 다)** |

**소유자 스코프가 있는 자원에서 403 과 404 를 구분해 응답하면, 남의 데이터가 존재한다는 사실이 새어 나간다.** 위 쿼리 방식이면 자연스럽게 404 하나로 떨어진다.

## 비밀번호와 토큰

- 비밀번호는 **해시로만 저장한다.** 관리자 비밀번호는 bcrypt 해시(`BUEONGI_ADMIN_PASSWORD_HASH`)로만 받는다. 직접 만든 해시나 양방향 암호화를 쓰지 않는다.

- 로그인 실패 응답에 "없는 아이디"와 "틀린 비밀번호"를 구분해 주지 않는다. 계정 존재 여부가 새어 나간다.

- **해시·토큰·리프레시 토큰을 응답 DTO ·로그·예외 메시지에 넣지 않는다** (`kotlin-error`·`kotlin-config`).

- 서명 키(`JWT_PRIVATE_KEY`·`JWT_PUBLIC_KEY`)는 저장소에 두지 않고 환경변수로 받는다 (`kotlin-config`). `local` 프로파일만 키가 없을 때 임시 키쌍을 만든다.

- 만료를 반드시 둔다. 무기한 토큰은 유출되면 회수할 방법이 없다. 리프레시 토큰으로 관리자 API 를 부를 수 없어야 한다.

## 테스트

인가는 유닛 테스트에서 스프링 없이 검증한다. **도메인 서비스에 식별자를 값으로 넘기는 구조라서 가능하다.**

- 내 것만 조회되는지: 다른 `memberId` 로 조회했을 때 결과가 비어 있는지 확인한다.

- 남의 것을 지우려 할 때: 소유자 조건이 걸린 리포지토리 메서드가 호출되는지 확인한다.

- 체인(401·403·공개 경로 열림)은 `core-app` 의 웹 테스트(`context` 태그, `spring-security-test`)에서 확인한다(**kotlin-test**).

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 관리자 기능을 `/api/admin/**` 밖 경로에 둠 | 공개 체인에 걸려 누구나 호출 | Critical |
| security 의존성만 넣고 `SecurityFilterChain` 없음 | `/api/health` 가 401 — 배포 컨테이너가 재시작 반복 | Critical |
| 보호 체인을 경로 나열 + `authenticated()` 로만 구성 | 새 엔드포인트가 기본으로 열린 채 배포됨 | Critical |
| 사용자 식별자를 요청 파라미터·본문에서 받음 | 남의 데이터 접근 통로 | Critical |
| 소유자 조건 없는 조회·수정·삭제 쿼리 | 남의 데이터 노출·파손 | Critical |
| 소유자 스코프 자원에서 403 과 404 를 구분해 응답 | 존재 여부 유출 | Critical |
| 비밀번호를 평문·양방향 암호화로 저장 | 유출 시 즉시 도용 | Critical |
| 해시·토큰이 응답·로그·예외 메시지에 등장 | 자격 증명 유출 | Critical |
| 로그인 실패를 "없는 계정"·"틀린 비밀번호"로 구분 | 계정 존재 여부 유출 | Critical |
| 서명 키·시크릿이 yml·코드에 값으로 존재 | 저장소 이력에 영구 유출 | Critical |
| 도메인에서 `SecurityContextHolder` 호출 | 도메인이 웹 문맥에 결합 | Important |
| 도메인 서비스 시그니처에 `Principal` 타입 | 레이어 침범 | Important |
| 만료 없는 토큰 | 유출 시 회수 불가 | Important |

## 체크리스트

- [ ] 관리자 기능이 `/api/admin/**` 아래에 있는가, 공개 경로는 로그인 없이 불러도 되는 것인가

- [ ] `/api/health` 가 인증 없이 200 인가

- [ ] 사용자 식별자를 인증 정보에서만 가져오는가 (요청 값이 아닌가)

- [ ] 조회·수정·삭제 쿼리에 소유자 조건이 걸렸는가

- [ ] 남의 소유물과 없는 자원이 똑같이 404 로 나가는가

- [ ] 비밀번호가 단방향 해시로만 저장되는가

- [ ] 토큰·해시·키가 응답·로그·저장소 어디에도 없는가

- [ ] 도메인 서비스가 식별자만 값으로 받는가
