// 파이프라인 오케스트레이터 — 원천 수집 → 좌표 변환/지오코딩 → 중복 제거/병합
// → 시군구 분할 → 컬렉션 파일 + manifest + 리포트 산출.
//
// 산출은 backend 서버가 아니라 cdn/ 디렉터리(정적 파일)로만 나간다.
// 실행: npm run run:pipeline

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { besselTmToWgs84, utmkToWgs84, validateWgs84 } from "./coord/transform.js";
import { geocodeBatch, type GeocodeCache, type GeocodeResult } from "./coord/geocode.js";
import { mergePolice } from "./dedup/police.js";
import { mergeEmergencyBell } from "./dedup/emergencyBell.js";
import { loadDistrictMap, getDistrictRule, processPerDistrict } from "./perDistrict.js";
import { buildCollection, buildManifest, type CollectionFile, type SourceStat } from "./manifest.js";
import * as F from "./sources/fetchers.js";
import type { CdnItem, CoordFailureRow, CoordOrigin, QuarantineRow, RawRecord, SourceId } from "./types.js";

const ROOT = resolve(import.meta.dirname, "..");
const CDN_OUT = join(ROOT, "..", "cdn");
const REPORT_OUT = join(CDN_OUT, "report");

const NOW = process.env.PIPELINE_NOW ?? new Date().toISOString();
const VERSION = process.env.PIPELINE_VERSION ?? NOW.slice(0, 10);
const BASE_URL = process.env.CDN_BASE_URL ?? "https://cdn.example.com/bueongi";

function emptyStat(): SourceStat {
  return { input: 0, output: 0, coordFailed: 0, outOfRange: 0, deduped: 0, quarantined: 0 };
}

/** data/geocode-cache.json (주소→{lat,lng}) 로드. 없으면 빈 캐시. */
function loadGeocodeCache(path: string): GeocodeCache {
  if (!existsSync(path)) return {};
  return JSON.parse(readFileSync(path, "utf8")) as GeocodeCache;
}

