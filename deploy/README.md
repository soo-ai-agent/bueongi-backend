# bueongi 배포 가이드

이 디렉터리는 운영 스택(프론트 nginx, 백엔드, PostgreSQL + PostGIS, Redis, 선택 Caddy)을 docker compose로 띄우는 파일을 담습니다. 이 문서는
구조, GitHub Actions 이미지 발행, 서버 배포 절차와 배포 때 주의할 점을 다룹니다.

## 구조

```text
모바일 앱(Expo), 관리자/보호자 브라우저
        │ HTTPS
        ▼
[caddy: tls 프로필, 선택] ──▶ [frontend: nginx, 호스트 ${HTTP_PORT:-8080} -> 80]
                                ├─ /admin/*         admin.html     관리자 SPA
                                ├─ /share/{token}   guardian.html  보호자 지도 뷰어
                                ├─ /api/*           ─▶ [backend]   장소 검색, 경로, 헬스
                                ├─ /cdn/*           ─▶ [backend]   안심시설 정적 JSON
                                └─ /share/create, /share/{token}/(location|end|watching|route)
                                                    ─▶ [backend]   위치 공유 API
                                [backend] ──▶ [db]     안심시설(PostGIS, db-data 볼륨)
                                [backend] ──▶ [redis]  위치 공유 세션(1~3시간 상한)
```

- 이미지는 `ghcr.io/soo-ai-agent/bueongi`(관리자, 보호자 웹)와 `ghcr.io/soo-ai-agent/bueongi-backend`(백엔드)입니다.
- 사용자 화면은 모바일 앱(`mobile/`, Expo)이 담당합니다. 웹에는 관리자와 보호자 두 번들만 있고, 그 외 경로는
  `/admin`으로 보냅니다.
- compose는 백엔드 포트를 호스트에 열지 않습니다. 바깥 요청은 모두 프론트 nginx를 거쳐 백엔드로 갑니다.
- compose와 `Dockerfile`은 백엔드 주소를 8119로 잡습니다(`BACKEND_ORIGIN: http://backend:8119`, `EXPOSE 8119`,
  헬스체크 `http://localhost:8119/api/health`).
- nginx 프록시 규칙은 dev(vite) 프록시를 운영에 옮긴 것입니다. 단일 세그먼트 `/share/{token}`은 보호자 뷰어이고,
  `/share/create`와 `/share/{token}/...`는 백엔드 API입니다.
- 백엔드 이미지에는 `cdn/`(안심시설 데이터)이 `/app/cdn`으로 들어가고 `BUEONGI_FACILITY_DATA_DIR=/app/cdn`이
  설정됩니다. 백엔드는 켜질 때 이 파일을 DB로 옮깁니다(업로드가 있는 종류는 건너뜀).
- `db` 서비스는 `postgis/postgis:16-3.4`이고 데이터는 `db-data` 볼륨에 둡니다. 백엔드는 `db`의 헬스체크
  (`pg_isready`)가 통과한 뒤에 뜹니다. 테이블은 백엔드가 기동할 때 Flyway로 만듭니다.

## CI/CD 파이프라인

GitHub Actions 워크플로는 다음과 같습니다.

| 저장소            | 워크플로                   | 트리거                                       | 하는 일                                  |
|-------------------|----------------------------|----------------------------------------------|------------------------------------------|
| `bueongi`         | `e2e.yml`                  | push, PR                                     | vitest, Playwright E2E                   |
| `bueongi`         | `docker-publish.yml`       | main push, `v*.*.*` 태그                     | typecheck, vitest 후 GHCR 이미지 발행    |
| `bueongi-backend` | `backend-ci.yml`           | `main`, `feat/**`, `claude/**` push, main PR | `./gradlew test`(UTF-8 로케일)           |
| `bueongi-backend` | `backend-skill-review.yml` | `.kt`, `.kts` 변경 PR                        | ktlint, 파일 규칙, 스킬 규칙 리뷰 코멘트 |
| `bueongi-backend` | `docker-publish.yml`       | main push, `v*.*.*` 태그, 수동 실행          | `./gradlew test` 후 GHCR 이미지 발행     |
| `bueongi-backend` | `pipeline-monthly.yml`     | 매월 1일 18:00 UTC, 수동 실행                | `cdn/` 재생성 후 변경분 커밋             |

- `backend-ci.yml`은 `docs/`, `deploy/`, `pipeline/`, `cdn/`과 `.md`만 바뀐 push와 PR에서는 돌지 않습니다.
- 백엔드 이미지 태그는 `latest`(main), `1.2.3`(`v1.2.3` 태그), `sha-<커밋>`(모든 발행, 롤백용 고정 태그)입니다.
- `pipeline-monthly.yml`은 `GITHUB_TOKEN`으로 push합니다. GitHub는 `GITHUB_TOKEN`으로 만든 이벤트로 다른
  워크플로를 시작하지 않으므로, 월간 `cdn/` 커밋 뒤 새 데이터 이미지는 `docker-publish.yml`을 수동 실행해 발행합니다.

