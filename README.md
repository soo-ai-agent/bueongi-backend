# bueongi-backend

안전(안심) 귀가 앱 **부엉이(bueongi)** 의 백엔드. 장소 검색·경로 비교·경로상 안심시설 조회 REST API를 제공한다.
App Factory 독립 프로젝트(`projectId=bueongi`)로, 별도 경로/포트/kanban으로 운영된다.

> 이 문서는 **빌드·실행·설정(온보딩/운영)** 안내다.
> API 요청/응답 계약(엔드포인트·shape·에러 code)은 [`docs/API.md`](docs/API.md)를 참조한다.

## 기술 스택

| 항목 | 버전 |
| --- | --- |
| Kotlin | 1.9.25 |
| Spring Boot | 3.4.0 |
| Java | 21 |
| 빌드 | Gradle (wrapper 포함) |

의존성: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-redis`(위치 공유 저장), `kotlin-reflect`, `jackson-module-kotlin`, (test) `spring-boot-starter-test`, `mockito-kotlin`.

- group: `io.soo.bueongi` / version: `0.0.1-SNAPSHOT`
- 장소/경로 API는 **stateless**이며 외부 키 없이 **결정론적**으로 동작한다(MVP). 실연동 시 내부 구현만 교체되고 응답 계약은 동일.
- **위치 공유(`/share`)만 stateful** — Redis(`BUEONGI_REDIS_URL`) 기반 1~3시간 TTL 자동 만료. **Redis 미설정 시 인메모리 폴백**으로 동일 계약·무중단 동작(`docs/LOCATION_SHARING_DESIGN.md`).

## 사전 요구사항

- **JDK 21** (Gradle toolchain이 `javaVersion=21` 고정).
- 별도 DB 불요. **Redis 는 선택**(미설정 시 위치 공유가 인메모리 폴백). 장소/경로는 인메모리 시드로 동작.

## 빌드·실행·테스트

```bash
# 개발 서버 실행 → http://localhost:8119
set -a
source .env.local
set +a
./gradlew bootRun --no-daemon

# 전체 테스트
./gradlew test

# 프로덕션 빌드(jar)
./gradlew build

# 빌드된 jar 직접 실행
java -jar core/core-api/build/libs/core-api-0.0.1-SNAPSHOT.jar  # 부팅 모듈(core:core-api) 산출물
```

`local` 프로파일은 `backend/.env.local`을 자동으로 읽는다. 로컬 실행은 아래 명령을 표준으로 사용한다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun --no-daemon
```

로컬에서 JWT 키가 없으면 실행 시 임시 RSA 키쌍을 생성하므로 별도 키 설정 없이 기동할 수 있다.
서버를 재시작하면 기존 관리자 토큰은 무효화된다. 운영에서는 `JWT_PUBLIC_KEY`와
`JWT_PRIVATE_KEY`가 모두 없으면 기동이 중단된다.

서버 포트는 **8119** 고정(`application.yml`의 `server.port`). 프론트 dev 서버는 3619(strictPort).

### 가동 확인

```bash
curl http://localhost:8119/api/health
# → {"status":"UP","service":"bueongi-backend","placeSource":"seed","locationStore":"memory"}
```

`placeSource`는 Kakao 키 주입 여부에서 파생한 라벨이다(`seed`=내장 시드 폴백, `live`=외부 실연동). `locationStore`는 위치 공유 저장소 라벨(`memory`=인메모리 폴백, `redis`=Redis). **키 값·Redis 자격증명은 응답에 절대 노출하지 않는다.**

## 환경변수 (설정)

