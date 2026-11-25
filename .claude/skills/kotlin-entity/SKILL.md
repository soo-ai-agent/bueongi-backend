---
paths:
  - "storage/**"
name: kotlin-entity
description: JPA 엔티티 작성과 리뷰 규칙. storage/db-<이름> 의 @Entity 클래스, kotlin("plugin.jpa")·allOpen 빌드 설정, (모듈이 정의했다면) BaseEntity 상속, protected set 과 행위 메서드, 애그리게이트 경계, 연관관계 페치 전략, PostGIS geometry 매핑을 다룰 때 사용한다. "테이블 추가", "엔티티 상태 변경", "컬럼 추가" 요청에도 사용할 것.
---

# Entity

**자리:** `storage/db-<이름>/…/io/soo/bueongi/storage/db/<이름>/XxxEntity.kt` (예: `storage/db-safety-spot/…/storage/db/safetyspot/`)

## 엔티티가 필요한가부터 본다

이 저장소의 DB 는 **PostgreSQL 16 + PostGIS** 다. JPA 엔티티는 **읽기 매핑이 편할 때만** 둔다.

- 공간 조회(경로 30m 안, 반경 500m)는 네이티브 SQL 이라 JdbcTemplate 으로 쓰고 불변 저장 모델로 받는다 — 엔티티가 필요 없다(**kotlin-repository**).
- 수십만 건 적재는 JPA `saveAll` 로 하지 않는다 — `IDENTITY` 키는 JDBC 배치를 끈다. `batchUpdate`·`COPY` 를 쓴다(**kotlin-repository** 「대량 적재」).
- 상태가 바뀌고 불변식이 있는 행(관리자 설정, 세션 등)에 엔티티가 값을 한다. 아래 규칙은 엔티티를 두기로 했을 때의 규칙이다.

## 빌드 설정 — 엔티티를 소유한 storage 모듈에서

엔티티를 처음 두는 `storage/db-<이름>/build.gradle.kts` 에 셋을 함께 넣는다. 루트 `build.gradle.kts` 는 `plugin.spring` 만 걸어 두었다.

```kotlin
// storage/db-<이름>/build.gradle.kts
plugins {
    kotlin("plugin.jpa")          // 엔티티에 인자 없는 생성자를 만든다 (버전은 settings.gradle.kts 의 kotlinVersion)
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.hibernate.orm:hibernate-spatial")   // geometry 컬럼을 매핑할 때만
}
```

`settings.gradle.kts` 의 `resolutionStrategy` 에 `"org.jetbrains.kotlin.plugin.jpa"` 를 `kotlinVersion` 으로 묶는 한 줄도 더한다.
`ddl-auto` 는 엔티티가 있는 모듈의 yml 에서 `validate` 로만 둔다(**kotlin-migration**).

## 상태는 스스로만 바꾼다

```kotlin
@Entity
@Table(name = "todo")
class TodoEntity(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    // 바뀌는 값은 생성자에서 프로퍼티로 선언하지 않는다. 아래 본문에서 선언해야 setter 를 좁힐 수 있다
    title: String,
    done: Boolean = false,
) : BaseEntity() {

    @Column(name = "title", nullable = false, length = 200)
    var title: String = title
        protected set

    @Column(name = "done", nullable = false)
    var done: Boolean = done
        protected set

    fun rename(newTitle: String) {
        require(newTitle.isNotBlank()) { "제목은 비어 있을 수 없습니다" }
        title = newTitle
    }

    fun toggle() {
        done = !done
    }
}
```

- 바뀌지 않는 값은 `val`. 바뀌는 값만 `var` + **`protected set`**.

- **setter 를 좁히려면 프로퍼티를 클래스 본문에 선언해야 한다.** 생성자 파라미터에는 접근자를 붙일 수 없다 (`private set,` 은 문법 오류다). 생성자로는 값만 받고, 본문에서 `var x: T = x` 로 받아 선언한다.

- **`private set` 이 아니라 `protected set` 이다.** 위 `allOpen` 이 `@Entity`·`@MappedSuperclass`·`@Embeddable` 을 `open` 으로 만드는데, 코틀린은 `open` 프로퍼티에 `private set` 을 금지한다 (`Private setters for open properties are prohibited`).

  엔티티가 `open` 이어야 Hibernate 가 지연 로딩 프록시를 만들 수 있으므로 `allOpen` 은 끄지 않는다.

  `protected set` 으로도 목적은 그대로 달성된다 — 다른 클래스에서 `entity.done = true` 는 컴파일되지 않는다.

- **공개 세터를 만들지 않는다.** 밖에서 `entity.done = true` 가 가능하면 위반이다.

- 상태 변경 규칙과 불변식은 **엔티티 메서드에 캡슐화**한다. 검증 없이 대입만 하는 메서드는 세터와 같으니 만들지 않는다.

  ```kotlin
  // ❌ 이름만 행위 메서드지 검증 없는 대입 — 공개 세터와 같다
  fun updateTitle(newTitle: String) {
      title = newTitle
  }
  // ✅ 위 rename() 처럼 require 로 불변식을 지킨 뒤 바꾼다
  ```

