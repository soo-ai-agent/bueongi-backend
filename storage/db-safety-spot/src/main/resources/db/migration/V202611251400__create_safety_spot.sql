-- 안심시설을 PostGIS 로 옮긴다. 메모리 격자 색인(GridSpatialIndex)을 대신한다.
-- postgis 확장은 확장 생성 권한이 있는 역할로 한 번만 만들면 된다(운영은 postgis/postgis 이미지가 미리 만든다).
CREATE EXTENSION IF NOT EXISTS postgis;

-- source: 'cdn'(배포 파일) | 'upload'(관리자 CSV) | 'api:<지역 키>'(외부 API 지역 조각)
CREATE TABLE safety_spot (
    id           BIGINT           GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type         VARCHAR(40)      NOT NULL,
    source       VARCHAR(80)      NOT NULL,
    region_code  VARCHAR(20),
    name         VARCHAR(300),
    address      VARCHAR(500),
    phone        VARCHAR(50),
    purpose      VARCHAR(100),
    camera_count INTEGER,
    lat          DOUBLE PRECISION NOT NULL,
    lng          DOUBLE PRECISION NOT NULL,
    geog         GEOGRAPHY(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    -- 좌표 5자리(약 1.1m)가 같으면 한 지점으로 센다(원천이 한 지점의 시설 여러 대를 행으로 나눠 준다).
    lat_e5       INTEGER          GENERATED ALWAYS AS (round(lat * 100000)::integer) STORED,
    lng_e5       INTEGER          GENERATED ALWAYS AS (round(lng * 100000)::integer) STORED,
    from_api     BOOLEAN          GENERATED ALWAYS AS (source LIKE 'api:%') STORED,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_safety_spot_geog ON safety_spot USING GIST (geog);
CREATE INDEX idx_safety_spot_type_source ON safety_spot (type, source);
CREATE INDEX idx_safety_spot_type_region ON safety_spot (type, region_code);

-- 출처 조각마다 마지막으로 바꾼 시각과 건수. 외부 API 조각의 재수집 주기(TTL) 판정에 쓴다.
CREATE TABLE safety_spot_slice (
    type       VARCHAR(40) NOT NULL,
    source     VARCHAR(80) NOT NULL,
    row_count  INTEGER     NOT NULL,
    saved_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (type, source)
);
