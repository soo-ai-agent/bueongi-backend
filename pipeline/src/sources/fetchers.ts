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
import type { AssetType, RawRecord, SourceId } from "../types.js";
import {
  fetchDataGoKrStandard,
  fetchOdcloud,
  fetchSeoulOpenApi,
  mapRowsToRaw,
  type KeyColumns,
} from "./liveApi.js";

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

// ---------------------------------------------------------------------------
// 라이브 원천 스펙. fixtures/data/raw 가 없을 때만 사용된다.
//
// ⚠️ uddi(odcloud 데이터셋 식별자) / service(서울 service명) 는 키 등록 후
//    실제값을 채워야 한다. 지금은 placeholder("") 라서 라이브 호출 시
//    liveApi 가 경고 후 [] 를 반환한다(안전 통과). catalogId/coordSystem/
//    keyColumns/type 매핑 로직 자체는 완성돼 있어 값만 채우면 동작한다.
//
// keyColumns 는 sources.yaml 의 정의와 1:1 로 맞춘다(논리필드→원천 컬럼명).
// ---------------------------------------------------------------------------

type OdcloudLiveSpec = {
  kind: "odcloud";
  catalogId: string;
  /** TODO(키 등록 후): odcloud 콘솔에서 데이터셋 uddi 확정 후 채울 것. */
  uddi: string;
  type: AssetType;
  coordSystem: RawRecord["coordSystem"];
  keyColumns: KeyColumns;
};

type SeoulLiveSpec = {
  kind: "seoul";
  /** TODO(키 등록 후): 서울 열린데이터광장 service명 확정 후 채울 것. */
  service: string;
  type: AssetType;
  coordSystem: RawRecord["coordSystem"];
  keyColumns: KeyColumns;
};

/** data.go.kr 표준 조회서비스(REST). ctx.dataGoKrKey(Encoding 키) 사용. */
type DataGoKrLiveSpec = {
  kind: "datagokr";
  /** 전체 엔드포인트 URL(예: https://api.data.go.kr/openapi/tn_pubr_public_cctv_api). */
  endpoint: string;
  type: AssetType;
  coordSystem: RawRecord["coordSystem"];
  keyColumns: KeyColumns;
};

type LiveSpec = OdcloudLiveSpec | SeoulLiveSpec | DataGoKrLiveSpec;

/** source 별 라이브 호출 스펙(키 등록 후 uddi/service 채움). */
const LIVE_SPEC: Partial<Record<SourceId, LiveSpec>> = {
  // B-1: 전국 CCTV — 행정안전부_CCTV정보 조회서비스(apis.data.go.kr/1741000/cctv_info).
  //  엔드포인트 확정(데이터포털 명시값). ⚠️ keyColumns(원천 필드명)는 키 활성화 후 실응답으로 최종 확정.
  //  purpose(설치목적구분)/cameraCount(카메라대수)는 방범 필터·표시용 — 조회서비스 영문 필드명은
  //  키 활성화 후 실응답으로 확정 필요(미설정 시 purpose 결측 → filterSecurityCctv 가 fail-open 통과).
  "B-1": {
    kind: "datagokr",
    endpoint: "https://apis.data.go.kr/1741000/cctv_info",
    type: "cctv",
    coordSystem: "wgs84",
    keyColumns: {
      lat: "latitude",
      lng: "longitude",
      name: "institutionNm",
      address: "rdnmadr",
      sigungu: "",
      purpose: "", // TODO(키 활성 후): 설치목적구분 영문 필드명 확정
      cameraCount: "", // TODO(키 활성 후): 카메라대수 영문 필드명 확정
    },
  },
  // B-2: 여성안심지킴이집 (WGS84) — catalogId 15034535
  "B-2": {
    kind: "odcloud",
    catalogId: "15034535",
    uddi: "", // TODO(키 등록 후): 여성안심지킴이집 uddi
    type: "safehouse",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "업소명", address: "소재지도로명주소" },
  },
  // B-3: 지구대·파출소 (주소만 → 지오코딩 필요) — catalogId 15077036
  "B-3": {
    kind: "odcloud",
    catalogId: "15077036",
    uddi: "", // TODO(키 등록 후): 지구대·파출소 uddi
    type: "police",
    coordSystem: "address",
    keyColumns: { name: "관서명", address: "소재지도로명주소", phone: "전화번호" },
  },
  // B-4: 치안안전시설 (EPSG:5179) — catalogId 15101889
  "B-4": {
    kind: "odcloud",
    catalogId: "15101889",
    uddi: "", // TODO(키 등록 후): 치안안전시설 uddi
    type: "police",
    coordSystem: "epsg5179",
    keyColumns: { x: "X좌표", y: "Y좌표", name: "시설명", address: "주소" },
  },
  // C-1: 서울 가로등 (WGS84) — 서울 열린데이터광장 (catalogId OA-22205)
  "C-1": {
    kind: "seoul",
    service: "", // TODO(키 등록 후): 서울 가로등 service명 (예: SearchStreetLampService 류)
    type: "lamp",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "관리번호", address: "설치위치" },
  },
  // C-2: 전국 보안등 (WGS84) — catalogId 15017320
  "C-2": {
    kind: "odcloud",
    catalogId: "15017320",
    uddi: "", // TODO(키 등록 후): 보안등 표준데이터 uddi
    type: "lamp",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "관리번호", address: "소재지지번주소", sigungu: "시군구코드" },
  },
  // D-1: 전국 비상벨 (EPSG:5174) — catalogId 15028206
  // D-1: 전국 안전비상벨 — 행정안전부_안전비상벨위치정보 조회서비스(apis.data.go.kr/1741000/emergency_call_box_info).
  //  이 조회서비스는 lat/lon(WGS84)을 직접 제공하므로 좌표변환 불요(기존 odcloud EPSG5174 대비 단순).
  //  ⚠️ data.go.kr 키 활성화 후 동작(키 미활성 시 liveApi 가 안전 통과). 필드명은 조회서비스 스펙 기준.
  "D-1": {
    kind: "datagokr",
    endpoint: "https://apis.data.go.kr/1741000/emergency_call_box_info",
    type: "emergency_bell",
    coordSystem: "wgs84",
    keyColumns: { lat: "lat", lng: "lon", name: "mng_inst", address: "rn_adres", sigungu: "sgg_cd" },
  },
  // A-4 / D-2 는 per-district(자치구별 상이) → 라이브 대상 아님. manual 유지.
};

