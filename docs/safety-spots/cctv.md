# CCTV — 데이터 저장

공통 구조(디스크 → 조각 → 색인)는 [DATA_STORAGE.md](../DATA_STORAGE.md) 에 있다. 이 문서는 CCTV 만 다룬다.

숫자는 2026-10-11 샘플 삭제 뒤 로컬 서버 기동 로그와 저장소 파일을 세어 확인한 값이다.

## 한 줄 요약

실데이터는 **전국 CCTV 원본 CSV 한 개를 관리자 업로드로 변환해 둔 것**이다(.gz 그대로 올림, 2026-10-11 일원화). 서버가 켜질 때 그 변환 결과(JSON)를 읽어 21만여 곳을 색인에 올린다. 외부 API 수집은 없다.

## 어디서 오나

```mermaid
flowchart LR
    CSV["backend/data/source/cctv/CCTV정보.csv.gz<br/>377,278행 (보관)"] -.->|"관리자 업로드에서<br/>방범 목적만 319,164행"| UP
    UP["facility-uploads/cctv.json"] --> FILE["파일 조각"]
    FILE -->|"좌표가 같은 곳은 하나로"| IDX["CCTV 색인 212,932곳"]
    IDX --> USE["안전 점수 CCTV 축 · 지도 마커 · 우회 경유지 후보"]
```

| 출처 | 위치 | 건수 | 실데이터인가 |
|---|---|---|---|
| 전국 CCTV 표준데이터 원본 | `backend/data/source/cctv/CCTV정보.csv.gz` → 업로드 `facility-uploads/cctv.json` | 377,278행 → 방범 목적 319,164행 | 예 |
| 관리자 업로드 | `backend/data/facility-uploads/cctv.json` | 지금 없음 | 올린 내용대로 |

CSV 에서는 설치 목적이 방범인 행만 남긴다.

```kotlin
// core-admin-safety-spot/domain/csv/cctv/CctvCsvContract.kt — 이 네 목적만 안심시설로 센다
val CCTV_SECURITY_PURPOSES: Set<String> = setOf("생활방범", "다목적", "어린이보호", "차량방범")
```

파이프라인이 샘플 원천으로 만든 CDN 파일은 2026-10-11 에 지웠다(실제 시설처럼 마커로 나가던 문제).

CCTV 전용 코드는 둘이다 — 시설 모양은 storage `safetyspot/cctv/CctvSpot.kt`, CSV 계약(머리글·방범 필터)은 core `domain/csv/cctv/CctvCsvContract.kt`.

## 언제 바뀌나

실행 중에 바뀌는 길은 관리자 업로드 하나다. 새 판이 나오면 받은 CSV(.gz 그대로 가능)를 업로드한다.

원본 CSV 를 새 판으로 바꾸는 절차도 업로드라서, 교체 시각과 건수가 관리자 화면에 남는다. 공공데이터포털의 CCTV 조회 API 는 실측에서 쓸 수 없었다(`docs/external-api-catalog.md:17`, `cctv_info` "불가").

## 어디에 쓰이나

경로 양옆 일정 거리 안의 CCTV 수가 안전 점수의 CCTV 축이 된다. 지도 마커와 "CCTV 가 많은 곳을 지나는 우회 경로" 후보에도 쓴다.

```kotlin
// SafetyScorer.kt:107
cctv = countWithinCorridor(SafetySpotType.CCTV, path),
```

## 이 방식이 맞는가

**맞는 점** — 전국 표준데이터는 몇 달에 한 번 바뀌고, 한 번에 통째로 새 판이 나온다. 그래서 "파일 하나를 읽어 메모리 색인"이 데이터 성격과 맞는다. 31만 행을 읽어도 서버 기동 전체가 7초 안팎이다.

**문제였던 것 — 원본을 바꾸는 절차가 코드 밖에 있었다.** 2026-10-11 업로드로 일원화해 해소됐다. 같은 날 파서도 하나가 되면서 따옴표 안 쉼표를 올바로 읽어 적재가 317,506 → 319,164행으로 늘었다(옛 파서는 쉼표 들어간 주소 뒤 칸을 밀려 읽었다).
