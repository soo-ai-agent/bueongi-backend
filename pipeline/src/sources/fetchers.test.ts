import { describe, expect, it, vi } from "vitest";
import { fetchC2 } from "./fetchers.js";
import type { RawRecord } from "../types.js";

const EMPTY_DIR = "/nonexistent-pipeline-input";

function jsonResponse(body: unknown): Response {
  return { ok: true, status: 200, json: async () => body } as Response;
}

/** tn_pubr 표준데이터 응답 봉투(B-2 안심지킴이집과 동일 구조). */
function envelope(items: Array<Record<string, unknown>>): unknown {
  return { response: { header: { resultCode: "00" }, body: { items, totalCount: items.length } } };
}

describe("fetchC2 (전국 보안등 표준데이터)", () => {
  it("uddi 조회 없이 tn_pubr 엔드포인트를 직접 호출하고 영문 표준 컬럼을 매핑한다", async () => {
    const urls: string[] = [];
    const fetchMock = vi.fn(async (url: string) => {
      urls.push(url);
      return jsonResponse(
        envelope([
          {
            scrtyLmpLctnNm: "역삼동 골목 보안등",
            latitude: "37.5008",
            longitude: "127.0359",
            rdnmadr: "테헤란로 1",
            sggCd: "11680",
          },
        ]),
      );
    });
    vi.stubGlobal("fetch", fetchMock);

    const records: RawRecord[] = await fetchC2({
      dataGoKrKey: "encoded-key",
      rawDir: EMPTY_DIR,
      csvDir: EMPTY_DIR,
    });

    vi.unstubAllGlobals();
    expect(urls[0]).toContain("tn_pubr_public_scrty_lmp_api");
    expect(urls[0]).not.toContain("uddi");
    expect(records).toHaveLength(1);
    expect(records[0]).toMatchObject({
      source: "C-2",
      type: "lamp",
      coordSystem: "wgs84",
      name: "역삼동 골목 보안등",
      address: "테헤란로 1",
      lat: 37.5008,
      lng: 127.0359,
      sigunguCode: "11680",
    });
  });

  it("키가 없으면 호출 없이 빈 배열로 안전 통과한다", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const records: RawRecord[] = await fetchC2({ rawDir: EMPTY_DIR, csvDir: EMPTY_DIR });

    vi.unstubAllGlobals();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(records).toEqual([]);
  });
});
