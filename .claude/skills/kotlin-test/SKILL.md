---
paths:
  - "**/src/test/**"
  - "**/src/testFixtures/**"
name: kotlin-test
description: 유닛 테스트 작성과 리뷰 규칙. 테스트 메서드 하나가 기능 하나만 검증하도록 쪼개는 법, mockito-kotlin 스텁 범위, 레이어별 테스트 대상, context 태그(스프링 컨텍스트·PostGIS)와 unitTest 태스크를 다룰 때 사용한다. 요구사항을 구현할 때는 항상 함께 사용해 테스트를 같이 작성한다. "테스트 추가", "테스트 짜줘", "커버리지" 요청에도 사용할 것.
---

# 유닛 테스트

> 이 문서는 테스트를 **언제 쓰고 무엇을 검증하나**와 그것을 **코틀린으로 어떻게 쓰나**(JUnit5·AssertJ·mockito-kotlin·Gradle 태스크)를 함께 다룬다.

**자리:** 대상과 같은 패키지의 `src/test/kotlin/...`, 파일명은 `<대상>Test.kt`. 웹 테스트(`@WebMvcTest`·`@SpringBootTest`)만 예외로 부팅 모듈
`core/core-app/src/test/` 에 둔다. 모듈을 넘나드는 테스트 대역은 `java-test-fixtures` 로 노출한다(`storage:db-share` 의 `InMemoryShareSessionStore`).

**실행:** `./gradlew unitTest` 는 `context` 태그를 뺀 테스트만, `./gradlew test` 는 전부 돈다(루트 `build.gradle.kts`).
유닛 테스트는 DB·스프링 컨텍스트 없이 돌아야 한다.

## 요구사항이 오면 테스트도 함께 온다

테스트가 필요한 새 동작(새 분기·정책·검증·계산 — 확정된 기능 중 자주 바뀌거나 직접 실행해 보기 어려운 것)을 만들면 **같은 변경에 유닛 테스트를 포함한다.** "나중에 테스트 추가"는 없다. 기능과 연관 없는 테스트는 쓰지 않는다 — 테스트도 유지보수 비용이다.

## 한 메서드 = 한 기능

테스트 메서드 하나는 **검증 대상 행위 하나**만 확인한다. 시나리오가 둘이면 메서드도 둘이다.

```kotlin
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TodoAppenderTest {
    private val todoStore: TodoStore = mock()
    private val appender: TodoAppender = TodoAppender(todoStore)

    @Test
    fun `제목이 있으면 저장한다`() {
        whenever(todoStore.save(any())).thenReturn(todoRecord(title = "장보기"))

        val result: TodoResult = appender.append(memberId = 1L, command = TodoCreateCommand("장보기"))

        assertThat(result.title).isEqualTo("장보기")
    }

    @Test
    fun `제목이 공백이면 예외를 던진다`() {
        assertThatThrownBy { appender.append(memberId = 1L, command = TodoCreateCommand("  ")) }
            .isInstanceOf(TodoTitleBlankException::class.java)
    }
}
```

```kotlin
// ❌ 한 메서드에 여러 기능 — 앞에서 실패하면 뒤는 확인조차 안 된다
@Test
fun `todo 생성 테스트`() {
    val created = appender.append(1L, TodoCreateCommand("장보기"))
    assertThat(created.title).isEqualTo("장보기")

    val toggled = updater.toggle(created.id)          // 다른 기능
    assertThat(toggled.done).isTrue()

    assertThatThrownBy { appender.append(1L, TodoCreateCommand("")) }  // 또 다른 기능
}
```

## 쪼개는 기준

- 검증 대상 메서드가 다르면 → 나눈다.

- 같은 메서드라도 **조건이 다르면** 나눈다: 성공 / 값이 없음 / 권한 없음 / 한도 초과.

- 단언은 **하나의 결론**만 확인한다. 한 결과 객체의 관련 필드 몇 개를 함께 보는 것은 하나로 친다.

- 준비(given) 때문에 다른 기능을 호출하는 것은 괜찮다. 그 호출 결과를 **단언하지 않으면** 된다.

- `when`/`then` 이 한 메서드에 두 번 반복되면 쪼개라는 신호다.

## 이름과 형식

- 이름은 백틱 한글로 **조건과 기대 결과**를 적는다: `` `제목이 공백이면 예외를 던진다` ``. 로케일이 UTF-8 이어야 컴파일된다(CI 는 `LANG=C.UTF-8`).

- `테스트`, `성공`, `케이스1` 같은 이름은 쓰지 않는다.

- 본문은 given → when → then 순서로 쓰고 빈 줄로 나눈다. 주석으로 `// given` 을 달지 않는다(구조로 보인다).

- 단언은 AssertJ(`assertThat`, `assertThatThrownBy`)로 통일한다.

## 무엇을 테스트하나

| 대상 | 테스트할 것 | 협력 객체 | 태그 |
|---|---|---|---|
| 도메인 서비스 | 유스케이스 흐름, 조건 분기, 예외 | 구현 레이어를 `mock()` | 없음 |
| 구현 레이어 | 조회·저장 결과 변환, 없음 처리 | Store·리포지토리를 `mock()` 또는 testFixtures 의 인메모리 대역 | 없음 |
| 엔티티·저장 모델·도메인 모델 | 상태 변경 규칙과 불변식, 자기 값으로 하는 계산 | 목 없이 직접 생성 | 없음 |
| 요청 DTO | 변환 메서드, 검증이 필요한 값 다듬기 | 없음 | 없음 |
| 컨트롤러 (입출력 모양·상태코드·`ProblemDetail`) | `@WebMvcTest` — `core-app` 에만 | 서비스를 `@MockitoBean` | `context` |
| Store·리포지토리 구현 (SQL·PostGIS 공간 쿼리·대량 적재) | 실제 PostGIS 에 대고 | 없음 | `context` |

