// manifest.json + 컬렉션 파일 직렬화/집계.

import { createHash } from "node:crypto";
import type { AssetType, CdnItem, SourceId } from "./types.js";

export interface SourceStat {
  input: number;
  output: number;
  coordFailed: number;
  outOfRange: number;
  deduped: number;
  quarantined: number;
}

export interface CollectionFile {
  path: string;
  type: AssetType;
  scope: string;
  sigunguCode?: string;
  json: string;
  count: number;
}

/** 표준 컬렉션 파일(JSON 문자열) 생성. */
export function buildCollection(
  type: AssetType,
  scope: string,
  items: CdnItem[],
  sources: SourceId[],
  generatedAt: string,
  sigunguCode?: string,
): CollectionFile {
  const body = {
    schemaVersion: 1 as const,
    type,
    scope,
    generatedAt,
    sources,
    count: items.length,
    items,
  };
  const json = JSON.stringify(body, null, 0);
  const path = sigunguCode ? `${type}/${sigunguCode}.json` : `${type}/${scope}.json`;
  return { path, type, scope, sigunguCode, json, count: items.length };
}

export function buildManifest(
  version: string,
  generatedAt: string,
  baseUrl: string,
  files: CollectionFile[],
  bySource: Record<string, SourceStat>,
) {
  return {
    schemaVersion: 1 as const,
    version,
    generatedAt,
    baseUrl,
    files: files.map((f) => ({
      path: f.path,
      type: f.type,
      scope: f.scope,
      ...(f.sigunguCode ? { sigunguCode: f.sigunguCode } : {}),
      count: f.count,
      bytes: Buffer.byteLength(f.json, "utf8"),
      sha256: createHash("sha256").update(f.json).digest("hex"),
    })),
    stats: { bySource },
  };
}
