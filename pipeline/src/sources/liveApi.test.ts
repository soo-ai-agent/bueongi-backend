// liveApi 단위테스트 — 실제 네트워크 없이 global fetch 를 mock 한다.
// 검증: odcloud 다중페이지 순회 · 서울 페이지네이션 · keyColumns 매핑
//       · 키/식별자 없을 때 [] · 에러 시 [].

import { describe, expect, it, vi } from "vitest";
import {
  fetchOdcloud,
  fetchSeoulOpenApi,
  mapRowsToRaw,
  type KeyColumns,
} from "./liveApi.js";
import type { RawRecord } from "../types.js";

/** Response 흉내 — ok/json 만 제공. */
function jsonRes(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: async () => body,
  } as unknown as Response;
}

const noFastDelay = { pageDelayMs: 0 };

describe("fetchOdcloud", () => {
  it("totalCount 기준으로 전체 페이지를 순회한다", async () => {
    // perPage=2, totalCount=3 → page1(2건) + page2(1건)
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonRes({ data: [{ n: 1 }, { n: 2 }], totalCount: 3 }))
      .mockResolvedValueOnce(jsonRes({ data: [{ n: 3 }], totalCount: 3 }));

    const rows = await fetchOdcloud({
      catalogId: "15013094",
      uddi: "uddi:test",
      serviceKey: "KEY",
      perPage: 2,
      fetchImpl: fetchImpl as unknown as typeof fetch,
      ...noFastDelay,
    });

    expect(rows).toHaveLength(3);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    // page 파라미터가 증가하는지 확인
    expect((fetchImpl.mock.calls[0]![0] as string)).toContain("page=1");
    expect((fetchImpl.mock.calls[1]![0] as string)).toContain("page=2");
    // 마지막 페이지가 perPage 미만이라 3페이지째 호출 안 함
  });

  it("페이지가 perPage 미만이면 거기서 멈춘다", async () => {
    const fetchImpl = vi.fn().mockResolvedValueOnce(jsonRes({ data: [{ n: 1 }], totalCount: 999 }));
    const rows = await fetchOdcloud({
      catalogId: "x",
      uddi: "uddi:y",
      serviceKey: "KEY",
      perPage: 1000,
      fetchImpl: fetchImpl as unknown as typeof fetch,
      ...noFastDelay,
    });
    expect(rows).toHaveLength(1);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it("serviceKey 가 없으면 호출 없이 []", async () => {
    const fetchImpl = vi.fn();
    const rows = await fetchOdcloud({
      catalogId: "x",
      uddi: "uddi:y",
      serviceKey: undefined,
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });
    expect(rows).toEqual([]);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("uddi 가 없으면(키 미확정) 호출 없이 []", async () => {
    const fetchImpl = vi.fn();
    const rows = await fetchOdcloud({
      catalogId: "x",
      uddi: "",
      serviceKey: "KEY",
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });
    expect(rows).toEqual([]);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("네트워크 에러는 [](부분수집)으로 안전 통과한다", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("ECONNRESET"));
    const rows = await fetchOdcloud({
      catalogId: "x",
      uddi: "uddi:y",
      serviceKey: "KEY",
      fetchImpl: fetchImpl as unknown as typeof fetch,
      ...noFastDelay,
    });
    expect(rows).toEqual([]);
  });

  it("HTTP 비정상(500)은 그때까지 모은 부분수집을 반환한다", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonRes({ data: [{ n: 1 }, { n: 2 }], totalCount: 10 }))
      .mockResolvedValueOnce(jsonRes({}, false, 500));
    const rows = await fetchOdcloud({
      catalogId: "x",
      uddi: "uddi:y",
      serviceKey: "KEY",
      perPage: 2,
      fetchImpl: fetchImpl as unknown as typeof fetch,
      ...noFastDelay,
    });
    expect(rows).toHaveLength(2); // page1 만 수집, page2 500 → 중단
  });
});

describe("fetchSeoulOpenApi", () => {
  it("list_total_count 기준으로 1000건 단위 페이지네이션한다", async () => {
    const service = "TestLampService";
    const page1 = Array.from({ length: 1000 }, (_, i) => ({ n: i }));
    const page2 = [{ n: 1000 }];
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonRes({ [service]: { row: page1, list_total_count: 1001 } }))
      .mockResolvedValueOnce(jsonRes({ [service]: { row: page2, list_total_count: 1001 } }));

    const rows = await fetchSeoulOpenApi({
      serviceKey: "KEY",
      service,
      pageSize: 1000,
      fetchImpl: fetchImpl as unknown as typeof fetch,
      ...noFastDelay,
    });

    expect(rows).toHaveLength(1001);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    expect((fetchImpl.mock.calls[0]![0] as string)).toContain("/1/1000/");
    expect((fetchImpl.mock.calls[1]![0] as string)).toContain("/1001/2000/");
  });

  it("serviceKey 없으면 호출 없이 []", async () => {
    const fetchImpl = vi.fn();
    const rows = await fetchSeoulOpenApi({
      serviceKey: undefined,
      service: "X",
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });
    expect(rows).toEqual([]);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("service명 미확정(빈값)이면 호출 없이 []", async () => {
    const fetchImpl = vi.fn();
    const rows = await fetchSeoulOpenApi({
      serviceKey: "KEY",
      service: "",
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });
    expect(rows).toEqual([]);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("에러는 [] 로 안전 통과한다", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("timeout"));
    const rows = await fetchSeoulOpenApi({
      serviceKey: "KEY",
      service: "X",
      fetchImpl: fetchImpl as unknown as typeof fetch,
      ...noFastDelay,
    });
    expect(rows).toEqual([]);
  });
});

describe("mapRowsToRaw (keyColumns → RawRecord)", () => {
  it("WGS84 원천의 위경도/이름/주소/시군구를 매핑한다", () => {
    const cols: KeyColumns = {
      lat: "위도",
      lng: "경도",
      name: "관리기관명",
      address: "소재지지번주소",
      sigungu: "시군구코드",
    };
    const rows = [
      { 위도: "37.5012", 경도: "127.0361", 관리기관명: "강남구", 소재지지번주소: "역삼동", 시군구코드: "11680" },
    ];
    const recs = mapRowsToRaw(rows, { source: "B-1", type: "cctv", coordSystem: "wgs84", keyColumns: cols });
    expect(recs).toHaveLength(1);
    const r = recs[0]!;
    expect(r.lat).toBeCloseTo(37.5012);
    expect(r.lng).toBeCloseTo(127.0361);
    expect(r.name).toBe("강남구");
    expect(r.address).toBe("역삼동");
    expect(r.sigunguCode).toBe("11680");
    expect(r.coordSystem).toBe("wgs84");
    expect(r.x).toBeUndefined();
    expect(r.raw).toEqual(rows[0]); // 원본 보존
  });

  it("투영좌표(epsg5179) 원천의 X/Y 좌표를 x/y 로 매핑한다", () => {
    const cols: KeyColumns = { x: "X좌표", y: "Y좌표", name: "시설명", address: "주소" };
    const rows = [{ X좌표: "961900.5", Y좌표: "1951200.2", 시설명: "안심귀갓길1", 주소: "강남구" }];
    const recs = mapRowsToRaw(rows, { source: "A-4", type: "safepath", coordSystem: "epsg5179", keyColumns: cols });
    const r = recs[0]!;
    expect(r.x).toBeCloseTo(961900.5);
    expect(r.y).toBeCloseTo(1951200.2);
    expect(r.lat).toBeUndefined();
    expect(r.lng).toBeUndefined();
    expect(r.name).toBe("안심귀갓길1");
  });

  it("좌표/필드 결측 행도 raw 보존하며 안전 통과한다", () => {
    const cols: KeyColumns = { lat: "위도", lng: "경도", name: "관리기관명" };
    const rows = [{ 관리기관명: "결측소" }]; // 위경도 없음
    const recs = mapRowsToRaw(rows, { source: "B-1", type: "cctv", coordSystem: "wgs84", keyColumns: cols });
    const r: RawRecord = recs[0]!;
    expect(r.lat).toBeUndefined();
    expect(r.lng).toBeUndefined();
    expect(r.name).toBe("결측소");
    expect(r.raw).toEqual(rows[0]);
  });

  it("비숫자 좌표값은 undefined 로 떨어진다", () => {
    const cols: KeyColumns = { lat: "위도", lng: "경도" };
    const rows = [{ 위도: "N/A", 경도: "" }];
    const recs = mapRowsToRaw(rows, { source: "C-2", type: "lamp", coordSystem: "wgs84", keyColumns: cols });
    expect(recs[0]!.lat).toBeUndefined();
    expect(recs[0]!.lng).toBeUndefined();
  });
});
