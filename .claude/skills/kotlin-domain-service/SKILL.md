---
paths:
  - "core/**/domain/service/**"
  - "core/**/domain/*/service/**"
name: kotlin-domain-service
description: 도메인 서비스 작성과 리뷰 규칙. core/domain 의 Service 클래스, 유스케이스 오케스트레이션, @Transactional 트랜잭션 경계, 구현 레이어 조합을 다룰 때 사용한다. "비즈니스 로직", "서비스 추가", "트랜잭션 경계" 요청에도 사용할 것.
---

# Domain Service

**자리:** `core/core-<도메인>/…/io/soo/bueongi/core/<도메인>/domain/service/XxxService.kt` (예: `ShareService`, `RouteOptionService`)

## 역할 — 흐름과 트랜잭션 경계만

유스케이스의 **흐름과 트랜잭션 경계**를 담당한다. 상세 구현은 구현 레이어에 맡기고, 서비스는 순서대로 조립한다.

**왜 리포지토리를 직접 부르지 않나.** 서비스에 조회 조건과 변환 코드까지 들어가면, 다음 유스케이스가 같은 조회를 필요로 할 때 복사하게 된다. 조회를 `TodoFinder` 로 한 번 빼 두면 여러 서비스가 같은 것을 쓴다.

그래서 서비스를 읽으면 **무엇을 어떤 순서로 하는지**만 보이고, 어떻게 하는지는 안 보이는 것이 정상이다.

```kotlin
@Service
class TodoService(
    private val todoFinder: TodoFinder,
    private val todoAppender: TodoAppender,
    private val todoUpdater: TodoUpdater,
) {
    @Transactional(readOnly = true)
    fun list(memberId: Long, pageable: Pageable): List<TodoResult> {
        return todoFinder.listByMember(memberId, pageable)
    }

    @Transactional
    fun create(memberId: Long, command: TodoCreateCommand): TodoResult {
        return todoAppender.append(memberId, command)
    }

    @Transactional
    fun complete(memberId: Long, todoId: Long): TodoResult {
        return todoUpdater.complete(memberId, todoId)
    }
}
```

## 규칙

- **트랜잭션 경계는 서비스가 소유한다.** 조회 전용은 `@Transactional(readOnly = true)`, 쓰기는 `@Transactional`.
  DB 를 쓰지 않는 서비스(Redis·파일·외부 API 만 쓰는 것)에는 붙이지 않는다.

- 예외 하나 — storage 의 Store 구현이 **한 메서드 안에서 끝나는 저장 단위**(대량 적재의 "조각 삭제 + 삽입")는 그 구현이
  스스로 트랜잭션을 건다(**kotlin-repository** 「대량 적재」). 서비스는 그 메서드를 한 번 부르기만 하고 바깥에 또 걸지 않는다.

- 구현 레이어(`Finder`/`Appender`/`Sender`)에는 트랜잭션을 걸지 않는다. 경계가 두 곳이 되면 커밋 시점을 알 수 없다.

- 서비스는 리포지토리·엔티티를 직접 만지지 않는다. 구현 레이어만 부른다.

- **찾은 것을 서비스가 들고 있다가 다른 구현 레이어에 넘기지 않는다.** 상태를 바꾸는 구현
  레이어는 **식별자를 받아 스스로 찾는다.** 서비스가 중간에 들고 있으면 그 변수의 타입이
  엔티티가 되어(도메인 모델로는 JPA 변경 감지가 걸리지 않는다) 엔티티가 서비스로 새어 나온다.

  ```kotlin
  // X — 엔티티가 서비스까지 올라온다
  val entity: TodoEntity = todoFinder.getEntity(todoId)
  return todoUpdater.rename(entity, command.title)

  // O — 찾기와 바꾸기가 구현 레이어 안에서 끝난다
  return todoUpdater.rename(todoId, command.title)
  ```

- 반환은 도메인 모델(`Result`)이다. 엔티티나 응답 DTO 를 반환하지 않는다 — 응답 DTO 변환은 컨트롤러의 일이다(**kotlin-controller**).
  저장소 README 1조와 일부 기존 서비스는 아직 응답 DTO 를 돌려주는데, 새 코드는 이 규칙을 따른다(**kotlin-module-layout** 참고).

- 상세 로직(쿼리 조립, 엔티티 변환, 외부 호출 형식)을 서비스에 쓰지 않는다. 커지면 구현 레이어로 내린다.