모든 설정은 환경변수로 주입한다. 선택 설정은 미설정 시 기본값으로 동작하지만
관리자 인증 관련 값은 모두 필수다. **시크릿 값은 코드/로그/커밋에 넣지 않는다.**

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `JWT_PUBLIC_KEY` | 없음 | 부엉이가 발급한 관리자 JWT 검증용 RSA 공개키. PEM 또는 base64-DER. |
| `JWT_PRIVATE_KEY` | 없음 | 관리자 JWT 발급용 PKCS#8 RSA 개인키. **서버에만 보관하며 Git에 커밋하지 않는다.** |
| `BUEONGI_ADMIN_LOGIN_ID` | 없음 | 관리자 로그인 아이디. |
| `BUEONGI_ADMIN_PASSWORD_HASH` | 없음 | 관리자 비밀번호의 bcrypt 해시. 원문 비밀번호는 저장하지 않는다. |
| `BUEONGI_ADMIN_EMAIL` | `admin@bueongi.local` | 로그인 응답에 표시할 관리자 이메일. |
| `KAKAO_REST_API_KEY` | (빈 값) | Kakao Local REST 키. **미설정이면 내장 시드 카탈로그**로 장소 검색. 주입 시 실연동을 시도하되 실패하면 시드로 폴백(안심앱: 검색 불능보다 근사 결과가 안전). |
| `DATA_GO_KR_SERVICE_KEY` | (빈 값) | 공공데이터포털(행안부 조회서비스) Encoding 키. **미설정이면 시설 라이브 갱신 비활성**(파일 적재분 유지). 주입 시 CCTV·안전비상벨·여성안심지킴이집·전국보안등을 기동 직후 1회 + `BUEONGI_FACILITY_REFRESH_CRON`(기본 매월 1일 04:00 KST) 주기로 재수집해 인덱스를 무중단 교체. 수집 실패/0건이면 교체하지 않는다. |
| `SAFEMAP_SERVICE_KEY` | (빈 값) | 생활안전지도(safemap.go.kr) 인증키. **미설정이면 치안시설 라이브 갱신 비활성**(파일 적재분 유지). 주입 시 전국 지역경찰관서(지구대·파출소·치안센터)를 같은 스케줄로 재수집 — 좌표는 EPSG:5179/WGS84 자동판별 후 WGS84 로 변환·검증. |
| `SAFETYDATA_SERVICE_KEY` | (빈 값) | 재난안전데이터 공유 플랫폼(safetydata.go.kr) 인증키. 주입 시 공통POI 가로등(DSSP-IF-00084)을 같은 스케줄로 재수집 — 좌표(XMAP_CRTS/YMAP_CRTS)는 실응답 EPSG:3857과 EPSG:5179/WGS84를 자동판별한 뒤 WGS84로 변환·검증. **가로등(LAMP) 인덱스는 이 키와 `DATA_GO_KR_SERVICE_KEY`(전국보안등 표준데이터)의 수집분을 합쳐 만든다** — 좌표 5자리(≈1.1m)로 중복 제거하며, 활성 원천 중 하나라도 실패하면 교체하지 않는다(부분 교체는 커버리지를 되레 줄인다). 두 키 모두 없으면 가로등 라이브 갱신 비활성(파일 적재분 유지). |
| `SLACK_WEBHOOK_URL` | (빈 값) | Slack Incoming Webhook URL. **미설정이면 운영 알림 전체 비활성**(무해). 주입 시 ①처리되지 않은 500 ②시설 라이브 갱신 실패/0건 ③Tmap 경로 전면 실패(폴백 전환) ④Kakao 장소검색 실패(시드 폴백)를 발송. 같은 알림 key 는 스로틀 창 안에서 1회만. |
| `SLACK_ALERT_THROTTLE_MINUTES` | `10` | 같은 알림 key 의 재발송 억제 창(분) — 장애 중 알림 폭주 방지. |
| `BUEONGI_FACILITY_REFRESH_CRON` | `0 0 4 1 * *` | 시설 라이브 갱신 cron(Asia/Seoul). 위 두 키 중 하나라도 있을 때만 동작. |
| `BUEONGI_CORS_ORIGINS` | `http://localhost:3619` | `/api/**`·`/share/**`에 허용할 CORS origin(콤마 구분). 운영 도메인은 여기에 덧붙인다. 허용 메서드는 GET/POST/OPTIONS 고정. |
| `BUEONGI_REDIS_URL` | (빈 값) | 위치 공유 저장 Redis(예 `redis://localhost:6379`). **미설정이면 인메모리 폴백**(단일 인스턴스, 재시작 시 휘발). 운영 다중 인스턴스·내구성이 필요하면 주입. |
| `BUEONGI_SHARE_BASE_URL` | `http://localhost:8119` | 공유 URL(`share_url`)의 base. 운영은 보호자가 접근 가능한 공개 도메인으로 덮어쓴다. |

