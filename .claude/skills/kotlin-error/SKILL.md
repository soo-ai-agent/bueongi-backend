---
paths:
  - "core/**/error/**"
  - "core/core-common/**"
name: kotlin-error
description: 실패를 표현하는 규칙. 예외가 자기 응답(status·code·title·detail)을 갖는 ApiException 베이스(core-common), ApiControllerAdvice 가 ProblemDetail + code 로 내보내는 방식, 예외를 던지는 도메인이 소유하는 규칙, 새 실패 유형을 추가하는 순서, HTTP 상태코드와 로그 레벨 고르는 기준, storage·clients 실패를 core 에서 번역하기, 응답 메시지에 담아도 되는 것을 다룬다. 기능에 실패 분기를 만들 때 kotlin-domain-service 와 함께 사용한다. "예외 처리", "에러 응답", "404 를 주고 싶다" 요청에도 사용할 것.
---

# 에러 설계

**자리:** 베이스는 `core/core-common/…/io/soo/bueongi/core/common/error/`(`ApiException`·`ApiFieldError`·`ApiControllerAdvice`),
구체 예외는 **던지는 도메인**의 `domain/error/`(예: `core-share/…/domain/error/ShareSessionNotFoundException.kt`)

## 응답 모양 — `ProblemDetail` + `code`

실패 응답은 Spring 의 `ProblemDetail`(RFC 9457) 본문이다. 응답 래퍼(`{result, data, error}`)는 없다.

```json
{
  "type": "about:blank",
  "title": "Share Session Not Found",
  "status": 404,
  "detail": "공유 세션을 찾을 수 없거나 만료되었습니다.",
  "instance": "/share/abc/location",
  "code": "SHARE_SESSION_NOT_FOUND"
}
```

검증 실패는 여기에 `errors`(필드별 `field`·`message`·`rejectedValue`)가 더해진다. **`code`·`errors` 는 클라이언트가 분기에 쓰는 와이어 계약이다** —
이름을 바꾸거나 래퍼로 감싸지 않는다([docs/API.md](../../../docs/API.md)).

## 예외가 자기 응답을 안다 — 핸들러를 늘리지 않는다

`@RestControllerAdvice` 에 예외 종류마다 핸들러를 더하고 있다면, advice 가 종류를 확인해
대신 결정하고 있는 것이다. 그 값을 **예외가 들고 오게** 한다. 지금 베이스가 그 모양이다.

```kotlin
// core-common/…/common/error/ApiException.kt (현행)
abstract class ApiException(
    val status: HttpStatus,
    val code: String,
    val title: String,
    val detail: String,                       // 사용자 문구(내부 수치 없음)
    val fieldErrors: List<ApiFieldError> = emptyList(),
    message: String,                          // 로그용. 내부 값을 담아도 된다
) : RuntimeException(message) {
    fun toProblemDetail(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = this@ApiException.title
            setProperty("code", code)
            if (fieldErrors.isNotEmpty()) {
                setProperty("errors", fieldErrors)
            }
        }
}
```

advice 는 종류를 묻지 않고 값만 옮겨 담는다. 새 예외를 추가해도 핸들러는 늘지 않는다.

```kotlin
@ExceptionHandler(ApiException::class)
fun handleApi(ex: ApiException): ProblemDetail {
    events.publishEvent(ApiErrorResponded(code = ex.code, causeName = ex.cause?.javaClass?.simpleName))
    return ex.toProblemDetail()
}
```

**원인(`cause`)을 넘길 자리가 아직 베이스에 없다.** 외부 예외를 감싸는 하위 예외가 처음 생길 때 베이스에
`cause: Throwable? = null` 파라미터를 더하고 `RuntimeException(message, cause)` 로 넘긴다 — 기존 하위 예외는 그대로 컴파일된다.

**프레임워크 예외**는 `ApiException` 을 상속할 수 없어 개별 핸들러가 남는다. 지금 `ApiControllerAdvice` 에 있는 것이 그 목록이고
(`MethodArgumentNotValidException`·`HttpMessageNotReadableException`·`MethodArgumentTypeMismatchException`·`MissingServletRequestParameterException` 등),
모두 같은 `ProblemDetail` + `code`(`VALIDATION_FAILED`·`MALFORMED_REQUEST` 등) 모양으로 낸다. 프레임워크 예외가 새로 문제를 일으킬 때만 늘린다.

