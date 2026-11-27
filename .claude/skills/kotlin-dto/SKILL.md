---
paths:
  - "core/**/api/request/**"
  - "core/**/api/response/**"
name: kotlin-dto
description: 요청·응답 DTO 작성과 리뷰 규칙. api/request 와 api/response 패키지의 data class, Request·Response 이름 규약, 검증 애너테이션, 도메인 모델과의 변환 메서드를 다룰 때 사용한다. "요청 DTO 추가", "응답 형태 변경" 요청에도 사용할 것.
---

# Request / Response DTO

**자리:** `core/core-<도메인>/…/io/soo/bueongi/core/<도메인>/api/request/` · `…/api/response/`

## 이름 — 접미사가 자리를 말한다

Spring 템플릿(`team-dodn/spring-boot-kotlin-template`)의 규약을 그대로 따른다. 접미사만 보면 그 타입이 어느 레이어의 것인지 알 수 있어야 한다.

| 무엇                | 접미사                   | 예                                        | 자리                     |
|---------------------|--------------------------|-------------------------------------------|--------------------------|
| 컨트롤러 요청 DTO   | **`Request`**            | `TodoCreateRequest`                       | `api/request/`           |
| 컨트롤러 응답 DTO   | **`Response`**           | `TodoResponse`, 중첩은 `TodoItemResponse` | `api/response/`          |
| 외부 API 어댑터 DTO | **`Request`·`Response`** | `PayApproveResponse`                      | `clients/client-*/`      |
| 도메인 입력 모델    | `Command`                | `TodoCreateCommand`                       | `<도메인>/domain/model/` |
| 도메인 결과 모델    | **`Result`**             | `TodoResult`                              | `<도메인>/domain/model/` |

**`Dto` 접미사는 쓰지 않는다.** 경계는 접미사(`Request`·`Response`)와 패키지(`api/request`·`api/response`)가 이미 말한다. 도메인 모델(`Command`·`Result`)과 이름이 겹칠 일도 없다.

파일 하나에 DTO 하나가 원칙이고, 파일명은 타입명과 같다. 응답 안에 중첩되는 항목 타입만 같은 파일에 둔다.

## 요청 DTO — 검증과 변환을 스스로 한다

```kotlin
data class TodoCreateRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(max = 200, message = "제목은 200자를 넘을 수 없습니다")
    // 필드를 안 보낸 요청에서 null 이 가능함
    val title: String?,
) {
    fun toCommand(): TodoCreateCommand {
        // @NotBlank 통과 뒤에만 불린다
        val validTitle: String = checkNotNull(title)
        return TodoCreateCommand(title = validTitle.trim())
    }
}
```

- `data class` + `val` 만. `var` 금지.

- Kotlin 에서 Bean Validation 은 **`@field:`** 접두가 필요하다. 빠뜨리면 검증이 걸리지 않는다.

- `Unresolved reference 'validation'` 이 나면 `core/core-<도메인>/build.gradle.kts` 에 `spring-boot-starter-validation` 이 빠진 것이다.
  그 도메인 모듈에 `implementation("org.springframework.boot:spring-boot-starter-validation")` 을 더한다.

- 쿼리 파라미터의 검증은 이 저장소의 스키마 계층 함수(`toValidatedQuery`·`toValidatedGeoPoint`)를 쓴다(README 1조).

- 애너테이션으로 표현되는 규칙(필수·길이·범위·형식)은 애너테이션으로 쓴다. 그것으로 안 되는 규칙(공백만 입력 등)은 변환 메서드나 도메인에서 다룬다.

- 도메인 입력 모델로 바꾸는 `toXxx()` 를 DTO 가 가진다. 컨트롤러가 손으로 조립하지 않는다.

## 응답 DTO — 도메인 결과에서 만든다

```kotlin
data class TodoResponse(
    val id: Long,
    val title: String,
    val done: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(result: TodoResult): TodoResponse {
            return TodoResponse(
                id = result.id,
                title = result.title,
                done = result.done,
                createdAt = result.createdAt,
            )
        }
    }
}
```

