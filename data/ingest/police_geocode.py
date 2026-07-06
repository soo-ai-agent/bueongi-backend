#!/usr/bin/env python3
"""경찰청 지구대·파출소 CSV(주소만) → Tmap 지오코딩 → cdn/police/all.json.

입력: backend/data/경찰청_전국 지구대 파출소 주소 현황_*.csv (CP949, 좌표 없음)
출력: cdn/police/all.json  ({type, items:[{name,lat,lng,address}]})
필요: 환경변수 TMAP_APP_KEY (backend/.env.local).
실행(레포 루트, 키 source 후): python3 backend/data/ingest/police_geocode.py
"""
import csv, glob, json, os, time, urllib.parse, urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SRC = sorted(glob.glob(os.path.join(ROOT, "backend/data/경찰청_전국 지구대 파출소 주소 현황_*.csv")))[-1]
OUT = os.path.join(ROOT, "cdn/police/all.json")
KEY = os.environ["TMAP_APP_KEY"]


def geocode(addr):
    q = urllib.parse.urlencode({"version": "1", "format": "json", "coordType": "WGS84GEO", "fullAddr": addr})
    req = urllib.request.Request(f"https://apis.openapi.sk.com/tmap/geo/fullAddrGeo?{q}", headers={"appKey": KEY})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            c = json.load(r)["coordinateInfo"]["coordinate"][0]
        lat = float(c.get("newLat") or c.get("lat"))
        lon = float(c.get("newLon") or c.get("lon"))
        if 33 <= lat <= 39.5 and 124 <= lon <= 132:
            return lat, lon
    except Exception:
        return None
    return None


def main():
    items, ok, fail = [], 0, 0
    with open(SRC, encoding="cp949", errors="replace", newline="") as f:
        r = csv.reader(f)
        h = next(r)
        idx = {x: i for i, x in enumerate(h)}
        iName, iGubun, iAddr = idx["관서명"], idx["구분"], idx["주소"]
        rows = list(r)
    print(f"{len(rows)}건 지오코딩 시작")
    for n, row in enumerate(rows, 1):
        if len(row) <= iAddr:
            continue
        addr = row[iAddr].strip().replace("  ", " ")
        name = f"{row[iName].strip()} {row[iGubun].strip()}".strip()
        g = geocode(addr)
        if g:
            items.append({"name": name, "lat": g[0], "lng": g[1], "address": addr})
            ok += 1
        else:
            fail += 1
        if n % 200 == 0:
            print(f"  {n}/{len(rows)} ok={ok} fail={fail}")
        time.sleep(0.02)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump({"type": "police", "items": items}, open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
    print(f"완료: ok={ok} fail={fail} → {OUT}")


if __name__ == "__main__":
    main()
