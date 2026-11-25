# CDN 정적 데이터 계약

이 디렉터리는 안심시설 정적 산출물(JSON)을 담습니다. 이 문서는 파일 배치, 아이템 형태와 이 디렉터리를 쓰는 쪽을
다룹니다. 파일을 만드는 쪽은 [`pipeline/`](../pipeline/README.md)과 [`data/ingest/`](../data/ingest/README.md)의
스크립트입니다.

## 읽는 쪽

- 백엔드가 기동할 때 `BUEONGI_FACILITY_DATA_DIR`(기본 `cdn`) 아래 `{종류}/*.json`과 `safepath/*.json`을 읽어 메모리
  인덱스를 만듭니다(`SafetySpotRepository`). 디렉터리가 없으면 경고만 남기고 관리자 업로드분만 적재합니다.
- 같은 종류의 관리자 CSV 업로드분이 있으면 이 디렉터리의 파일 대신 업로드분을 씁니다.
- `manifest.json`이 있으면 `generatedAt`과 `version`을 데이터 신선도 표기에 씁니다. 없으면 표기를 생략합니다.
- 백엔드는 이 디렉터리를 `/cdn/**` 경로로 그대로 서빙합니다(`CdnResourceConfig`, 30분 캐시).
- 운영 이미지에는 이 디렉터리가 `/app/cdn`으로 들어갑니다. 바꾼 데이터는 이미지를 다시 발행해야 반영됩니다.

백엔드가 읽는 종류 폴더 이름은 `SafetySpotType.cdnType`입니다: `cctv`, `lamp`, `security_lamp`, `emergency_bell`,
`safehouse`, `police`, `pedestrian_signal`, `crime_prone_area`.

## 파일 목록

| 경로                              | 만드는 곳                        | 원천                 | scope  | 좌표 출처            |
|-----------------------------------|----------------------------------|----------------------|--------|----------------------|
| `cctv/{시군구코드}.json`          | 파이프라인                       | B-1                  | 시군구 | WGS84                |
| `lamp/seoul.json`                 | 파이프라인                       | C-1                  | 서울   | WGS84                |
| `lamp/smart.json`                 | 파이프라인                       | C-4                  | 전국   | WGS84                |
| `security_lamp/{시군구코드}.json` | 파이프라인                       | C-2                  | 시군구 | WGS84                |
| `pedestrian_signal/seoul.json`    | 파이프라인                       | C-3                  | 서울   | WGS84                |
| `safepath/all.json`               | 파이프라인                       | A-4                  | 전국   | 자치구별(verified만) |
| `manifest.json`                   | 파이프라인                       | 전체                 | 전국   | 해당 없음            |
| `emergency_bell/nationwide.json`  | `data/ingest/bell_ingest.py`     | 안전비상벨 CSV       | 전국   | WGS84                |
| `police/all.json`                 | `data/ingest/police_geocode.py`  | 지구대/파출소 CSV    | 전국   | Tmap 지오코딩        |
| `safehouse/all.json`              | `data/ingest/safehouse_fetch.py` | 여성안심지킴이집 API | 전국   | WGS84                |

지금 이 디렉터리에는 `lamp/seoul.json`과 `lamp/smart.json`만 있고 `manifest.json`은 없습니다.
`emergency_bell/nationwide.json`은 대용량이라 `.gitignore` 대상이며 필요할 때 다시 만듭니다.

샘플 원천으로 만든 산출물은 넣지 않습니다. 서버가 이 디렉터리의 파일을 실제 안심시설로 적재하기 때문입니다
(2026-10-11 삭제). 파이프라인을 키 없이 돌리면 `pipeline/data/raw/` 샘플로 파일이 생기므로 그 결과는 커밋하지
않습니다.

## 아이템 형태

파이프라인이 만드는 컬렉션 파일은 `pipeline/schema/collection.schema.json` 형태이고, `items[]`의 각 원소는
`pipeline/schema/cdn-item.schema.json`(표준 아이템)입니다. manifest는 `pipeline/schema/manifest.schema.json`을
따르며 파일마다 `count`, `bytes`, `sha256`을 담습니다.

- 좌표는 예외 없이 WGS84 `{lat, lng}`이며 한국 범위(lat 33~39, lng 124~132) 안에 있습니다.
- `properties.coordOrigin`은 그 좌표의 변환 경로(`wgs84`, `epsg5179`, `epsg5174`, `per-district`)를 기록합니다.
- `data/ingest/` 스크립트의 산출물은 이 스키마가 아니라 `{type, items}` 형태입니다.

## 감사 리포트

좌표 격리와 좌표 변환 실패 같은 감사 리포트는 앱 배포물이 아니라서 이 디렉터리에 두지 않습니다.
`pipeline/report/`를 봅니다.
