import { describe, expect, it } from "vitest";
import { besselTmToWgs84, utmkToWgs84, inKoreaBounds } from "../coord/transform.js";
import { processPerDistrict } from "../perDistrict.js";
import { fetchB1, loadRawFile } from "../sources/fetchers.js";
import type { RawRecord } from "../types.js";

describe("좌표 변환 (EPSG:5179 / EPSG:5174)", () => {
  it("UTM-K 서울시청 근처 좌표가 WGS84 서울 범위로 역검증된다", () => {
    // 서울시청 부근 UTM-K(EPSG:5179) 대략값
    const c = utmkToWgs84(961900, 1951200);
    expect(c).not.toBeNull();
    expect(c!.lat).toBeGreaterThan(37.4);
    expect(c!.lat).toBeLessThan(37.7);
    expect(c!.lng).toBeGreaterThan(126.8);
    expect(c!.lng).toBeLessThan(127.1);
  });

  it("Bessel 중부원점 TM 변환 결과가 한국 범위 안이다", () => {
    const c = besselTmToWgs84(198000, 451000);
    expect(c).not.toBeNull();
    expect(inKoreaBounds(c!.lat, c!.lng)).toBe(true);
  });

  it("범위를 벗어난 투영좌표는 null", () => {
    expect(utmkToWgs84(0, 0)).toBeNull();
  });
});

describe("A-4 자치구별 좌표계 (verified 변환 / quarantined 격리)", () => {
  const NOW = "2026-06-19T00:00:00Z";
  const rec = (over: Partial<RawRecord>): RawRecord => ({
    source: "A-4",
    type: "safepath",
    coordSystem: "unknown",
    ...over,
  });

  it("verified + epsg5174 자치구는 변환되어 아이템이 된다", () => {
    const rule = {
      sigungu: "41135",
      name: "분당 안심귀갓길",
      status: "verified" as const,
      coordSystem: "epsg5174" as const,
      columns: {},
    };
    const out = processPerDistrict("A-4", [rec({ x: 198000, y: 451000, name: "구간1" })], rule, NOW);
    expect(out.items).toHaveLength(1);
    expect(out.items[0]!.properties?.coordOrigin).toBe("per-district");
    expect(out.quarantine).toHaveLength(0);
  });

  it("quarantined 자치구는 변환하지 않고 격리 리포트만 남긴다", () => {
    const rule = {
      sigungu: "48121",
      name: "창원 안심귀갓길",
      status: "quarantined" as const,
      coordSystem: "unknown" as const,
      columns: {},
      reason: "좌표계 미확정",
    };
    const out = processPerDistrict("A-4", [rec({ name: "구간2" })], rule, NOW);
    expect(out.items).toHaveLength(0);
    expect(out.quarantine).toHaveLength(1);
    expect(out.quarantine[0]!.reason).toContain("좌표계");
  });
});

describe("오프라인 raw 로더 (data/raw)", () => {
  it("data/raw/B-1.json 을 RawRecord[] 로 읽어 fetcher 가 비어있지 않다", async () => {
    const direct = loadRawFile("B-1");
    expect(direct).not.toBeNull();
    expect(direct!.length).toBeGreaterThan(0);
    const fetched = await fetchB1({});
    expect(fetched.length).toBe(direct!.length);
    expect(fetched[0]!.coordSystem).toBe("wgs84");
  });

  it("fixtures 주입이 raw 파일보다 우선한다", async () => {
    const fixed: RawRecord[] = [
      { source: "B-1", type: "cctv", coordSystem: "wgs84", lat: 37.5, lng: 127.0 },
    ];
    const fetched = await fetchB1({ fixtures: { "B-1": fixed } });
    expect(fetched).toBe(fixed);
  });
});
