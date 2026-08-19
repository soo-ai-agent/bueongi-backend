// 파이프라인 공통 타입. 표준 CDN 아이템과 중간 처리 구조.

export type AssetType =
  | "cctv"
  | "safehouse"
  | "police"
  | "lamp"
  | "security_lamp"
  | "emergency_bell"
  | "pedestrian_signal"
  | "safepath";

export type SourceId =
  | "A-4"
  | "B-1"
  | "B-2"
  | "B-3"
  | "B-4"
  | "C-1"
  | "C-2"
  | "C-3"
  | "D-1"
  | "D-2";

export type CoordOrigin =
  | "wgs84"
  | "geocode"
  | "epsg5179"
  | "epsg5174"
  | "per-district";

/** schema/cdn-item.schema.json 과 1:1 대응하는 표준 산출 아이템. */
export interface CdnItem {
  id: string;
  source: SourceId;
  type: AssetType;
  name?: string;
  address?: string;
  lat: number;
  lng: number;
  sigunguCode?: string;
  updatedAt: string;
  properties?: {
    phone?: string;
    mergedSources?: string[];
    aliases?: string[];
    coordOrigin?: CoordOrigin;
    [k: string]: unknown;
  };
}

/**
 * 정규화 직후 단계. 좌표는 아직 없을 수 있고(B-3 주소만), 좌표계 메타를 들고 다닌다.
 * 변환 단계에서 lat/lng 를 채우고 CdnItem 으로 승격한다.
 */
export interface RawRecord {
  source: SourceId;
  type: AssetType;
  name?: string;
  address?: string;
  phone?: string;
  /** 원천 좌표 (변환 전). wgs84 면 lat/lng, 투영좌표면 x/y. */
  lat?: number;
  lng?: number;
  x?: number;
  y?: number;
  coordSystem: "wgs84" | "address" | "epsg5179" | "epsg5174" | "unknown";
  sigunguCode?: string;
  /** CCTV(B-1) 설치목적구분(용도). 방범 필터 + 프론트 표시용. 다른 소스는 미사용. */
  purpose?: string;
  /** CCTV(B-1) 카메라 대수. 프론트 표시용. */
  cameraCount?: number;
  raw?: Record<string, unknown>;
}

/** 한 원천을 처리한 결과와 통계. manifest.stats.bySource 로 집계된다. */
export interface SourceResult {
  source: SourceId;
  items: CdnItem[];
  stats: {
    input: number;
    output: number;
    coordFailed: number;
    outOfRange: number;
    deduped: number;
    quarantined: number;
  };
  /** 격리/실패 상세 (report/*.json 로 기록). */
  quarantine: QuarantineRow[];
  coordFailures: CoordFailureRow[];
}

export interface QuarantineRow {
  source: SourceId;
  sigunguCode?: string;
  name?: string;
  reason: string;
}

export interface CoordFailureRow {
  source: SourceId;
  name?: string;
  address?: string;
  reason: string;
}
