---
paths:
  - "storage/**"
name: kotlin-repository
description: 저장소 접근 작성과 리뷰 규칙. storage/db-<이름> 의 Store 인터페이스(질의형 메서드·불변 저장 모델)와 그 뒤의 PostGIS 구현(JdbcTemplate·네이티브 SQL), JpaRepository 쿼리 메서드 이름, 페이징, N+1, 대량 적재(batchUpdate·COPY, 종류·출처 조각 교체)를 다룰 때 사용한다. "조회 쿼리 추가", "공간 쿼리", "반경 조회", "CSV 적재", "쿼리가 느리다", "N+1" 요청에도 사용할 것.
---

# Repository · Store

**자리:** `storage/db-<이름>/…/io/soo/bueongi/storage/db/<이름>/` — 예: `storage/db-safety-spot/…/storage/db/safetyspot/store/`

## 저장소 접근은 여기서 끝난다

SQL·JPQL·Redis 명령·파일 경로는 storage 모듈 밖으로 새지 않는다. 서비스·컨트롤러에 쿼리 문자열이 있으면 위반이다.

core 가 보는 것은 **Store 인터페이스(또는 JpaRepository)와 storage 소유의 불변 저장 모델**뿐이다.

## Store 인터페이스 — 질의로 말하고, 저장 모델을 돌려준다

```kotlin
// storage/db-safety-spot/…/storage/db/safetyspot/store/SafetySpotSpatialSource.kt
interface SafetySpotSpatialSource {
    fun findAlongPath(type: SafetySpotType, path: List<GeoPoint>, withinMeters: Int): List<SafetySpot>

    fun findNear(type: SafetySpotType, center: GeoPoint, radiusMeters: Int): List<SafetySpot>

    fun countAlongPath(type: SafetySpotType, path: List<GeoPoint>, withinMeters: Int): Int
}
```

- **메서드는 질의다** — "경로 30m 안", "반경 500m" 처럼 core 가 묻고 싶은 것을 이름과 파라미터로 말한다.
  저장소 명명(`findBy…`·`findNear…`·`countBy…`·`save`·`deleteBy…`)을 따른다(README 9조).

- **반환은 storage 소유의 불변 저장 모델**(`data class` + `val`)이다. `GeoPoint`·`SafetySpot` 처럼 위도·경도·이름 같은 값만 담는다.

- **기술 타입을 시그니처에 올리지 않는다.** JTS `Geometry`·`Point`, Hibernate 타입, `ResultSet`, Redis 직렬화 DTO,
  격자 인덱스 같은 자료구조(`GridSpatialIndex` 류)가 인터페이스에 보이면 저장 기술이 core 로 샌 것이다.
  지금의 `index(type): GridSpatialIndex` 같은 메서드는 위 질의형으로 옮겨 갈 대상이다.

- 구현 선택은 `@Conditional`·`@ConditionalOnProperty` 로 설정이 한다. core 나 부팅 모듈이 `if (postgis 있음)` 분기를 갖지 않는다.

```
storage/db-safety-spot/…/store/
  SafetySpotSpatialSource.kt          인터페이스 — core 가 아는 유일한 이름
  PostgisSafetySpotSpatialSource.kt   구현 — JdbcTemplate + 네이티브 SQL (internal 이어도 된다)
storage/db-share/…/
  ShareSessionStore.kt                인터페이스
  RedisShareSessionStore.kt           구현
  (testFixtures) InMemoryShareSessionStore.kt   테스트 대역
```

### 이 인터페이스는 의도된 예외다

**kotlin-common** 은 "구현이 하나뿐인 인터페이스 신설"을, 아래 「JPA 는 Store 래퍼를 만들지 않는다」는 JPA 위의 래퍼를 막는다.
공간 조회 Store 는 **두 규칙의 명시적 예외**다. 이유는 셋이다.

1. **공간 질의는 네이티브 SQL 이다.** `ST_DWithin` 은 JPQL·파생 쿼리로 표현되지 않으니, JpaRepository 가 Store 역할을 대신해 주지 못한다.
2. **core 는 JTS·Hibernate 타입을 보면 안 된다.** 인터페이스가 그 타입을 막는 경계이고, 구현이 저장 모델로 바꿔 올린다.
3. **인터페이스가 이미 있고 core 의 호출부가 많다.** 파일 기반 구현을 PostGIS 구현으로 바꿔도 호출부는 한 줄도 안 바뀐다.

