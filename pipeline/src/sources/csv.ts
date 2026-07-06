// 오프라인 CSV 입력 로더 (data/csv/{sourceId}.csv).
//
// 공공데이터포털 표준데이터는 CSV 내려받기를 제공한다 — 운영자가 받은 파일을
// data/csv/ 에 그대로 두면(파일명 = 원천 id) 파이프라인이 최우선 입력으로 쓴다.
// 라이브 API 키가 없어도 실데이터로 cdn/ 을 채울 수 있는 "CSV 기본, API 폴백" 경로.
//
// 인코딩: 공공데이터 CSV 는 CP949(EUC-KR)와 UTF-8(BOM 포함/미포함)이 섞여 있다.
// UTF-8 로 엄격 디코딩을 시도하고 실패하면 EUC-KR 로 재해석한다(자동 판별).
// 파서: 따옴표 필드("a,b" / "" 이스케이프)·CRLF·빈 줄을 처리하는 표준 CSV 규칙.

import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

/** UTF-8(BOM 허용) 우선, 깨지면 EUC-KR(CP949) 로 디코딩한다. */
export function decodeCsvBuffer(buf: Uint8Array): string {
  // UTF-8 BOM 제거
  const body = buf.length >= 3 && buf[0] === 0xef && buf[1] === 0xbb && buf[2] === 0xbf ? buf.subarray(3) : buf;
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(body);
  } catch {
    // Node 22(full-ICU)의 TextDecoder 는 euc-kr(=CP949 계열) 라벨을 지원한다.
    return new TextDecoder("euc-kr").decode(body);
  }
}

/**
 * CSV 본문 → 행 배열(string[][]). RFC 4180 규칙:
 * 따옴표 필드, "" 이스케이프, 필드 내 개행/쉼표, CRLF/LF 모두 처리.
 */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let inQuotes = false;
  let i = 0;

  const pushField = () => {
    row.push(field);
    field = "";
  };
  const pushRow = () => {
    pushField();
    // 완전히 빈 줄(필드 1개·빈 문자열)은 버린다.
    if (!(row.length === 1 && row[0] === "")) rows.push(row);
    row = [];
  };

  while (i < text.length) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        inQuotes = false;
        i += 1;
        continue;
      }
      field += ch;
      i += 1;
      continue;
    }
    if (ch === '"') {
      inQuotes = true;
      i += 1;
      continue;
    }
    if (ch === ",") {
      pushField();
      i += 1;
      continue;
    }
    if (ch === "\r") {
      if (text[i + 1] === "\n") i += 1;
      pushRow();
      i += 1;
      continue;
    }
    if (ch === "\n") {
      pushRow();
      i += 1;
      continue;
    }
    field += ch;
    i += 1;
  }
  // 마지막 줄(개행 없이 끝나는 경우)
  if (field !== "" || row.length > 0) pushRow();
  return rows;
}

/** 첫 행을 헤더로 삼아 각 행을 {헤더: 값} 객체로 만든다(값은 trim). */
export function csvToObjects(text: string): Array<Record<string, string>> {
  const rows = parseCsv(text);
  const headerRow = rows[0];
  if (!headerRow || rows.length < 2) return [];
  const header = headerRow.map((h) => h.trim());
  return rows.slice(1).map((cells) => {
    const obj: Record<string, string> = {};
    header.forEach((h, idx) => {
      if (!h) return;
      obj[h] = (cells[idx] ?? "").trim();
    });
    return obj;
  });
}

/**
 * data/csv/{id}.csv 를 읽어 헤더 매핑된 행 배열로 반환. 파일이 없으면 null.
 * (컬럼→RawRecord 매핑은 호출부가 sources.yaml 과 동일한 keyColumns 로 수행)
 */
export function loadCsvFile(id: string, csvDir: string): Array<Record<string, string>> | null {
  const p = join(csvDir, `${id}.csv`);
  if (!existsSync(p)) return null;
  const rows = csvToObjects(decodeCsvBuffer(readFileSync(p)));
  if (rows.length === 0) {
    console.warn(`[csv] ${p} 가 비어 있거나 헤더만 있습니다 → 무시(다음 입력 소스로 폴백)`);
    return null;
  }
  return rows;
}