| 프레임워크 예외 | 언제 | 응답 |
|---|---|---|
| `MethodArgumentNotValidException` | `@Valid` 검증 실패 | 400 `VALIDATION_FAILED` + `errors` |
| `HttpMessageNotReadableException` | 본문 누락·깨진 JSON | 400 `MALFORMED_REQUEST` (없으면 **500 으로 나간다** — 실측) |

## 예외는 던지는 도메인이 소유한다

`throw` 하는 모듈을 센다.

| 던지는 모듈 | 사는 곳 |
|---|---|
| 한 도메인 | 그 도메인의 `domain/error/` |
| 둘 이상 | `core-common` |
| 베이스·공용 값(`ApiException`·`ApiFieldError`) | `core-common` |
| `storage`·`clients`·`support` | **`ApiException` 을 던지지 않는다** — `core-common` 을 의존하지 않기 때문이다 |

`api/request` 에서 던지는 예외도 `domain/error` 에 둔다(`api/` → `domain/` 은 허용 방향).
**옮긴 뒤 "다른 도메인이 이 예외를 import 하는가"를 세어 0인지 확인한다.**

**storage·clients 의 실패는 core 가 번역한다.** 그 모듈들은 `null`, 표준 예외(`check`·`require` → `IllegalStateException`),
또는 자기 모듈의 예외(`TmapCallFailedException` 같은 `RuntimeException`)로 알린다. 그것을 받는 core 의 구현 레이어가
`ApiException` 하위 예외로 바꿔 던진다(**kotlin-implement**).

## 새 실패를 만났을 때 — 순서대로

**1. 이미 있는 예외가 스스로 가진 status·code 로 되는지 본다.** "할 일이 없음"과 "회원이 없음"이 클라이언트에게 똑같이 취급된다면 하나로 충분하다. 매번 새로 만들면 코드만 늘고 분기는 못 한다.

**2. 안 되면 셋을 함께 추가한다.**

```kotlin
// core/core-<도메인>/…/<도메인>/domain/error/TodoNotFoundException.kt
class TodoNotFoundException(
    todoId: Long,
) : ApiException(
        status = HttpStatus.NOT_FOUND,
        code = "TODO_NOT_FOUND",
        title = "Todo Not Found",
        detail = "할 일을 찾을 수 없습니다.",      // 사용자에게 보이는 문구
        message = "할 일이 없다: id=$todoId",      // 로그용. 내부 값을 담아도 된다
    )
```

`code` 는 클라이언트가 분기에 쓰는 문자열이다. `E404` 같은 상태코드 재탕이 아니라
**무엇이 잘못됐는지**를 말하는 이름으로 짓는다(`TODO_NOT_FOUND`·`SHARE_SESSION_NOT_FOUND`).

**`:` 앞에서 줄을 바꾼 것에 이유가 있다.** 클래스 선언 줄이 길어지면 ktlint 가
`Super type should start on a newline` 으로 막는다. 생성자 인자가 있는 예외는 위 형태로 쓴다.

**3. 도메인에서 던진다.** 던지는 자리는 그 실패를 처음 아는 곳이다 — 보통 구현 레이어나 도메인 서비스다.

```kotlin
fun getById(id: Long): TodoResult {
    val record: TodoRecord = todoStore.findById(id)
        ?: throw TodoNotFoundException(id)
    return record.toResult()
}
```

## 상태코드 고르기

| 상황 | 상태 | 로그 레벨 |
|---|---|---|
| 요청 값이 형식·검증에 어긋남 | 400 | `WARN` |
| 로그인하지 않았거나 토큰이 만료됨 | 401 | `WARN` |
| 로그인했지만 그 **기능**을 쓸 권한이 없음 (관리자 전용 API 에 일반 토큰) | 403 | `WARN` |
| 대상이 없음, 또는 **남의 소유물** | 404 | `WARN` |
| 이미 있음·상태가 맞지 않아 못 함 (중복 가입, 이미 완료) | 409 | `WARN` |
| 의존 저장소가 없음 (Redis 미연결 등) | 503 | `WARN` |
| 우리 코드나 외부 시스템이 잘못됨 | 500 | `ERROR` |

**403 과 404 의 경계는 하나다 — 403 은 "기능" 권한, 남의 소유물은 언제나 404 다.** 소유자 스코프가 있는 자원에서
"있지만 네 것이 아님"을 403 으로 알려주면 남의 데이터가 존재한다는 사실이 새어 나간다(**kotlin-auth**).

