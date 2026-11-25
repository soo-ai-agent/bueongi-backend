# 안심귀가(부엉이) 데이터 파이프라인

공공데이터를 WGS84 단일 좌표계의 정적 JSON으로 바꿔 `../cdn/`에 쓰는 TypeScript 파이프라인입니다. 이 문서는 처리
흐름, 입력 우선순위, 실행 방법과 월간 자동 갱신을 다룹니다. 산출물 계약은 [`cdn/README.md`](../cdn/README.md)를
봅니다.

기준 문서는 bueongi-service 저장소의 `docs/mvp-orchestrator-design.md`(작업 2)입니다.

## 흐름

```text
원천(A-4, B-1, C-1~C-4) -> 정규화 -> 좌표 검증/변환 -> 시군구 분할 -> cdn/ + manifest.json + report/
```

원천 정의는 `sources.yaml`, 오케스트레이션은 `src/pipeline.ts`에 있습니다.

| 원천 | 이름                    | 산출 경로                         | 갱신 주기 |
|------|-------------------------|-----------------------------------|-----------|
| A-4  | 자치구 안심귀갓길       | `safepath/all.json`               | manual    |
| B-1  | 전국 CCTV 표준데이터    | `cctv/{시군구코드}.json`          | monthly   |
| C-1  | 서울 가로등 위치정보    | `lamp/seoul.json`                 | monthly   |
| C-2  | 전국 보안등 표준데이터  | `security_lamp/{시군구코드}.json` | monthly   |
| C-3  | 서울 보행등(보행신호등) | `pedestrian_signal/seoul.json`    | monthly   |
| C-4  | 전국 스마트가로등       | `lamp/smart.json`                 | monthly   |

- B-1 CCTV는 방범 성격의 설치목적구분(생활방범, 다목적, 어린이보호, 차량방범)만 남깁니다. 목적 값이 전부 비어
  있으면 필터 없이 통과시키고 경고를 남깁니다.
- C-3은 이름과 달리 조명이 아니라 보행신호등이라 조명 밀도 점수에 섞이지 않게 `lamp`와 분리합니다.
- 비상벨, 경찰 시설과 안심지킴이집은 이 파이프라인이 만들지 않습니다. [`data/ingest/`](../data/ingest/README.md)의
  스크립트가 만듭니다.

## 좌표 변환

| 원천                    | 입력 좌표     | 처리                                  | 구현                                 |
|-------------------------|---------------|---------------------------------------|--------------------------------------|
| B-1, C-1, C-2, C-3, C-4 | WGS84         | 한국 범위 검증                        | `coord/transform.ts` `validateWgs84` |
| A-4                     | 자치구별 상이 | `config/district-coord-map.yaml` 규칙 | `perDistrict.ts`                     |

A-4의 자치구 규칙이 EPSG:5179(UTM-K)면 `utmkToWgs84`, EPSG:5174(Bessel 중부원점, towgs84 보정)면
`besselTmToWgs84`로 바꿉니다. 한국 범위(lat 33~39, lng 124~132) 밖의 좌표는 버립니다.

## A-4 격리 규칙

`config/district-coord-map.yaml`은 자치구별 `status`(`verified`, `quarantined`)와 `coordSystem`을 정합니다.
`quarantined`이거나 `coordSystem: unknown`인 자치구, 규칙이 없는 자치구는 변환하지 않고
`report/quarantine.json`에만 기록합니다. 좌표 변환에 실패한 행은 `report/coord-failures.json`에 남습니다.

## 입력 우선순위

`src/sources/fetchers.ts`는 원천마다 다음 순서로 `RawRecord[]`를 얻습니다. 앞 단계에서 데이터를 얻으면 뒤 단계는
보지 않습니다.

1. `ctx.fixtures[id]`: 테스트용 인메모리 주입입니다.
2. `data/csv/{id}.csv`: 운영자가 내려받은 공공데이터 CSV입니다. CP949와 UTF-8(BOM)을 자동 판별하고, `fetchers.ts`의
   `CSV_SPEC` 열 매핑으로 읽습니다. 파일 대응은 [`data/csv/README.md`](data/csv/README.md)를 봅니다.
