# 범죄주의구간 — 데이터 저장

공통 구조(디스크 → 조각 → 색인)는 [DATA_STORAGE.md](../DATA_STORAGE.md) 에 있다. 이 문서는 범죄주의구간 만 다룬다.

숫자는 2026-10-10 로컬 서버 기동 로그와 저장소 파일을 세어 확인한 값이다.

## 한 줄 요약

**데이터가 0건이다.** 관리자 업로드로만 들어올 수 있고, 아직 올린 적이 없다. 안심시설이 아니라 위험 신호라서 점수 감점에만 쓴다.

## 어디서 오나

```mermaid
flowchart LR
    UP["관리자 CSV 업로드<br/>(올린 적 없음)"] -.-> FILE["파일 조각"]
    FILE --> IDX["범죄주의구간 색인 0곳"]
    IDX --> USE["안전 점수 감점만<br/>(지도 마커로는 안 내보냄)"]
```

CDN 파일도, 원본 CSV 도, 외부 API 도 없다. 서버 기동 로그의 종류별 건수에도 이 종류는 나오지 않는다.

```kotlin
// SafetySpotType.kt — 안심시설이 아니라 위험 신호다
// 안심시설이 아니라 위험 신호 — 점수 차감에만 쓰고 지도 마커로는 내보내지 않는다.
CRIME_PRONE_AREA("crime_prone_area", "범죄주의구간"),
```

## 어디에 쓰이나

경로 주변에 있으면 안전 점수에서 최대 20%를 깎는다.

```kotlin
// SafetyScorer.kt:122-124 — 경로 주변 범죄주의구간 수
private fun countRiskNear(path: List<GeoPoint>): Int {
    return safetySpots.index(SafetySpotType.CRIME_PRONE_AREA).withinCorridor(path, RISK_CORRIDOR_METERS).size
}

// SafetyScorer.kt:244
const val MAX_RISK_PENALTY: Double = 0.20
```

## 이 방식이 맞는가

**저장 방식 자체는 문제없다.** 업로드 파일 하나를 메모리에 올리는 다른 종류와 같다.

**문제 — 데이터가 없어 감점이 한 번도 작동하지 않는다.** 점수 계산 코드는 있지만 결과에 영향을 주지 않는다.

**권장** — 원본 데이터(지자체 범죄주의구간 공개 자료 등)를 정해 업로드한다(근본 해결). 당분간 넣을 계획이 없다면 그 사실을 관리자 화면이나 문서에 밝혀, 점수에 반영되고 있다고 오해하지 않게 한다(임시 — 데이터가 생기기 전까지만 유효).