**로그 레벨의 기준은 하나다 — "새벽에 깨워야 하는가."**

사용자가 잘못 보낸 요청(4xx)은 정상 동작이므로 `WARN` 이나 `INFO` 다. `ERROR` 는 **우리가 고쳐야 할 것**에만 쓴다. 4xx 를 `ERROR` 로 찍으면 진짜 장애가 로그에 묻히고 Slack 알림이 무뎌진다.

## 응답 메시지에 담아도 되는 것

`ApiException.detail` 은 **사용자에게 그대로 보인다.**

- 담는다: 무엇이 잘못됐고 무엇을 하면 되는지. "할 일을 찾을 수 없습니다."

- 담지 않는다: 스택 트레이스, SQL, 내부 ID, 파일 경로, 다른 사람의 개인정보.

필드별 사유가 필요하면 `ApiException.fieldErrors` 에 담는다 — 응답의 `errors` 로 나간다. 검증 실패가 그 예다.

## 핸들러 순서

핸들러는 **좁은 예외부터 위에, 포괄 `Exception` 은 맨 아래**에 둔다. 순서가 바뀌면 좁은 핸들러가 영영 안 걸린다.

## 잡지 말아야 할 것

- **컨트롤러에서 `try/catch` 하지 않는다.** `ApiControllerAdvice` 가 한 곳에서 번역한다.

- `catch (e: Exception)` 는 전역 핸들러·배치 루프 같은 최상위 경계에서만 쓴다. 그 자리에서도 원인을 로그로 남긴다.

- 예외를 바꿔 던질 때 **원인을 버리지 않는다.**

```kotlin
// ❌ 원래 무엇이 터졌는지 영영 알 수 없다
catch (e: IOException) {
    throw UnexpectedServerException()
}

// ✅ 원인을 안고 간다
catch (e: IOException) {
    throw UnexpectedServerException(cause = e)
}
```

`ApiException` 하위 예외에 원인을 넘길 자리가 없으면 위 「원인을 넘길 자리」대로 베이스에 만든다.

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 컨트롤러의 `try/catch` | 예외 처리 중복·누락 | Critical |
| 사유 주석 없는 `catch (e: Exception)` | 진짜 장애가 묻힘 | Critical |
| 원인(`cause`)을 버리는 예외 교체 | 디버깅 불가 | Critical |
| 예외 메시지에 스택·SQL·내부 ID·개인정보 | 정보 유출 | Critical |
| 남의 소유물에 403 을 응답 (404 가 아님) | 남의 데이터 존재 여부 노출 | Critical |
| 실패 응답을 래퍼로 감싸거나 `code`·`errors` 이름을 바꿈 | 와이어 계약 파손 | Critical |
| storage·clients 가 `ApiException` 을 던짐 (`core-common` 의존) | 하위가 상위를 안다 | Critical |
| 좁은 핸들러가 포괄 `Exception` 핸들러 아래에 있음 | 좁은 핸들러가 안 걸림 | Critical |
| 4xx 상황을 `ERROR` 로 | 진짜 장애가 로그에 묻힘 | Important |
| 실패마다 예외를 새로 만듦 (기존 status·code 로 충분한데) | 코드만 늘고 구분은 안 됨 | Important |
| `ApiException` 으로 될 실패에 advice 핸들러를 추가 | 응답을 정하는 자리가 둘이 됨 | Important |
| 서비스가 `HttpStatus`·`ResponseEntity` 를 직접 다룸 | 레이어 침범 | Important |

## 체크리스트

- [ ] 새 실패가 기존 예외가 스스로 가진 status·code 로 안 되는 것이 확실한가

- [ ] 새 예외가 `ApiException` 을 상속하고 `title`·`detail`·`code`·`message` 를 다 갖는가

- [ ] 상태코드와 로그 레벨이 위 표 기준과 맞는가 (4xx 는 `ERROR` 가 아니다, 남의 소유물은 404)

- [ ] 메시지에 내부 정보·개인정보가 없는가

- [ ] 예외를 바꿔 던질 때 원인을 안고 가는가

- [ ] 컨트롤러에 `try/catch` 가 없는가

- [ ] `ProblemDetail` 의 `code`·`errors` 계약이 그대로인가 (`core-app` 웹 테스트로 확인)