이 예외는 **storage 의 Store 에만** 적용된다. core 안에 "나중에 바꿀지도 모르니" 인터페이스를 두는 것은 여전히 위반이다.

## 공간 쿼리 — PostGIS 구현 쪽 규칙

```kotlin
@Repository
internal class PostgisSafetySpotSpatialSource(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : SafetySpotSpatialSource {
    override fun findNear(type: SafetySpotType, center: GeoPoint, radiusMeters: Int): List<SafetySpot> {
        val params: MapSqlParameterSource = MapSqlParameterSource()
            .addValue("type", type.name)
            .addValue("lng", center.lng)
            .addValue("lat", center.lat)
            .addValue("radius", radiusMeters)
        return jdbcTemplate.query(FIND_NEAR_SQL, params, SafetySpotRowMapper)
    }

    companion object {
        // geography 로 바꿔야 반경이 미터다. location 의 GIST 인덱스를 타도록 ST_DWithin 을 쓴다
        private const val FIND_NEAR_SQL: String = """
            SELECT id, type, name, ST_Y(location) AS lat, ST_X(location) AS lng
            FROM safety_spot
            WHERE type = :type
              AND ST_DWithin(location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius)
            LIMIT 500
        """
    }
}
```

- 거리 조건은 `ST_DWithin` 으로 건다. `ST_Distance(...) < r` 은 인덱스를 못 탄다.
- 미터 단위는 `::geography`, 좌표 순서는 **경도(x), 위도(y)** 다(`ST_MakePoint(lng, lat)`). 순서를 바꾸면 조용히 엉뚱한 곳이 나온다.
- 경로를 따라 찾을 때는 경로를 `LINESTRING` 하나로 넘겨 한 쿼리로 묻는다. 좌표마다 쿼리를 돌리지 않는다(N+1).
- 결과 수에 상한(`LIMIT`)을 둔다.

## 대량 적재 — 한 트랜잭션에서 조각을 지우고 다시 넣는다

CSV 업로드처럼 수십만 행을 넣을 때는 **JPA `saveAll` 을 쓰지 않는다.** `IDENTITY` 키는 INSERT 마다 키를 받아 와야 해서
Hibernate 가 JDBC 배치를 끈다 — 수십만 번 왕복이 된다.

```kotlin
@Transactional
override fun replace(type: SafetySpotType, source: String, rows: List<SafetySpotRow>) {
    val sliceParams: MapSqlParameterSource = MapSqlParameterSource()
        .addValue("type", type.name)
        .addValue("source", source)
    jdbcTemplate.update(DELETE_SLICE_SQL, sliceParams)

    val batch: Array<MapSqlParameterSource> = toBatchParams(type, source, rows)
    jdbcTemplate.batchUpdate(INSERT_SQL, batch)
}
```

- **(종류, 출처) 조각 단위로 지우고 넣는다** — 한 출처를 새로 받는 일이 다른 출처를 건드리지 않는다.
- **지우기와 넣기는 한 트랜잭션이다.** 넣다가 실패하면 지운 것도 되돌아가 기존 데이터가 남는다(0건·실패가 기존 데이터를 지우지 않는다 — **kotlin-client**).
- `batchUpdate` 로 충분하지 않을 만큼 크면 PostgreSQL `COPY`(`CopyManager`)를 쓴다. 어느 쪽이든 Store 구현 안에서 끝난다.
- PostgreSQL JDBC URL 에 `reWriteBatchedInserts=true` 를 두면 배치 INSERT 가 한 문장으로 묶인다(**kotlin-config**).
- 이 트랜잭션은 저장 전용이다 — 안에서 외부 API 를 부르지 않는다.
- 엔티티가 있다면 **읽기 매핑용**이다. 적재 경로는 엔티티를 거치지 않는다.

## JpaRepository 를 쓸 때

```kotlin
interface TodoRepository : JpaRepository<TodoEntity, Long> {
    fun findAllByMemberIdOrderByIdDesc(memberId: Long, pageable: Pageable): List<TodoEntity>
    fun findByIdAndMemberId(id: Long, memberId: Long): TodoEntity?
    fun existsByMemberIdAndTitle(memberId: Long, title: String): Boolean
    fun deleteByIdAndMemberId(id: Long, memberId: Long): Long
}
```

- 이름으로 의도가 드러나게: `findBy...`, `existsBy...`, `save`, `deleteBy...`, `countBy...`.

