// 라이브 공공데이터 API 호출 헬퍼.
//
// 두 원천 계열을 지원한다:
//   1) data.go.kr 표준데이터(odcloud)  — fetchOdcloud
//   2) 서울 열린데이터광장(openapi)     — fetchSeoulOpenApi
//
// 공통 규약:
//   - 키/식별자(uddi·service명)가 없으면 호출하지 않고 [] 로 안전 통과.
//   - 네트워크/HTTP/파싱 에러도 [] 로 흡수해 파이프라인을 깨지 않는다.
//   - 전체 페이지를 순회해 모든 행을 모은다.
//   - keyColumns 매핑은 mapRowsToRaw 로 RawRecord[] 로 정규화한다.
//
// ⚠️ uddi(odcloud 데이터셋 식별자)와 service(서울 service명)는 키 등록 후
//    실제값이 확정된다. 호출부(fetchers.ts)는 placeholder 를 넘기며,
//    값이 비어 있으면 여기서 경고 후 [] 를 반환한다.

import type { RawRecord, SourceId, AssetType } from "../types.js";

/** 호출 타임아웃 (ms). 느린 원천에서도 파이프라인이 멈추지 않게 한다. */
const DEFAULT_TIMEOUT_MS = 15000;
/** 페이지 간 rate-limit 딜레이 (ms). 공공 API 부하/차단 회피. */
const DEFAULT_PAGE_DELAY_MS = 120;
/** 안전장치: 무한 순회 방지용 최대 페이지 수. */
const MAX_PAGES = 1000;

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * 타임아웃이 걸린 fetch. 실패 시 throw → 호출부에서 [] 로 흡수.
 */
async function fetchWithTimeout(
  url: string,
  fetchImpl: typeof fetch,
  timeoutMs: number,
  init?: RequestInit,
): Promise<Response> {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    return await fetchImpl(url, { ...init, signal: ctrl.signal });
  } finally {
    clearTimeout(t);
  }
}

// ---------------------------------------------------------------------------
// 1) data.go.kr 표준데이터 (odcloud)
// ---------------------------------------------------------------------------

export interface OdcloudOptions {
  /** sources.yaml 의 catalogId (예: "15013094"). */
  catalogId: string;
  /**
   * 데이터셋 uddi. 키 등록 후 odcloud 콘솔에서 확정.
   * 비어 있으면 호출 불가 → [] + 경고.
   */
  uddi?: string;
  /** data.go.kr 디코딩 인증키. 없으면 [] 로 안전 통과. */
  serviceKey?: string;
  /** 페이지당 건수 (odcloud 기본 한도 안). */
  perPage?: number;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
  pageDelayMs?: number;
}

interface OdcloudPage {
  data?: Array<Record<string, unknown>>;
  totalCount?: number;
  currentCount?: number;
  page?: number;
  perPage?: number;
}

/**
 * odcloud 표준데이터를 전체 페이지 순회로 수집한다.
 *
 * 엔드포인트:
 *   https://api.odcloud.kr/api/{catalogId}/v1/uddi:{uddi}
 *     ?serviceKey=&page=&perPage=&returnType=JSON
 * 응답: { data: [...], totalCount }
 *
 * @returns 원천 행 배열(Record). keyColumns 매핑은 mapRowsToRaw 가 담당.
 */