/**
 * LIVE_SPEC 기반 라이브 콜백 생성.
 * - odcloud 계열: ctx.dataGoKrKey, 서울 계열: ctx.seoulKey 사용.
 * - 키 없으면 liveApi 가 [] 반환(기존 안전 통과 유지).
 * - 원천 행 → keyColumns 매핑 → RawRecord[] 정규화.
 */
function liveFor(id: SourceId): ((ctx: FetchContext) => Promise<RawRecord[]>) | undefined {
  const spec = LIVE_SPEC[id];
  if (!spec) return undefined;
  return async (ctx: FetchContext): Promise<RawRecord[]> => {
    const rows =
      spec.kind === "odcloud"
        ? await fetchOdcloud({ catalogId: spec.catalogId, uddi: spec.uddi, serviceKey: ctx.dataGoKrKey })
        : spec.kind === "datagokr"
          ? await fetchDataGoKrStandard({ endpoint: spec.endpoint, serviceKey: ctx.dataGoKrKey })
          : await fetchSeoulOpenApi({ service: spec.service, serviceKey: ctx.seoulKey });
    return mapRowsToRaw(rows, {
      source: id,
      type: spec.type,
      coordSystem: spec.coordSystem,
      keyColumns: spec.keyColumns,
    });
  };
}

// B-1: 전국 CCTV 표준 (WGS84) — catalogId 15013094
export const fetchB1 = (ctx: FetchContext) => collect("B-1", ctx, liveFor("B-1"));
// B-2: 여성안심지킴이집 (WGS84) — catalogId 15034535
export const fetchB2 = (ctx: FetchContext) => collect("B-2", ctx, liveFor("B-2"));
// B-3: 지구대·파출소 (주소만 → 지오코딩 필요) — catalogId 15077036
export const fetchB3 = (ctx: FetchContext) => collect("B-3", ctx, liveFor("B-3"));
// B-4: 치안안전시설 (EPSG:5179) — catalogId 15101889
export const fetchB4 = (ctx: FetchContext) => collect("B-4", ctx, liveFor("B-4"));
// C-1: 서울 가로등 (WGS84) — OA-22205 (서울 열린데이터광장)
export const fetchC1 = (ctx: FetchContext) => collect("C-1", ctx, liveFor("C-1"));
// C-2: 전국 보안등 (WGS84) — catalogId 15017320
export const fetchC2 = (ctx: FetchContext) => collect("C-2", ctx, liveFor("C-2"));
// D-1: 전국 비상벨 (EPSG:5174) — catalogId 15028206
export const fetchD1 = (ctx: FetchContext) => collect("D-1", ctx, liveFor("D-1"));
// D-2: 자치구 비상벨 보완 (자치구별 상이) — per-district, manual 유지
export const fetchD2 = (ctx: FetchContext) => collect("D-2", ctx);
// A-4: 자치구 안심귀갓길 (자치구별 상이) — per-district, manual 유지
export const fetchA4 = (ctx: FetchContext) => collect("A-4", ctx);