- 반환 타입을 명시한다. 없을 수 있으면 `?`, 목록이면 `List<T>`.

- 단건 조회는 파생 쿼리(`findByIdAndMemberId`, `findOneById`)로 이름이 조건을 말하게 하는 편이 낫다. Spring Data 의 `findByIdOrNull`
  확장 함수를 써도 되며, 테스트에서는 그 안에서 불리는 `findById` 를 `whenever(...).thenReturn(Optional.of(...))` 로 스텁한다(**kotlin-test**).

**JPA 는 Store 래퍼를 만들지 않는다.** `JpaRepository` 인터페이스가 이미 그 역할이다 —
쓰지도 않을 교체 가능성을 위해 Store 를 한 겹 더 두는 것은 과잉이다. 예외는 위 「이 인터페이스는 의도된 예외다」뿐이다.

## 규칙

- **상한 없는 전체 조회를 만들지 않는다.** 목록은 조건이나 `Pageable`·`LIMIT` 을 받는다. 계약상 전체 반환이 맞다면 그 이유를 주석으로 남긴다.
  안심시설처럼 지금 종류별 전체를 메모리로 읽어 격자로 찾는 조회는 **공간 쿼리(`findNear`·`findAlongPath`)로 바꾸는 대상**이다 — 같은 꼴을 새로 만들지 않는다.

- 연관관계를 함께 쓸 조회는 `@EntityGraph` 나 `join fetch` 로 한 번에 가져온다 (N+1 방지).

- 조건이 세 개를 넘거나 조합이 유동적이면 메서드 이름을 늘리지 말고 `Specification` 을 별도 파일(`XxxSpecs.kt`)로 분리한다.

- 벌크 갱신은 `@Modifying(clearAutomatically = true)` 와 함께 쓰고, 영속성 컨텍스트가 어긋날 수 있음을 인지한다.

- 소유자 스코프가 있는 데이터는 **조회 단계에서 소유자 조건을 건다**(`findByIdAndMemberId`). 조회 후 애플리케이션에서 비교하지 않는다.

- storage 는 `core-common` 을 모른다. 없음·불변식 위반은 `null`·표준 예외(`check`·`require`)로 알리고, core 가 `ApiException` 하위 예외로 바꾼다.

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 소유자 조건 없이 id 로만 조회 후 코드에서 비교 | 권한 우회 위험 | Critical |
| 서비스·컨트롤러에 쿼리 문자열 | 저장소 격리 붕괴 | Critical |
| Store 인터페이스 시그니처에 JTS·Hibernate·`ResultSet`·인덱스 자료구조 | 저장 기술이 core 로 샘 | Critical |
| 조각 교체의 삭제와 삽입이 다른 트랜잭션 | 실패 시 기존 데이터가 사라진다 | Critical |
| 벌크 `@Modifying` 에 컨텍스트 정리 없음 | 낡은 엔티티 사용 | Critical |
| 수십만 행 적재를 JPA `saveAll`(IDENTITY)로 | 배치가 꺼져 왕복 폭증 | Important |
| `ST_Distance(...) < r` 로 거리 조건 | 인덱스를 못 탐 | Important |
| 경로 좌표마다 공간 쿼리 | N+1 | Important |
| 상한 없는 `findAll`·전체 메모리 적재 후 거르기 | 메모리·응답 폭증 | Important |
| 루프 안 단건 조회 | N+1 | Important |
| 이름이 의도를 감춤(`findData`) | 가독성 | Important |
| 조건이 많아 이름이 비대해진 메서드 | Specification 미분리 | Important |

## 체크리스트

- [ ] 메서드 이름이 조회 조건을 그대로 드러내는가

- [ ] Store 시그니처에 기술 타입 없이 저장 모델만 오가는가

- [ ] 목록·공간 조회에 조건·페이징·`LIMIT` 이 있는가

- [ ] 공간 조건이 `ST_DWithin` + GIST 인덱스를 타는가 (경도·위도 순서 확인)

- [ ] 대량 적재가 한 트랜잭션 안의 조각 삭제 + `batchUpdate`/`COPY` 인가

- [ ] 연관관계 조회가 N+1 을 만들지 않는가

- [ ] 소유자 조건이 쿼리에 포함됐는가

- [ ] SQL 을 실제 PostGIS 에 대고 `context` 테스트로 확인했는가
