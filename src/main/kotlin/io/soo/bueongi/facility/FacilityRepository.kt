package io.soo.bueongi.facility

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.charset.Charset

/**
 * 공공데이터(CDN) 시설/안심귀갓길을 startup 시 적재해 인메모리 공간 인덱스를 만드는 저장소.
 *
 * 데이터 디렉터리는 `bueongi.facility.data-dir`(환경변수 `BUEONGI_FACILITY_DATA_DIR`)로 설정한다.
 * 기본값은 backend 실행 경로 기준 `../cdn`(= apps/bueongi/cdn). 설정 경로가 없으면 몇 개 후보 경로를
 * 차례로 시도하고, 그래도 없으면 빈 인덱스로 동작한다(경고 로그). 일부 파일 누락/파싱 실패에도 죽지 않고
 * 가능한 것만 적재한다 — 데이터 부재는 점수 0 수렴(폭주 금지)으로 안전하게 흡수된다.
 *
 * 적재 결과는 불변 인덱스라 thread-safe. 같은 데이터 → 같은 인덱스(결정론적).
 */
@Component
class FacilityRepository(
    @Value("\${bueongi.facility.data-dir:../cdn}") private val configuredDir: String,
    private val objectMapper: ObjectMapper,
    @Value("\${bueongi.facility.cctv-csv:CCTV정보.csv}") private val cctvCsvPath: String = "CCTV정보.csv",
) : FacilitySpatialSource {
    private val log = LoggerFactory.getLogger(FacilityRepository::class.java)

    @Volatile
    private var indexByType: Map<FacilityType, GridSpatialIndex> = emptyMap()

    @Volatile
    private var _safePaths: List<SafePath> = emptyList()

    /** 적재된 안심귀갓길 좌표열(불변 스냅샷). */
    override val safePaths: List<SafePath> get() = _safePaths

    /** 시설 종류별 공간 인덱스. 미적재 종류는 빈 인덱스를 돌려준다(NPE 방지). */
    override fun index(type: FacilityType): GridSpatialIndex =
        indexByType[type] ?: EMPTY_INDEX

    @PostConstruct
    fun load() {
        val dir = resolveDataDir()
        if (dir == null) {
            log.warn(
                "안심시설 데이터 디렉터리를 찾지 못했습니다(configured='{}'). 빈 인덱스로 동작합니다.",
                configuredDir,
            )
            return
        }
        val byType = LinkedHashMap<FacilityType, MutableList<FacilityPoint>>()
        val paths = ArrayList<SafePath>()
        var fileCount = 0

        // 컬렉션 디렉터리(type 별 하위 폴더)를 훑어 *.json 을 적재. safepath 는 좌표열로 따로.
        for (type in FacilityType.entries) {
            val typeDir = File(dir, type.cdnType)
            if (!typeDir.isDirectory) continue
            for (file in typeDir.listJsonFiles()) {
                val collection = parseCollection(file) ?: continue
                fileCount++
                val bucket = byType.getOrPut(type) { ArrayList() }
                for (item in collection.items) {
                    val point = item.toFacilityPoint(type) ?: continue
                    bucket.add(point)
                }
            }
        }
        // safepath(안심귀갓길) 좌표열.
        File(dir, "safepath").takeIf { it.isDirectory }?.listJsonFiles()?.forEach { file ->
            val collection = parseCollection(file) ?: return@forEach
            fileCount++
            collection.items.forEach { item -> item.toSafePath()?.let { paths.add(it) } }
        }

        // 전국 CCTV 실데이터 CSV(있으면) 적재 — JSON CCTV 포인트와 합쳐 단일 인덱스를 만든다.
        val csvCctv = loadCctvCsv()
        if (csvCctv.isNotEmpty()) {
            byType.getOrPut(FacilityType.CCTV) { ArrayList() }.addAll(csvCctv)
        }

        indexByType = byType.mapValues { (_, pts) -> GridSpatialIndex(pts) }
        _safePaths = paths
        log.info(
            "안심시설 적재 완료: dir='{}', files={}, cctv={}(csv={}), lamp={}, bell={}, safehouse={}, police={}, safepaths={}",
            dir.path,
            fileCount,
            indexByType[FacilityType.CCTV]?.size ?: 0,
            csvCctv.size,
            indexByType[FacilityType.LAMP]?.size ?: 0,
            indexByType[FacilityType.BELL]?.size ?: 0,
            indexByType[FacilityType.SAFEHOUSE]?.size ?: 0,
            indexByType[FacilityType.POLICE]?.size ?: 0,
            paths.size,
        )
    }

    /**
     * 전국 CCTV CSV(CP949/MS949 인코딩, 37만행)를 스트리밍 파싱해 CCTV 포인트 목록으로 적재한다.
     * 파일이 없으면 빈 목록(경고 로그 후 skip — 죽지 않음). 헤더·콤마밀림·좌표불량 행은 조용히 건너뛴다.
     */
    private fun loadCctvCsv(): List<FacilityPoint> {
        val file = resolveCctvCsv()
        if (file == null) {
            log.warn("CCTV CSV 파일을 찾지 못했습니다(configured='{}'). CSV 적재를 건너뜁니다.", cctvCsvPath)
            return emptyList()
        }
        return try {
            file.bufferedReader(CP949).useLines { lines ->
                lines.drop(1) // 헤더 건너뜀
                    .mapNotNull { parseCctvCsvLine(it) }
                    .toList()
            }
        } catch (e: Exception) {
            log.warn("CCTV CSV 적재 실패(건너뜀): {} ({})", file.path, e.message)
            emptyList()
        }
    }

    /** 설정 경로 → 폴백 후보 순으로 첫 존재하는 CSV 파일을 찾는다. 빈 설정값이면 CSV 적재 비활성(null). */
    private fun resolveCctvCsv(): File? {
        if (cctvCsvPath.isBlank()) return null
        val candidates = buildList {
            add(cctvCsvPath)
            add("CCTV정보.csv")
            add("../backend/CCTV정보.csv")
            add("backend/CCTV정보.csv")
        }
        return candidates
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?.absoluteFile
    }

    /** 설정 경로 → 폴백 후보 순으로 첫 존재하는 디렉터리를 찾는다. */
    private fun resolveDataDir(): File? {
        val candidates = buildList {
            add(configuredDir)
            // backend cwd 기준 흔한 위치들(테스트/실행 환경 차이 흡수).
            add("../cdn")
            add("../../cdn")
            add("cdn")
        }
        return candidates
            .map { File(it) }
            .firstOrNull { it.isDirectory }
            ?.absoluteFile
    }

    private fun parseCollection(file: File): FacilityCollectionDto? = try {
        objectMapper.readValue(file, FacilityCollectionDto::class.java)
    } catch (e: Exception) {
        log.warn("안심시설 파일 파싱 실패(건너뜀): {} ({})", file.path, e.message)
        null
    }

    private fun File.listJsonFiles(): List<File> =
        listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()

    private companion object {
        val EMPTY_INDEX = GridSpatialIndex(emptyList())
    }
}

