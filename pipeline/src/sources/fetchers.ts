// 원천 수집/정규화 계약.
//
// 각 fetcher 는 원천 → RawRecord[] 정규화만 책임진다.
// (좌표 변환/지오코딩/중복 제거는 상위 파이프라인 단계가 수행)
//
// 입력 우선순위 ("CSV 기본, API 폴백"):
//   1) ctx.fixtures[id]              테스트 주입(인메모리)
//   2) data/csv/{id}.csv             운영자가 내려받은 공공데이터 CSV(CP949/UTF-8 자동 판별).
//                                    sources.yaml 과 동일한 keyColumns 로 매핑한다.
//   3) data/raw/{id}.json            오프라인 raw 데이터(RawRecord[]). 운영 수집 산출/샘플.
//   4) 라이브 공공데이터 API          키가 있을 때만(월 1회 워크플로의 자동 수집 경로).
//                                    키/네트워크가 없으면 [] 로 안전 통과.

import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import type { AssetType, RawRecord, SourceId } from "../types.js";
import { loadCsvFile } from "./csv.js";
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
  /** CSV 데이터 디렉터리 override (기본 pipeline/data/csv). */
  csvDir?: string;
}

const DEFAULT_RAW_DIR = join(import.meta.dirname, "..", "..", "data", "raw");
const DEFAULT_CSV_DIR = join(import.meta.dirname, "..", "..", "data", "csv");

// CSV 입력 스펙 — sources.yaml 의 keyColumns(한글 원천 컬럼명)와 1:1.
// 공공데이터포털에서 내려받은 표준데이터 CSV 를 data/csv/{id}.csv 로 두면
// 이 스펙으로 RawRecord 매핑된다. A-4/D-2(자치구별 좌표계 상이·per-district)는
// CSV 일괄 매핑 대상이 아니다(manual 경로 유지).

type CsvSpec = {
  type: AssetType;
  coordSystem: RawRecord["coordSystem"];
  keyColumns: KeyColumns;
};

const CSV_SPEC: Partial<Record<SourceId, CsvSpec>> = {
  "B-1": {
    type: "cctv",
    coordSystem: "wgs84",
    keyColumns: {
      lat: "위도",
      lng: "경도",
      name: "관리기관명",
      address: "소재지지번주소",
      sigungu: "시군구코드",
      purpose: "설치목적구분",
      cameraCount: "카메라대수",
    },
  },
  "B-2": {
    type: "safehouse",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "업소명", address: "소재지도로명주소" },
  },
  "B-3": {
    type: "police",
    coordSystem: "address",
    keyColumns: { name: "관서명", address: "소재지도로명주소", phone: "전화번호" },
  },
  "B-4": {
    type: "police",
    coordSystem: "epsg5179",
    keyColumns: { x: "X좌표", y: "Y좌표", name: "시설명", address: "주소" },
  },
  "C-1": {
    type: "lamp",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "관리번호", address: "설치위치" },
  },
  "C-2": {
    type: "security_lamp",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "관리번호", address: "소재지지번주소", sigungu: "시군구코드" },
  },
  "C-3": {
    type: "pedestrian_signal",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "신호등 종류", address: "주소" },
  },
  "C-4": {
    type: "lamp",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "도로명", address: "소재지지번주소", sigungu: "제공기관코드" },
  },
  "D-1": {
    type: "emergency_bell",
    coordSystem: "epsg5174",
    keyColumns: { x: "X좌표", y: "Y좌표", name: "설치장소", address: "소재지지번주소" },
  },
};

/** data/csv/{id}.csv 가 있으면 sources.yaml 컬럼 계약으로 RawRecord[] 매핑. 없으면 null. */
export function loadCsvRecords(id: SourceId, csvDir = DEFAULT_CSV_DIR): RawRecord[] | null {
  const spec = CSV_SPEC[id];
  const rows = loadCsvFile(id, csvDir);
  if (!rows) return null;
  if (!spec) {
    console.warn(`[csv] ${id}.csv 발견 — 그러나 ${id} 는 CSV 일괄 매핑 대상이 아님(per-district). 무시.`);
    return null;
  }
  console.log(`[csv] ${id}: data/csv/${id}.csv ${rows.length}행 사용(최우선 입력)`);
  return mapRowsToRaw(rows, { source: id, type: spec.type, coordSystem: spec.coordSystem, keyColumns: spec.keyColumns });
}

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
 * 공통 수집 경로. fixtures → data/csv CSV(최우선 오프라인 실데이터) → data/raw 파일 → 라이브 API 순.
 * @param live 라이브 API 호출 구현(키가 있을 때만). 없으면 [] 반환.
 */
async function collect(
  id: SourceId,
  ctx: FetchContext,
  live?: (ctx: FetchContext) => Promise<RawRecord[]>,
): Promise<RawRecord[]> {
  if (ctx.fixtures?.[id]) return ctx.fixtures[id]!;
  const csv = loadCsvRecords(id, ctx.csvDir ?? DEFAULT_CSV_DIR);
  if (csv) return csv;
  const file = loadRawFile(id, ctx.rawDir ?? DEFAULT_RAW_DIR);
  if (file) return file;
  if (live) return live(ctx);
  return [];
}

