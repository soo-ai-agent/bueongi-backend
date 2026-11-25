---
paths:
  - "support/logging/**"
  - "support/monitoring/**"
  - "clients/client-slack/**"
name: kotlin-logging
description: 로그와 운영 알림 규칙. 로거 선언, 레벨 고르는 기준, 파라미터 치환({})으로 남기기, traceId(support:logging 의 MDC)로 요청 하나를 잇는 법, 로그에 넣으면 안 되는 것, 어디서 남기고 어디서 남기지 않는지, Slack 알림(AlertNotifier)을 다룬다. 실패 처리나 외부 연동을 만들 때 kotlin-error 와 함께 사용한다. "로그 추가", "무슨 일이 있었는지 남기기" 요청에도 사용할 것.
---

# 로그와 모니터링

**설정 자리:** 레벨·문자셋은 `support/logging/src/main/resources/logging.yml`, 추적 키는 `support/logging/…/support/logging/LogTrace.kt`

## 로그는 나중에 나를 부를 사람을 위한 것

로그를 남기는 목적은 하나다 — **장애가 났을 때 무슨 일이 있었는지 재구성하는 것.** 그 기준으로 남길지 말지를 정한다.

이 프로젝트는 요청이 들어올 때 수신 필터(`core-common` 의 `RequestLogFilter`)가 `beginTrace` 로 짧은 `traceId` 를 MDC 에 심는다.
같은 스레드에서 남기는 로그는 `traceColumn()` 으로 그 값을 함께 찍는다. **한 요청이 여러 로그로 흩어져도 `traceId` 로 다시 모을 수 있다.**

- 요청 밖(스케줄러·비동기 리스너)에서는 `traceId` 가 비어 있다 — 그 로그는 사건 이름과 식별자로 찾을 수 있게 남긴다.
- 새 진입점(스케줄러 루프 등)이 한 단위의 일을 시작하면 그 자리에서 `beginTrace`/`endTrace` 로 감싼다.

## 로거 선언

```kotlin
class TodoService(...) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
}
```

`javaClass` 를 쓴다 — 클래스명을 문자열로 적으면 클래스를 옮겼을 때 로그만 엉뚱한 이름으로 남는다.

## 레벨 — 기준은 "누가 봐야 하는가"

| 레벨 | 언제 | 누가 본다 |
|---|---|---|
| `ERROR` | **우리가 고쳐야 할 것.** 외부 장애, 예상 못한 예외 | 알림을 받고 사람이 즉시 |
| `WARN` | 사용자 잘못이거나 재시도로 넘어간 것. 4xx, 외부 일시 실패 | 쌓이면 본다 |
| `INFO` | 업무상 의미 있는 사건. 주문 생성, 결제 승인 | 추적할 때 |
| `DEBUG` | 개발 중 흐름 확인 | 로컬에서만 |

**4xx 를 `ERROR` 로 찍지 않는다.** 사용자가 잘못 보낸 요청은 정상 동작이고, 그것으로 로그를 채우면 진짜 장애가 묻힌다 (`kotlin-error` 의 같은 규칙).

기본 레벨은 `io.soo.bueongi: info` 다. 운영에서 보고 싶은 것을 `DEBUG` 로 남기면 볼 수 없다.

## 메시지는 파라미터 치환으로

```kotlin
// ❌ 로그를 안 남기는 레벨에서도 문자열을 만든다
log.debug("할 일 조회 memberId=" + memberId + ", size=" + todos.size)

// ✅ 레벨이 꺼져 있으면 조립조차 하지 않는다
log.debug("할 일 조회 memberId={}, size={}", memberId, todos.size)
```

예외는 **마지막 인자로 그대로 넘긴다.** 그래야 스택 트레이스가 남는다.

```kotlin
log.error("결제 승인 실패 orderId={}", orderId, e)   // e.message 가 아니라 e
```

## 어디서 남기나

**한 사건은 한 번만 남긴다.** 잡아서 로그를 찍고 다시 던지면 같은 일이 두세 줄로 늘어나 원인 찾기가 더 어려워진다.

| 자리 | 남기나 |
|---|---|
| `ApiControllerAdvice` | **여기서 남긴다.** 모든 예외가 여기로 모인다 |
| 도메인 서비스 | 업무상 의미 있는 사건만 `INFO`. 예외는 던지기만 하고 로그는 찍지 않는다 |
| 구현 레이어·리포지토리 | 남기지 않는다 |
| 외부 연동(`Client`) | 외부 실패를 `WARN` 으로. 다시 던질 것이면 로그는 생략 가능 |