/** 전국 CCTV CSV 의 인코딩. CP949(=MS949, EUC-KR 의 윈도 확장 superset). */
private val CP949: Charset = Charset.forName("MS949")

/**
 * 전국 CCTV CSV 한 줄을 [FacilityPoint](type=CCTV)로 파싱한다. 헤더·빈줄·좌표불량 행은 null.
 *
 * 표준 18컬럼이나 약 8.5% 행은 주소에 콤마가 섞여 컬럼이 밀린다(따옴표가 있어도 단순 split 으로 깨짐).
 * 따라서 앞 인덱스 대신 **뒤에서 고정 오프셋**으로 좌표를 읽는다 — 뒤 6컬럼
 * (위도,경도,기준일자,갱신구분,갱신시점,수정시점)은 콤마가 없어 안정적이다.
 *   - lat = parts[N-6], lng = parts[N-5]  (N = split 결과 크기)
 *   - name = parts[2] (관리기관명; 앞 2컬럼은 숫자코드라 안정적)
 * lat/lng 는 Double 파싱 + 유효범위(±90/±180, isFinite) 검증, 실패 시 null(조용히 skip).
 */
internal fun parseCctvCsvLine(line: String): FacilityPoint? {
    val parts = line.split(',')
    // 최소: 뒤 6컬럼(lat=parts[N-6]) + name=parts[2] 가 모두 존재해야 함(size>=6 이면 둘 다 보장).
    if (parts.size < 6) return null
    val lat = parts[parts.size - 6].trim().toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
    val lng = parts[parts.size - 5].trim().toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
    val name = parts[2].trim().trim('"').takeIf { it.isNotEmpty() }
    return FacilityPoint(type = FacilityType.CCTV, lat = lat, lng = lng, name = name)
}

/** CDN 컬렉션 파일 래퍼. 미사용 메타(schemaVersion/sources 등)는 무시. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FacilityCollectionDto(
    val type: String? = null,
    val items: List<FacilityItemDto> = emptyList(),
)

/**
 * 시설/경로 1건. 시설은 lat/lng 점이고, safepath 는 좌표열(coords)을 가질 수 있다 —
 * coords 가 없으면 단일 점(lat/lng)을 1점짜리 경로로 본다. 비유효 좌표(NaN/∞/범위 밖)는 제외한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FacilityItemDto(
    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val sigunguCode: String? = null,
    val coords: List<CoordDto>? = null,
) {
    fun toFacilityPoint(type: FacilityType): FacilityPoint? {
        val la = lat?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val ln = lng?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        return FacilityPoint(type = type, lat = la, lng = ln, name = name, sigungu = sigunguCode)
    }

    fun toSafePath(): SafePath? {
        val coordList = coords?.mapNotNull { it.toGeoPoint() }
        if (!coordList.isNullOrEmpty()) return SafePath(coords = coordList, name = name)
        // coords 없으면 단일 점 폴백.
        val la = lat?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val ln = lng?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        return SafePath(coords = listOf(GeoPoint(la, ln)), name = name)
    }
}

/** safepath 좌표열의 한 점. {lat, lng} 또는 [lng, lat] 어느 쪽이든 lat/lng 키 우선. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CoordDto(
    val lat: Double? = null,
    val lng: Double? = null,
) {
    fun toGeoPoint(): GeoPoint? {
        val la = lat?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val ln = lng?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        return GeoPoint(la, ln)
    }
}
