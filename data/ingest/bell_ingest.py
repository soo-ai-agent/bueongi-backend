#!/usr/bin/env python3
"""안전비상벨 CSV → cdn/emergency_bell/nationwide.json (백엔드 적재용 풍부 필드 포함).

입력: backend/data/안전비상벨위치정보.csv (CP949, 표준데이터 24컬럼, 좌표 보유)
출력: cdn/emergency_bell/nationwide.json  ({type, items:[{name,lat,lng,sigunguCode,address,phone,properties:{purpose}}]})
실행(레포 루트에서): python3 backend/data/ingest/bell_ingest.py
"""
import csv, json, os, collections

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SRC = os.path.join(ROOT, "backend/data/안전비상벨위치정보.csv")
OUT = os.path.join(ROOT, "cdn/emergency_bell/nationwide.json")


def main():
    items, bad = [], 0
    with open(SRC, encoding="cp949", errors="replace", newline="") as f:
        r = csv.reader(f)
        h = next(r)
        idx = {name: i for i, name in enumerate(h)}
        iLat, iLng = idx["WGS84위도"], idx["WGS84경도"]
        iLoc, iOrg = idx["설치위치"], idx["관리기관명"]
        iAddr, iJibun = idx["소재지도로명주소"], idx["소재지지번주소"]
        iTel, iPurp, iSgg = idx["관리기관전화번호"], idx["설치목적"], idx["개방자치단체코드"]
        for row in r:
            if len(row) <= max(iLat, iLng, iTel):
                continue
            try:
                lat, lng = float(row[iLat]), float(row[iLng])
            except ValueError:
                bad += 1
                continue
            if not (33.0 <= lat <= 39.5 and 124.0 <= lng <= 132.0):
                bad += 1
                continue
            it = {
                "name": (row[iLoc].strip() or row[iOrg].strip()) or "안전비상벨",
                "lat": lat, "lng": lng,
                "sigunguCode": row[iSgg].strip() or None,
            }
            addr = row[iAddr].strip() or row[iJibun].strip()
            if addr:
                it["address"] = addr
            if row[iTel].strip():
                it["phone"] = row[iTel].strip()
            if row[iPurp].strip():
                it["properties"] = {"purpose": row[iPurp].strip()}
            items.append(it)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump({"type": "emergency_bell", "items": items}, open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
    c = collections.Counter(i.get("properties", {}).get("purpose") for i in items)
    print(f"비상벨 {len(items)}건 → {OUT} (제외 {bad}) 설치목적={dict(c.most_common(5))}")


if __name__ == "__main__":
    main()
