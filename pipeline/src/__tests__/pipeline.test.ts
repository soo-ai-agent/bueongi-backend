import { describe, expect, it } from "vitest";
import { besselTmToWgs84, utmkToWgs84, inKoreaBounds } from "../coord/transform.js";
import { geocodeBatch } from "../coord/geocode.js";
import { mergePolice } from "../dedup/police.js";
import { mergeEmergencyBell } from "../dedup/emergencyBell.js";
import { distanceMeters, normalizeName, normalizePhone } from "../dedup/cluster.js";
import { processPerDistrict } from "../perDistrict.js";
import { fetchB1, loadRawFile } from "../sources/fetchers.js";
import type { CdnItem, RawRecord } from "../types.js";

const item = (over: Partial<CdnItem>): CdnItem => ({
  id: "x",
  source: "B-3",
  type: "police",
  lat: 37.5,
  lng: 127.0,
  updatedAt: "2026-06-19T00:00:00Z",
  ...over,
});

describe("좌표 변환 (B-4 EPSG:5179, D-1 EPSG:5174)", () => {
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

describe("B-3 + B-4 파출소 병합 (50m, B-3 우선)", () => {
  it("근접/동명 시설은 하나로 병합되고 B-3 좌표를 유지한다", () => {
    const b3 = [item({ source: "B-3", name: "역삼지구대", lat: 37.5, lng: 127.03, properties: { phone: "02-111-2222" } })];
    const b4 = [item({ source: "B-4", name: "역삼지구대", lat: 37.50005, lng: 127.03005 })];
    const { items, deduped } = mergePolice(b3, b4);
    expect(items).toHaveLength(1);
    expect(deduped).toBe(1);
    expect(items[0]!.source).toBe("B-3");
    expect(items[0]!.lat).toBe(37.5); // B-3 좌표 유지
    expect(items[0]!.properties?.mergedSources).toEqual(expect.arrayContaining(["B-3", "B-4"]));
  });

  it("B-3 누락분은 B-4 가 신규 아이템으로 채택된다", () => {
    const b4 = [item({ source: "B-4", name: "신규파출소", lat: 35.1, lng: 129.0 })];
    const { items } = mergePolice([], b4);
    expect(items).toHaveLength(1);
    expect(items[0]!.source).toBe("B-4");
    expect(items[0]!.properties?.coordOrigin).toBe("epsg5179");
  });
});

describe("D-1 + D-2 비상벨 병합 (20m, D-1 기본)", () => {
  it("D-1 커버 자치구의 매칭 D-2 는 흡수되어 dedup 된다", () => {
    const d1 = [item({ source: "D-1", type: "emergency_bell", name: "공원비상벨", lat: 37.5, lng: 127.0, sigunguCode: "11110" })];
    const d2 = [item({ source: "D-2", type: "emergency_bell", name: "공원비상벨", lat: 37.50001, lng: 127.00001, sigunguCode: "11110" })];
    const { items, deduped } = mergeEmergencyBell(d1, d2, new Set(["11110"]));
    expect(items).toHaveLength(1);
    expect(deduped).toBe(1);
  });

  it("D-1 누락 자치구의 D-2 만 신규 채택된다", () => {
    const d1 = [item({ source: "D-1", type: "emergency_bell", lat: 37.5, lng: 127.0, sigunguCode: "11110" })];
    const d2 = [item({ source: "D-2", type: "emergency_bell", name: "타구비상벨", lat: 35.1, lng: 129.0, sigunguCode: "26110" })];
    const { items } = mergeEmergencyBell(d1, d2, new Set(["11110"]));
    expect(items).toHaveLength(2);
  });
});

describe("A-4/D-2 자치구별 좌표계 (verified 변환 / quarantined 격리)", () => {
  const NOW = "2026-06-19T00:00:00Z";
  const rec = (over: Partial<RawRecord>): RawRecord => ({
    source: "D-2",
    type: "emergency_bell",
    coordSystem: "unknown",
    ...over,
  });

  it("verified + epsg5174 자치구는 변환되어 아이템이 된다", () => {
    const rule = {
      sigungu: "41135",
      name: "분당 비상벨",
      status: "verified" as const,
      coordSystem: "epsg5174" as const,
      columns: {},
    };
    const out = processPerDistrict("D-2", [rec({ x: 198000, y: 451000, name: "벨1" })], rule, NOW);
    expect(out.items).toHaveLength(1);
    expect(out.items[0]!.properties?.coordOrigin).toBe("per-district");
    expect(out.quarantine).toHaveLength(0);
  });

  it("quarantined 자치구는 변환하지 않고 격리 리포트만 남긴다", () => {
    const rule = {
      sigungu: "48121",
      name: "창원 비상벨",
      status: "quarantined" as const,
      coordSystem: "unknown" as const,
      columns: {},
      reason: "좌표계 미확정",
    };
    const out = processPerDistrict("D-2", [rec({ name: "벨2" })], rule, NOW);
    expect(out.items).toHaveLength(0);
    expect(out.quarantine).toHaveLength(1);
    expect(out.quarantine[0]!.reason).toContain("좌표계");
  });
});

describe("E-2 지오코딩 캐시 (오프라인/쿼터 절약)", () => {
  it("캐시 적중 주소는 네트워크 없이 좌표를 돌려준다", async () => {
    const cache = { "서울특별시 강남구 테헤란로 142": { lat: 37.5006, lng: 127.0356 } };
    const noNet = (() => {
      throw new Error("네트워크 호출 금지");
    }) as unknown as typeof fetch;
    const res = await geocodeBatch(
      ["서울특별시 강남구 테헤란로 142"],
      "", // 키 없음
      10,
      noNet,
      cache,
    );
    expect(res[0]!.ok).toBe(true);
    expect(res[0]!.coord!.lat).toBeCloseTo(37.5006);
  });

  it("캐시 미스 + 키 없음은 no-kakao-key 실패로 보고된다", async () => {
    const res = await geocodeBatch(["미등록 주소"], "", 10, undefined, {});
    expect(res[0]!.ok).toBe(false);
    expect(res[0]!.reason).toBe("no-kakao-key");
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

describe("정규화/거리 유틸", () => {
  it("이름 정규화는 괄호/공백을 제거한다", () => {
    expect(normalizeName("역삼 지구대 (강남)")).toBe("역삼지구대");
  });
  it("전화번호는 숫자만 남긴다", () => {
    expect(normalizePhone("02-111-2222")).toBe("021112222");
  });
  it("Haversine 거리가 합리적이다", () => {
    const d = distanceMeters({ lat: 37.5, lng: 127.0 }, { lat: 37.50045, lng: 127.0 });
    expect(d).toBeGreaterThan(40);
    expect(d).toBeLessThan(60);
  });
});
