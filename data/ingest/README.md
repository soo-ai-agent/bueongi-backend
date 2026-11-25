# 안심시설 데이터 적재 스크립트

이 디렉터리의 파이썬 스크립트는 공공데이터 CSV와 공공 API를 백엔드가 읽는 `cdn/{종류}/*.json`으로 변환합니다.
백엔드는 기동할 때 `cdn/{종류}/*.json`을 적재하고, 마커 정보 카드에 주소, 전화와 용도를 표시합니다.

| 스크립트             | 입력                                                     | 출력                                 | 필요한 키           |
|----------------------|----------------------------------------------------------|--------------------------------------|---------------------|
| `bell_ingest.py`     | `backend/data/안전비상벨위치정보.csv`                    | `cdn/emergency_bell/nationwide.json` | 없음                |
| `police_geocode.py`  | `backend/data/경찰청_전국 지구대 파출소 주소 현황_*.csv` | `cdn/police/all.json`                | `TMAP_APP_KEY`      |
| `safehouse_fetch.py` | 여성안심지킴이집 표준데이터 API                          | `cdn/safehouse/all.json`             | `SAFEHOUSE_API_KEY` |

- `bell_ingest.py`는 좌표, 주소, 전화와 설치 목적을 담습니다(약 8.9만 건).
- `police_geocode.py`는 좌표가 없는 주소를 Tmap 지오코딩으로 좌표로 바꿉니다(약 2천 건).
- `safehouse_fetch.py`는 키 승인 직후 1시간 넘게 code 30(`SERVICE KEY IS NOT REGISTERED`)이 날 수 있으므로 키가
  활성화된 뒤 실행합니다.

## 경로 기준

스크립트는 이 저장소의 상위 디렉터리를 루트로 잡습니다(`data/ingest/`에서 세 단계 위). 즉 이 저장소가 `backend/`로
들어 있는 bueongi-service 배치를 전제로 하며, 입력은 `<루트>/backend/data/`, 출력은 `<루트>/cdn/`입니다.

- 입력 CSV는 압축을 푼 `.csv`여야 합니다. 이 저장소의 원천 CSV는 `data/source/`에 `.csv.gz`로만 있으므로, 필요한
  파일을 풀어 `backend/data/`에 둡니다([`data/source/README.md`](../source/README.md) 참고).
- 출력이 이 저장소의 `cdn/`이 아니라 루트의 `cdn/`에 생기므로, 백엔드가 읽게 하려면 이 저장소의 `cdn/`으로
  옮기거나 `BUEONGI_FACILITY_DATA_DIR`로 그 디렉터리를 가리킵니다.

## 실행

bueongi-service 루트에서 실행합니다.

```bash
set -a && . backend/.env.local && set +a   # TMAP_APP_KEY, SAFEHOUSE_API_KEY 로드
python3 backend/data/ingest/bell_ingest.py
python3 backend/data/ingest/police_geocode.py
python3 backend/data/ingest/safehouse_fetch.py   # 키 활성화 후
```

만든 뒤 백엔드를 다시 기동하면 반영됩니다.

## 참고

- CCTV는 이 스크립트로 만들지 않습니다. 관리자 CSV 업로드로 적재합니다. 절차는
  [`docs/DATA_STORAGE.md`](../../docs/DATA_STORAGE.md)를 참고합니다.
- 대용량 산출물 `cdn/emergency_bell/nationwide.json`은 `.gitignore` 대상입니다(다시 만들 수 있습니다).
