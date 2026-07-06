#!/usr/bin/env python3
"""여성안심지킴이집 표준데이터 API → cdn/safehouse/all.json.

API: https://api.data.go.kr/openapi/tn_pubr_public_female_safety_prtchouse_api
필요: 환경변수 SAFEHOUSE_API_KEY (data.go.kr 디코딩 인증키, backend/.env.local).
주의: 키 승인 직후 ~1시간+ 동안 'SERVICE KEY IS NOT REGISTERED'(code 30) 가 날 수 있음 → 활성화 후 실행.
필드명은 표준데이터 응답 기준으로 유연 매핑하며, 실응답 확인 후 보정 가능.
실행(레포 루트): python3 backend/data/ingest/safehouse_fetch.py
"""
import json, os, urllib.parse, urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
OUT = os.path.join(ROOT, "cdn/safehouse/all.json")
ENDPOINT = "https://api.data.go.kr/openapi/tn_pubr_public_female_safety_prtchouse_api"
KEY = os.environ["SAFEHOUSE_API_KEY"]

# 표준데이터 응답 필드명 후보(실응답 확인 후 보정).
F_NAME = ["bsshNm", "fcltyNm", "instNm", "업소명", "시설명"]
F_LAT = ["latitude", "lat", "위도", "la"]
F_LNG = ["longitude", "lng", "lon", "경도", "lo"]
F_ADDR = ["rdnmadr", "lnmadr", "소재지도로명주소", "소재지지번주소", "address"]
F_TEL = ["telno", "phoneNumber", "전화번호", "tel"]


def pick(d, keys):
    for k in keys:
        v = d.get(k)
        if v not in (None, ""):
            return v
    return None


def fetch_page(page, rows=1000):
    q = urllib.parse.urlencode({"serviceKey": KEY, "pageNo": page, "numOfRows": rows, "type": "json"})
    with urllib.request.urlopen(f"{ENDPOINT}?{q}", timeout=30) as r:
        return json.load(r)


def main():
    items, page = [], 1
    while True:
        d = fetch_page(page)
        hdr = d.get("response", {}).get("header", {})
        if hdr.get("resultCode") not in ("00", "0", None):
            print(f"API 오류: {hdr.get('resultCode')} {hdr.get('resultMsg')} (키 활성화 전이면 잠시 후 재시도)")
            return
        body = d["response"]["body"]
        rows = body.get("items") or []
        if isinstance(rows, dict):
            rows = rows.get("item") or []
        if not rows:
            break
        for it in rows:
            try:
                lat = float(pick(it, F_LAT)); lng = float(pick(it, F_LNG))
            except (TypeError, ValueError):
                continue
            if not (33 <= lat <= 39.5 and 124 <= lng <= 132):
                continue
            o = {"name": pick(it, F_NAME) or "여성안심지킴이집", "lat": lat, "lng": lng}
            addr = pick(it, F_ADDR)
            if addr:
                o["address"] = addr
            tel = pick(it, F_TEL)
            if tel:
                o["phone"] = tel
            items.append(o)
        total = int(body.get("totalCount") or 0)
        if page * 1000 >= total or len(rows) < 1000:
            break
        page += 1
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump({"type": "safehouse", "items": items}, open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
    print(f"여성안심지킴이집 {len(items)}건 → {OUT}")


if __name__ == "__main__":
    main()