### 관리자 로그인 키와 비밀번호 준비

부엉이가 관리자 로그인과 JWT 발급을 직접 담당한다. 저장소 밖의 안전한 서버 위치에서 키쌍을 만들고,
개인키는 부엉이 백엔드에만 주입한다.

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem

export JWT_PRIVATE_KEY="$(openssl pkey -in jwt-private.pem -outform DER | base64 | tr -d '\n')"
export JWT_PUBLIC_KEY="$(openssl pkey -pubin -in jwt-public.pem -outform DER | base64 | tr -d '\n')"
export BUEONGI_ADMIN_LOGIN_ID=admin
export BUEONGI_ADMIN_PASSWORD_HASH="$(htpasswd -bnBC 12 admin '원하는 비밀번호' | cut -d: -f2 | tr -d '\n')"
```

`jwt-private.pem`, `.env.local`과 환경변수 출력값은 Git에 커밋하지 않는다. 키 교체 시 공개키와 개인키를
같은 키쌍으로 함께 교체하며, 교체 전에 발급된 토큰은 즉시 무효화된다.

#### 검증

```bash
curl --fail http://localhost:8119/api/health
curl -i -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  http://localhost:8119/api/admin/me
```

먼저 `POST /api/v1/auth/admin/login`으로 토큰을 발급한 뒤 access token으로 `/api/admin/me`가
200을 반환하는지 확인한다. 리프레시 토큰은 관리자 API에 사용할 수 없다.

`backend/.env.local` 예:

```dotenv
JWT_PUBLIC_KEY=<base64-DER 또는 PEM 공개키>
JWT_PRIVATE_KEY=<base64-DER 또는 PKCS#8 PEM 개인키>
BUEONGI_ADMIN_LOGIN_ID=admin
BUEONGI_ADMIN_PASSWORD_HASH='<bcrypt 해시>'
KAKAO_REST_API_KEY=<your-key>
BUEONGI_CORS_ORIGINS=http://localhost:3619,https://bueongi.example.com
BUEONGI_REDIS_URL=redis://localhost:6379
BUEONGI_SHARE_BASE_URL=https://bueongi.example.com
```

```bash
set -a
source .env.local
set +a
./gradlew bootRun --no-daemon
```

## 프로젝트 구조 (멀티모듈 — 템플릿 그대로)

모듈과 패키지 모두 [team-dodn/spring-boot-kotlin-template](https://github.com/team-dodn/spring-boot-kotlin-template) 을 따른다.
**`core:core-api` 하나가 도메인들을 모두 포함한다** — 템플릿이 "초기 개발 생산성을 극대화하기 위해" 택한 형태다.

```
backend/
├── core/
│   ├── core-api/                # 부팅 모듈(유일한 bootJar/bootRun)
│   │   └── io/soo/bueongi/
│   │       ├── BueongiApplication.kt
│   │       └── core/
│   │           ├── api/
│   │           │   ├── auth/            # 인증(JWT·시큐리티 설정·토큰 서비스)
│   │           │   ├── config/          # CORS·CDN·스케줄링·요청 계측
│   │           │   └── controller/
│   │           │       ├── ApiControllerAdvice.kt · HealthController.kt
│   │           │       └── v1/          # 도메인별 컨트롤러
│   │           │           ├── request/     # 요청 DTO
│   │           │           └── response/    # 응답 DTO
│   │           ├── domain/              # 유스케이스 — 도메인별 서브패키지
│   │           │   ├── route/ share/ facility/(+coord) place/ admin/
│   │           └── support/error/       # 도메인 예외(에러 계약)
│   └── core-enum/               # 전 계층 공용 어휘(io.soo.bueongi.core.enums)
├── storage/db-core/             # 저장 격리 — 공유 세션(Redis/인메모리)·시설 적재(파일)·공간 인덱스
├── clients/                     # 외부 API 어댑터(자립 모듈)
│   ├── client-tmap/ · client-kakao/
└── support/ (logging · monitoring · util)
    └── util/                           # 도메인 지식 없는 범용 함수만 — 좌표변환(EPSG)·통계·시간버킷·포맷·JS이스케이프·PEM파싱
                                        #  판단 기준: 다른 프로젝트에 그대로 옮겨도 말이 되면 util, 아니면 소유 도메인에 둔다