## 넣으면 안 되는 것

로그는 파일과 수집 시스템에 오래 남고, 접근 권한이 코드보다 넓다.

- **비밀번호·토큰·API 키·해시** — 어떤 레벨에서도 안 된다 (`kotlin-config`·`kotlin-auth`)

- **개인정보** — 주민번호·카드번호·전화번호·주소. 필요하면 식별자만 남긴다 (`memberId=42`)

- 요청·응답 본문 전체 — 위 둘이 섞여 들어온다. 필요한 필드만 골라 남긴다

## 운영 알림 — Slack

사람을 불러야 하는 일은 로그가 아니라 **Slack 알림**으로 알린다. Sentry·액추에이터·Prometheus 는 이 저장소에 없다.

| 자리 | 하는 일 |
|---|---|
| `support:monitoring` 의 `AlertNotifier`·`AlertRecord` | 알림 **계약**(인터페이스). core 는 이것만 안다 |
| `clients:client-slack` 의 `SlackAlertNotifier` | 구현 — 웹훅 발송, 스로틀(`SLACK_ALERT_THROTTLE_MINUTES`), 비동기. `SLACK_WEBHOOK_URL` 이 비면 보내지 않는다 |
| `core-admin-ops` 의 리스너 | `core-common` 사건(`OperationEvent`·`ApiErrorResponded` 등)을 받아 `AlertNotifier.notify` 를 부른다 |

- 알림을 보내는 대상: 처리되지 않은 500, 외부 API 전면 실패·폴백, 시설 적재 0건, 도착 미확인(README 「환경변수」 아래 목록).
- 도메인은 Slack 을 직접 부르지 않는다 — 사건을 발행한다(**kotlin-domain-service** 「도메인끼리는 이벤트로만 말한다」).
- 알림 `key` 는 같은 사건을 묶는 이름이다. 스로틀이 그 key 로 반복을 줄이므로, 요청마다 달라지는 값(토큰·시각)을 key 에 넣지 않는다.
- 알림 본문에도 로그와 같은 금지(비밀값·개인정보)가 적용된다. `SlackAlertNotifier` 가 시크릿을 가리지만 그것에 기대지 않는다.
- `ERROR` 와 알림을 남발하면 알림이 무뎌진다 — 4xx 는 알림 대상이 아니다.

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 로그에 비밀번호·토큰·키·해시 | 자격 증명 유출 | Critical |
| 로그에 개인정보(주민번호·카드·전화·주소) | 개인정보 유출 | Critical |
| 요청·응답 본문 전체를 로그로 | 위 둘이 함께 새어 나감 | Critical |
| 4xx·사용자 입력 오류를 `ERROR` 로 | 진짜 장애가 묻히고 알림이 무뎌짐 | Important |
| 예외를 잡아 로그 찍고 그대로 다시 던짐 | 같은 사건이 여러 줄로 중복 | Important |
| 예외를 `e.message` 로만 남김 | 스택 트레이스 유실 | Important |
| 문자열 연결로 로그 메시지 조립 | 꺼진 레벨에서도 비용 발생 | Important |
| `LoggerFactory.getLogger("TodoService")` 문자열 | 클래스 이동 시 이름이 어긋남 | Important |
| 운영에서 봐야 할 것을 `DEBUG` 로 | 운영에서 보이지 않음 | Important |
| 폴백·전면 실패·0건 적재에 알림 없음 | 고장이 정상으로 보인다 | Critical |
| 도메인이 Slack(`SlackAlertNotifier`)을 직접 호출 | 알림 수단이 도메인에 묶임 | Important |
| 알림 key 에 요청마다 다른 값 | 스로틀이 안 걸려 알림 폭주 | Important |

## 체크리스트

- [ ] 로거를 `javaClass` 로 선언했는가

- [ ] 레벨이 "누가 봐야 하는가" 기준과 맞는가 (4xx 는 `ERROR` 가 아니다)

- [ ] 메시지를 `{}` 치환으로 쓰고 예외는 마지막 인자로 넘겼는가

- [ ] 같은 사건을 여러 곳에서 중복해 남기지 않는가

- [ ] 비밀값·개인정보·본문 전체가 로그·알림에 없는가

- [ ] 사람이 알아야 할 실패가 사건 → `AlertNotifier`(Slack)로 가는가
