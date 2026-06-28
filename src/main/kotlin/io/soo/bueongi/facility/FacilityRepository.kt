package io.soo.bueongi.facility

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File

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

        indexByType = byType.mapValues { (_, pts) -> GridSpatialIndex(pts) }
        _safePaths = paths
        log.info(
            "안심시설 적재 완료: dir='{}', files={}, cctv={}, lamp={}, bell={}, safehouse={}, police={}, safepaths={}",
            dir.path,
            fileCount,
            indexByType[FacilityType.CCTV]?.size ?: 0,
            indexByType[FacilityType.LAMP]?.size ?: 0,
            indexByType[FacilityType.BELL]?.size ?: 0,
            indexByType[FacilityType.SAFEHOUSE]?.size ?: 0,
            indexByType[FacilityType.POLICE]?.size ?: 0,
            paths.size,
        )
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