- 식별자·생성시각·수정시각은 **그 모듈이 `BaseEntity`(`@MappedSuperclass`)를 정의했다면** 그것이 갖는다 — 다시 선언하지 않는다.
  정의하지 않았다면 위 예시의 `: BaseEntity()` 를 빼고 엔티티에 `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0` 을 직접 둔다.
  `BaseEntity` 는 엔티티가 둘 이상 생길 때 만든다.

## 애그리게이트

**애그리게이트는 "항상 함께 바뀌어야 하는 엔티티 묶음"이다.** 주문과 주문항목처럼, 따로 바꾸면 앞뒤가 안 맞는 것들을 하나로 묶는다.

그 묶음의 대표를 **루트**라고 하고, 바깥은 루트만 안다. 주문항목을 바꾸고 싶으면 주문에게 부탁한다 — 그래야 "금액 합계가 항목과 맞는다" 같은 규칙을 주문이 지킬 수 있다.

- 경계를 넘는 상태 변경은 **루트 엔티티를 통해** 수행한다. 자식 엔티티를 밖에서 직접 바꾸지 않는다.

- 자식 컬렉션은 `private` 으로 두고 읽기 전용 뷰만 노출한다.

```kotlin
@OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
private val _items: MutableList<OrderItemEntity> = mutableListOf()
val items: List<OrderItemEntity> get() = _items.toList()

fun addItem(product: Long, quantity: Int) {
    require(quantity > 0) { "수량은 1 이상이어야 합니다" }
    _items.add(OrderItemEntity(this, product, quantity))
}
```

## 매핑

- 연관관계는 전부 `FetchType.LAZY`. `@ManyToOne` 기본값이 EAGER 이므로 명시적으로 덮는다.

- `@Column` 에 `nullable`, `length` 를 명시한다. DB 제약을 애플리케이션 검증에 미루지 않는다.

- enum 은 `@Enumerated(EnumType.STRING)`. ORDINAL 은 값 추가 시 데이터가 깨진다.

- 시각은 `Instant`/`OffsetDateTime` ↔ `TIMESTAMPTZ` 로 맞춘다. `LocalDateTime` 은 시간대를 잃는다.

- 공간 컬럼(`geometry(Point,4326)`)을 엔티티에 매핑하면 JTS 타입(`org.locationtech.jts.geom.Point`)이 생긴다.
  **그 타입은 storage 밖으로 나가지 않는다** — Store 가 위도·경도 값 모델로 바꿔 돌려준다(**kotlin-repository**).

- 조회 조건이 있는 컬럼에는 인덱스를 `@Table(indexes = [...])` 로 선언한다.

- 동시 수정이 걸리는 엔티티에는 `@Version` 으로 낙관적 락을 건다.

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 공개 세터(`var` 에 `protected set` 없음) | 예측 불가능한 상태 변경 | Critical |
| 생성자 파라미터에 붙인 `private set`·`protected set` | 문법 오류 — 컴파일되지 않는다 | Critical |
| 엔티티 프로퍼티에 `private set` | `open` 프로퍼티라 금지된다 — `protected set` 을 쓴다 | Critical |
| 밖에서 프로퍼티 직접 대입 | 불변식 우회 | Critical |
| 자식 엔티티를 루트 밖에서 변경 | 애그리게이트 경계 붕괴 | Critical |
| `MutableList` 를 그대로 노출 | 내부 컬렉션 변형 | Critical |
| `@Enumerated` 기본값(ORDINAL) | 값 추가 시 데이터 파손 | Critical |
| `@ManyToOne` 에 LAZY 미지정 | 불필요한 조회·N+1 | Important |
| JTS `Geometry`·엔티티가 storage 밖 시그니처에 등장 | 저장 기술이 core 로 샘 | Critical |
| `plugin.jpa`·`allOpen` 없이 `@Entity` 추가 | 기본 생성자·프록시가 없어 런타임 실패 | Critical |
| 대량 적재를 엔티티 `saveAll` 로 | IDENTITY 키라 배치가 꺼져 수십만 번 왕복 | Important |
| `nullable`·`length` 미지정 | DB 제약 부재 | Important |
| 검증 없이 대입만 하는 변경 메서드 | 세터와 동일 | Important |

## 체크리스트

- [ ] 모든 변경 가능 프로퍼티가 **클래스 본문에 선언**되고 `protected set` 인가

- [ ] 상태 변경이 규칙을 가진 행위 메서드로만 일어나는가

- [ ] 컬렉션이 읽기 전용으로 노출되는가

- [ ] 연관관계가 LAZY 이고 enum 이 STRING 인가

- [ ] 엔티티를 둔 storage 모듈에 `plugin.jpa`·`allOpen` 이 있는가

- [ ] 같은 커밋에 Flyway 마이그레이션이 있는가 (**kotlin-migration**)
