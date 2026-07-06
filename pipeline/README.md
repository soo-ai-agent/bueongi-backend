# 안심귀가(부엉이) Data Pipeline (작업 2)

공공데이터 → **WGS84 단일 좌표계 CDN JSON** 파이프라인. 백엔드 서버로 정적 데이터를
보내지 않고 `../cdn/` 정적 파일 + `manifest.json` 으로만 산출한다.

기준 문서: `../docs/mvp-orchestrator-design.md` (작업 2)

## 흐름

```
원천(A-4,B-1~D-2) → 정규화 → 좌표 변환/지오코딩 → 중복 제거/병합 → 시군구 분할 → cdn/ + manifest + report
```

## 좌표 변환

| 원천 | 입력 좌표 | 처리 | 구현 |
|---|---|---|---|
| B-1,B-2,C-1,C-2 | WGS84 | 범위 검증 | `coord/transform.ts` `validateWgs84` |
| B-3 | 주소 | E-2 카카오 지오코딩 | `coord/geocode.ts` |
| B-4 | EPSG:5179 (UTM-K) | proj4 → WGS84 | `coord/transform.ts` `utmkToWgs84` |
| D-1 | EPSG:5174 (Bessel 중부원점) | proj4 → WGS84 (towgs84 보정) | `coord/transform.ts` `besselTmToWgs84` |
| A-4,D-2 | 자치구별 상이 | `config/district-coord-map.yaml` 규칙 | `perDistrict.ts` |

## 중복 제거

- **B-3+B-4 파출소** (`dedup/police.ts`): 명칭/전화/주소 정규화 + 50m 클러스터, B-3 좌표 우선·B-4 보완.
- **D-1+D-2 비상벨** (`dedup/emergencyBell.ts`): 명칭/관리기관/주소 + 20m 클러스터, D-1 기본·D-2 는 누락 자치구/좌표 결측 보완만.

## A-4/D-2 격리 규칙

`config/district-coord-map.yaml` 에서 자치구별 `status`(`verified|quarantined`)와 `coordSystem` 을 둔다.
`quarantined` 또는 `coordSystem: unknown` 은 변환하지 않고 `cdn/report/quarantine.json` 으로만 보고한다(PM 보고).

## 입력 소스 (fetcher 우선순위 — "CSV 기본, API 폴백")

`sources/fetchers.ts` 는 다음 순서로 원천 `RawRecord[]` 를 확보한다.

1. `ctx.fixtures[id]` — 테스트 인메모리 주입
2. **`data/csv/{id}.csv`** — 공공데이터포털에서 내려받은 표준데이터 CSV(운영자 투입). CP949/UTF-8(BOM)
   자동 판별 + `sources.yaml` `keyColumns` 매핑(`sources/csv.ts`). 파일명 대응은 `data/csv/README.md`.
3. **`data/raw/{id}.json`** — 오프라인 raw 데이터(운영 수집 산출물 또는 샘플). 키/네트워크 없이 전 구간 결정적 실행.
4. 라이브 공공데이터 API — `DATA_GO_KR_SERVICE_KEY`/`SEOUL_OPEN_API_KEY` 가 있을 때만
   (`sources/liveApi.ts`: odcloud·data.go.kr 조회서비스·서울 열린데이터, 전체 페이지 순회).
   B-1/D-1 은 엔드포인트 확정, 나머지는 키 등록 후 uddi/service 값을 채우면 동작. 없으면 `[]` 로 안전 통과.

B-3 지오코딩은 `data/geocode-cache.json`(주소→`{lat,lng}`)을 먼저 보고, 캐시 미스만 `KAKAO_REST_API_KEY` 로 네트워크 호출한다(월1회 운영 시 쿼터 절약 + 오프라인 재현).

> 현재 `cdn/` 산출물은 `data/raw/`의 **대표 샘플 데이터**로 생성된 것이다(`scripts/gen-sample-raw.mjs` 로 재현). 좌표 변환·dedup·per-district·manifest 전 구간을 검증하기 위한 입력이며, 운영 데이터는 라이브 키 연동 또는 실수집 raw 파일 교체로 대체한다.

## 실행

```bash
npm install
cp .env.example .env             # KAKAO_REST_API_KEY 등 (선택; 오프라인이면 불필요)
node scripts/gen-sample-raw.mjs  # data/raw/* 샘플 생성(재현용)
npm run run:pipeline             # cdn/ 산출
npm run validate                 # JSON Schema 검증
npm test                         # 좌표 변환/중복 제거/로더/캐시 단위 테스트
```

결정적 산출(해시 고정)을 위해 `PIPELINE_NOW`/`PIPELINE_VERSION` 환경변수로 타임스탬프를 고정할 수 있다.

## 갱신 주기 / 자동화

- B/C/D: 월 1회
- B-3/B-4: 분기 1회
- A-4/D-2: 자치구 수집 파일 갱신 시 수동

**월 1회 자동 갱신**은 `../.github/workflows/pipeline-monthly.yml` 이 담당한다.
매월 1일 18:00 UTC(KST 2일 03:00) 또는 수동(`workflow_dispatch`) 트리거 시
`npm ci → npm test → npm run run:pipeline → npm run validate` 를 거쳐 `cdn/` 변경분이
있으면 자동 커밋한다. 라이브 수집은 저장소 secrets(`KAKAO_REST_API_KEY`,
`DATA_GO_KR_SERVICE_KEY`, `SEOUL_OPEN_API_KEY`)가 있을 때만 동작하고, 없으면
`data/raw/` 기반으로 안전 통과한다. 로컬 1회 갱신은 `npm run refresh`.
