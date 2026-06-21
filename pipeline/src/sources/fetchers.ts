// 원천 수집/정규화 계약.
//
// 각 fetcher 는 원천 → RawRecord[] 정규화만 책임진다.
// (좌표 변환/지오코딩/중복 제거는 상위 파이프라인 단계가 수행)
//
// 입력 우선순위:
//   1) ctx.fixtures[id]              테스트 주입(인메모리)
//   2) data/raw/{id}.json            오프라인 raw 데이터(RawRecord[]). 운영 수집 산출/샘플.
//   3) 라이브 공공데이터 API          키가 있을 때만. 미구현 시 빈 배열로 안전 통과.
//
// 라이브 API 연동(2단계가 비어 있을 때)은 sources.yaml 의 catalogId/keyColumns/endpoint
// 로 교체한다. 키/네트워크가 없으면 [] 를 반환해 파이프라인이 깨지지 않는다.

import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import type { RawRecord, SourceId } from "../types.js";

export interface FetchContext {
  dataGoKrKey?: string;
  seoulKey?: string;
  /** 테스트/오프라인용 고정 입력. 있으면 네트워크 대신 사용. */
  fixtures?: Partial<Record<string, RawRecord[]>>;
  /** raw 데이터 디렉터리 override (기본 pipeline/data/raw). */
  rawDir?: string;
}

const DEFAULT_RAW_DIR = join(import.meta.dirname, "..", "..", "data", "raw");

/** data/raw/{id}.json 을 읽어 RawRecord[] 로 반환. 없으면 null. */
export function loadRawFile(id: SourceId, rawDir = DEFAULT_RAW_DIR): RawRecord[] | null {
  const p = join(rawDir, `${id}.json`);
  if (!existsSync(p)) return null;
  const parsed = JSON.parse(readFileSync(p, "utf8"));
  if (!Array.isArray(parsed)) {
    throw new Error(`raw file ${p} 는 RawRecord[] 배열이어야 한다`);
  }
  return parsed as RawRecord[];
}

/**
 * 공통 수집 경로. fixtures → data/raw 파일 → 라이브 API(미구현) 순.
 * @param live 라이브 API 호출 구현(키가 있을 때만). 없으면 [] 반환.
 */
async function collect(
  id: SourceId,
  ctx: FetchContext,
  live?: (ctx: FetchContext) => Promise<RawRecord[]>,
): Promise<RawRecord[]> {
  if (ctx.fixtures?.[id]) return ctx.fixtures[id]!;
  const file = loadRawFile(id, ctx.rawDir ?? DEFAULT_RAW_DIR);
  if (file) return file;
  if (live) return live(ctx);
  return [];
}

// B-1: 전국 CCTV 표준 (WGS84) — catalogId 15013094
export const fetchB1 = (ctx: FetchContext) => collect("B-1", ctx);
// B-2: 여성안심지킴이집 (WGS84) — catalogId 15034535
export const fetchB2 = (ctx: FetchContext) => collect("B-2", ctx);
// B-3: 지구대·파출소 (주소만 → 지오코딩 필요) — catalogId 15077036
export const fetchB3 = (ctx: FetchContext) => collect("B-3", ctx);
// B-4: 치안안전시설 (EPSG:5179) — catalogId 15101889
export const fetchB4 = (ctx: FetchContext) => collect("B-4", ctx);
// C-1: 서울 가로등 (WGS84) — OA-22205
export const fetchC1 = (ctx: FetchContext) => collect("C-1", ctx);
// C-2: 전국 보안등 (WGS84) — catalogId 15017320
export const fetchC2 = (ctx: FetchContext) => collect("C-2", ctx);
// D-1: 전국 비상벨 (EPSG:5174) — catalogId 15028206
export const fetchD1 = (ctx: FetchContext) => collect("D-1", ctx);
// D-2: 자치구 비상벨 보완 (자치구별 상이)
export const fetchD2 = (ctx: FetchContext) => collect("D-2", ctx);
// A-4: 자치구 안심귀갓길 (자치구별 상이)
export const fetchA4 = (ctx: FetchContext) => collect("A-4", ctx);
