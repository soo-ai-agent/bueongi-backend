# 안심시설 데이터 적재 스크립트

`backend/data/*.csv`(+ 공공 API)를 백엔드가 읽는 `cdn/<type>/*.json` 으로 변환한다.
백엔드는 startup 시 `cdn/<type>/*.json` 을 적재하고, 마커 클릭 정보카드에 주소·전화·용도를 표시한다.

| 스크립트 | 입력 | 출력 | 비고 |
|---|---|---|---|
| `bell_ingest.py` | `data/안전비상벨위치정보.csv` | `cdn/emergency_bell/nationwide.json` | 좌표·주소·전화·설치목적 포함(약 8.9만) |
| `police_geocode.py` | `data/경찰청_…지구대 파출소…csv` | `cdn/police/all.json` | 주소→Tmap 지오코딩(약 2천), `TMAP_APP_KEY` 필요 |
| `safehouse_fetch.py` | 여성안심지킴이집 표준데이터 API | `cdn/safehouse/all.json` | `SAFEHOUSE_API_KEY` 필요. 키 승인 직후 ~1시간+ code 30 가능 → 활성화 후 실행 |

## 실행 (레포 루트에서)

```sh
set -a && . backend/.env.local && set +a   # 키 로드(TMAP_APP_KEY / SAFEHOUSE_API_KEY)
python3 backend/data/ingest/bell_ingest.py
python3 backend/data/ingest/police_geocode.py
python3 backend/data/ingest/safehouse_fetch.py   # 키 활성화 후
```
생성 후 백엔드 재기동(`./gradlew bootRun`)하면 반영된다.

## 참고
- CCTV는 78MB라 cdn JSON 대신 백엔드가 `data/CCTV정보.csv` 를 직접 스트리밍 적재(`BUEONGI_FACILITY_CCTV_CSV`).
- 대용량 산출물(`cdn/emergency_bell/nationwide.json`)은 `.gitignore` 대상(재생성 가능).
