// 샘플 원천 데이터 생성기 (재현용).
//
// data/raw/{ID}.json (RawRecord[]) 와 data/geocode-cache.json 을 만든다.
// 목적: 라이브 공공데이터 키 없이도 파이프라인 전 구간(좌표 변환·지오코딩 캐시·
// B-3+B-4/D-1+D-2 dedup·A-4/D-2 per-district verified/quarantined)을 결정적으로
// 검증할 수 있는 대표 입력을 만든다.
//
// 좌표계 메타는 sources.yaml / district-coord-map.yaml 과 일치한다.
// B-4(EPSG:5179)·D-1/D-2(EPSG:5174)·A-4(EPSG:5179) 의 x/y 는 목표 WGS84 점을
// proj4 역변환해 산출하므로 dedup 임계(50m/20m)를 정확히 통제한다.
//
// 실행: node scripts/gen-sample-raw.mjs

import { mkdirSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import proj4 from "proj4";

proj4.defs(
  "EPSG:5179",
  "+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 +x_0=1000000 +y_0=2000000 +ellps=GRS80 +units=m +no_defs",
);
proj4.defs(
  "EPSG:5174",
  "+proj=tmerc +lat_0=38 +lon_0=127.0028902777778 +k=1 +x_0=200000 +y_0=500000 +ellps=bessel +units=m +no_defs +towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43",
);

const ROOT = resolve(import.meta.dirname, "..");
const RAW = join(ROOT, "data", "raw");
mkdirSync(RAW, { recursive: true });

// WGS84(lat,lng) -> 투영좌표(x,y). proj4 는 [lng,lat] 입력/[x,y] 출력.
const toEpsg = (epsg, lat, lng) => {
  const [x, y] = proj4("EPSG:4326", epsg, [lng, lat]);
  return { x: Math.round(x * 1000) / 1000, y: Math.round(y * 1000) / 1000 };
};

// 위경도 미세 오프셋(미터 → 도 근사). 위도 1deg≈111_000m, 경도(37.5N) 1deg≈88_000m.
const offset = (lat, lng, north_m, east_m) => ({
  lat: lat + north_m / 111_000,
  lng: lng + east_m / 88_000,
});

const write = (id, rows) =>
  writeFileSync(join(RAW, `${id}.json`), JSON.stringify(rows, null, 2), "utf8");

// ---- B-1 CCTV (WGS84, 시군구 분할) ----
write("B-1", [
  { source: "B-1", type: "cctv", coordSystem: "wgs84", sigunguCode: "11680", name: "강남구 CCTV 001", address: "서울특별시 강남구 역삼동 718", lat: 37.5012, lng: 127.0361 },
  { source: "B-1", type: "cctv", coordSystem: "wgs84", sigunguCode: "11680", name: "강남구 CCTV 002", address: "서울특별시 강남구 삼성동 159", lat: 37.5103, lng: 127.0594 },
  { source: "B-1", type: "cctv", coordSystem: "wgs84", sigunguCode: "11110", name: "종로구 CCTV 001", address: "서울특별시 종로구 청운동 1", lat: 37.5869, lng: 126.9690 },
]);

// ---- B-2 여성안심지킴이집 (WGS84, all) ----
write("B-2", [
  { source: "B-2", type: "safehouse", coordSystem: "wgs84", sigunguCode: "11680", name: "역삼 안심지킴이집", address: "서울특별시 강남구 역삼동 720", lat: 37.5004, lng: 127.0352 },
  { source: "B-2", type: "safehouse", coordSystem: "wgs84", sigunguCode: "11110", name: "청운 안심지킴이집", address: "서울특별시 종로구 청운동 50", lat: 37.5881, lng: 126.9701 },
]);

// ---- B-3 파출소 (주소 → E-2 지오코딩) + 캐시 ----
const yeoksamAddr = "서울특별시 강남구 테헤란로 142";
const cheongunAddr = "서울특별시 종로구 자하문로 92";
const yeoksam = { lat: 37.5006, lng: 127.0356 };
const cheongun = { lat: 37.5872, lng: 126.9695 };
write("B-3", [
  { source: "B-3", type: "police", coordSystem: "address", sigunguCode: "11680", name: "역삼지구대", address: yeoksamAddr, phone: "02-3665-0112" },
  { source: "B-3", type: "police", coordSystem: "address", sigunguCode: "11110", name: "청운효자동파출소", address: cheongunAddr, phone: "02-737-0112" },
]);
writeFileSync(
  join(ROOT, "data", "geocode-cache.json"),
  JSON.stringify({ [yeoksamAddr]: yeoksam, [cheongunAddr]: cheongun }, null, 2),
  "utf8",
);

// ---- B-4 치안안전시설 (EPSG:5179) ----
// (1) 역삼지구대: B-3 좌표 30m 이내 → 병합(dedup). (2) 수서치안센터: B-3 없음 → 신규 채택.
const yeoksamB4 = offset(yeoksam.lat, yeoksam.lng, 20, 15); // 약 25m
const suseo = { lat: 37.4870, lng: 127.1010 };
write("B-4", [
  { source: "B-4", type: "police", coordSystem: "epsg5179", sigunguCode: "11680", name: "역삼지구대", address: yeoksamAddr, ...toEpsg("EPSG:5179", yeoksamB4.lat, yeoksamB4.lng) },
  { source: "B-4", type: "police", coordSystem: "epsg5179", sigunguCode: "11680", name: "수서치안센터", address: "서울특별시 강남구 수서동 724", ...toEpsg("EPSG:5179", suseo.lat, suseo.lng) },
]);

// ---- C-1 서울 가로등 (WGS84) ----
write("C-1", [
  { source: "C-1", type: "lamp", coordSystem: "wgs84", sigunguCode: "11680", name: "강남-22205-001", address: "역삼동 가로등", lat: 37.5008, lng: 127.0359 },
  { source: "C-1", type: "lamp", coordSystem: "wgs84", sigunguCode: "11110", name: "종로-22205-001", address: "청운동 가로등", lat: 37.5875, lng: 126.9698 },
]);

// ---- C-2 전국 보안등 (WGS84, 시군구 분할) ----
write("C-2", [
  { source: "C-2", type: "lamp", coordSystem: "wgs84", sigunguCode: "11680", name: "강남 보안등 001", address: "서울특별시 강남구 역삼동", lat: 37.5010, lng: 127.0363 },
  { source: "C-2", type: "lamp", coordSystem: "wgs84", sigunguCode: "26110", name: "부산중구 보안등 001", address: "부산광역시 중구 대청동", lat: 35.1018, lng: 129.0320 },
]);

// ---- C-3 서울 보행등=보행자 신호등 (WGS84) ----
write("C-3", [
  { source: "C-3", type: "pedestrian_signal", coordSystem: "wgs84", name: "보행등", address: "서울특별시 강남구 역삼동 테헤란로", lat: 37.5013, lng: 127.0368 },
  { source: "C-3", type: "pedestrian_signal", coordSystem: "wgs84", name: "보행등", address: "서울특별시 종로구 청운동", lat: 37.5871, lng: 126.9702 },
]);

// ---- D-1 전국 비상벨 (EPSG:5174, 시군구 분할) ----
const bellGangnam = { lat: 37.5015, lng: 127.0366 };
const bellBundang = { lat: 37.3825, lng: 127.1190 };
write("D-1", [
  { source: "D-1", type: "emergency_bell", coordSystem: "epsg5174", sigunguCode: "11680", name: "역삼공원 비상벨", address: "서울특별시 강남구 역삼동 공원", ...toEpsg("EPSG:5174", bellGangnam.lat, bellGangnam.lng) },
  { source: "D-1", type: "emergency_bell", coordSystem: "epsg5174", sigunguCode: "41135", name: "분당 중앙공원 비상벨", address: "성남시 분당구 수내동", ...toEpsg("EPSG:5174", bellBundang.lat, bellBundang.lng) },
]);

// ---- D-2 자치구 비상벨 보완 (per-district; 26110 wgs84 신규, 41135 epsg5174 흡수, 48121 격리) ----
const bellBundangD2 = offset(bellBundang.lat, bellBundang.lng, 8, 6); // 약 10m → D-1 41135 와 흡수
write("D-2", [
  { source: "D-2", type: "emergency_bell", coordSystem: "wgs84", sigunguCode: "26110", name: "부산중구 비상벨 001", address: "부산광역시 중구 광복로", lat: 35.0995, lng: 129.0357 },
  { source: "D-2", type: "emergency_bell", coordSystem: "epsg5174", sigunguCode: "41135", name: "분당 중앙공원 비상벨", address: "성남시 분당구 수내동", ...toEpsg("EPSG:5174", bellBundangD2.lat, bellBundangD2.lng) },
  { source: "D-2", type: "emergency_bell", coordSystem: "unknown", sigunguCode: "48121", name: "창원성산 비상벨 001", address: "창원시 성산구 상남동" },
]);

// ---- A-4 자치구 안심귀갓길 (per-district; 26110 wgs84, 27110 epsg5179, 29110 격리) ----
const daeguPath = { lat: 35.8693, lng: 128.6062 };
write("A-4", [
  { source: "A-4", type: "safepath", coordSystem: "wgs84", sigunguCode: "26110", name: "부산중구 안심귀갓길 1구간", address: "부산광역시 중구 보수동", lat: 35.1042, lng: 129.0288 },
  { source: "A-4", type: "safepath", coordSystem: "epsg5179", sigunguCode: "27110", name: "대구중구 안심귀갓길 1구간", address: "대구광역시 중구 동인동", ...toEpsg("EPSG:5179", daeguPath.lat, daeguPath.lng) },
  { source: "A-4", type: "safepath", coordSystem: "unknown", sigunguCode: "29110", name: "광주동구 안심귀갓길 1구간", address: "광주광역시 동구 충장로" },
]);

console.log("sample raw data + geocode cache 생성 완료:", RAW);
