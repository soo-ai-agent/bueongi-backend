// 좌표 변환 — proj4 기반 투영좌표 → WGS84, 그리고 WGS84 범위 검증.
//
// 명시 대상 (문서 작업 2):
//   B-4: EPSG:5179 (UTM-K, GRS80)            → WGS84
//   D-1: EPSG:5174 (Bessel 중부원점 TM)       → WGS84
//
// 주의: EPSG:5174 는 Bessel 타원체라 datum shift(towgs84)가 없으면
// 수백 m 오차가 난다. 아래 정의는 7-parameter Bessel→WGS84 보정을 포함한다.

import proj4 from "proj4";

export const WGS84 = "EPSG:4326";

// UTM-K (GRS80) — 표준 정의. datum shift 불필요.
proj4.defs(
  "EPSG:5179",
  "+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 +x_0=1000000 +y_0=2000000 " +
    "+ellps=GRS80 +units=m +no_defs",
);

// 중부원점 TM (Bessel) — towgs84 7-parameter 보정 포함.
proj4.defs(
  "EPSG:5174",
  "+proj=tmerc +lat_0=38 +lon_0=127.0028902777778 +k=1 +x_0=200000 +y_0=500000 " +
    "+ellps=bessel +units=m +no_defs " +
    "+towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43",
);

/** 대한민국 WGS84 유효 범위. 이 밖이면 변환 오류로 간주하고 폐기. */
export const KOREA_BOUNDS = {
  latMin: 33.0,
  latMax: 39.0,
  lngMin: 124.0,
  lngMax: 132.0,
} as const;

export function inKoreaBounds(lat: number, lng: number): boolean {
  return (
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    lat >= KOREA_BOUNDS.latMin &&
    lat <= KOREA_BOUNDS.latMax &&
    lng >= KOREA_BOUNDS.lngMin &&
    lng <= KOREA_BOUNDS.lngMax
  );
}

export interface LatLng {
  lat: number;
  lng: number;
}

/**
 * 투영좌표(x=동, y=북) → WGS84. proj4 는 [lng,lat] 순서를 반환한다.
 * 범위 밖이면 null 을 돌려 호출자가 outOfRange 로 집계하게 한다.
 */
function projectToWgs84(
  fromEpsg: "EPSG:5179" | "EPSG:5174",
  x: number,
  y: number,
): LatLng | null {
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  const [lng, lat] = proj4(fromEpsg, WGS84, [x, y]);
  if (!inKoreaBounds(lat, lng)) return null;
  return { lat, lng };
}

/** B-4: EPSG:5179 (UTM-K) → WGS84 */
export function utmkToWgs84(x: number, y: number): LatLng | null {
  return projectToWgs84("EPSG:5179", x, y);
}

/** D-1: EPSG:5174 (Bessel 중부원점 TM) → WGS84 */
export function besselTmToWgs84(x: number, y: number): LatLng | null {
  return projectToWgs84("EPSG:5174", x, y);
}

/** WGS84 원천(B-1/B-2/C-1/C-2). 범위 검증만 수행. */
export function validateWgs84(lat: number, lng: number): LatLng | null {
  return inKoreaBounds(lat, lng) ? { lat, lng } : null;
}
