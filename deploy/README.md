# bueongi 배포 가이드 — Docker + GitHub CI/CD

## 구조 한눈에

```
사용자 ── 모바일 앱(Expo) ──HTTPS──▶ [backend:8119]  경로·장소검색·위치공유 API

관리자·보호자 ──HTTPS──▶ [caddy(선택, 자동 TLS)] ──▶ [frontend: nginx]
                                               ├─ /admin/*     admin.html   관리자 SPA
                                               ├─ /share/{token} guardian.html 보호자 지도 뷰어
                                               ├─ /api/*       ─▶ [backend:8119] 장소검색·경로·헬스
                                               ├─ /cdn/*       ─▶ backend       안심시설 정적 JSON
                                               └─ /share/create, /share/{t}/(location|end|watching|route)
                                                               ─▶ backend       위치 공유 API
                                  [backend] ──(redis)── 위치공유 세션(1~3h 상한)
```

- **이미지**: `ghcr.io/soo-ai-agent/bueongi`(관리자·보호자 웹), `ghcr.io/soo-ai-agent/bueongi-backend`(백엔드)
- 사용자 화면은 모바일 앱(`mobile/`, Expo)이 담당한다. 웹에는 관리자·보호자 두 번들만 있고, 그 외 경로는 `/admin` 으로 보낸다.
- nginx 프록시 규칙은 dev(vite) 프록시의 운영 미러다 — `/share/{token}` 단일 세그먼트는 보호자 뷰어,
  `/share/create`·`/share/{t}/...` 는 백엔드 API.
- 백엔드 이미지에 `cdn/`(안심시설 데이터)이 구워진다. **월 1회 파이프라인**(pipeline-monthly)이
  `cdn/` 을 main 에 커밋하면 → `docker-publish` 가 자동으로 최신 데이터 이미지를 재발행한다.

## CI/CD 파이프라인 (GitHub Actions)

| 리포 | 워크플로우 | 트리거 | 하는 일 |
|---|---|---|---|
| bueongi | `e2e.yml`(기존) | push/PR | vitest + Playwright E2E |
| bueongi | `docker-publish.yml` | main push · `v*.*.*` 태그 | typecheck+vitest → GHCR 이미지 발행 |
| bueongi-backend | `backend-ci.yml` | 백엔드 소스 push/PR | gradle test (UTF-8 로케일) |
| bueongi-backend | `docker-publish.yml` | main push · `v*.*.*` 태그 | gradle test → GHCR 이미지 발행 |
| bueongi-backend | `pipeline-monthly.yml`(기존) | 매월 1일 | cdn/ 재생성 커밋 → 이미지 자동 재발행 |

이미지 태그: `latest`(main), `v1.2.3`(태그), `sha-<커밋>`(모든 발행 — 롤백용 고정 태그).

### 리포 설정(1회)

**bueongi (프론트) — Settings → Secrets and variables → Actions**

VITE_* 는 번들에 인라인되는 클라이언트 공개 값이다(카카오 JS 키는 도메인 제한으로 보호).
비워 두면 해당 기능은 설계된 폴백으로 동작한다(지도=MapMock).

| 종류 | 이름 | 값 |
|---|---|---|
| Secret | `VITE_KAKAO_JS_KEY` | Kakao JavaScript 키 (콘솔에 운영 도메인 등록 필수) |
| Variable | `VITE_SHARE_API_BASE_URL` | **운영 공개 origin** 예: `https://bueongi.example.com` |

**bueongi-backend (백엔드)** — 추가 설정 불요(GITHUB_TOKEN 으로 GHCR 발행).
GHCR 패키지가 private 이면 배포 서버에서 `docker login ghcr.io` 필요(read:packages PAT).

## 서버 배포 (docker compose)

```bash
# 1) 이 디렉터리(deploy/)를 서버에 복사 후
cp .env.example .env   # PUBLIC_ORIGIN·키·DOMAIN 채우기

# 2) (private 패키지인 경우) GHCR 로그인
echo $GHCR_PAT | docker login ghcr.io -u <github-user> --password-stdin

# 3) 기동 — 프로필 조합 선택
docker compose pull
docker compose up -d                                   # HTTP(앞단 TLS 프록시 뒤에 둘 때)
docker compose --profile tls up -d                     # + Caddy 자동 HTTPS(80/443 개방 필요)

# 4) 확인
curl -fs http://localhost:${HTTP_PORT:-8080}/api/health   # {"status":"UP",...}

# 업데이트(새 이미지 발행 후)
docker compose pull && docker compose up -d
```

### 반드시 알아야 할 것

1. **HTTPS 필수** — 위치(geolocation)·클립보드는 브라우저 보안 컨텍스트(https) 에서만 동작한다.
   공개 배포에서 http 로 열면 앱의 핵심 기능(현재 위치, 위치 공유)이 전부 차단된다.
   `tls` 프로필(Caddy 자동 인증서) 또는 앞단 TLS 프록시를 쓰라.
2. **도메인 결합** — 프론트 이미지의 `VITE_SHARE_API_BASE_URL`(빌드 시 인라인)과 백엔드의
   `PUBLIC_ORIGIN`(런타임 env), Kakao 콘솔 등록 도메인이 **같은 공개 origin** 이어야 한다.
   도메인이 바뀌면 프론트 이미지는 재빌드(Variable 수정 후 docker-publish 재실행)해야 한다.
3. **키 없이도 뜬다** — 모든 외부 키는 선택이다. 없으면 결정론적 폴백(목업 경로·시드 검색·MapMock)
   으로 동작하므로, 스테이징은 키 없이 먼저 올려 구조를 검증할 수 있다.
4. **Redis 는 필수** — 위치공유 세션 저장소다. compose 가 함께 띄운다. 연결이 끊기면 위치공유 API 만 오류를 낸다.
5. **API 요청 제한** — nginx 가 `/api/*`·`/cdn/*`·위치공유 API 에 IP 당 10r/s(burst 20)를 걸고
   초과분은 429 로 끊는다(외부 API 쿼터 소진 공격 방어). 규칙은 프론트 이미지의 `deploy/nginx.default.conf.template`.

## 로컬에서 이미지 빌드(레지스트리 없이)

```bash
# 백엔드 (컨텍스트 = 저장소 루트: cdn/ 포함)
cd bueongi-backend && docker build -t ghcr.io/soo-ai-agent/bueongi-backend:latest .

# 프론트
cd bueongi && docker build -t ghcr.io/soo-ai-agent/bueongi:latest \
  --build-arg VITE_SHARE_API_BASE_URL=http://localhost:8080 .

# 로컬 이미지로 즉시 기동(태그가 latest 라 pull 없이 사용됨)
cd bueongi-backend/deploy && docker compose up -d
```