async function main(): Promise<void> {
  const ctx: F.FetchContext = {
    dataGoKrKey: process.env.DATA_GO_KR_SERVICE_KEY,
    seoulKey: process.env.SEOUL_OPEN_API_KEY,
  };
  const kakaoKey = process.env.KAKAO_REST_API_KEY ?? "";
  const districtMap = loadDistrictMap(join(ROOT, "config", "district-coord-map.yaml"));

  const files: CollectionFile[] = [];
  const bySource: Record<string, SourceStat> = {};
  const quarantine: QuarantineRow[] = [];
  const coordFailures: CoordFailureRow[] = [];

  // ---- B-1 CCTV: WGS84, 시군구 분할 ----
  {
    const raw = await F.fetchB1(ctx);
    // 방범 성격(설치목적구분) CCTV만 적재 — 백엔드 CSV 경로(parseCctvCsvLine)와 동일 정책으로 양 경로 일관성 유지.
    const { items, stat } = convertWgs84(filterSecurityCctv(raw), "B-1");
    bySource["B-1"] = stat;
    for (const [sg, group] of splitBySigungu(items)) {
      files.push(buildCollection("cctv", sg, group, ["B-1"], NOW, sg));
    }
  }

  // ---- B-2 여성안심지킴이집: WGS84, all.json ----
  {
    const raw = await F.fetchB2(ctx);
    const { items, stat } = convertWgs84(raw, "B-2");
    bySource["B-2"] = stat;
    files.push(buildCollection("safehouse", "all", items, ["B-2"], NOW));
  }

  // ---- B-3 + B-4 파출소 병합: police/all.json ----
  {
    const b3raw = await F.fetchB3(ctx);
    const b4raw = await F.fetchB4(ctx);

    // B-3: E-2 카카오 지오코딩 (캐시 우선, 미스만 네트워크)
    const b3stat = emptyStat();
    b3stat.input = b3raw.length;
    const b3items: CdnItem[] = [];
    const geocodeCache = loadGeocodeCache(join(ROOT, "data", "geocode-cache.json"));
    const geos: GeocodeResult[] = await geocodeBatch(
      b3raw.map((r) => r.address ?? ""),
      kakaoKey,
      10,
      fetch,
      geocodeCache,
    );
    b3raw.forEach((r, i) => {
      const g = geos[i]!;
      if (g.ok && g.coord) {
        b3items.push(toItem(r, "B-3", g.coord.lat, g.coord.lng, "geocode", b3items.length));
      } else {
        b3stat.coordFailed++;
        coordFailures.push({ source: "B-3", name: r.name, address: r.address, reason: g.reason ?? "geocode-fail" });
      }
    });
    b3stat.output = b3items.length;

    const { items: b4items, stat: b4stat } = convertProjected(b4raw, "B-4", "epsg5179");

    const merged = mergePolice(b3items, b4items);
    b3stat.deduped = merged.deduped; // B-4 가 B-3 에 흡수된 수
    bySource["B-3"] = b3stat;
    bySource["B-4"] = b4stat;
    files.push(buildCollection("police", "all", merged.items, ["B-3", "B-4"], NOW));
  }

  // ---- C-1 서울 가로등: WGS84, lamp/seoul.json ----
  {
    const raw = await F.fetchC1(ctx);
    const { items, stat } = convertWgs84(raw, "C-1");
    bySource["C-1"] = stat;
    files.push(buildCollection("lamp", "seoul", items, ["C-1"], NOW));
  }

  // ---- C-2 전국 보안등: WGS84, 시군구 분할 ----
  {
    const raw = await F.fetchC2(ctx);
    const { items, stat } = convertWgs84(raw, "C-2");
    bySource["C-2"] = stat;
    for (const [sg, group] of splitBySigungu(items)) {
      files.push(buildCollection("lamp", sg, group, ["C-2"], NOW, sg));
    }
  }

  // ---- C-3 서울 보행등(보행자 신호등): WGS84, pedestrian_signal/seoul.json ----
  {
    const raw = await F.fetchC3(ctx);
    const { items, stat } = convertWgs84(raw, "C-3");
    bySource["C-3"] = stat;
    files.push(buildCollection("pedestrian_signal", "seoul", items, ["C-3"], NOW));
  }

  // ---- D-1 + D-2 비상벨 병합: emergency_bell/{시군구코드}.json ----
  {
    const d1raw = await F.fetchD1(ctx);
    const { items: d1items, stat: d1stat } = convertProjected(d1raw, "D-1", "epsg5174");
    bySource["D-1"] = d1stat;

    // D-2: 자치구별 좌표계 (verified 만 변환, quarantined 격리)
    const d2raw = await F.fetchD2(ctx);
    const d2items: CdnItem[] = [];
    const d2stat = emptyStat();
    d2stat.input = d2raw.length;
    for (const [sg, group] of splitBySigungu(d2raw)) {
      const rule = getDistrictRule(districtMap, "D-2", sg);
      if (!rule) {
        d2stat.quarantined += group.length;
        quarantine.push({ source: "D-2", sigunguCode: sg, reason: "district rule 없음" });
        continue;
      }
      const out = processPerDistrict("D-2", group, rule, NOW);
      d2items.push(...out.items);
      d2stat.quarantined += out.quarantine.length ? group.length : 0;
      d2stat.coordFailed += out.coordFailures.length;
      quarantine.push(...out.quarantine);
      coordFailures.push(...out.coordFailures);
    }
    d2stat.output = d2items.length;
    bySource["D-2"] = d2stat;

    const d1Sigungus = new Set(d1items.map((i) => i.sigunguCode).filter((s): s is string => !!s));
    const merged = mergeEmergencyBell(d1items, d2items, d1Sigungus);

    for (const [sg, group] of splitBySigungu(merged.items)) {
      files.push(buildCollection("emergency_bell", sg, group, ["D-1", "D-2"], NOW, sg));
    }
    bySource["D-1"]!.deduped += merged.deduped;
  }

  // ---- A-4 자치구 안심귀갓길: safepath/all.json (verified 만 통합) ----
  {
    const a4raw = await F.fetchA4(ctx);
    const a4items: CdnItem[] = [];
    const a4stat = emptyStat();
    a4stat.input = a4raw.length;
    for (const [sg, group] of splitBySigungu(a4raw)) {
      const rule = getDistrictRule(districtMap, "A-4", sg);
      if (!rule) {
        a4stat.quarantined += group.length;
        quarantine.push({ source: "A-4", sigunguCode: sg, reason: "district rule 없음" });
        continue;
      }
      const out = processPerDistrict("A-4", group, rule, NOW);
      a4items.push(...out.items);
      a4stat.quarantined += out.quarantine.length ? group.length : 0;
      a4stat.coordFailed += out.coordFailures.length;
      quarantine.push(...out.quarantine);
      coordFailures.push(...out.coordFailures);
    }
    a4stat.output = a4items.length;
    bySource["A-4"] = a4stat;
    files.push(buildCollection("safepath", "all", a4items, ["A-4"], NOW));
  }

  // ---- 리포트 + manifest 출력 ----
  writeReport("quarantine.json", quarantine);
  writeReport("geocode-failures.json", coordFailures.filter((c) => c.source === "B-3"));
  writeReport("coord-failures.json", coordFailures);

  const reports = {
    quarantine: "report/quarantine.json",
    geocodeFailures: "report/geocode-failures.json",
    coordFailures: "report/coord-failures.json",
  };
  const manifest = buildManifest(VERSION, NOW, BASE_URL, files, bySource, reports);

  for (const f of files) writeCdn(f.path, f.json);
  writeCdn("manifest.json", JSON.stringify(manifest, null, 2));

  console.log(`[pipeline] ${files.length} files, version=${VERSION}`);
  console.log(`[pipeline] quarantined=${quarantine.length}, coordFailures=${coordFailures.length}`);
}

