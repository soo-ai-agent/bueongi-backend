# CDN 정적 데이터 계약

이 디렉터리는 안심시설 정적 파일(JSON)을 담습니다. 이 문서는 파일 배치, 아이템 형태와 이 디렉터리를 쓰는 쪽을
다룹니다.

## 읽는 쪽

- 백엔드가 기동할 때 `BUEONGI_FACILITY_DATA_DIR`(기본 `cdn`) 아래 `{종류}/*.json`을 읽어 DB의 `cdn` 조각으로
  옮깁니다(`SafetySpotCdnSync`). 디렉터리가 없으면 경고만 남기고 넘어갑니다.
- 같은 종류의 관리자 CSV 업로드분이 있으면 이 디렉터리의 파일은 건너뜁니다.
- `safepath/*.json`(안심귀갓길)과 `manifest.json`(데이터 신선도 표기의 `generatedAt`, `version`)은 있으면 읽고
  없으면 생략합니다.
- 백엔드는 이 디렉터리를 `/cdn/**` 경로로 그대로 서빙합니다(`CdnResourceConfig`).
- 운영 이미지에는 이 디렉터리가 `/app/cdn`으로 들어갑니다. 바꾼 데이터는 이미지를 다시 발행해야 반영됩니다.

백엔드가 읽는 종류 폴더 이름은 `SafetySpotType.cdnType`입니다: `cctv`, `lamp`, `security_lamp`, `emergency_bell`,
`safehouse`, `police`, `pedestrian_signal`, `crime_prone_area`.

## 파일 목록

| 경로              | 원본 CSV                                                    |   건수 |
|-------------------|-------------------------------------------------------------|-------:|
| `lamp/seoul.json` | `data/source/street-lamp/서울_가로등_위치정보.csv.gz`       | 19,291 |
| `lamp/smart.json` | `data/source/street-lamp/전국스마트가로등표준데이터.csv.gz` | 12,714 |

- 지금 이 디렉터리에는 가로등 파일 두 개만 있습니다. 나머지 종류는 관리자 CSV 업로드와 외부 API로 DB에 바로
  들어옵니다. 종류별 경로는 [`docs/DATA_STORAGE.md`](../docs/DATA_STORAGE.md)를 봅니다.
- 이 파일을 만들던 변환 도구는 저장소에 없습니다(2026-11-27 삭제). 가로등 새 판은 포털에서 받은 CSV를 관리자
  화면에서 가로등으로 업로드해 반영합니다. 업로드분이 이 디렉터리의 파일을 대신합니다.
- 샘플 데이터는 넣지 않습니다. 서버가 이 디렉터리의 파일을 실제 안심시설로 적재하기 때문입니다.

## 아이템 형태

파일 하나는 `{type, items}` 형태이고 서버는 `items[]`의 다음 칸만 읽습니다(`StoredSafetySpotItem`). 나머지 칸은
무시합니다.

| 칸            | 내용                                       |
|---------------|--------------------------------------------|
| `lat`, `lng`  | WGS84 좌표. 하나라도 비면 그 행을 버립니다 |
| `name`        | 이름                                       |
| `address`     | 주소                                       |
| `phone`       | 전화번호                                   |
| `sigunguCode` | 지역 코드                                  |