export async function fetchOdcloud(
  opts: OdcloudOptions,
): Promise<Array<Record<string, unknown>>> {
  const {
    catalogId,
    uddi,
    serviceKey,
    perPage = 1000,
    fetchImpl = fetch,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    pageDelayMs = DEFAULT_PAGE_DELAY_MS,
  } = opts;

  if (!serviceKey) {
    // 키 없음은 정상 흐름(오프라인). 조용히 빈 배열.
    return [];
  }
  if (!uddi) {
    // uddi 미확정 → 키 등록 후 채워야 함. 호출 불가.
    console.warn(`[liveApi] odcloud uddi 미설정(catalogId=${catalogId}). 키 등록 후 uddi 확정 필요 → []`);
    return [];
  }

  const rows: Array<Record<string, unknown>> = [];
  let page = 1;
  let totalCount = Infinity;

  while (page <= MAX_PAGES && rows.length < totalCount) {
    const url =
      `https://api.odcloud.kr/api/${encodeURIComponent(catalogId)}/v1/uddi:${encodeURIComponent(uddi)}` +
      `?serviceKey=${encodeURIComponent(serviceKey)}&page=${page}&perPage=${perPage}&returnType=JSON`;

    let body: OdcloudPage;
    try {
      const res = await fetchWithTimeout(url, fetchImpl, timeoutMs);
      if (!res.ok) {
        console.warn(`[liveApi] odcloud http ${res.status} (catalogId=${catalogId}, page=${page}) → 중단, 부분수집 반환`);
        break;
      }
      body = (await res.json()) as OdcloudPage;
    } catch (e) {
      console.warn(`[liveApi] odcloud 호출 실패 (catalogId=${catalogId}, page=${page}): ${(e as Error).message} → 중단`);
      break;
    }

    const data = Array.isArray(body.data) ? body.data : [];
    if (typeof body.totalCount === "number" && Number.isFinite(body.totalCount)) {
      totalCount = body.totalCount;
    }
    rows.push(...data);

    // 더 받을 게 없으면(빈 페이지 또는 perPage 미만) 종료.
    if (data.length === 0 || data.length < perPage) break;
    page += 1;
    if (pageDelayMs > 0) await sleep(pageDelayMs);
  }

  return rows;
}

// ---------------------------------------------------------------------------
// 2) 서울 열린데이터광장 (openapi)
// ---------------------------------------------------------------------------

export interface SeoulOpenApiOptions {
  /** 서울 열린데이터광장 인증키. 없으면 [] 로 안전 통과. */
  serviceKey?: string;
  /**
   * service 명(예: 가로등 정보 서비스명). 키 등록 후 확정.
   * 비어 있으면 호출 불가 → [] + 경고.
   */
  service?: string;
  /** 페이지당 건수(서울 API 단건 호출 최대 1000). */
  pageSize?: number;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
  pageDelayMs?: number;
}

interface SeoulServiceBlock {
  list_total_count?: number;
  row?: Array<Record<string, unknown>>;
  RESULT?: { CODE?: string; MESSAGE?: string };
}

/**
 * 서울 열린데이터광장 서비스를 1000건 단위로 순회 수집한다.
 *
 * 엔드포인트:
 *   http://openapi.seoul.go.kr:8088/{serviceKey}/json/{service}/{start}/{end}/
 * 응답: { {service}: { row: [...], list_total_count } }
 *
 * @returns 원천 행 배열(Record). keyColumns 매핑은 mapRowsToRaw 가 담당.
 */
export async function fetchSeoulOpenApi(
  opts: SeoulOpenApiOptions,
): Promise<Array<Record<string, unknown>>> {
  const {
    serviceKey,
    service,
    pageSize = 1000,
    fetchImpl = fetch,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    pageDelayMs = DEFAULT_PAGE_DELAY_MS,
  } = opts;

  if (!serviceKey) return [];
  if (!service) {
    console.warn(`[liveApi] 서울 openapi service명 미설정. 키 등록 후 service명 확정 필요 → []`);
    return [];
  }

  const rows: Array<Record<string, unknown>> = [];
  let start = 1;
  let totalCount = Infinity;

  for (let i = 0; i < MAX_PAGES && rows.length < totalCount; i++) {
    const end = start + pageSize - 1;
    const url =
      `http://openapi.seoul.go.kr:8088/${encodeURIComponent(serviceKey)}/json/` +
      `${encodeURIComponent(service)}/${start}/${end}/`;

    let body: Record<string, unknown>;
    try {
      const res = await fetchWithTimeout(url, fetchImpl, timeoutMs);
      if (!res.ok) {
        console.warn(`[liveApi] 서울 openapi http ${res.status} (service=${service}, start=${start}) → 중단`);
        break;
      }
      body = (await res.json()) as Record<string, unknown>;
    } catch (e) {
      console.warn(`[liveApi] 서울 openapi 호출 실패 (service=${service}, start=${start}): ${(e as Error).message} → 중단`);
      break;
    }

    const block = body[service] as SeoulServiceBlock | undefined;
    if (!block) {
      // RESULT 만 있고 데이터가 없는 경우(권한/오타 등) → 경고 후 중단.
      console.warn(`[liveApi] 서울 openapi 응답에 '${service}' 블록 없음 → 중단`);
      break;
    }
    const data = Array.isArray(block.row) ? block.row : [];
    if (typeof block.list_total_count === "number" && Number.isFinite(block.list_total_count)) {
      totalCount = block.list_total_count;
    }
    rows.push(...data);

    if (data.length === 0 || data.length < pageSize) break;
    start += pageSize;
    if (pageDelayMs > 0) await sleep(pageDelayMs);
  }

  return rows;
}

