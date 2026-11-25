---
paths:
  - "core/**/src/docs/**"
  - "docs/API.md"
name: kotlin-api-docs
description: API 문서 규칙. 지금 이 저장소의 API 계약 문서는 손으로 쓰는 docs/API.md 이고 Spring REST Docs 는 없다 — 엔드포인트를 바꿀 때 docs/API.md 를 함께 고치는 법과, (도입하기로 정했을 때) REST Docs 테스트의 자리·형태·스니펫 이름·index.adoc 을 다룬다. 엔드포인트를 추가·변경할 때 kotlin-controller 와 함께 사용한다. "API 문서", "RestDocs", "문서 테스트" 요청에도 사용할 것.
---

# API 문서

## 지금 — `docs/API.md` 가 계약이다

이 저장소에는 **Spring REST Docs 가 없다**(`restdocs`·`asciidoctor` 설정, `RestDocsTest`, `tests:api-docs` 모듈 모두 없음).
API 요청·응답 계약은 손으로 쓰는 [docs/API.md](../../../docs/API.md) 가 기준이고, 웹·모바일 클라이언트가 그것을 보고 붙는다.

**엔드포인트를 추가·변경하면 같은 변경에 `docs/API.md` 를 고친다.** 손으로 쓰는 문서는 코드와 어긋날 수 있으므로 셋을 지킨다.

- 응답 필드·`ProblemDetail` 의 `code` 를 문서에 적을 때는 **실제로 호출한 응답**을 보고 적는다(`curl http://localhost:8119/...`).
- `core-app` 의 `@WebMvcTest` 가 같은 필드·상태코드를 단언하게 한다 — 문서와 코드가 어긋나면 테스트가 먼저 알려 준다(**kotlin-test**).
- README 「API 요약」 표에도 한 줄을 더한다.

아래는 **REST Docs 를 도입하기로 정했을 때**의 규칙이다. 도입 결정 없이 이 절을 근거로 REST Docs 를 요구하거나 리뷰에서 지적하지 않는다.

---

## (도입 시) Spring REST Docs

**자리:** 테스트는 웹 테스트 규칙대로 부팅 모듈 `core/core-app/src/test/…/XxxControllerDocsTest.kt`, 문서는 `core/core-app/src/docs/asciidoc/index.adoc`

**도입 때 함께 할 것:** `core-app/build.gradle.kts` 에 `org.asciidoctor.jvm.convert` 플러그인과 `spring-restdocs-mockmvc` 를 더하고,
`restdocs` 태그만 도는 `restDocsTest` 태스크를 만들어 `asciidoctor` 가 그것에 의존하게 한다. 루트의 `unitTest` 는 `restdocs` 태그도 빼게 고친다.

### 문서를 손으로 쓰지 않는다

REST Docs 는 **테스트를 실제로 돌려서** 요청·응답 조각(스니펫)을 뽑고, 그것을 문서에 끼워 넣는다.

그래서 문서가 코드와 어긋날 수 없다 — 응답 필드가 바뀌면 **문서 테스트가 먼저 실패한다.**

```
XxxControllerDocsTest 실행 → build/generated-snippets/<식별자>/*.adoc 생성
                           → index.adoc 이 include → asciidoctor 가 HTML 로
```

### 컨트롤러 테스트가 곧 문서 테스트다

`@Tag("restdocs")` 를 붙여 `restDocsTest` 태스크에만 잡히게 한다. 스프링 컨텍스트를 띄우지 않고 `MockMvcBuilders.standaloneSetup` 으로 컨트롤러 하나만 세운다.