### 저장소 설정

`bueongi`(프론트) 저장소의 Settings, Secrets and variables, Actions에 다음 값을 둡니다. `VITE_*`는 번들에 인라인되는
클라이언트 공개 값입니다(카카오 JS 키는 도메인 제한으로 보호합니다). 비워 두면 해당 기능은 설계된 폴백으로
동작합니다(지도는 MapMock).

| 종류     | 이름                      | 값                                                        |
|----------|---------------------------|-----------------------------------------------------------|
| Secret   | `VITE_KAKAO_JS_KEY`       | Kakao JavaScript 키. 콘솔에 운영 도메인을 등록해야 합니다 |
| Variable | `VITE_SHARE_API_BASE_URL` | 운영 공개 origin. 예: `https://bueongi.example.com`       |

`bueongi-backend` 저장소는 추가 설정이 필요 없습니다. `GITHUB_TOKEN`으로 GHCR에 발행합니다. GHCR 패키지가
private이면 배포 서버에서 `read:packages` 권한 PAT로 `docker login ghcr.io`를 해야 합니다.

## 서버 배포

1. 이 디렉터리(`deploy/`)를 서버에 복사하고 `.env`를 만듭니다. `BUEONGI_DB_PASSWORD`, `PUBLIC_ORIGIN`, 키와
   `DOMAIN`을 채웁니다. `BUEONGI_DB_PASSWORD`가 비어 있으면 compose가 기동을 거부합니다.

   ```bash
   cp .env.example .env
   ```

2. GHCR 패키지가 private이면 로그인합니다.

   ```bash
   echo $GHCR_PAT | docker login ghcr.io -u <github-user> --password-stdin
   ```

3. 이미지를 받고 프로필 조합을 골라 기동합니다. 앞단 TLS 프록시 뒤에 둘 때는 첫 번째, Caddy 자동 HTTPS를 쓸 때는
   두 번째 명령을 씁니다(80/443 개방 필요).

   ```bash
   docker compose pull
   docker compose up -d
   docker compose --profile tls up -d
   ```

4. 프론트 nginx를 거쳐 백엔드 헬스를 확인합니다.

   ```bash
   curl -fs http://localhost:${HTTP_PORT:-8080}/api/health
   ```

새 이미지가 발행되면 `docker compose pull && docker compose up -d`로 업데이트합니다.

정상이면 헬스 응답은 다음과 같습니다.

```json
{"status":"UP","service":"bueongi-backend","locationStore":"redis"}
```

### 환경변수

`.env.example`을 `.env`로 복사해 채웁니다. `.env`는 커밋하지 않습니다.

| 환경변수                 | 기본값                                                         | 설명                                                                               |
|--------------------------|----------------------------------------------------------------|------------------------------------------------------------------------------------|
| `PUBLIC_ORIGIN`          | `http://localhost:8080`                                        | 공개 origin. 백엔드 `BUEONGI_SHARE_BASE_URL`과 `BUEONGI_CORS_ORIGINS`에 들어갑니다 |
| `HTTP_PORT`              | `8080`                                                         | 프론트 컨테이너의 호스트 포트                                                      |
| `TMAP_APP_KEY`           | (빈 값)                                                        | Tmap 경로와 장소 검색                                                              |
| `DATA_GO_KR_SERVICE_KEY` | (빈 값)                                                        | 공공데이터포털 보안등 지연 적재                                                    |
| `SAFEMAP_SERVICE_KEY`    | (빈 값)                                                        | 생활안전지도 치안시설 지연 적재                                                    |
| `SAFETYDATA_SERVICE_KEY` | (빈 값)                                                        | 재난안전데이터 가로등 지연 적재                                                    |
| `SLACK_WEBHOOK_URL`      | (빈 값)                                                        | 운영 알림. 비우면 알림을 보내지 않습니다                                           |
| `BUEONGI_DB_PASSWORD`    | (없음, 필수)                                                   | 안심시설 DB 비밀번호. `db` 컨테이너와 백엔드에 같은 값이 들어갑니다                |
| `BUEONGI_DB_USERNAME`    | `bueongi`                                                      | 안심시설 DB 사용자                                                                 |
| `BUEONGI_DB_URL`         | `jdbc:postgresql://db:5432/bueongi?reWriteBatchedInserts=true` | 안심시설 DB. 외부 DB를 쓸 때만 덮어씁니다                                          |
| `BUEONGI_REDIS_URL`      | `redis://redis:6379`                                           | 위치 공유 Redis. 외부 Redis를 쓸 때만 덮어씁니다                                   |
| `DOMAIN`                 | (빈 값)                                                        | `tls` 프로필의 인증서 도메인. 이 프로필을 쓰면 필수입니다                          |
| `BACKEND_TAG`            | `latest`                                                       | 백엔드 이미지 태그 고정                                                            |
| `FRONTEND_TAG`           | `latest`                                                       | 프론트 이미지 태그 고정                                                            |

