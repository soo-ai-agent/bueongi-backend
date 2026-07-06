import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { csvToObjects, decodeCsvBuffer, parseCsv } from "./csv.js";
import { fetchB2, loadCsvRecords } from "./fetchers.js";

describe("parseCsv", () => {
  it("따옴표 필드·이스케이프·필드 내 쉼표/개행·CRLF 를 처리한다", () => {
    const text = '이름,주소\r\n"김, 상점","서울 ""강남"" 대로\n2층"\r\n단순,값\n';
    expect(parseCsv(text)).toEqual([
      ["이름", "주소"],
      ['김, 상점', '서울 "강남" 대로\n2층'],
      ["단순", "값"],
    ]);
  });

  it("빈 줄은 버리고 개행 없이 끝나는 마지막 행도 읽는다", () => {
    expect(parseCsv("a,b\n\n1,2")).toEqual([
      ["a", "b"],
      ["1", "2"],
    ]);
  });
});

describe("decodeCsvBuffer", () => {
  it("UTF-8 BOM 을 제거하고 디코딩한다", () => {
    const utf8 = new TextEncoder().encode("이름\n값");
    const withBom = new Uint8Array([0xef, 0xbb, 0xbf, ...utf8]);
    expect(decodeCsvBuffer(withBom)).toBe("이름\n값");
  });

  it("UTF-8 이 아니면 EUC-KR(CP949) 로 재해석한다", () => {
    // '업소명,위도,경도\n행복편의점,37.5,127.01\n' 의 CP949 인코딩 바이트.
    const eucKr = new Uint8Array([
      0xbe, 0xf7, 0xbc, 0xd2, 0xb8, 0xed, 0x2c, 0xc0, 0xa7, 0xb5, 0xb5, 0x2c, 0xb0, 0xe6, 0xb5, 0xb5, 0x0a,
      0xc7, 0xe0, 0xba, 0xb9, 0xc6, 0xed, 0xc0, 0xc7, 0xc1, 0xa1, 0x2c, 0x33, 0x37, 0x2e, 0x35, 0x2c, 0x31,
      0x32, 0x37, 0x2e, 0x30, 0x31, 0x0a,
    ]);
    expect(decodeCsvBuffer(eucKr)).toBe("업소명,위도,경도\n행복편의점,37.5,127.01\n");
  });
});

describe("csvToObjects", () => {
  it("첫 행을 헤더로 삼아 객체 배열을 만든다(값 trim)", () => {
    expect(csvToObjects("이름, 위도\n상점 , 37.5\n")).toEqual([{ 이름: "상점", 위도: "37.5" }]);
  });

  it("헤더만 있으면 빈 배열", () => {
    expect(csvToObjects("이름,위도\n")).toEqual([]);
  });
});

describe("loadCsvRecords / fetcher 우선순위", () => {
  const dirs: string[] = [];
  const mkdir = () => {
    const d = mkdtempSync(join(tmpdir(), "bueongi-csv-"));
    dirs.push(d);
    return d;
  };
  afterEach(() => {
    for (const d of dirs.splice(0)) rmSync(d, { recursive: true, force: true });
  });

  it("B-2 CSV 를 sources.yaml 컬럼 계약(RawRecord)으로 매핑한다", () => {
    const csvDir = mkdir();
    writeFileSync(
      join(csvDir, "B-2.csv"),
      "업소명,소재지도로명주소,위도,경도\n행복편의점,서울 강남구 테헤란로 1,37.5,127.01\n",
      "utf8",
    );
    const records = loadCsvRecords("B-2", csvDir);
    expect(records).toHaveLength(1);
    expect(records![0]).toMatchObject({
      source: "B-2",
      type: "safehouse",
      coordSystem: "wgs84",
      name: "행복편의점",
      address: "서울 강남구 테헤란로 1",
      lat: 37.5,
      lng: 127.01,
    });
  });

  it("CSV 파일이 없으면 null → 다음 입력 소스로 폴백", () => {
    expect(loadCsvRecords("B-2", mkdir())).toBeNull();
  });

  it("fetcher 는 CSV 를 data/raw 보다 우선한다(CSV 기본, 폴백 순서 보장)", async () => {
    const csvDir = mkdir();
    const rawDir = mkdir();
    writeFileSync(join(csvDir, "B-2.csv"), "업소명,위도,경도\nCSV상점,37.51,127.02\n", "utf8");
    writeFileSync(
      join(rawDir, "B-2.json"),
      JSON.stringify([{ source: "B-2", type: "safehouse", coordSystem: "wgs84", name: "RAW상점", lat: 1, lng: 2 }]),
      "utf8",
    );
    const records = await fetchB2({ csvDir, rawDir });
    expect(records.map((r) => r.name)).toEqual(["CSV상점"]);
  });

  it("CSV 가 없으면 기존 data/raw 경로를 그대로 쓴다(회귀 없음)", async () => {
    const csvDir = mkdir();
    const rawDir = mkdir();
    writeFileSync(
      join(rawDir, "B-2.json"),
      JSON.stringify([{ source: "B-2", type: "safehouse", coordSystem: "wgs84", name: "RAW상점", lat: 1, lng: 2 }]),
      "utf8",
    );
    const records = await fetchB2({ csvDir, rawDir });
    expect(records.map((r) => r.name)).toEqual(["RAW상점"]);
  });
});
