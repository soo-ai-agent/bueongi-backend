// 중복 제거 공통 유틸 — 정규화, 거리 계산, 근접 클러스터링.

import type { CdnItem } from "../types.js";

/** 시설명/관서명 정규화: 공백/괄호/지점표기 제거, 소문자화. */
export function normalizeName(s?: string): string {
  if (!s) return "";
  return s
    .replace(/\(.*?\)/g, "")
    .replace(/[\s·.\-_]/g, "")
    .replace(/지구대|파출소|치안센터/g, (m) => m) // 형태 유지(매칭 키로 사용)
    .toLowerCase()
    .trim();
}

/** 전화번호 정규화: 숫자만. */
export function normalizePhone(s?: string): string {
  return (s ?? "").replace(/\D/g, "");
}

/** 주소 정규화: 공백/구분자 제거, 동/번지 표기 통일. */
export function normalizeAddress(s?: string): string {
  if (!s) return "";
  return s
    .replace(/대한민국|대한민국\s/g, "")
    .replace(/[\s,]/g, "")
    .replace(/번지/g, "")
    .toLowerCase()
    .trim();
}

/** 두 WGS84 좌표 간 거리(m). Haversine. */
export function distanceMeters(
  a: { lat: number; lng: number },
  b: { lat: number; lng: number },
): number {
  const R = 6371000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export interface MatchKeys {
  nameNorm: string;
  phoneNorm?: string;
  addressNorm?: string;
}

/** 두 아이템이 같은 시설일 후보인지 — 키 일치 또는 거리 임계 이내. */
export function isSameCluster(
  a: CdnItem,
  ka: MatchKeys,
  b: CdnItem,
  kb: MatchKeys,
  distanceThresholdM: number,
): boolean {
  const near = distanceMeters(a, b) <= distanceThresholdM;
  const phoneMatch = !!ka.phoneNorm && ka.phoneNorm === kb.phoneNorm;
  const nameMatch = !!ka.nameNorm && ka.nameNorm === kb.nameNorm;
  const addrMatch = !!ka.addressNorm && ka.addressNorm === kb.addressNorm;

  // 전화번호 일치는 단독으로 강한 신호. 그 외엔 (이름 또는 주소) + 근접.
  if (phoneMatch) return true;
  return near && (nameMatch || addrMatch);
}
