---
paths:
  - "core/**/domain/service/**"
  - "core/**/domain/*/service/**"
name: kotlin-implement
description: 구현 레이어 작성과 리뷰 규칙. domain/service/ 의 Finder·Appender·Updater·Remover·Sender 처럼 재사용 단위로 쪼갠 상세 구현, 저장 모델·엔티티와 도메인 모델 변환, storage·clients 의 실패를 ApiException 으로 바꾸는 자리를 다룰 때 사용한다. "조회 로직 분리", "재사용 컴포넌트" 요청에도 사용할 것.
---

# Implement (구현 레이어)

**자리:** `core/core-<도메인>/…/io/soo/bueongi/core/<도메인>/domain/service/` (관심사 하위 폴더가 있으면 `domain/<관심사>/service/`)

## 역할 — 재사용 단위로 쪼갠다

도메인 서비스가 조립할 **재사용 가능한 단위**다. 이름이 곧 역할이다.

**이 레이어는 Spring 템플릿에 없다.** 이 저장소가 추가한 것이고, 목적은 둘이다 — 여러 유스케이스가 같은 조회·저장을 재사용하게 하는 것, 그리고 **엔티티·저장 모델을 도메인 모델로 바꿔 저장 사정이 위로 새지 않게** 막는 것 (`kotlin-module-layout`).
이 저장소의 예: `SafetyScorer`·`SafeDetourPlanner`·`NearestPoliceFinder`(README 3조).

한 클래스가 한 가지 일만 한다. `TodoService` 하나에 다 넣지 않고 `TodoFinder`·`TodoAppender` 로 쪼개는 이유다.

| 이름 | 하는 일 |
|---|---|
| `XxxFinder` | 조회. 없으면 `ApiException` 하위 예외를 던지는 `getXxx`, 목록을 주는 `listXxx` |
| `XxxAppender` | 신규 저장 |
| `XxxUpdater` | 기존 상태 변경 (엔티티 행위 메서드 호출) |
| `XxxRemover` | 삭제·비활성화 |
| `SmsSender`·`EmailSender` | 외부 발송 어댑터 |

```kotlin
@Component
class TodoFinder(
    private val todoRepository: TodoRepository,
) {
    fun listByMember(memberId: Long, pageable: Pageable): List<TodoResult> {
        val entities: List<TodoEntity> = todoRepository.findAllByMemberIdOrderByIdDesc(memberId, pageable)
        val results: ArrayList<TodoResult> = ArrayList(entities.size)
        for (entity in entities) {
            results.add(entity.toResult())
        }
        return results
    }

    fun getOwned(memberId: Long, todoId: Long): TodoResult {
        val entity: TodoEntity = todoRepository.findByIdAndMemberId(todoId, memberId)
            ?: throw TodoNotFoundException()
        return entity.toResult()
    }
}
```

## 규칙

- 클래스 하나가 **역할 하나**를 갖는다. `TodoManager` 처럼 뭐든 하는 이름을 만들지 않는다.

- **엔티티 ↔ 도메인 모델 변환은 여기가 유일한 자리다.** 위로는 도메인 모델만 올려보낸다.
  변환은 구현 레이어 파일의 **최상위 `internal` 확장 함수**로 둔다 — `TodoResult.from(entity)` 를
  도메인 모델의 companion 에 두면 **모델이 storage 를 import 하게 되어** 경계가 그리로 샌다.

  ```kotlin
  // domain/service/TodoEntityMapper.kt — 구현 레이어 소유
  internal fun TodoEntity.toResult(): TodoResult {
      return TodoResult(
          id = id,
          title = title,
          done = done,
      )
  }
  ```

  **storage 쪽에 둘 수도 없다.** 변환 결과(`TodoResult`)가 core 소유라 storage 가 core 를
  import 하게 된다 — 방향 위반. 갈 수 있는 자리는 구현 레이어 하나뿐이다.

- `@Component` 로 등록하고 생성자 주입만 쓴다.

- `@Transactional` 을 붙이지 않는다. 트랜잭션 경계는 서비스가 소유한다.

- 없음 처리 방식을 이름으로 구분한다: `getXxx` 는 없으면 예외, `findXxx` 는 `null` 반환.

- 다른 도메인 모듈의 코드를 주입하지 않는다(도메인 모듈끼리 의존하지 않는다). 남의 데이터는 그 데이터를 가진 storage 의 Store 인터페이스로 읽는다.

- 외부 시스템 호출은 어댑터(`clients:*`)에 두고, 여기서는 그것을 부르기만 한다.

- **storage·clients 의 실패를 우리 예외로 바꾸는 자리가 여기다.** 그 모듈들은 `core-common` 을 모르므로 `null`·자기 예외로 알린다 —
  구현 레이어가 받아 `ApiException` 하위 예외(`TodoNotFoundException`·`ExternalCallFailedException`)로 바꾸고, 원인(`cause`)을 안고 간다.

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 엔티티를 그대로 위로 반환 | 저장소 격리 붕괴 | Critical |
| 구현 레이어에 `@Transactional` | 트랜잭션 경계 중복 | Critical |
| 다른 도메인 모듈의 클래스 주입 | 도메인 경계 침범 | Critical |
| 한 클래스가 조회·저장·삭제를 모두 담당 | 역할 불명확·재사용 불가 | Important |
| `getXxx` 인데 `null` 반환 | 이름과 동작 불일치 | Important |
| 유스케이스 흐름(여러 단계 조립)이 여기 들어옴 | 서비스 책임 침범 | Important |
| 외부 호출 상세(HTTP 조립)가 여기 있음 | 어댑터 책임 침범 | Important |

## 체크리스트

- [ ] 클래스 이름이 역할 하나를 가리키는가

- [ ] 반환이 도메인 모델인가

- [ ] 트랜잭션 애너테이션이 없는가

- [ ] `get`/`find` 이름과 없음 처리가 일치하는가