function toItem(
  r: RawRecord,
  source: SourceId,
  lat: number,
  lng: number,
  origin: CoordOrigin,
  idx: number,
): CdnItem {
  return {
    id: `${source}:${r.sigunguCode ?? "na"}:${idx}`,
    source,
    type: r.type,
    name: r.name,
    address: r.address,
    lat,
    lng,
    sigunguCode: r.sigunguCode,
    updatedAt: NOW,
    properties: {
      coordOrigin: origin,
      ...(r.phone ? { phone: r.phone } : {}),
      ...(r.purpose ? { purpose: r.purpose } : {}),
      ...(r.cameraCount != null ? { cameraCount: r.cameraCount } : {}),
    },
  };
}

/**
 * CCTV(B-1) 방범 필터 — 안심 귀가에 의미 있는 설치목적구분만 남긴다(백엔드 CSV 경로와 동일 keep-set).
 * 목적 결측 행은 필터 불가 → fail-open(통과)하되, 전량 결측이면 경고(keyColumns.purpose 매핑 미설정 신호).
 */
const CCTV_SECURITY_PURPOSES = new Set(["생활방범", "다목적", "어린이보호", "차량방범"]);
function filterSecurityCctv(raw: RawRecord[]): RawRecord[] {
  let withPurpose = 0;
  const kept = raw.filter((r) => {
    if (r.purpose == null) return true;
    withPurpose++;
    return CCTV_SECURITY_PURPOSES.has(r.purpose);
  });
  if (raw.length > 0 && withPurpose === 0) {
    console.warn(
      "[pipeline] B-1 CCTV: 설치목적구분 결측 → 방범 필터 미적용(전량 통과). keyColumns.purpose 매핑 확인 필요.",
    );
  }
  return kept;
}

function convertWgs84(raw: RawRecord[], source: SourceId) {
  const stat = emptyStat();
  stat.input = raw.length;
  const items: CdnItem[] = [];
  raw.forEach((r, i) => {
    const c = r.lat != null && r.lng != null ? validateWgs84(r.lat, r.lng) : null;
    if (!c) {
      stat.outOfRange++;
      return;
    }
    items.push(toItem(r, source, c.lat, c.lng, "wgs84", i));
  });
  stat.output = items.length;
  return { items, stat };
}

function convertProjected(raw: RawRecord[], source: SourceId, crs: "epsg5179" | "epsg5174") {
  const stat = emptyStat();
  stat.input = raw.length;
  const items: CdnItem[] = [];
  raw.forEach((r, i) => {
    const c =
      r.x != null && r.y != null
        ? crs === "epsg5179"
          ? utmkToWgs84(r.x, r.y)
          : besselTmToWgs84(r.x, r.y)
        : null;
    if (!c) {
      stat.coordFailed++;
      return;
    }
    items.push(toItem(r, source, c.lat, c.lng, crs, i));
  });
  stat.output = items.length;
  return { items, stat };
}

function splitBySigungu<T extends { sigunguCode?: string }>(items: T[]): Map<string, T[]> {
  const m = new Map<string, T[]>();
  for (const it of items) {
    const sg = it.sigunguCode ?? "unknown";
    (m.get(sg) ?? m.set(sg, []).get(sg)!).push(it);
  }
  return m;
}

function writeCdn(rel: string, content: string): void {
  const p = join(CDN_OUT, rel);
  mkdirSync(dirname(p), { recursive: true });
  writeFileSync(p, content, "utf8");
}

function writeReport(name: string, rows: unknown[]): void {
  mkdirSync(REPORT_OUT, { recursive: true });
  writeFileSync(join(REPORT_OUT, name), JSON.stringify({ count: rows.length, rows }, null, 2), "utf8");
}

main().catch((e) => {
  console.error("[pipeline] failed:", e);
  process.exit(1);
});
