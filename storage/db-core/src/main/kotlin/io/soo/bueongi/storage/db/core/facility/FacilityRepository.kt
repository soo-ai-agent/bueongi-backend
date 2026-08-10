package io.soo.bueongi.storage.db.core.facility
import io.soo.bueongi.core.enums.FacilityType

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.charset.Charset

@Component
class FacilityRepository(
    @Value("\${bueongi.facility.data-dir:../cdn}") private val configuredDir: String,
    private val objectMapper: ObjectMapper,
    private val uploadStore: FacilityUploadStore,
    @Value("\${bueongi.facility.cctv-csv:CCTV정보.csv}") private val cctvCsvPath: String = "CCTV정보.csv",
) : FacilitySpatialSource, FacilityLiveStore {
    private val log: Logger = LoggerFactory.getLogger(FacilityRepository::class.java)

    @Volatile
    private var indexByType: Map<FacilityType, GridSpatialIndex> = emptyMap()

    @Volatile
    private var _safePaths: List<SafePath> = emptyList()

    @Volatile
    private var _freshness: FacilityFreshness? = null

    override val safePaths: List<SafePath> get() = _safePaths

    override val freshness: FacilityFreshness? get() = _freshness

    override fun index(type: FacilityType): GridSpatialIndex =
        indexByType[type] ?: EMPTY_INDEX

    override fun replace(type: FacilityType, points: List<FacilityPoint>): Int {
        indexByType = indexByType + (type to GridSpatialIndex(points))
        log.info("안심시설 라이브 교체: type={}, count={}", type, points.size)
        return points.size
    }

    @PostConstruct
    fun load() {
        val byType: LinkedHashMap<FacilityType, MutableList<FacilityPoint>> = LinkedHashMap<FacilityType, MutableList<FacilityPoint>>()
        val paths: ArrayList<SafePath> = ArrayList<SafePath>()
        var fileCount: Int = 0

        val dir: File? = resolveDataDir()
        if (dir == null) {
            log.warn(
                "안심시설 CDN 데이터 디렉터리를 찾지 못했습니다(configured='{}'). CDN 적재 없이 업로드분만 적재합니다.",
                configuredDir,
            )
        } else {
            for (type in FacilityType.entries) {
                val typeDir: File = File(dir, type.cdnType)
                if (!typeDir.isDirectory) {
                    continue
                }
                for (file in typeDir.listJsonFiles()) {
                    val collection: FacilityCollectionDto = parseCollection(file) ?: continue
                    fileCount++
                    val bucket: MutableList<FacilityPoint> = byType.getOrPut(type) { ArrayList() }
                    for (item in collection.items) {
                        val point: FacilityPoint = item.toFacilityPoint(type) ?: continue
                        bucket.add(point)
                    }
                }
            }
            File(dir, "safepath").takeIf { it.isDirectory }?.listJsonFiles()?.forEach { file ->
                val collection: FacilityCollectionDto = parseCollection(file) ?: return@forEach
                fileCount++
                collection.items.forEach { item -> item.toSafePath()?.let { paths.add(it) } }
            }

            _freshness = File(dir, "manifest.json").takeIf { it.isFile }?.let { file ->
                try {
                    val manifestJson: JsonNode = objectMapper.readTree(file)
                    FacilityFreshness(
                        generatedAt = manifestJson.get("generatedAt")?.asText()?.takeIf { it.isNotBlank() },
                        version = manifestJson.get("version")?.asText()?.takeIf { it.isNotBlank() },
                    )
                } catch (e: Exception) {
                    log.warn("manifest.json 파싱 실패(신선도 표기 생략): {}", e.message)
                    null
                }
            }
        }

        val csvCctv: List<FacilityPoint> = loadCctvCsv()
        if (csvCctv.isNotEmpty()) {
            byType.getOrPut(FacilityType.CCTV) { ArrayList() }.addAll(csvCctv)
        }

        var uploadedTypes: Int = 0
        for (type in FacilityType.entries) {
            val uploaded: List<FacilityPoint> = uploadStore.findByType(type) ?: continue
            byType[type] = uploaded.toMutableList()
            uploadedTypes++
        }

        indexByType = byType.mapValues { (_, pts) -> GridSpatialIndex(pts) }
        _safePaths = paths
        log.info(
            "안심시설 적재 완료: dir='{}', files={}, uploadedTypes={}, cctv={}(csv={}), lamp={}, bell={}, safehouse={}, police={}, safepaths={}",
            dir?.path ?: "(없음)",
            fileCount,
            uploadedTypes,
            indexByType[FacilityType.CCTV]?.size ?: 0,
            csvCctv.size,
            indexByType[FacilityType.LAMP]?.size ?: 0,
            indexByType[FacilityType.BELL]?.size ?: 0,
            indexByType[FacilityType.SAFEHOUSE]?.size ?: 0,
            indexByType[FacilityType.POLICE]?.size ?: 0,
            paths.size,
        )
    }

    private fun loadCctvCsv(): List<FacilityPoint> {
        val file: File? = resolveCctvCsv()
        if (file == null) {
            log.warn("CCTV CSV 파일을 찾지 못했습니다(configured='{}'). CSV 적재를 건너뜁니다.", cctvCsvPath)
            return emptyList()
        }
        return try {
            file.bufferedReader(CP949).useLines { lines ->
                lines.drop(1)
                    .mapNotNull { parseCctvCsvLine(it) }
                    .toList()
            }
        } catch (e: Exception) {
            log.warn("CCTV CSV 적재 실패(건너뜀): {} ({})", file.path, e.message)
            emptyList()
        }
    }

    private fun resolveCctvCsv(): File? {
        if (cctvCsvPath.isBlank()) {
            return null
        }
        val candidates: List<String> = buildList {
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

    private fun resolveDataDir(): File? {
        val candidates: List<String> = buildList {
            add(configuredDir)
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
        val EMPTY_INDEX: GridSpatialIndex = GridSpatialIndex(emptyList())
    }
}

private val CP949: Charset = Charset.forName("MS949")

/** 전국 CCTV 표준데이터 설치목적구분 10종 중 방범 성격 4종만 적재 — 부팅 CSV·관리자 업로드 공용 단일 기준. */
val CCTV_SECURITY_PURPOSES: Set<String> = setOf("생활방범", "다목적", "어린이보호", "차량방범")

/** 주소 콤마로 컬럼이 밀리는 행(~8.5%) 때문에 뒤에서 고정 오프셋으로 읽는다(후행 12컬럼은 콤마 없음) — N-13=설치목적, N-12=카메라대수, N-6=위도, N-5=경도. */
internal fun parseCctvCsvLine(line: String): FacilityPoint? {
    val parts: List<String> = line.split(',')
    if (parts.size < 13) {
        return null
    }
    val purpose: String = parts[parts.size - 13].trim().trim('"')
    if (purpose !in CCTV_SECURITY_PURPOSES) {
        return null
    }
    val point: GeoPoint = GeoPoint.orNull(
        lat = parts[parts.size - 6].trim().toDoubleOrNull(),
        lng = parts[parts.size - 5].trim().toDoubleOrNull(),
    ) ?: return null
    val name: String? = parts[2].trim().trim('"').takeIf { it.isNotEmpty() }
    val cameraCount: Int? = parts[parts.size - 12].trim().toIntOrNull()?.takeIf { it > 0 }
    return FacilityPoint(
        type = FacilityType.CCTV,
        lat = point.lat,
        lng = point.lng,
        name = name,
        purpose = purpose,
        cameraCount = cameraCount,
    )
}