- 도메인 모듈끼리는 의존하지 않는다. 다른 도메인의 **데이터가 필요한 조회**는 그 데이터를 가진 storage 의 Store 인터페이스를 구현 레이어가 읽는다
  (`core-route` 가 `storage:db-safety-spot` 을 읽듯). 답을 기다리지 않는 통지·명령은 아래 "이벤트로만 말한다"로 보낸다.

- 같은 트랜잭션 안에서 외부 시스템 호출(메일·SMS·결제)을 하지 않는다. 실패 시 롤백 범위가 뒤엉킨다 — 커밋 후 이벤트로 분리한다.

- 메서드명은 유스케이스를 동사 하나로 나타낸다: `list`, `create`, `toggle`, `reissue`. 대상은 서비스 이름이 말한다(`tokenService.reissue()`) — 정본은 README 8조.

## 도메인끼리는 이벤트로만 말한다

도메인이 다른 도메인의 구현을 부르지 않는다. **사건을 발행하고, 받는 쪽이 알아서 한다.**
"주문이 완료되면 알림을 보낸다"에서 주문 도메인은 알림 수단을 몰라야 한다.

```kotlin
// X — 주문 도메인이 알림 수단을 안다
notificationSender.send(memberId, "주문이 완료되었습니다.")

// O — 사건만 낸다. 누가 받아 무엇을 하는지 모른다
orderEventPublisher.orderCompleted(orderId)
```

**세 가지를 지킨다.**

1. **발행 전용 서비스를 도메인마다 둔다.** 서비스가 `ApplicationEventPublisher.publishEvent` 를
   직접 부르지 않는다 — `OrderEventPublisher` 처럼 그 일만 하는 클래스가 맡는다.
   사건 이름이 도메인 어휘로 드러나고(`orderCompleted`), 테스트가 그 클래스만 가짜로 바꾸면 된다.

2. **사건 타입은 `core-common` 에 둔다**(`core-common/.../common/event/`).
   지금은 사건 타입이 하나도 없어 이 패키지도 없다. 첫 사건 타입을 만들 때 함께 만든다.
   발행 도메인에 두면 받는 쪽이 그 도메인을 의존하게 되어 "도메인을 넘지 않는다"가 깨진다.

3. **받는 쪽(`@EventListener`)은 그 일을 업으로 하는 도메인이다.**
   운영 알림은 사건으로 보내지 않는다. 앱은 알림을 보내지 않고 로그만 남긴다(`kotlin-logging`).
   `support` 모듈은 상위(core)를 참조할 수 없으므로 리스너를 거기 두지 않는다.

**도메인을 넘는 사건의 리스너는 무조건 비동기다.** 발행한 도메인의 요청 스레드가 다른 도메인의
일에 붙잡히면 안 된다 — 리스너에 `@Async` 를 붙이고 executor 를 설정한다(`kotlin-config`).
트랜잭션 커밋 후에 받아야 하는 사건이면 `@Async` 와 함께
`@TransactionalEventListener(phase = AFTER_COMMIT)` 를 쓴다 — 롤백된 주문의 알림이 나가는 것을 막는다.

## 적발 신호

| 신호                                        | 문제                                | 심각도    |
|---------------------------------------------|-------------------------------------|-----------|
| 리포지토리·엔티티 직접 사용                 | 구현 레이어 우회                    | Critical  |
| 트랜잭션 애너테이션 없는 다단계 쓰기        | 부분 반영 위험                      | Critical  |
| 구현 레이어에도 `@Transactional`            | 경계 중복                           | Critical  |
| 엔티티·응답 DTO 반환                        | 레이어 침범                         | Critical  |
| 트랜잭션 안 외부 시스템 호출                | 롤백 범위 오염·지연                 | Critical  |
| 조회 전용에 `readOnly` 누락                 | 불필요한 더티 체킹                  | Important |
| 서비스에 쿼리·변환 상세                     | 재사용 불가·비대화                  | Important |
| 다른 도메인 모듈의 클래스 주입              | 경계 침범                           | Critical  |
| 도메인을 넘는 이벤트 리스너에 `@Async` 없음 | 발행 도메인 스레드가 남의 일에 묶임 | Critical  |

## 체크리스트

- [ ] 트랜잭션 경계가 서비스에만 있는가

- [ ] 흐름 조립 외의 상세 로직이 없는가

- [ ] 반환 타입이 도메인 모델인가

- [ ] 외부 호출이 트랜잭션 밖으로 나갔는가

- [ ] 도메인을 넘는 사건이 이벤트로 발행되고, 리스너가 `@Async` 인가