// ---------------------------------------------------------------------------
// 2-b) data.go.kr 표준 조회서비스 (REST, apis.data.go.kr/openapi/tn_pubr_*)
// ---------------------------------------------------------------------------

export interface DataGoKrStandardOptions {
  /** 전체 엔드포인트 URL (예: https://api.data.go.kr/openapi/tn_pubr_public_cctv_api). */
  endpoint: string;
  /**
   * data.go.kr 인증키 — **Encoding(URL-인코딩된) 형태**를 그대로 받는다.
   * 이미 인코딩돼 있으므로 URL 조립 시 재인코딩하지 않는다(이중 인코딩 방지).
   * 없으면 [] 로 안전 통과(오프라인).
   */
  serviceKey?: string;
  /** 페이지당 건수(표준 조회서비스 보통 최대 1000). */
  numOfRows?: number;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
  pageDelayMs?: number;
}

interface DataGoKrBody {
  items?: Array<Record<string, unknown>> | { item?: Array<Record<string, unknown>> | Record<string, unknown> };
  totalCount?: number;
  pageNo?: number;
  numOfRows?: number;
}

interface DataGoKrEnvelope {
  response?: {
    header?: { resultCode?: string; resultMsg?: string };
    body?: DataGoKrBody;
  };
}

/** 응답 body.items 를 행 배열로 정규화(items 가 배열이거나 {item:[]} 형태 모두 수용). */
function extractDataGoKrItems(body: DataGoKrBody | undefined): Array<Record<string, unknown>> {
  if (!body) return [];
  const items = body.items;
  if (Array.isArray(items)) return items;
  if (items && typeof items === "object" && "item" in items) {
    const inner = (items as { item?: unknown }).item;
    if (Array.isArray(inner)) return inner as Array<Record<string, unknown>>;
    if (inner && typeof inner === "object") return [inner as Record<string, unknown>];
  }
  return [];
}

/**
 * data.go.kr 표준 조회서비스(REST)를 pageNo 순회로 전국 수집한다.
 *
 * 엔드포인트: {endpoint}?serviceKey=&pageNo=&numOfRows=&type=json
 * 응답: { response: { header:{resultCode}, body:{ items:[...], totalCount } } }
 *  - resultCode "00" 정상. 그 외(예: "30" 키 미등록)는 경고 후 부분/빈 수집 반환.
 *
 * serviceKey 는 이미 URL-인코딩된 값이므로 재인코딩하지 않는다(이중 인코딩 시 키 거부됨).
 * @returns 원천 행 배열(Record). keyColumns 매핑은 mapRowsToRaw 가 담당.
 */
export async function fetchDataGoKrStandard(
  opts: DataGoKrStandardOptions,
): Promise<Array<Record<string, unknown>>> {
  const {
    endpoint,
    serviceKey,
    numOfRows = 1000,
    fetchImpl = fetch,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    pageDelayMs = DEFAULT_PAGE_DELAY_MS,
  } = opts;

  if (!serviceKey) return [];

  const rows: Array<Record<string, unknown>> = [];
  let pageNo = 1;
  let totalCount = Infinity;

  while (pageNo <= MAX_PAGES && rows.length < totalCount) {
    // serviceKey 는 인코딩된 그대로 사용(재인코딩 금지). 나머지 파라미터만 평범히 조립.
    const url =
      `${endpoint}?serviceKey=${serviceKey}` +
      `&pageNo=${pageNo}&numOfRows=${numOfRows}&type=json`;

    let body: DataGoKrEnvelope;
    try {
      const res = await fetchWithTimeout(url, fetchImpl, timeoutMs);
      if (!res.ok) {
        console.warn(`[liveApi] data.go.kr http ${res.status} (${endpoint}, page=${pageNo}) → 중단, 부분수집 반환`);
        break;
      }
      body = (await res.json()) as DataGoKrEnvelope;
    } catch (e) {
      console.warn(`[liveApi] data.go.kr 호출 실패 (${endpoint}, page=${pageNo}): ${(e as Error).message} → 중단`);
      break;
    }

    const code = body.response?.header?.resultCode;
    if (code && code !== "00") {
      console.warn(`[liveApi] data.go.kr resultCode=${code} (${body.response?.header?.resultMsg ?? ""}) → 중단`);
      break;
    }

    const data = extractDataGoKrItems(body.response?.body);
    const tc = body.response?.body?.totalCount;
    if (typeof tc === "number" && Number.isFinite(tc)) totalCount = tc;
    rows.push(...data);

    if (data.length === 0 || data.length < numOfRows) break;
    pageNo += 1;
    if (pageDelayMs > 0) await sleep(pageDelayMs);
  }

  return rows;
}

