# CDN 정적 데이터 계약 (App ↔ Data Pipeline)

이 디렉터리는 파이프라인이 생성하는 **정적 산출물의 형태/계약**이다.
백엔드 서버를 거치지 않는다. 앱은 CDN base URL 에서 직접 받는다.

## 진입점

1. 앱은 항상 `manifest.json` 을 먼저 받는다. (`schema/manifest.schema.json`)
2. `manifest.version` 이 앱 캐시 버전과 다르거나, 사용자의 **자치구(시군구코드)가 바뀌면** 해당 파일만 다시 받는다.
3. 파일 선택은 결정적이다: `files[].sigunguCode` 로 현재 위치의 시군구 파일을 고른다. 없으면 인접/all 파일로 폴백.
4. 무결성은 `files[].sha256` 으로 검증한다.

## 파일 목록

| 경로 | 원천 | scope | 좌표 출처 |
|---|---|---|---|
| `cctv/{시군구코드}.json` | B-1 | 시군구 | WGS84 |
| `safehouse/all.json` | B-2 | 전국 | WGS84 |
| `police/all.json` | B-3+B-4 | 전국 | B-3 지오코딩 우선, B-4(EPSG:5179) 보완 |
| `lamp/seoul.json` | C-1 | 서울 | WGS84 |
| `lamp/{시군구코드}.json` | C-2 | 시군구 | WGS84 |
| `emergency_bell/{시군구코드}.json` | D-1+D-2 | 시군구 | D-1(EPSG:5174), D-2 자치구 보완 |
| `safepath/all.json` | A-4 | 전국 | 자치구별(verified 만) |

## 아이템 형태

모든 컬렉션 파일은 `schema/collection.schema.json` 형태이고,
`items[]` 의 각 원소는 `schema/cdn-item.schema.json`(표준 아이템)이다.
**좌표는 예외 없이 WGS84 `{lat,lng}`** 이며 한국 범위(lat 33–39, lng 124–132) 안에 있다.

`properties.coordOrigin` 으로 그 좌표가 어떤 변환 경로(`wgs84|geocode|epsg5179|epsg5174|per-district`)로
나왔는지 추적할 수 있다. 병합 아이템은 `properties.mergedSources` 에 기여 원천을 담는다.

## report/

- `report/quarantine.json` — A-4/D-2 좌표계 미확정으로 격리된 원천 (PM 보고 대상)
- `report/geocode-failures.json` — B-3 지오코딩 실패 행 (B-4 보완 여부 추적)
- `report/coord-failures.json` — 전체 좌표 변환 실패 행 (QA 검증용)

> 이 디렉터리의 `manifest.json` 과 `*/*.json` 은 파이프라인 산출 예시/초기값이다.
> 실제 값은 `pipeline/` 실행(`npm run run:pipeline`)으로 채워진다.
