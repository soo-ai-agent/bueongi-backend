---
paths:
  - "storage/*/src/main/resources/db/migration/**"
  - "storage/*/build.gradle.kts"
name: kotlin-migration
description: DB 스키마 변경 규칙(PostgreSQL 16 + PostGIS, Flyway). 마이그레이션 파일의 자리(테이블을 소유한 storage 모듈)와 이름(V<YYYYMMDDHHmm>__), PostGIS 확장·geometry 컬럼·GIST 인덱스, 이미 적용된 파일을 고치지 않는 규칙, 무중단 배포를 위한 컬럼 추가·삭제 순서를 다룬다. 테이블·컬럼을 추가·변경·삭제할 때 kotlin-entity·kotlin-repository 와 항상 함께 사용한다. "테이블 추가", "컬럼 추가", "스키마 변경", "마이그레이션", "PostGIS" 요청에도 사용할 것.
---

# DB 마이그레이션 (PostgreSQL + PostGIS, Flyway)

**자리:** 그 테이블을 소유한 storage 모듈 — `storage/db-<이름>/src/main/resources/db/migration/V<YYYYMMDDHHmm>__<설명>.sql`
(예: 안심시설 테이블은 `storage/db-safety-spot/src/main/resources/db/migration/`)

## 왜 필요한가 — 스키마의 원본은 마이그레이션 파일이다

DB 는 **PostgreSQL 16 + PostGIS** 하나다. 로컬도 운영도 같은 엔진을 쓴다 — H2 같은 메모리 DB 는 PostGIS 함수와
`geometry` 타입을 돌리지 못하므로 쓰지 않는다.

스키마를 만드는 것은 Flyway 뿐이다. 앱이 뜰 때 Flyway 가 아직 안 돈 파일을 버전 순서대로 실행한다.

| 무엇 | 로컬(`local` 프로파일) | 운영(default) |
|---|---|---|
| DB | docker compose 의 `postgis/postgis` (Docker 가 없으면 로컬 설치 PostgreSQL + PostGIS) | 운영 PostgreSQL + PostGIS |
| Flyway | 켜짐 | 켜짐 |
| `ddl-auto` | JPA 엔티티가 있는 모듈만 `validate` | 같음 |

**JPA 엔티티를 둔 모듈은 `ddl-auto: validate` 로만 둔다.** Hibernate 가 뜰 때 엔티티와 실제 테이블이 다르면 앱이 뜨지 않는다 —
그래서 엔티티에 필드를 더하고 마이그레이션을 빠뜨리면 부팅에 실패한다. `create`·`update` 는 어느 프로파일에서도 쓰지 않는다.
JdbcTemplate 만 쓰는 모듈은 `ddl-auto` 를 두지 않는다.

## 파일 이름 — 버전은 날짜와 시각으로

```
V202608141530__add_todo_due_date.sql
│└──────────┘  └────────────────┘
│  버전(분까지)     무엇을 하는지 (소문자 스네이크)
└ 접두 V 는 고정
```

- 버전은 **`YYYYMMDDHHmm`** 로 쓴다. 순번(`V1`, `V2`)을 쓰지 않는다.

  여러 브랜치가 **병렬로** 스키마를 바꾼다. 두 작업이 동시에 `V3` 을 만들면 Flyway 가 중복 버전으로 실패한다. 시각 기반이면 부딪히지 않는다.

- 시각 버전은 먼저 머지된 브랜치보다 **더 이른 버전**이 나중에 들어올 수 있다. Flyway 는 기본으로 그것을 거부하므로
  `spring.flyway.out-of-order: true` 를 그 storage 모듈의 yml 에 둔다(아래 「처음 한 번」).

- 설명은 파일만 보고 무엇을 하는지 알 수 있게 쓴다. `V202608141530__update.sql` 은 이름이 없는 것과 같다.

- 구분자는 **밑줄 두 개**(`__`)다. 하나면 Flyway 가 인식하지 못한다.

- **storage 모듈이 둘 이상 테이블을 가지면 Flyway 기록표를 나눈다.** 모든 모듈의 `db/migration` 이 같은 클래스패스
  위치로 합쳐지므로, 모듈마다 위치(`classpath:db/migration/<이름>`)를 따로 두거나 버전이 겹치지 않게 한다 — 같은 버전 두 파일은 부팅 실패다.

## 쓰는 법 — PostgreSQL 문법

```sql
-- V202608141530__add_todo_due_date.sql
ALTER TABLE todo
    ADD COLUMN due_date DATE NULL;

COMMENT ON COLUMN todo.due_date IS '마감일';
```

- **엔티티·Store 변경과 마이그레이션은 같은 커밋에 넣는다.** 따로 커밋하면 그 사이 커밋에서 부팅·테스트가 깨진다.