// ---------------------------------------------------------------------------
// 3) keyColumns → RawRecord 매퍼
// ---------------------------------------------------------------------------

/**
 * sources.yaml 의 keyColumns(논리필드 → 원천 컬럼명) 매핑.
 * 예: { lat: "위도", lng: "경도", name: "관리기관명", ... }
 */
export interface KeyColumns {
  lat?: string;
  lng?: string;
  x?: string;
  y?: string;
  name?: string;
  address?: string;
  phone?: string;
  sigungu?: string;
  /** CCTV(B-1) 설치목적구분(용도) 원천 컬럼명. 방범 필터·표시용. */
  purpose?: string;
  /** CCTV(B-1) 카메라 대수 원천 컬럼명. 표시용. */
  cameraCount?: string;
}

export interface MapOptions {
  source: SourceId;
  type: AssetType;
  coordSystem: RawRecord["coordSystem"];
  keyColumns: KeyColumns;
}

/** 원천 행에서 컬럼값을 문자열로 안전 추출(공백 trim, 빈값은 undefined). */
function pickStr(row: Record<string, unknown>, col?: string): string | undefined {
  if (!col) return undefined;
  const v = row[col];
  if (v == null) return undefined;
  const s = String(v).trim();
  return s === "" ? undefined : s;
}

/** 컬럼값을 숫자로 안전 변환. 비숫자/빈값은 undefined. */
function pickNum(row: Record<string, unknown>, col?: string): number | undefined {
  const s = pickStr(row, col);
  if (s === undefined) return undefined;
  const n = Number(s);
  return Number.isFinite(n) ? n : undefined;
}

/**
 * 원천 행 1건을 RawRecord 로 변환.
 * - coordSystem 에 따라 lat/lng(wgs84) 또는 x/y(투영) 를 채운다.
 * - 좌표/주소가 결측이어도 raw 에 원본을 보존하고 안전 통과(상위에서 집계).
 */
export function mapRowToRaw(
  row: Record<string, unknown>,
  opts: MapOptions,
): RawRecord {
  const { source, type, coordSystem, keyColumns: k } = opts;

  const rec: RawRecord = {
    source,
    type,
    coordSystem,
    name: pickStr(row, k.name),
    address: pickStr(row, k.address),
    phone: pickStr(row, k.phone),
    sigunguCode: pickStr(row, k.sigungu),
    purpose: pickStr(row, k.purpose),
    cameraCount: pickNum(row, k.cameraCount),
    raw: row,
  };

  if (coordSystem === "wgs84") {
    rec.lat = pickNum(row, k.lat);
    rec.lng = pickNum(row, k.lng);
  } else if (coordSystem === "epsg5179" || coordSystem === "epsg5174") {
    rec.x = pickNum(row, k.x);
    rec.y = pickNum(row, k.y);
  }
  // coordSystem === "address" 면 좌표 없이 주소만(상위에서 지오코딩).

  return rec;
}

/** 원천 행 배열 전체를 RawRecord[] 로 매핑. */
export function mapRowsToRaw(
  rows: Array<Record<string, unknown>>,
  opts: MapOptions,
): RawRecord[] {
  return rows.map((r) => mapRowToRaw(r, opts));
}