- 변환은 `companion object` 의 `from(result)` 로 통일한다. **응답 DTO 는 `from` 으로만 만든다** — 한 칸짜리 감싸기(`AlertsResponse(alerts = alerts)`)도 생성자를 밖에서 부르지 않고 `from(alerts)` 를 둔다. 응답의 모양을 아는 곳이 DTO 파일 하나로 모인다.
  **`from` 은 컨트롤러가 부른다**(kotlin-controller). 저장소 README 1조는 아직 "서비스가 만든다"고 적혀 있어 이 방향으로 고칠 예정이고,
  새 코드는 컨트롤러 변환을 따른다. 기존의 서비스 변환 코드는 그 파일을 고칠 일이 생길 때 옮긴다.

- **목록 변환도 DTO 가 갖는다.** 같은 companion 에 `from(results: List<…>)` 를 두고 안은 `for` 로 모은다.
  컨트롤러는 `TodoResponse.from(results)` 한 줄만 쓴다 — 컨트롤러에 `map` 도 `for` 도 두지 않는다.

  ```kotlin
  fun from(results: List<TodoResult>): List<TodoResponse> {
      val responses: ArrayList<TodoResponse> = ArrayList(results.size)
      for (result in results) {
          responses.add(from(result))
      }
      return responses
  }
  ```

- **엔티티에서 바로 만들지 않는다.** 입력은 항상 도메인 모델(Result)이다.

- 응답에 비밀번호 해시·내부 식별자·감사 컬럼 같은 내부 정보를 담지 않는다.

- 응답 DTO 는 본문 그대로 나간다 — `{result, data, error}` 같은 래퍼로 감싸지 않는다. 실패 응답은 `ProblemDetail` 이 맡는다(**kotlin-error**).
  필드 이름·모양은 와이어 계약이라 [docs/API.md](../../../docs/API.md) 와 맞춘다.

- 필드 타입을 명시하고 nullable 을 최소화한다. 없을 수 있는 값만 `?`.

- **`?` 필드는 선언 바로 위에 사유를 적는다.** 문장은 `<언제> null 이 가능함` 꼴로 끝낸다.
  클래스 위에 한 줄로 몰아 적으면 어느 필드 이야기인지 흐려진다.

  ```kotlin
  data class LocationSummary(
      val expired: Boolean,
      // 첫 위치 갱신 전이면 null 이 가능함
      val updatedAt: Instant?,
  )
  ```

- **요청 DTO 의 검증 대상은 nullable 로 둔다.** `@field:NotNull val quantity: Int?` 에서 `?` 를
  지우면 안 된다. Kotlin non-null 원시 타입은 값이 없을 때 예외가 아니라 **기본값이 들어간다**
  (`Int`·`Double` 은 `0`, `Boolean` 은 `false`). 그래서 필드를 통째로 안 보낸 요청이
  "값이 없습니다" 가 아니라 **0 을 보낸 요청과 똑같이** 처리되고, 엉뚱한 곳에서 다른 메시지로
  실패한다. nullable + `@NotNull` 이면 어느 필드가 빠졌는지 응답에 담긴다.
  요청 DTO 안의 `Int`·`Double`·`Boolean` 은 non-null 로 선언하지 않는다.

## 적발 신호

| 신호                                           | 문제                                  | 심각도    |
|------------------------------------------------|---------------------------------------|-----------|
| DTO 에 `var`                                   | 예측 불가능한 상태 변경               | Critical  |
| 응답 DTO 가 엔티티를 입력으로 받음             | 저장소 격리 붕괴                      | Critical  |
| 비밀번호·해시·내부 필드 노출                   | 정보 유출                             | Critical  |
| `@field:` 없는 검증 애너테이션                 | 검증이 동작하지 않음                  | Critical  |
| DTO 이름이 `Request`·`Response` 로 끝나지 않음 | 경계가 이름으로 안 보임               | Important |
| 이름에 `Dto` 접미사                            | 접미사·패키지가 이미 말하는 것의 중복 | Important |
| 변환 로직이 컨트롤러에 흩어짐                  | 중복·불일치                           | Important |
| 검증 메시지 없음                               | 클라이언트가 원인 모름                | Important |
| 습관적 nullable 필드                           | 계약 불명확                           | Important |

## 체크리스트

- [ ] 모든 필드가 `val` 이고 타입이 명시됐는가

- [ ] 검증 애너테이션에 `@field:` 가 붙었는가

- [ ] 이름이 `Request`·`Response` 로 끝나는가 (도메인 모델은 `Command`·`Result`, `Dto` 접미사는 쓰지 않는다)

- [ ] 요청은 `toXxx()`, 응답은 `from(result)` 로 변환하는가

- [ ] 내부 정보가 응답에 없는가