- 자료형은 PostgreSQL 것을 쓴다 — 식별자 `BIGINT GENERATED ALWAYS AS IDENTITY`(또는 `bigserial`), 시각 `TIMESTAMPTZ`,
  참·거짓 `BOOLEAN`, 문자열 `VARCHAR(n)`/`TEXT`. MySQL 의 `AUTO_INCREMENT`·`TINYINT(1)`·`DATETIME`·인라인 `COMMENT`·`KEY` 는 없다.

- 인덱스는 `CREATE INDEX` 문으로 따로 만든다. 엔티티가 있으면 `@Table(indexes = [...])` 와 이름을 맞춘다.

```sql
-- V202608141600__create_todo.sql
CREATE TABLE todo (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL,
    done       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_todo_member_id ON todo (member_id);
```

- 모듈이 `BaseEntity` 를 정의했다면 그것이 갖는 컬럼(`id`·`created_at`·`updated_at`)을 새 테이블에 빠뜨리지 않는다.

## PostGIS — 확장, 공간 컬럼, 공간 인덱스

**첫 마이그레이션에서 확장을 켠다.**

```sql
-- V202611250900__enable_postgis.sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

`CREATE EXTENSION` 은 **확장을 만들 권한이 있는 역할**로만 돈다(슈퍼유저, 또는 PostgreSQL 13+ 의 신뢰 확장이면 DB 소유자).
앱 계정에 그 권한이 없으면 이 파일에서 Flyway 가 멈춘다 — 운영에서는 DBA 가 미리 확장을 만들어 두거나, Flyway 만
권한 있는 계정(`spring.flyway.user`)으로 돌린다. `postgis/postgis` 도커 이미지는 기본 사용자가 슈퍼유저라 로컬은 그대로 된다.

**좌표는 `geometry(Point,4326)` 한 컬럼으로 둔다.** 위도·경도 두 숫자 컬럼에 거리 계산을 맡기지 않는다 — 인덱스를 못 탄다.

```sql
-- V202611250910__create_safety_spot.sql
CREATE TABLE safety_spot (
    id          BIGINT               GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type        VARCHAR(40)          NOT NULL,
    source      VARCHAR(40)          NOT NULL,
    name        VARCHAR(200)         NOT NULL,
    location    geometry(Point, 4326) NOT NULL,
    imported_at TIMESTAMPTZ          NOT NULL DEFAULT now()
);

CREATE INDEX idx_safety_spot_location ON safety_spot USING GIST (location);
CREATE INDEX idx_safety_spot_type_source ON safety_spot (type, source);
```

- 공간 조회(`ST_DWithin`·`ST_Intersects`)를 하는 컬럼에는 **GIST 인덱스**를 반드시 만든다. 없으면 전 행을 훑는다.
- 미터 단위 거리가 필요하면 쿼리에서 `location::geography` 로 바꿔 쓴다(**kotlin-repository**). 컬럼은 4326 하나로 둔다.
- 대량 적재 단위(종류·출처)로 지우고 다시 넣는다면 그 조합에 인덱스를 둔다(위 `type, source`).

## 절대 하면 안 되는 것 — 적용된 파일 수정

**한 번이라도 실행된 마이그레이션 파일은 고치지도, 지우지도, 이름을 바꾸지도 않는다.**

Flyway 는 실행한 파일의 체크섬을 DB 에 기록해 둔다. 내용이 바뀌면 다음 배포에서 체크섬 불일치로 **멈춘다.**

잘못 만들었으면 그 파일을 고치는 대신 **바로잡는 새 파일을 추가한다.**

아직 아무 데도 배포되지 않았다고 확신할 수 있을 때만(= 내 브랜치에서 방금 만들었고 아직 머지 전) 고쳐도 된다.
그때도 내 로컬 DB 에는 이미 기록이 남아 있으니 `flyway_schema_history` 의 그 줄을 지우거나 로컬 DB 를 다시 만든다.

## 배포 중에도 앱은 돌고 있다 — 확장 후 수축

배포는 순간이 아니다. 새 버전이 뜨는 동안 **구버전 앱이 같은 DB 를 보고 돌아간다.** 그래서 순서가 중요하다.

**컬럼 추가** — 한 번에 해도 안전하다. 단, `NULL` 허용이거나 `DEFAULT` 가 있어야 한다.

```sql
ALTER TABLE todo ADD COLUMN due_date DATE NULL;
```

`NOT NULL` 을 기본값 없이 추가하면 구버전 앱의 INSERT 가 전부 실패한다.

**컬럼 삭제·이름 변경** — 반드시 **배포 두 번**에 나눠서 한다.

| 순서 | 배포 | 하는 일 |
|---|---|---|
| 1 | 앱 먼저 | 코드에서 그 컬럼을 더 이상 읽지도 쓰지도 않게 바꾼다. 스키마는 그대로 |
| 2 | 그다음 | 아무도 안 쓰는 것을 확인하고 `DROP COLUMN` 마이그레이션을 넣는다 |

이름 변경은 삭제와 같다 — `RENAME` 대신 **새 컬럼 추가 → 데이터 복사 → 구 컬럼 삭제** 세 단계로 나눈다.

**데이터를 옮기는 SQL 과 스키마를 바꾸는 SQL 은 파일을 나눈다.** 하나가 실패했을 때 어디까지 됐는지 알 수 있어야 한다.

큰 테이블에 인덱스를 더할 때는 `CREATE INDEX CONCURRENTLY` 를 쓰고, 그 파일은 트랜잭션 밖에서 돌아야 하므로 문장 하나만 둔다.

## 처음 한 번 — 의존성과 설정

테이블을 처음 갖게 되는 storage 모듈에 넣는다. 이미 되어 있으면 넘어간다.

```kotlin
// storage/db-<이름>/build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-jdbc")
implementation("org.flywaydb:flyway-core")
runtimeOnly("org.flywaydb:flyway-database-postgresql")
runtimeOnly("org.postgresql:postgresql")
```

```yaml
# storage/db-<이름>/src/main/resources/db-<이름>.yml
spring:
  flyway:
    enabled: true
    out-of-order: true     # 병렬 브랜치의 시각 버전이 늦게 들어와도 받는다