3. `data/raw/{id}.json`: 오프라인 raw 데이터입니다. 지금은 대표 샘플(A-4, B-1, C-1, C-2, C-3)이 들어 있습니다.
4. 라이브 API: `fetchers.ts`의 `LIVE_SPEC`에 있는 원천만, `DATA_GO_KR_SERVICE_KEY`가 있을 때 부릅니다. 키가 없거나
   스펙이 비어 있으면 빈 배열로 통과합니다.

라이브 API 상태는 다음과 같습니다.

| 원천 | 방식                                      | 상태                                                      |
|------|-------------------------------------------|-----------------------------------------------------------|
| B-1  | data.go.kr `1741000/cctv_info`            | 엔드포인트 확정                                           |
| C-2  | data.go.kr `tn_pubr_public_scrty_lmp_api` | 엔드포인트 확정                                           |
| C-1  | odcloud(포털 미러 15107934)               | `uddi` 미확정이라 빈 배열. `data/csv/C-1.csv`가 운영 경로 |
| C-3  | odcloud(포털 미러 15124273)               | `uddi` 미확정이라 빈 배열. `data/csv/C-3.csv`가 운영 경로 |
| C-4  | 없음                                      | CSV 전용                                                  |
| A-4  | 없음                                      | 자치구별 수집 파일(manual)                                |

- `SEOUL_OPEN_API_KEY`는 읽지만 지금 서울 열린데이터광장 방식을 쓰는 원천이 없습니다.
- 지오코딩 단계는 없습니다. `KAKAO_REST_API_KEY`와 `data/geocode-cache.json`은 파이프라인 코드가 읽지 않습니다.

`data/raw/`는 좌표 변환, per-district와 manifest 경로를 검증하는 테스트 입력입니다. 키와 CSV 없이 파이프라인을
돌리면 이 샘플로 `cdn/cctv/` 등에 파일이 생기는데, 서버가 `cdn/`을 그대로 안심시설로 적재하므로 그 결과는 커밋하지
않습니다(2026-10-11 샘플 산출물 삭제). 운영 데이터는 CSV 투입이나 라이브 키로 만듭니다.

## 실행

```bash
npm install
cp .env.example .env               # 키는 선택. 오프라인이면 필요 없습니다
set -a && . ./.env && set +a       # 파이프라인은 .env를 스스로 읽지 않습니다
npm run run:pipeline               # ../cdn/ 산출
npm run validate                   # JSON Schema 검증
npm test                           # 단위 테스트
```

| 스크립트               | 하는 일                                                                  |
|------------------------|--------------------------------------------------------------------------|
| `npm run run:pipeline` | `src/pipeline.ts` 실행, `../cdn/`과 `report/` 산출                       |
| `npm run validate`     | `../cdn/`의 manifest와 컬렉션을 스키마로 검증                            |
| `npm run refresh`      | `run:pipeline` 후 `validate`                                             |
| `npm test`             | vitest(좌표 변환, A-4 격리, CSV 로더, fetcher 우선순위, 라이브 API 매핑) |
| `npm run typecheck`    | `tsc --noEmit`                                                           |
| `npm run build`        | `tsc` 빌드                                                               |

- `node scripts/gen-sample-raw.mjs`는 `data/raw/` 샘플과 `data/geocode-cache.json`을 다시 만듭니다. 지금 파이프라인이
  읽지 않는 B-2, B-3, B-4, D-1, D-2 파일도 함께 만듭니다.
- 산출 해시를 고정하려면 `PIPELINE_NOW`, `PIPELINE_VERSION`으로 타임스탬프와 버전을 고정합니다. `CDN_BASE_URL`은
  manifest의 `baseUrl`에 들어갑니다.

## 월간 자동 갱신

`../.github/workflows/pipeline-monthly.yml`이 매월 1일 18:00 UTC(KST 2일 03:00)와 수동 실행(`workflow_dispatch`)
때 `npm ci`, `npm test`, `npm run run:pipeline`, `npm run validate`를 차례로 돌리고, `cdn/`에 변경이 있으면 커밋해
push합니다.

- 저장소 secrets `KAKAO_REST_API_KEY`, `DATA_GO_KR_SERVICE_KEY`, `SEOUL_OPEN_API_KEY`와 variable `CDN_BASE_URL`을
  환경변수로 넘깁니다. 이 중 실제로 라이브 수집에 쓰이는 것은 `DATA_GO_KR_SERVICE_KEY`입니다.
- 로컬에서 한 번 갱신하려면 `npm run refresh`를 씁니다.