// 라이브 원천 스펙. fixtures/data/raw 가 없을 때만 사용된다.
//
// ⚠️ uddi(odcloud 데이터셋 식별자) / service(서울 service명) 는 키 등록 후
//    실제값을 채워야 한다. 지금은 placeholder("") 라서 라이브 호출 시
//    liveApi 가 경고 후 [] 를 반환한다(안전 통과). catalogId/coordSystem/
//    keyColumns/type 매핑 로직 자체는 완성돼 있어 값만 채우면 동작한다.
//
// keyColumns 는 sources.yaml 의 정의와 1:1 로 맞춘다(논리필드→원천 컬럼명).

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
  // B-1: 전국 CCTV — 행정안전부_CCTV정보 조회서비스.
  "B-1": {
    kind: "datagokr",
    endpoint: "https://apis.data.go.kr/1741000/cctv_info/info",
    type: "cctv",
    coordSystem: "wgs84",
    keyColumns: {
      lat: "WGS84_LAT",
      lng: "WGS84_LOT",
      name: "MNG_INST_NM",
      address: "LCTN_LOTNO_ADDR",
      sigungu: "",
      purpose: "INSTL_PRPS_SE_NM",
      cameraCount: "CAM_CNTOM",
    },
  },
  // B-2: 여성안심지킴이집 (WGS84) — 표준데이터 조회서비스(활용신청 승인 2026-06-30, 일일 트래픽 1000).
  //  백엔드 라이브 갱신(client-datagokr)과 동일 원천.
  //  keyColumns 는 표준데이터 영문 규격 1순위(키 활성 후 실응답으로 확정; 백엔드 클라이언트는 한글 폴백도 가짐).
  "B-2": {
    kind: "datagokr",
    endpoint: "https://api.data.go.kr/openapi/tn_pubr_public_female_safety_prtchouse_api",
    type: "safehouse",
    coordSystem: "wgs84",
    keyColumns: { lat: "latitude", lng: "longitude", name: "storNm", address: "rdnmadr" },
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
  // C-1: 서울 가로등 (WGS84) — 서울 열린데이터광장 OA-22205.
  //  OA-22205 는 열린데이터광장에서 **파일로만** 열려 있어 openapi.seoul.go.kr service 명이 존재하지 않는다.
  //  라이브 경로는 공공데이터포털 미러(서울특별시_가로등 위치 정보, catalogId 15107934)의 파일데이터
  //  오픈API 자동변환(odcloud)을 쓴다. 자동변환분은 원본 파일의 한글 헤더를 그대로 돌려주므로
  //  keyColumns 도 CSV 입력(data/csv/C-1.csv)과 같다.
  "C-1": {
    kind: "odcloud",
    catalogId: "15107934",
    uddi: "", // TODO(포털 확인 후): 15107934 파일데이터 uddi. 그전까지는 data/csv/C-1.csv 가 운영 경로.
    type: "lamp",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "관리번호", address: "설치위치" },
  },
  // C-2: 전국 보안등 (WGS84) — 표준데이터 조회서비스(catalogId 15017320).
  //  백엔드 라이브 갱신(client-datagokr fetchSecurityLamps)과 동일 원천.
  //  B-2 와 같은 tn_pubr 계열이라 uddi 조회 없이 엔드포인트를 직접 부른다.
  "C-2": {
    kind: "datagokr",
    endpoint: "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api",
    type: "security_lamp",
    coordSystem: "wgs84",
    keyColumns: {
      lat: "latitude",
      lng: "longitude",
      name: "scrtyLmpLctnNm",
      address: "rdnmadr",
      sigungu: "sggCd",
    },
  },
  // C-3: 서울 보행등 (WGS84) — 열린데이터광장 OA-22354.
  //  이름이 '보행등'이지만 조명이 아니라 **보행신호등**이다(항목: 자치구·신호등 종류·위도·경도·주소).
  //  조명 밀도 점수에 섞이면 안 되므로 lamp 가 아닌 별도 자산으로 낸다.
  //  C-1 과 같이 열린데이터광장은 파일 전용이라 라이브는 포털 미러(15124273) odcloud 자동변환을 쓴다.
  "C-3": {
    kind: "odcloud",
    catalogId: "15124273",
    uddi: "", // TODO(포털 확인 후): 15124273 파일데이터 uddi. 그전까지는 data/csv/C-3.csv 가 운영 경로.
    type: "pedestrian_signal",
    coordSystem: "wgs84",
    keyColumns: { lat: "위도", lng: "경도", name: "신호등 종류", address: "주소" },
  },
  // D-1: 전국 안전비상벨 — 행정안전부_안전비상벨위치정보 조회서비스.
  "D-1": {
    kind: "datagokr",
    endpoint: "https://apis.data.go.kr/1741000/emergency_call_box_info/info",
    type: "emergency_bell",
    coordSystem: "wgs84",
    keyColumns: {
      lat: "WGS84_LAT",
      lng: "WGS84_LOT",
      name: "INSTL_PSTN",
      address: "LCTN_LOTNO_ADDR",
      sigungu: "",
    },
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
// C-3: 서울 보행등=보행신호등 (WGS84) — OA-22354 / 포털 미러 15124273
export const fetchC3 = (ctx: FetchContext) => collect("C-3", ctx, liveFor("C-3"));

// C-4: 전국 스마트가로등 표준데이터 (WGS84) — CSV 전용 투입(라이브 스펙 미배선)
export const fetchC4 = (ctx: FetchContext) => collect("C-4", ctx);
// D-1: 전국 비상벨 (EPSG:5174) — catalogId 15028206
export const fetchD1 = (ctx: FetchContext) => collect("D-1", ctx, liveFor("D-1"));
// D-2: 자치구 비상벨 보완 (자치구별 상이) — per-district, manual 유지
export const fetchD2 = (ctx: FetchContext) => collect("D-2", ctx);
// A-4: 자치구 안심귀갓길 (자치구별 상이) — per-district, manual 유지
export const fetchA4 = (ctx: FetchContext) => collect("A-4", ctx);