```

접속 정보(`spring.datasource.*`)는 환경변수로 채운다(**kotlin-config**). 버전은 적지 않는다 — Spring Boot 의 의존성 관리가 맞는 버전을 넣어 준다.

## 적발 신호

| 신호 | 문제 | 심각도 |
|---|---|---|
| 엔티티·Store 쿼리가 바뀌었는데 마이그레이션 파일이 없음 | `validate` 실패로 부팅 불가, 또는 운영에서 SQL 오류 | Critical |
| 이미 머지된 마이그레이션 파일을 수정·삭제·개명 | 체크섬 불일치로 이후 배포 전부 중단 | Critical |
| 기본값 없는 `NOT NULL` 컬럼 추가 | 배포 중 구버전 앱의 INSERT 실패 | Critical |
| 한 배포에서 컬럼을 지우거나 이름 변경 | 배포 중 구버전 앱이 없는 컬럼 참조 | Critical |
| `ddl-auto` 를 `create`·`update` 로 둠 | 스키마의 원본이 둘이 된다 | Critical |
| 공간 조회 컬럼에 GIST 인덱스 없음 | 전 행 스캔 | Important |
| MySQL 문법(`AUTO_INCREMENT`·`TINYINT(1)`·`DATETIME`·인라인 `KEY`) | PostgreSQL 에서 실패 | Important |
| 순번 버전(`V1`, `V2`) 사용 | 병렬 브랜치끼리 버전 충돌 | Important |
| 시각 버전인데 `out-of-order` 미설정 | 늦게 머지된 이른 버전이 거부됨 | Important |
| 구분자가 밑줄 하나(`V..._name.sql`) | Flyway 가 파일을 인식하지 못함 | Important |
| 테이블을 소유하지 않은 모듈(core·core-app)에 마이그레이션 | 스키마 소유가 흐려진다 | Important |
| 엔티티 변경과 마이그레이션이 다른 커밋 | 중간 커밋에서 빌드·부팅 깨짐 | Important |
| 스키마 변경과 데이터 이관이 한 파일 | 실패 지점 파악 불가 | Important |

## 체크리스트

- [ ] 테이블을 바꿨다면 같은 커밋에, 그 테이블을 소유한 storage 모듈에 마이그레이션 파일이 있는가

- [ ] 파일명이 `V<YYYYMMDDHHmm>__<설명>.sql` 이고 밑줄이 두 개인가

- [ ] PostgreSQL 문법이고, 좌표는 `geometry(Point,4326)` + GIST 인덱스인가

- [ ] 첫 마이그레이션이 `CREATE EXTENSION IF NOT EXISTS postgis` 를 갖고, 그 권한을 누가 갖는지 확인했는가

- [ ] 추가한 컬럼이 `NULL` 허용이거나 `DEFAULT` 를 갖는가

- [ ] 삭제·개명을 한 배포에 몰아넣지 않았는가

- [ ] 이미 머지된 마이그레이션 파일을 건드리지 않았는가

- [ ] 로컬 PostGIS 에 대고 앱을 한 번 띄워 Flyway 가 통과하는가
