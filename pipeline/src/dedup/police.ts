// B-3 + B-4 파출소 병합 (police/all.json).
//
// 규칙 (문서 작업 2):
//   - 클러스터 후보: 경찰시설명/전화번호/주소 정규화 + 좌표 50m 이내
//   - B-3(지오코딩 성공) 좌표 우선, 실패 시 B-4(EPSG:5179 변환) 좌표로 보완
//   - alias/sourceId 는 properties.mergedSources / aliases 에 병합

import type { CdnItem } from "../types.js";
import {
  isSameCluster,
  normalizeAddress,
  normalizeName,
  normalizePhone,
  type MatchKeys,
} from "./cluster.js";

const POLICE_DISTANCE_M = 50;

function keysOf(item: CdnItem): MatchKeys {
  return {
    nameNorm: normalizeName(item.name),
    phoneNorm: normalizePhone(item.properties?.phone as string | undefined),
    addressNorm: normalizeAddress(item.address),
  };
}

export interface MergeResult {
  items: CdnItem[];
  deduped: number;
}

/**
 * B-3 우선, B-4 보완. b3 는 이미 지오코딩된(좌표 있는) 아이템만 들어온다.
 * b4 는 EPSG:5179 변환 완료 아이템.
 */
export function mergePolice(b3: CdnItem[], b4: CdnItem[]): MergeResult {
  const clusters: { rep: CdnItem; keys: MatchKeys; members: CdnItem[] }[] = [];

  // B-3 를 먼저 클러스터 대표로 심는다 (우선권).
  for (const item of b3) {
    clusters.push({ rep: item, keys: keysOf(item), members: [item] });
  }

  let deduped = 0;

  // B-4 는 기존 클러스터에 흡수되거나(중복), 신규 클러스터로 추가(B-3 누락 보완).
  for (const item of b4) {
    const k = keysOf(item);
    const hit = clusters.find((c) => isSameCluster(c.rep, c.keys, item, k, POLICE_DISTANCE_M));
    if (hit) {
      hit.members.push(item);
      deduped++;
    } else {
      clusters.push({ rep: item, keys: k, members: [item] });
    }
  }

  const items = clusters.map((c) => collapse(c.rep, c.members));
  return { items, deduped };
}

/** 클러스터를 단일 아이템으로. B-3 좌표 우선, 결측 시 B-4 좌표. */
function collapse(rep: CdnItem, members: CdnItem[]): CdnItem {
  const b3 = members.find((m) => m.source === "B-3");
  const b4 = members.find((m) => m.source === "B-4");

  const coordSource = b3 ?? b4 ?? rep;
  const mergedSources = Array.from(new Set(members.map((m) => m.source)));
  const aliases = Array.from(
    new Set(members.map((m) => m.name).filter((n): n is string => !!n && n !== rep.name)),
  );

  return {
    ...rep,
    source: b3 ? "B-3" : (rep.source as CdnItem["source"]),
    lat: coordSource.lat,
    lng: coordSource.lng,
    name: rep.name ?? b3?.name ?? b4?.name,
    address: rep.address ?? b3?.address ?? b4?.address,
    properties: {
      ...rep.properties,
      phone: rep.properties?.phone ?? b3?.properties?.phone ?? b4?.properties?.phone,
      coordOrigin: b3 ? "geocode" : "epsg5179",
      mergedSources,
      ...(aliases.length ? { aliases } : {}),
    },
  };
}