```kotlin
@Tag("restdocs")
@ExtendWith(RestDocumentationExtension::class)
class TodoControllerDocsTest {
    private val todoService: TodoService = mock()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp(restDocumentation: RestDocumentationContextProvider) {
        mockMvc = MockMvcBuilders.standaloneSetup(TodoController(todoService))
            .apply<StandaloneMockMvcBuilder>(documentationConfiguration(restDocumentation))
            .build()
    }

    @Test
    fun `할 일 등록`() {
        whenever(todoService.create(any(), any())).thenReturn(TodoResult(1L, "장보기", false, Instant.now()))

        mockMvc.perform(
            post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"장보기"}"""),
        )
            .andExpect(status().isCreated)   // 생성은 201 — 컨트롤러의 @ResponseStatus 와 맞춘다
            .andDo(
                document(
                    "todoCreate",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("title").type(JsonFieldType.STRING).description("할 일 제목"),
                    ),
                    responseFields(
                        fieldWithPath("id").type(JsonFieldType.NUMBER).description("할 일 식별자"),
                        fieldWithPath("title").type(JsonFieldType.STRING).description("제목"),
                        fieldWithPath("done").type(JsonFieldType.BOOLEAN).description("완료 여부"),
                        fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각"),
                    ),
                ),
            )
    }
}
```

- 서비스는 mockito-kotlin `mock()` 으로 세운다. 문서 테스트는 **컨트롤러의 입출력 모양**을 검증하는 것이지 업무 규칙을 검증하는 것이 아니다.

- 응답은 래퍼 없이 응답 DTO 본문 그대로다 — `result`·`data` 같은 껍데기 필드는 없다. 실패 응답을 문서화할 때는 `ProblemDetail` 필드(`title`·`status`·`detail`·`code`·`errors`)를 적는다.

- `document()` 의 첫 인자가 **스니펫 폴더 이름**이다. `<도메인><동작>` 카멜케이스로 쓴다 (`todoCreate`, `todoList`, `todoDelete`). 이 이름으로 `index.adoc` 에서 끌어 쓴다.

- **응답 필드를 하나라도 빠뜨리면 테스트가 실패한다.** 쓰지 않는 필드는 `.ignored()`.

- 경로 변수는 `pathParameters`, 쿼리는 `queryParameters` 로 함께 문서화한다.

### 문서에 끼워 넣기

스니펫만 만들면 아무 데도 안 나온다. `index.adoc` 에 절을 추가해야 보인다.

```asciidoc
== Todo API

=== 할 일 등록
==== Curl Request
include::{snippets}/todoCreate/curl-request.adoc[]
==== Request Fields
include::{snippets}/todoCreate/request-fields.adoc[]
==== Http Response
include::{snippets}/todoCreate/http-response.adoc[]
==== Response Fields
include::{snippets}/todoCreate/response-fields.adoc[]
```

`{snippets}` 는 파일 머리에 `:snippets: build/generated-snippets` 로 정의한다.

### `docs/API.md` 와의 관계

도입한 뒤에도 `docs/API.md` 는 개요·흐름 설명으로 남기고, 필드 상세는 REST Docs 결과물을 가리키게 한다. 둘이 어긋나면 **REST Docs 가 맞다** — 실제로 실행해서 뽑은 것이기 때문이다.

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 엔드포인트·응답 필드·에러 `code` 를 바꾸고 `docs/API.md` 를 안 고침 | 클라이언트가 옛 계약을 보고 붙는다 | Important |
| `docs/API.md` 의 예시를 실호출 없이 추측으로 적음 | 문서가 코드와 어긋난다 | Important |
| (도입 시) 응답 필드 일부만 문서화 | 테스트 실패 — 빠진 필드가 있으면 REST Docs 가 거부한다 | Critical |
| (도입 시) 문서 테스트에서 `@SpringBootTest`·실제 서비스 사용 | 느려지고 통합 테스트로 변질 | Important |
| (도입 시) 스니펫만 만들고 `index.adoc` 에 include 누락 | 문서에 안 나옴 | Important |
| (도입 시) 스니펫 이름이 중복되거나 도메인·동작을 안 드러냄 | 문서에서 찾을 수 없음 | Important |
| `description` 이 필드명 반복 (`"title"` → `"title"`) | 문서로서 가치 없음 | Code Smell |

## 체크리스트

- [ ] 엔드포인트를 바꿨다면 같은 변경에 `docs/API.md` 와 README 「API 요약」을 고쳤는가

- [ ] 문서의 응답 예시를 실제 호출로 확인했는가

- [ ] `core-app` 웹 테스트가 같은 필드·상태코드·`code` 를 단언하는가

- [ ] (도입 시) 스니펫 이름이 `<도메인><동작>` 이고 `index.adoc` 에 include 했는가