- `.env.example`의 `KAKAO_REST_API_KEY`는 compose가 어느 서비스에도 넘기지 않습니다.
- compose는 `SAFE182_ESNTL_ID`, `SAFE182_AUTH_KEY`를 넘기지 않으므로 아동안전지킴이집 지연 적재는 꺼진 상태입니다.
- 각 변수의 백엔드 쪽 의미는 [`README.md`](../README.md)의 환경변수 절을 참고합니다.

### 반드시 알아야 할 것

1. **HTTPS 필수**: 위치(geolocation)와 클립보드는 브라우저 보안 컨텍스트(https)에서만 동작합니다. 공개 배포를
   http로 열면 현재 위치와 위치 공유가 모두 막힙니다. `tls` 프로필(Caddy 자동 인증서)이나 앞단 TLS 프록시를 씁니다.
2. **도메인 결합**: 프론트 이미지의 `VITE_SHARE_API_BASE_URL`(빌드 때 인라인), 백엔드의 `PUBLIC_ORIGIN`(런타임
   환경변수)과 Kakao 콘솔 등록 도메인이 같은 공개 origin이어야 합니다. 도메인이 바뀌면 Variable을 고친 뒤 프론트
   `docker-publish`를 다시 돌려 이미지를 재빌드합니다.
3. **키가 없어도 서버는 뜹니다**: 외부 키는 모두 선택입니다. 다만 `TMAP_APP_KEY`가 없으면 경로 비교는 503
   `ROUTE_SEARCH_UNAVAILABLE`, 장소 검색은 503 `PLACE_SEARCH_UNAVAILABLE`을 돌려줍니다. 지어낸 경로나 검색 결과는
   내보내지 않습니다. 공공데이터 키가 없으면 해당 시설은 이미지에 든 `cdn/` 데이터와 관리자 업로드분만 씁니다.
4. **DB 필수**: 안심시설 저장소(PostgreSQL + PostGIS)이며 compose가 함께 띄웁니다. 닿지 않으면 백엔드가 뜨지
   않습니다.
5. **Redis 필수**: 위치 공유 세션 저장소이며 compose가 함께 띄웁니다. 연결이 끊기면 위치 공유 API만 오류를 냅니다.
6. **데이터 영속**: 관리자 CSV 업로드분과 외부 API로 받은 지역 데이터는 DB(`db-data` 볼륨)에 있어 컨테이너를 다시
   만들어도 남습니다. `docker compose down -v`는 볼륨까지 지우므로 쓰지 않습니다. 관리자 CSV 보기 화면의 원본
   미리보기 파일(`./data/facility-uploads/originals`)만 백엔드 컨테이너 안에 있어 다시 만들면 사라집니다.
7. **API 요청 제한**: nginx가 `/api/*`, `/cdn/*`와 위치 공유 API에 IP당 10r/s(burst 20)를 걸고 초과분은 429로
   끊습니다(외부 API 쿼터 소진 공격 방어). 규칙은 프론트 이미지의 `deploy/nginx.default.conf.template`에 있습니다.

## 로컬에서 이미지 빌드

레지스트리 없이 이미지를 만들어 띄울 수 있습니다.

1. 백엔드 이미지를 만듭니다. 빌드 컨텍스트는 `cdn/`을 포함하는 저장소 루트입니다.

   ```bash
   cd bueongi-backend && docker build -t ghcr.io/soo-ai-agent/bueongi-backend:latest .
   ```

2. 프론트 이미지를 만듭니다.

   ```bash
   cd bueongi && docker build -t ghcr.io/soo-ai-agent/bueongi:latest \
     --build-arg VITE_SHARE_API_BASE_URL=http://localhost:8080 .
   ```

3. 로컬 이미지로 기동합니다. 태그가 `latest`라 pull 없이 로컬 이미지를 씁니다.

   ```bash
   cd bueongi-backend/deploy && docker compose up -d
   ```

`deploy/`에서 `docker compose build backend`를 실행해도 같은 백엔드 이미지를 만듭니다. compose의 `build.context`가
저장소 루트(`..`)를 가리킵니다.
