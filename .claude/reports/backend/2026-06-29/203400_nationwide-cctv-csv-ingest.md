# 전국 CCTV CSV(37만건) 백엔드 공간 인덱스 적재 (산출물: 구현)

> 엔진: Claude(opus, backend) / 2026-06-29 (KST)
> data.go.kr API 키 지연 대응 — 사용자가 직접 넣은 전국 CCTV 실데이터 CSV를 키 없이 전국 안심 라우팅에 투입.

## 요청
- `backend/CCTV정보.csv`(CP949, 78MB, 374,246행)를 `FacilityRepository`의 CCTV `GridSpatialIndex`에 적재.
- 기존 JSON CCTV 포인트와 **합쳐** 단일 인덱스 구성. 키 없이 전국 safe-compare가 실 CCTV로 동작하게.
- 순수 파싱 로직 분리해 단위 테스트, CSV는 `.gitignore`, 기존 계약/동작 불변.

## 진행
- CSV 사양 검증: 헤더 18컬럼, lat=`WGS84위도`(idx12)/lng=`WGS84경도`(idx13). 약 8.5% 행은 주소 콤마로 컬럼 밀림(따옴표가 있어도 단순 split 시 깨짐) → **뒤 6컬럼(위도,경도,기준일자,갱신구분,갱신시점,수정시점)은 안정** 확인.
- 파싱 규칙 구현: `line.split(',')` 후 `lat=parts[N-6]`, `lng=parts[N-5]`, `name=parts[2]`(관리기관명). lat/lng Double+유효범위(±90/±180, isFinite) 검증, 실패 행 조용히 skip. 헤더는 `WGS84위도`가 Double 파싱 실패라 자연 skip.
- 인코딩: `Charset.forName("MS949")`(CP949 superset)로 `useLines` 스트리밍 → 37만행 메모리 효율 유지.
- 빈 설정값이면 CSV 적재 비활성(코드베이스의 옵션 비활성화 관례 = kakao/tmap 빈키와 동형). 테스트 격리에 활용.

## 산출물
- `src/main/kotlin/io/soo/bueongi/facility/FacilityRepository.kt`
  - 생성자에 `@Value("\${bueongi.facility.cctv-csv:CCTV정보.csv}")` 추가(끝자리, 기본값 보유 — 기존 위치인자 호출 보존).
  - `load()`에 CSV CCTV를 JSON CCTV 버킷과 merge 후 인덱스 빌드. 로그에 `cctv=총합(csv=건수)` 추가.
  - `loadCctvCsv()`(스트리밍·예외시 빈목록 skip), `resolveCctvCsv()`(설정→폴백 후보, 빈값이면 비활성).
  - top-level `internal fun parseCctvCsvLine(line): FacilityPoint?` — 분리·테스트 가능.
- `src/main/resources/application.yml` — `bueongi.facility.cctv-csv: ${BUEONGI_FACILITY_CCTV_CSV:CCTV정보.csv}`.
- `.gitignore` — `*.csv` 추가(78MB 커밋 금지).
- `src/test/.../facility/CctvCsvParseTest.kt`(신규 5) — 정상18컬럼/콤마밀림19컬럼/헤더/좌표불량/짧은행.
- `src/test/.../facility/FacilityRepositoryTest.kt`(신규 2 + 기존 5 호출 조정) — CP949 CSV merge 적재, 빈설정 비활성.

## 검증
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL(2m44s). XML 집계 **tests=199**(기존 192 + 신규 7), failures/errors=0 — 회귀 0.
- **실기동(`./gradlew bootRun`)** startup 로그 확인:
  `안심시설 적재 완료: ... cctv=375223(csv=375220), lamp=4, bell=3, safehouse=2, police=3, safepaths=2`
  → CSV 375,220건 적재(약 37.5만, 좌표불량/밀림 일부 skip 반영), JSON CCTV 3건과 merge되어 총 375,223건. **목표 약 37만 충족.**
- 적재(PostConstruct)는 web server bind 이전에 완료됨. 직후 "Port 8119 already in use"는 **이미 다른 backend 인스턴스가 8119에 떠 있어** 발생한 것으로 본 변경과 무관(적재 검증에는 영향 없음).
- 기존 `/api/routes/*` 계약·동작 불변. 결정론 유지(같은 CSV→같은 인덱스). 시크릿/대용량 파일 미커밋.

## 잔여·인계
- frontend(bueongi-dev): CCTV 마커 표시 검증은 8119의 실데이터로 가능(전국 좌표 어디서나). API 계약 변동 없음 — 별도 대응 불요.
- 운영: 환경변수 `BUEONGI_FACILITY_CCTV_CSV`로 경로 오버라이드 가능, 빈값이면 CSV 비활성. 파일 부재 시 경고 로그 후 skip(죽지 않음).
- `.claude/reports`의 두 `@SpringBootTest`(ContextLoad/WebCors)는 기본 cctv-csv가 비어있지 않아 실 CSV를 1회 로드(컨텍스트 캐시). 테스트는 CCTV 건수를 단언하지 않아 통과하나, 추후 test resources에 `cctv-csv:` 빈값 프로파일을 두면 더 빠르고 파일독립적이게 만들 수 있음(선택).
- DB/파괴적 변경·secret·외부발송 없음. main 직접 push 없음.