```

- **컨트롤러는 `core/api/controller/v1`, 유스케이스는 `core/domain/<도메인>`.** 레이어로 먼저 나누고 그 안에서 도메인으로 나눈다.
- 요청·응답 DTO 는 `controller/v1/request`·`response` 에 둔다. 도메인 패키지에 두지 않는다.
- 도메인 서브패키지는 템플릿의 성장형이다(템플릿 동봉 예제는 `core/domain/` 평면).

- 부팅 모듈이 core-api 하나라 루트에서 `./gradlew bootRun|bootJar|test` 가 그대로 동작한다.
- 각 모듈은 자기 설정 파일(yml)을 소유하고 application.yml 이 `spring.config.import` 로 합성한다.

## 코드 작성 규칙

모든 백엔드 코드는 아래 규칙을 따른다. 신규 코드·리뷰·감사의 판정 기준이며,
위반은 리팩터링 대상이다(감사 이력: CLAUDE.md 변경 이력 2026-07-09·07-10).
상세 작성·리뷰 절차(레이어별 예시·이름 규칙 표·리뷰 7단계)는
**`.claude/skills/backend-spring-kotlin/SKILL.md`** 스킬이 정의한다 — 아래 16조와 함께 적용한다.

### 레이어·모듈
1. **Controller 는 요청/응답 변환과 인증 정보 추출만** 담당한다. 검증은 스키마 계층
   (본문 DTO `@Valid`·`toValidatedQuery`, 쿼리 파라미터 `toValidatedGeoPoint`)에서 한다.
2. **Business 흐름은 각 도메인 모듈의 `domain/*`** 에서 오케스트레이션한다.
3. **상세 구현은 구현 레이어로 분리**하고 재사용 가능한 단위로 작성한다
   (예: `SafetyScorer`·`SafeDetourPlanner`·`NearestPoliceFinder`·`SlackAlertNotifier`).
4. **저장소 접근은 `storage:db-core` 로 격리**한다 — 파일/Redis 접근 코드는 storage 밖에 두지 않는다.
5. **레이어 참조는 상위 → 하위 단방향**: core-api → (storage·clients·support·core-enum).
   storage/clients/support 는 서로도, 상위도 참조하지 않는다(clients 는 완전 자립).
6. **외부 시스템/DB/프레임워크 의존 로직은 도메인 모델 밖**(clients 어댑터·storage)으로 분리한다.

### 명명
7. **클래스명은 대상**(도메인/역할/책임)을 나타낸다. 구현 레이어는 `~Finder`·`~Scorer`·`~Planner`·
   `~Notifier` 처럼 재사용 역할이 드러나게 짓는다.
8. **메서드명은 그 클래스 맥락에서 수행 동작**을 나타낸다. 클래스가 대상을 충분히 설명하면
   `list`·`create`·`update` 같은 행위 중심, 모호하면 `getById`·`listActive` 처럼 조건/목적/대상을 보강한다.
9. **저장소 계층은 `findBy...`·`existsBy...`·`save`·`deleteBy...`** 등 의도가 드러나는 이름을 쓴다
   (예: `LocationStore.findByToken`/`deleteByToken`).
10. **도메인 용어(route·place·share·facility)를 클래스/메서드명에 일관** 반영한다.

### 도메인 모델
11. **상태 변경 규칙/불변식은 엔티티 메서드에 캡슐화**한다
    (예: `ShareSession.withLocation()`·`withRoute()`·`isExpired()` — 외부에서 필드 조작 금지).
12. **애그리게이트 경계를 넘는 상태 변경은 루트 엔티티를 통해** 수행한다.
13. **Domain Service 는 유스케이스 오케스트레이션/트랜잭션 경계에 집중**한다
    (예: `ShareService`·`SafeRouteService`·`FacilityRefreshService`).

### 기초 문법·가독성
14. 코드를 모르는 사람이 봐도 흐름을 따라갈 수 있게 **기초 문법에 충실**하게 쓴다 —
    data class·if/when/for·널 안전(`?.`/`?:`)·표준 컬렉션 함수 범위. 널 단언(`!!`)·리플렉션·기교 금지.
    **조건 분기(if/else·when)는 한 줄로 압축하지 않고 여러 줄 블록(`{ }`)으로 작성**한다 —
    `val x = if (cond) a else b` (금지) → `val x = if (cond) {` … `} else {` … `}` (권장).
15. 모든 클래스 상단 KDoc 에 **"왜 이렇게 했는지"** 를 한국어로 남긴다. 수학·동시성처럼 본질적으로
    어려운 코드는 한 파일에 격리하고 테스트(앵커·왕복 등)로 정답을 보증한다.
16. 웹 DTO ↔ 도메인 입력 ↔ 저장 모델을 계층별로 분리하고, **변환 메서드(`toReport`·`toStoredRoute` 등)를
    DTO 안 한 곳에** 모은다. 저장 모델은 불변 data class 로 둔다.
17. 최상위 `class`·`data class`·`enum class`·`interface`·`object`는 이름이 같은 개별 `.kt` 파일에 둔다.
    구현 세부인 중첩 타입만 소유 클래스 파일 안에 둔다. 여러 DTO나 예외를 하나의 묶음 파일에 모으지 않는다.

## API 계약

엔드포인트·요청/응답 shape·에러 code(10종)·도메인 상수는 **[`docs/API.md`](docs/API.md)** 가 단일 레퍼런스다(코드에서 파생한 현재 스냅샷, 계약 변경 시 코드와 함께 갱신). 위치 공유 설계/배경은 [`docs/LOCATION_SHARING_DESIGN.md`](docs/LOCATION_SHARING_DESIGN.md).

요약:

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/health` | 운영 readiness + `placeSource`·`locationStore` |
| `GET` | `/api/places/search?keyword=` | 장소 검색(이름/주소 부분일치) |
| `POST` | `/api/routes/compare` | safe/main/fast 3개 경로 비교 |
| `POST` | `/api/routes/facilities` | 경로상 안심시설(CCTV·비상벨·편의점·경찰) |
| `POST` | `/share/create` | 위치 공유 토큰 생성(`expires_in_hours` 1~3) |
| `POST` | `/share/{token}/location` | 사용자 위치 갱신(5초마다) |
| `GET` | `/share/{token}/location` | 보호자 위치 조회(5초 폴링, `expired` 플래그) |
| `GET` | `/share/{token}` | 보호자 지도 HTML(Leaflet+OSM) |

## 메모

- git: 현재 워크스페이스는 커밋 전(untracked). 커밋·push는 사용자 승인 영역.
- 누적 개발 이력/합의 베이턴은 `.claude/memories/consensus.md`, 이 문서와 `docs/API.md`는 현재 스냅샷만 담는다.
</content>
</invoke>
