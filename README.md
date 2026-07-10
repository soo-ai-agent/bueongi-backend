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
./gradlew bootRun

# 전체 테스트
./gradlew test

# 프로덕션 빌드(jar)
./gradlew build

# 빌드된 jar 직접 실행
java -jar core/core-api/build/libs/core-api-0.0.1-SNAPSHOT.jar  # 부팅 모듈(core:core-api) 산출물
```

서버 포트는 **8119** 고정(`application.yml`의 `server.port`). 프론트 dev 서버는 3619(strictPort).

### 가동 확인

```bash
curl http://localhost:8119/api/health
# → {"status":"UP","service":"bueongi-backend","placeSource":"seed","locationStore":"memory"}
```

`placeSource`는 Kakao 키 주입 여부에서 파생한 라벨이다(`seed`=내장 시드 폴백, `live`=외부 실연동). `locationStore`는 위치 공유 저장소 라벨(`memory`=인메모리 폴백, `redis`=Redis). **키 값·Redis 자격증명은 응답에 절대 노출하지 않는다.**

## 환경변수 (설정)

모든 설정은 환경변수로 주입하며, 미설정 시 기본값으로 동작한다. **시크릿 값은 코드/로그/커밋에 넣지 않는다.**

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `KAKAO_REST_API_KEY` | (빈 값) | Kakao Local REST 키. **미설정이면 내장 시드 카탈로그**로 장소 검색. 주입 시 실연동을 시도하되 실패하면 시드로 폴백(안심앱: 검색 불능보다 근사 결과가 안전). |
| `DATA_GO_KR_SERVICE_KEY` | (빈 값) | 공공데이터포털(행안부 조회서비스) Encoding 키. **미설정이면 시설 라이브 갱신 비활성**(파일 적재분 유지). 주입 시 CCTV·안전비상벨·여성안심지킴이집을 기동 직후 1회 + `BUEONGI_FACILITY_REFRESH_CRON`(기본 매월 1일 04:00 KST) 주기로 재수집해 인덱스를 무중단 교체. 수집 실패/0건이면 교체하지 않는다. |
| `SAFEMAP_SERVICE_KEY` | (빈 값) | 생활안전지도(safemap.go.kr) 인증키. **미설정이면 치안시설 라이브 갱신 비활성**(파일 적재분 유지). 주입 시 전국 지역경찰관서(지구대·파출소·치안센터)를 같은 스케줄로 재수집 — 좌표는 EPSG:5179/WGS84 자동판별 후 WGS84 로 변환·검증. |
| `SLACK_WEBHOOK_URL` | (빈 값) | Slack Incoming Webhook URL. **미설정이면 운영 알림 전체 비활성**(무해). 주입 시 ①처리되지 않은 500 ②시설 라이브 갱신 실패/0건 ③Tmap 경로 전면 실패(폴백 전환) ④Kakao 장소검색 실패(시드 폴백)를 발송. 같은 알림 key 는 스로틀 창 안에서 1회만. |
| `SLACK_ALERT_THROTTLE_MINUTES` | `10` | 같은 알림 key 의 재발송 억제 창(분) — 장애 중 알림 폭주 방지. |
| `BUEONGI_FACILITY_REFRESH_CRON` | `0 0 4 1 * *` | 시설 라이브 갱신 cron(Asia/Seoul). 위 두 키 중 하나라도 있을 때만 동작. |
| `BUEONGI_CORS_ORIGINS` | `http://localhost:3619` | `/api/**`·`/share/**`에 허용할 CORS origin(콤마 구분). 운영 도메인은 여기에 덧붙인다. 허용 메서드는 GET/POST/OPTIONS 고정. |
| `BUEONGI_REDIS_URL` | (빈 값) | 위치 공유 저장 Redis(예 `redis://localhost:6379`). **미설정이면 인메모리 폴백**(단일 인스턴스, 재시작 시 휘발). 운영 다중 인스턴스·내구성이 필요하면 주입. |
| `BUEONGI_SHARE_BASE_URL` | `http://localhost:8119` | 공유 URL(`share_url`)의 base. 운영은 보호자가 접근 가능한 공개 도메인으로 덮어쓴다. |

예:

```bash
KAKAO_REST_API_KEY=<your-key> \
BUEONGI_CORS_ORIGINS=http://localhost:3619,https://bueongi.example.com \
BUEONGI_REDIS_URL=redis://localhost:6379 \
BUEONGI_SHARE_BASE_URL=https://bueongi.example.com \
./gradlew bootRun
```

## 프로젝트 구조

```
src/main/kotlin/io/soo/bueongi/
├── BueongiApplication.kt        # Spring Boot 진입점
├── common/ApiExceptionHandler   # 표준 에러 응답(RFC 7807 ProblemDetail + code)
├── config/WebCorsConfig         # CORS 정책(/api/**·/share/** allowed-origins)
├── config/ClockConfig           # 주입형 Clock(위치 공유 TTL 결정론 테스트)
├── health/HealthController       # GET /api/health (운영 readiness + placeSource·locationStore)
├── places/                       # 장소 검색
│   ├── PlaceController            #   GET /api/places/search
│   ├── PlaceSearchService         #   소스 선택(시드 ↔ Kakao 폴백)
│   ├── PlaceProvider / SeedPlaceProvider / KakaoPlaceProvider / RemotePlaceProvider
│   └── PlaceCatalog               #   내장 시드 카탈로그
├── routes/                       # 경로·안심시설
│   ├── RouteController            #   POST /api/routes/compare, /api/routes/facilities
│   ├── RouteService / FacilityService
│   ├── RouteType / Geo            #   유형별 계수·haversine 거리
│   └── RouteDtos / FacilityDtos / PlaceDtos
└── share/                        # 위치 공유(보호자, stateful·TTL)
    ├── ShareController            #   POST /share/create, POST·GET /share/{token}/location, GET /share/{token}(HTML)
    ├── ShareService               #   토큰 발급·위치 갱신·조회 + 1~3h TTL
    ├── LocationStore              #   저장소 추상화 + ShareSession/StoredLocation 모델
    ├── RedisLocationStore / InMemoryLocationStore  #   Redis(share:{token}/location:{token} HASH+TTL) ↔ 인메모리 폴백
    └── ShareStoreConfig           #   redis-url 유무로 저장소 택1(상호배타 Condition)
        (resources/share/guardian.html  # 보호자 지도 HTML: Leaflet+OSM, 5초 폴링)
```

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