- **DB 가 필요한 테스트는 `@Tag("context")` 를 붙이고 실제 PostGIS 에 대고 돈다.** 개발 PC·CI 는 docker compose 의
  `postgis/postgis` 를, Docker 가 없는 환경은 로컬에 설치한 PostgreSQL + PostGIS 를 쓴다(**kotlin-config** 「로컬 DB」).
  H2 같은 메모리 DB 로 대신하지 않는다 — PostGIS 함수(`ST_DWithin` 등)와 `geometry` 타입을 돌릴 수 없다.
  Testcontainers 는 요구하지 않는다.

- 유닛 테스트에 스프링 컨텍스트(`@SpringBootTest`)를 띄우지 않는다. 느리고, `unitTest` 에서 빠진다.

## mockito-kotlin 사용 범위

- 목은 `mock()`, 스텁은 `whenever(대상.메서드(...)).thenReturn(값)`, 인자 매처는 `org.mockito.kotlin.any()`·`eq()` 로 쓴다.
  Mockito 의 `Mockito.any()` 는 Kotlin non-null 파라미터에서 `null` 을 넘겨 NPE 가 나므로 쓰지 않는다.

- **쓰는 값만 스텁한다.** 테스트가 보지 않는 호출까지 `whenever` 로 채우지 않는다.

- 반환값 검증으로 충분하면 `verify` 를 쓰지 않는다. 호출 횟수가 그 자체로 요구사항일 때만 쓴다.

- 테스트 대상은 목으로 만들지 않는다. 목이 대상을 대신하면 아무것도 검증하지 않는 테스트가 된다.

- 최상위·확장 함수(예: Spring Data 의 `findByIdOrNull`)는 그 자체를 스텁하지 않는다. 확장 함수가 안에서 부르는
  인터페이스 메서드(`findById`)를 스텁하면 된다 — 리포지토리 이름 규약은 **kotlin-repository**.

- 인터페이스 하나에 대역이 반복해서 필요하면 목보다 **testFixtures 의 인메모리 구현**이 읽기 쉽다(`InMemoryShareSessionStore` 처럼).

- 반복되는 픽스처는 파일 하단의 작은 팩토리 함수로 만든다(`private fun todoRecord(...)`). 프레임워크를 도입하지 않는다.

## 완료 전 검증

- **실계수를 본다.** `BUILD SUCCESSFUL` 만으로는 테스트가 실제로 돌았는지 모른다 — `build/test-results` 에서 이번에 추가한
  테스트가 집계됐는지 확인한다.

- **새 단언에 이빨이 있는지 본다.** 대상 코드의 핵심 한 줄을 일부러 틀리게 바꿔(변이) 테스트가 실패하는지 한 번 확인하고 되돌린다.

서버 기동·실호출 대조는 테스트가 아니라 배포 전 점검이다 — 리팩토링의 동치 증명이 필요할 때만 한다(**kotlin-module-layout** 「나눈 뒤 반드시 확인하는 것」).

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 새 분기·정책·검증에 테스트 없음 | 미검증 동작 유입 | Critical |
| 한 메서드가 여러 기능·시나리오를 검증 | 실패 지점 은폐 | Critical |
| 테스트 대상을 목으로 대체 | 아무것도 검증하지 않음 | Critical |
| 단언 없는 테스트 | 통과만 하는 껍데기 | Critical |
| 실패를 통과시키려 단언 약화·테스트 삭제 | 검증 무력화 | Critical |
| DB·컨텍스트가 필요한 테스트에 `context` 태그 없음 | `unitTest` 가 DB 없는 환경에서 깨진다 | Important |
| 공간 쿼리를 H2·목으로만 검증 | PostGIS 동작을 검증하지 못함 | Important |
| 유닛 테스트에 `@SpringBootTest` | 느림·유닛 테스트 대상에서 빠짐 | Important |
| `@WebMvcTest`·`@SpringBootTest` 가 `core-app` 밖에 있음 | 부팅 모듈 밖에서는 컨텍스트가 안 뜬다 | Important |
| 쓰지 않는 호출까지 스텁 | 결합·잡음 | Important |
| `테스트1`·`성공` 같은 이름 | 실패 시 원인 불명 | Important |
| 반환값으로 충분한데 `verify` 남발 | 구현 결합 | Important |

## 체크리스트

- [ ] 이번 변경의 새 동작마다 테스트가 있는가

- [ ] 테스트 메서드 하나가 기능 하나만 확인하는가

- [ ] 이름이 조건과 기대 결과를 말하는가

- [ ] 성공 1 + 실패(조건별) 가 각각 별도 메서드인가

- [ ] 검증 대상이 이번에 작성·수정한 코드로 한정되는가 (남의 코드·프레임워크 동작은 대상이 아니다)

- [ ] DB·컨텍스트 테스트에 `@Tag("context")` 가 붙었는가

- [ ] `./gradlew ktlintCheck unitTest` 가 통과하는가 (DB 를 건드렸다면 PostGIS 를 띄우고 `test` 까지)
