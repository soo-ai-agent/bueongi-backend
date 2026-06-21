// D-1 + D-2 비상벨 병합 (emergency_bell/{시군구코드}.json).
//
// 규칙 (문서 작업 2):
//   - 클러스터 후보: 시설명/관리기관/주소 정규화 + 좌표 20m 이내
//   - D-1(EPSG:5174 변환) 기본 채택
//   - D-2 는 (a) D-1 누락 자치구 또는 (b) D-1 좌표 결측 보완에만 채택

import type { CdnItem } from "../types.js";
import {
  isSameCluster,
  normalizeAddress,
  normalizeName,
  type MatchKeys,
} from "./cluster.js";

const BELL_DISTANCE_M = 20;

function keysOf(item: CdnItem): MatchKeys {
  const manager = (item.properties?.manager as string | undefined) ?? "";
  return {
    nameNorm: normalizeName(item.name),
    addressNorm: normalizeAddress(item.address),
    // 관리기관을 phone 슬롯 대신 별도 가중치로 쓰지 않고 name 정규화에 합산.
    phoneNorm: normalizeName(manager) || undefined,
  };
}

export interface MergeResult {
  items: CdnItem[];
  deduped: number;
}

/**
 * @param d1 EPSG:5174 변환 완료, 좌표 있는 아이템 (좌표 결측 가능)
 * @param d2 자치구 보완(좌표계 verified 만 들어옴)
 * @param d1Sigungus D-1 이 커버하는 시군구코드 집합 (누락 판단용)
 */
export function mergeEmergencyBell(
  d1: CdnItem[],
  d2: CdnItem[],
  d1Sigungus: Set<string>,
): MergeResult {
  const clusters: { rep: CdnItem; keys: MatchKeys; members: CdnItem[] }[] =
    d1.map((item) => ({ rep: item, keys: keysOf(item), members: [item] }));

  let deduped = 0;

  for (const item of d2) {
    const k = keysOf(item);
    const hit = clusters.find((c) =>
      isSameCluster(c.rep, c.keys, item, k, BELL_DISTANCE_M),
    );

    if (hit) {
      // 같은 시설 → D-1 좌표가 결측일 때만 D-2 로 좌표 보완.
      const repValid = isValidCoord(hit.rep);
      if (!repValid && isValidCoord(item)) {
        hit.rep = { ...hit.rep, lat: item.lat, lng: item.lng };
        hit.rep.properties = { ...hit.rep.properties, coordOrigin: "per-district" };
      }
      hit.members.push(item);
      deduped++;
      continue;
    }

    // 클러스터 없음 → D-1 누락 자치구일 때만 신규 채택.
    const sg = item.sigunguCode;
    if (sg && !d1Sigungus.has(sg)) {
      clusters.push({ rep: item, keys: k, members: [item] });
    } else {
      // D-1 이 커버하는 자치구인데 매칭 실패 → 보완 대상 아님(중복/노이즈). 폐기.
      deduped++;
    }
  }

  const items = clusters.map((c) => collapse(c.rep, c.members));
  return { items, deduped };
}

function isValidCoord(item: CdnItem): boolean {
  return Number.isFinite(item.lat) && Number.isFinite(item.lng) && item.lat !== 0;
}

function collapse(rep: CdnItem, members: CdnItem[]): CdnItem {
  const mergedSources = Array.from(new Set(members.map((m) => m.source)));
  return {
    ...rep,
    properties: {
      ...rep.properties,
      coordOrigin: rep.properties?.coordOrigin ?? "epsg5174",
      mergedSources,
    },
  };
}
