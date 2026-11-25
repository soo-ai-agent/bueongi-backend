# bueongi-backend

안심 귀가 앱 부엉이(bueongi)의 백엔드입니다. 장소 검색, 안전 경로 비교, 주변 안심시설 조회와 보호자 위치 공유 API를
제공합니다. 이 문서는 로컬 실행, 설정, 모듈 구조와 코드 작성 규칙을 다룹니다.

- API 요청과 응답 계약: [`docs/API.md`](docs/API.md)
- 켜질 때와 요청 시 부르는 외부 서비스: [`docs/EXTERNAL_SERVICES.md`](docs/EXTERNAL_SERVICES.md)
- 위치 공유 설계: [`docs/LOCATION_SHARING_DESIGN.md`](docs/LOCATION_SHARING_DESIGN.md)
- 운영 배포: [`deploy/README.md`](deploy/README.md)

## 기술 스택

| 항목        | 버전                                      |
|-------------|-------------------------------------------|
| Kotlin      | 1.9.25                                    |
| Spring Boot | 3.4.0                                     |
| Java        | 21                                        |
| 빌드        | Gradle 8.11 (wrapper 포함)                |
| 저장소      | Redis(위치 공유), 파일(안심시설 CSV, CDN) |

## 로컬 실행

### 1. 준비

- JDK 21이 필요합니다.
- Redis가 필요합니다. 위치 공유 저장소이며, 없어도 서버는 뜨지만 위치 공유 API가 503을 돌려줍니다.

  ```bash
  docker run -d -p 6379:6379 redis:7-alpine
  ```

