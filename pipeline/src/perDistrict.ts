// A-4 / D-2 자치구별 좌표계 처리 — config/district-coord-map.yaml 기반.
//
// 규칙 (문서 작업 2):
//   - 원천별 coordSystem 메타를 기록하고 WGS84 변환
//   - status=quarantined 또는 coordSystem=unknown 은 변환하지 않고 격리 리포트
//   - verified 만 표준 스키마로 통합 JSON에 병합

import { readFileSync } from "node:fs";
import { parse as parseYaml } from "yaml";
import { besselTmToWgs84, utmkToWgs84, validateWgs84 } from "./coord/transform.js";
import type {
  CdnItem,
  CoordFailureRow,
  QuarantineRow,
  RawRecord,
  SourceId,
} from "./types.js";

type DistrictStatus = "verified" | "quarantined";
type DistrictCrs = "wgs84" | "epsg5179" | "epsg5174" | "address" | "unknown";

interface DistrictRule {
  sigungu: string;
  name: string;
  status: DistrictStatus;
  coordSystem: DistrictCrs;
  columns: Record<string, string>;
  reason?: string;
}

interface DistrictMap {
  [mapKey: string]: {
    defaults: { status: DistrictStatus; coordSystem: DistrictCrs };
    districts: DistrictRule[];
  };
}

export function loadDistrictMap(path: string): DistrictMap {
  const doc = parseYaml(readFileSync(path, "utf8")) as Record<string, unknown>;
  const out: DistrictMap = {};
  for (const [k, v] of Object.entries(doc)) {
    if (k === "version") continue;
    out[k] = v as DistrictMap[string];
  }
  return out;
}

export function getDistrictRule(
  map: DistrictMap,
  mapKey: string,
  sigungu: string,
): DistrictRule | undefined {
  return map[mapKey]?.districts.find((d) => d.sigungu === sigungu);
}

export interface PerDistrictOutcome {
  items: CdnItem[];
  quarantine: QuarantineRow[];
  coordFailures: CoordFailureRow[];
}

/**
 * 자치구별 원천 레코드를 규칙에 따라 변환/격리한다.
 * records 는 sigunguCode 가 채워진 RawRecord 묶음.
 */
export function processPerDistrict(
  source: SourceId,
  records: RawRecord[],
  rule: DistrictRule,
  now: string,
): PerDistrictOutcome {
  const items: CdnItem[] = [];
  const quarantine: QuarantineRow[] = [];
  const coordFailures: CoordFailureRow[] = [];

  // 격리 대상: 변환하지 않고 리포트에만 남긴다.
  if (rule.status === "quarantined" || rule.coordSystem === "unknown") {
    quarantine.push({
      source,
      sigunguCode: rule.sigungu,
      name: rule.name,
      reason: rule.reason ?? `coordSystem=${rule.coordSystem}, status=${rule.status}`,
    });
    return { items, quarantine, coordFailures };
  }

  for (const r of records) {
    const coord = convert(rule.coordSystem, r);
    if (!coord) {
      coordFailures.push({
        source,
        name: r.name,
        address: r.address,
        reason: `per-district convert failed (crs=${rule.coordSystem})`,
      });
      continue;
    }
    items.push({
      id: `${source}:${rule.sigungu}:${items.length}`,
      source,
      type: r.type,
      name: r.name,
      address: r.address,
      lat: coord.lat,
      lng: coord.lng,
      sigunguCode: rule.sigungu,
      updatedAt: now,
      properties: { coordOrigin: "per-district" },
    });
  }

  return { items, quarantine, coordFailures };
}

function convert(crs: DistrictCrs, r: RawRecord) {
  switch (crs) {
    case "wgs84":
      return r.lat != null && r.lng != null ? validateWgs84(r.lat, r.lng) : null;
    case "epsg5179":
      return r.x != null && r.y != null ? utmkToWgs84(r.x, r.y) : null;
    case "epsg5174":
      return r.x != null && r.y != null ? besselTmToWgs84(r.x, r.y) : null;
    // address(지오코딩 필요)는 per-district MVP 범위 밖 — 격리로 처리됨.
    default:
      return null;
  }
}
