// E-2 카카오 지오코딩 — B-3 주소 → WGS84 좌표.
// 실패 건은 호출자가 CoordFailureRow 로 모아 report 에 남기고 B-4 보완 후보로 처리한다.

import { inKoreaBounds, type LatLng } from "./transform.js";

const KAKAO_ENDPOINT = "https://dapi.kakao.com/v2/local/search/address.json";

export interface GeocodeResult {
  ok: boolean;
  coord?: LatLng;
  reason?: string;
}

interface KakaoAddressResponse {
  documents: Array<{ x: string; y: string }>;
}

/**
 * 단건 지오코딩. 카카오는 x=lng, y=lat 문자열로 반환.
 * rate limit 은 호출자(geocodeBatch)에서 제어한다.
 */
export async function geocodeAddress(
  address: string,
  apiKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<GeocodeResult> {
  if (!address || !address.trim()) {
    return { ok: false, reason: "empty-address" };
  }

  const url = `${KAKAO_ENDPOINT}?query=${encodeURIComponent(address)}`;
  let res: Response;
  try {
    res = await fetchImpl(url, {
      headers: { Authorization: `KakaoAK ${apiKey}` },
    });
  } catch (e) {
    return { ok: false, reason: `network: ${(e as Error).message}` };
  }

  if (!res.ok) {
    return { ok: false, reason: `http ${res.status}` };
  }

  const body = (await res.json()) as KakaoAddressResponse;
  const doc = body.documents?.[0];
  if (!doc) {
    return { ok: false, reason: "no-match" };
  }

  const lat = Number(doc.y);
  const lng = Number(doc.x);
  if (!inKoreaBounds(lat, lng)) {
    return { ok: false, reason: "out-of-range" };
  }
  return { ok: true, coord: { lat, lng } };
}

/** 주소 → 좌표 캐시. 월1회 운영 시 쿼터 절약 + 오프라인 재현용. */
export type GeocodeCache = Record<string, { lat: number; lng: number }>;

/**
 * 배치 지오코딩. 캐시 우선, 미스만 네트워크 호출(키 있을 때).
 * rateLimitPerSec 에 맞춰 호출 간격을 둔다. 결과는 입력 순서와 1:1로 정렬된다.
 */
export async function geocodeBatch(
  addresses: string[],
  apiKey: string,
  rateLimitPerSec = 10,
  fetchImpl: typeof fetch = fetch,
  cache: GeocodeCache = {},
): Promise<GeocodeResult[]> {
  const out: GeocodeResult[] = [];
  const intervalMs = Math.ceil(1000 / Math.max(1, rateLimitPerSec));
  for (const addr of addresses) {
    const cached = addr ? cache[addr] : undefined;
    if (cached && inKoreaBounds(cached.lat, cached.lng)) {
      out.push({ ok: true, coord: cached });
      continue;
    }
    if (!apiKey) {
      out.push({ ok: false, reason: cached ? "cache-out-of-range" : "no-kakao-key" });
      continue;
    }
    out.push(await geocodeAddress(addr, apiKey, fetchImpl));
    await sleep(intervalMs);
  }
  return out;
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}