- 저장소 루트에 `.env.local`(gitignore)을 만듭니다. 관리자 로그인 두 값이 없으면 기동하지 않습니다. 값을 만드는 법은
  [관리자 로그인 준비](#관리자-로그인-준비)를 참고합니다.

  ```dotenv
  BUEONGI_ADMIN_LOGIN_ID=admin
  BUEONGI_ADMIN_PASSWORD_HASH='<bcrypt 해시>'
  ```

### 2. 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun --no-daemon
```

`local` 프로파일은 다음을 처리합니다.

- `.env.local`을 읽습니다. 셸에서 `source`할 필요가 없습니다.
- 포트를 8119로 둡니다. 프론트 dev 서버(3619)의 프록시가 이 포트를 봅니다. `SERVER_PORT`를 주면 그 값이 우선합니다.
- `JWT_PUBLIC_KEY`, `JWT_PRIVATE_KEY`가 없으면 기동할 때 임시 RSA 키쌍을 만듭니다. 재시작하면 이전에 발급한 관리자
  토큰은 무효가 됩니다.

### 3. 확인

```bash
curl http://localhost:8119/api/health
```

```json
{"status":"UP","service":"bueongi-backend","locationStore":"redis"}
```

`locationStore`는 위치 공유 저장소의 종류입니다. Redis 연결 상태를 뜻하지 않습니다.

### IntelliJ에서 실행

프로젝트를 열면 `.run/`에 저장된 실행 구성 "Bueongi Backend (8119)"가 보입니다. 프로파일(`local`), 포트(8119)와
작업 디렉터리가 들어 있어 실행 버튼만 누르면 됩니다.

단, 따옴표를 벗긴 `.env.local.properties`(gitignore)가 있어야 합니다. `.env.local`의 따옴표 값(`'$2y$...'`)은
셸용이라 스프링이 그대로 읽으면 따옴표까지 해시로 받아 기동이 실패합니다. `.env.local`을 바꿨다면 아래 명령으로 다시
만듭니다.

```bash
python3 -c "
import pathlib
out=[]
for l in pathlib.Path('.env.local').read_text().splitlines():
    if '=' in l and not l.lstrip().startswith('#'):
        k,v=l.split('=',1); v=v.strip()
        if len(v)>=2 and v[0]==v[-1] and v[0] in ('\'','\"'): v=v[1:-1]
        out.append(f'{k}={v}')
    else: out.append(l)
pathlib.Path('.env.local.properties').write_text('\n'.join(out)+'\n')"
```

## 테스트와 빌드

```bash
./gradlew test --no-daemon
./gradlew build --no-daemon
java -jar core/core-app/build/libs/core-app-0.0.1-SNAPSHOT.jar
```

- `test`는 전체 테스트를 돌립니다. 테스트는 인메모리 대역을 쓰므로 Redis가 없어도 됩니다.
- `build`는 테스트 후 jar를 만듭니다. 실행 jar는 부팅 모듈 `core-app`의 산출물입니다.
- 테스트 메서드 이름이 한글이라 로케일이 UTF-8이어야 합니다. CI는 `LANG=C.UTF-8`로 돕니다.
- 부팅 모듈이 `core-app` 하나라 루트에서 `bootRun`, `bootJar`, `test`가 그대로 동작합니다.

## 환경변수

모든 설정은 환경변수로 넣습니다. 관리자 인증 값 외에는 비워 두면 기본값이나 폴백으로 동작합니다. 시크릿은 코드,
로그와 커밋에 넣지 않습니다.

### 필수

| 환경변수                      | 설명                                                                                         |
|-------------------------------|----------------------------------------------------------------------------------------------|
| `BUEONGI_ADMIN_LOGIN_ID`      | 관리자 로그인 아이디                                                                         |
| `BUEONGI_ADMIN_PASSWORD_HASH` | 관리자 비밀번호의 bcrypt 해시. 원문은 저장하지 않습니다                                      |
| `JWT_PUBLIC_KEY`              | 관리자 JWT 검증용 RSA 공개키(PEM 또는 base64-DER). `local` 프로파일에서는 생략할 수 있습니다 |
| `JWT_PRIVATE_KEY`             | 관리자 JWT 발급용 PKCS#8 RSA 개인키. `local` 프로파일에서는 생략할 수 있습니다               |

### 선택

| 환경변수                       | 기본값                                         | 설명                                                               |
|--------------------------------|------------------------------------------------|--------------------------------------------------------------------|
| `BUEONGI_REDIS_URL`            | `redis://localhost:6379`                       | 위치 공유 Redis. 비밀번호가 있으면 `redis://:비밀번호@호스트:6379` |
| `BUEONGI_SHARE_BASE_URL`       | `http://localhost:8119`                        | 공유 링크(`share_url`)의 앞부분. 운영은 보호자가 여는 공개 도메인  |
| `BUEONGI_CORS_ORIGINS`         | `https://localhost:3619,http://localhost:3619` | `/api/**`, `/share/**`에 허용할 origin(쉼표 구분)                  |
| `BUEONGI_ADMIN_EMAIL`          | (빈 값)                                        | 로그인 응답에 표시할 관리자 이메일                                 |
| `TMAP_APP_KEY`                 | (빈 값)                                        | Tmap 경로와 장소 검색                                              |
| `DATA_GO_KR_SERVICE_KEY`       | (빈 값)                                        | 공공데이터포털(보안등)                                             |
| `SAFEMAP_SERVICE_KEY`          | (빈 값)                                        | 생활안전지도(경찰 시설)                                            |
| `SAFETYDATA_SERVICE_KEY`       | (빈 값)                                        | 재난안전데이터(가로등)                                             |
| `SLACK_WEBHOOK_URL`            | (빈 값)                                        | 운영 알림. 비우면 알림을 보내지 않음                               |
| `SLACK_ALERT_THROTTLE_MINUTES` | `10`                                           | 같은 알림을 다시 보내기까지 기다리는 시간(분)                      |
| `BUEONGI_FACILITY_DATA_DIR`    | `cdn`                                          | 안심시설 CDN 파일 디렉터리                                         |
| `BUEONGI_FACILITY_CCTV_CSV`    | `data/source/cctv/CCTV정보.csv`                | CCTV 초기 적재 CSV. `.gz`도 읽음                                   |
| `BUEONGI_WATCHDOG_INTERVAL_MS` | `60000`                                        | 도착 미확인 감시 주기(ms)                                          |
| `BUEONGI_HTTP_CONNECT_TIMEOUT` | `3s`                                           | 외부 API 연결 타임아웃                                             |
| `BUEONGI_HTTP_READ_TIMEOUT`    | `10s`                                          | 외부 API 응답 타임아웃                                             |

- Slack 알림은 처리되지 않은 500, 시설 적재 0건, Tmap 경로 전면 실패, 장소 검색 실패, 도착 미확인 때 보냅니다.
- CCTV 원천 CSV는 [`data/source/README.md`](data/source/README.md)를 참고합니다.

### 관리자 로그인 준비

저장소 밖의 안전한 곳에서 키쌍과 비밀번호 해시를 만듭니다. 개인키는 백엔드에만 넣습니다.

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem

export JWT_PRIVATE_KEY="$(openssl pkey -in jwt-private.pem -outform DER | base64 | tr -d '\n')"
export JWT_PUBLIC_KEY="$(openssl pkey -pubin -in jwt-public.pem -outform DER | base64 | tr -d '\n')"
export BUEONGI_ADMIN_LOGIN_ID=admin
export BUEONGI_ADMIN_PASSWORD_HASH="$(htpasswd -bnBC 12 admin '원하는 비밀번호' | cut -d: -f2 | tr -d '\n')"
```

- `jwt-private.pem`, `.env.local`과 위 출력값은 커밋하지 않습니다.
- 키를 바꿀 때는 공개키와 개인키를 같은 쌍으로 함께 바꿉니다. 이전에 발급한 토큰은 바로 무효가 됩니다.

로그인을 확인하려면 `POST /api/v1/auth/admin/login`으로 토큰을 받은 뒤 access token으로 `/api/admin/me`가 200을
돌려주는지 봅니다. 리프레시 토큰으로는 관리자 API를 부를 수 없습니다.

```bash
curl -i -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" http://localhost:8119/api/admin/me
```

## API 요약

상세 계약은 [`docs/API.md`](docs/API.md)가 기준입니다. 관리자 API(`/api/admin/**`)는 로그인이 필요합니다.

| 메서드 | 경로                          | 설명                              |
|--------|-------------------------------|-----------------------------------|
| `GET`  | `/api/health`                 | 기동 확인과 위치 공유 저장소 종류 |
| `GET`  | `/api/app-info`               | 서버 버전                         |
| `GET`  | `/api/places/search?keyword=` | 장소 검색                         |
| `POST` | `/api/routes`                 | 안전, 큰길, 빠른 경로 비교        |
| `GET`  | `/api/safety-spots/nearby`    | 주변 안심시설                     |
| `POST` | `/share/create`               | 위치 공유 시작(1~3시간)           |
| `POST` | `/share/{token}/location`     | 위치 올리기                       |
| `GET`  | `/share/{token}/location`     | 보호자 위치 조회                  |
| `POST` | `/share/{token}/route`        | 경로 올리기                       |
| `GET`  | `/share/{token}/route`        | 보호자 경로 조회                  |
| `POST` | `/share/{token}/watching`     | 보호자가 보고 있는지 확인         |
| `POST` | `/share/{token}/end`          | 위치 공유 종료                    |
| `GET`  | `/share/{token}`              | 보호자 지도 페이지(HTML)          |
| `POST` | `/api/v1/auth/admin/login`    | 관리자 로그인                     |

## 프로젝트 구조

도메인마다 모듈을 하나씩 둡니다. 한 기능의 컨트롤러부터 서비스까지가 한 모듈 안에서 끝납니다. 레이어 이름과 패키지
규약은 [team-dodn/spring-boot-kotlin-template](https://github.com/team-dodn/spring-boot-kotlin-template)을 따릅니다.

```text
core/
├── core-app/                # 유일한 실행 모듈: application.yml, /api/health, /api/app-info, 웹 테스트
├── core-route/              # 경로 비교(후보 수집, 안전 점수, 경로변 시설)
├── core-share/              # 위치 공유, 보호자 페이지, 관리자 공유 조회
├── core-place/              # 장소 검색
├── core-safety-spot/        # 안심시설 적재와 주변 조회
├── core-admin-auth/         # 관리자 로그인, JWT, SecurityConfig
├── core-admin-ops/          # 운영 알림, Slack 설정, 외부 수집 현황
├── core-admin-safety-spot/  # 관리자 시설 현황, CSV 업로드
├── core-common/             # 둘 이상의 도메인이 쓰는 것, 앱 전역 웹 설정(advice, CORS, 필터)
└── core-enum/               # 여러 계층이 함께 쓰는 enum
storage/
├── db-safety-spot/          # 시설 데이터(파일)와 공간 인덱스
└── db-share/                # 위치 공유 세션(Redis)
clients/                     # 외부 API 어댑터: 서로, 상위를 참조하지 않음
├── client-place/            # 장소 검색 계약(외부 호출 없음)
├── client-tmap-place/       # 장소 검색 Tmap 구현
├── client-tmap/             # Tmap 경로
├── client-slack/            # 운영 알림 발송
├── client-safemap/          # 안심시설 공공 API
├── client-safe182/
├── client-datagokr-security-lamp/
└── client-safetydata-street-lamp/
support/
├── logging/
├── monitoring/
└── util/                    # 도메인 지식 없는 범용 함수(좌표 변환, 통계, 포맷 등)
```

- 도메인 모듈 안은 `api/`(`controller/`, `request/`, `response/`)와 `domain/`(`service/`, `model/`, 필요하면
  `config/`, `error/`)으로 나눕니다. 역할별 폴더는 파일이 10개를 넘을 때부터 나눕니다.
- 도메인 모듈끼리는 참조하지 않습니다. 공유가 필요하면 `core-common`이나 `storage` 인터페이스를 거칩니다.
- `/api/admin/**`은 모듈이 아니라 경로 규약입니다. 관리자 조회도 그 데이터를 가진 도메인 모듈이 답합니다.
- `@WebMvcTest`, `@SpringBootTest`는 `core-app`에만 둡니다. 모듈을 넘나드는 테스트 대역은 `java-test-fixtures`로
  노출합니다(예: `storage:db-share`의 `InMemoryShareSessionStore`).
- 모듈마다 자기 설정 yml을 갖고, `application.yml`이 `spring.config.import`로 합칩니다.

## 코드 작성 규칙

모든 백엔드 코드와 리뷰에 적용합니다. 레이어별 예시와 리뷰 절차는 `bueongi-service` 저장소의
`.claude/skills/backend-spring-kotlin` 스킬에 있습니다.

### 레이어와 모듈

1. Controller는 요청 변환, 인증 정보 추출과 서비스 위임만 합니다. 응답 DTO는 서비스가 `XxxResponse.from(...)`으로
   만들어 돌려줍니다. 검증은 스키마 계층(본문 DTO의 `@Valid`, `toValidatedQuery`와 쿼리 파라미터의
   `toValidatedGeoPoint`)에서 합니다.
2. 업무 흐름은 각 도메인 모듈의 `domain/*`에서 조율합니다.
3. 상세 구현은 구현 레이어로 분리하고 재사용 가능한 단위로 씁니다(예: `SafetyScorer`, `SafeDetourPlanner`,
   `NearestPoliceFinder`).
4. 저장소 접근은 `storage:db-<도메인>`으로 격리합니다. 파일과 Redis 접근 코드는 storage 밖에 두지 않습니다.
5. 참조는 상위에서 하위로 한 방향입니다: `core-app` -> `core-<도메인>` -> (`core-common`, `core-enum`, storage,
   clients, support). storage, clients, support는 서로도, 상위도 참조하지 않습니다. 도메인 모듈 사이 순환은
   허용하지 않습니다.
6. 외부 시스템과 프레임워크에 의존하는 로직은 도메인 모델 밖(clients, storage)에 둡니다.

### 이름

7. 클래스 이름은 대상(도메인, 역할, 책임)을 나타냅니다. 구현 레이어는 `~Finder`, `~Scorer`, `~Planner`,
   `~Notifier`처럼 역할이 드러나게 짓습니다.
8. 메서드 이름은 그 클래스 맥락에서 하는 동작을 나타냅니다. 클래스가 대상을 충분히 말하면 `list`, `create`처럼 짓고,
   모호하면 `getById`, `listActive`처럼 조건이나 대상을 덧붙입니다.
9. 저장소 계층은 `findBy...`, `existsBy...`, `save`, `deleteBy...`처럼 의도가 드러나게 짓습니다(예:
   `ShareSessionStore.findByToken`).
10. 도메인 용어(route, place, share, safetySpot)를 클래스와 메서드 이름에 일관되게 씁니다.

### 도메인 모델

11. 상태 변경 규칙과 불변식은 엔티티 메서드에 둡니다(예: `ShareSession.withLocation()`, `isExpired()`). 밖에서
    필드를 직접 바꾸지 않습니다.
12. 애그리게이트 경계를 넘는 상태 변경은 루트 엔티티를 통해 합니다.
13. 도메인 서비스는 유스케이스 조율에 집중합니다(예: `ShareService`, `RouteOptionService`).

### 기초 문법과 가독성

14. 코드를 모르는 사람도 흐름을 따라갈 수 있게 기초 문법으로 씁니다. 널 단언(`!!`), 리플렉션과 기교는 쓰지
    않습니다. 조건 분기는 한 줄로 압축하지 않고 여러 줄 블록(`{ }`)으로 씁니다.
15. 클래스에는 "왜 이렇게 했는지"를 한국어로 남깁니다. 수학, 동시성처럼 본질적으로 어려운 코드는 한 파일에 모으고
    테스트로 정답을 보증합니다.
16. 웹 DTO, 도메인 입력과 저장 모델을 계층별로 나누고, 변환 메서드(`toReport`, `toStoredRoute` 등)는 DTO 안 한
    곳에 모읍니다. 저장 모델은 불변 data class로 둡니다.
17. 최상위 `class`, `data class`, `enum class`, `interface`, `object`는 이름이 같은 개별 `.kt` 파일에 둡니다.
    중첩 타입만 소유 클래스 파일 안에 둡니다.

### URL

18. 경로는 자원을 가리키고, 행위는 HTTP 메서드가 말합니다. 자원은 복수형 명사와 하이픈 표기(`/api/safety-spots`),
    단건은 경로 식별자, 걸러 보기는 쿼리 문자열로 나타내며 중첩은 한 겹까지만 둡니다. 경로에 `create`, `end`,
    `search` 같은 동사를 넣지 않습니다. 생성은 201, 본문 없는 삭제는 204, 그 외 성공은 200을 돌려줍니다.
    - 이 규칙 전에 만든 경로 일부(`/share/create`, `/share/{token}/end`, `/api/places/search` 등)는 어긋나
      있습니다. 웹과 모바일이 이미 부르고 있어 클라이언트와 한 번에, 사람 승인 후에만 바꿉니다.
    - 새 엔드포인트는 처음부터 이 규칙대로 만듭니다.
