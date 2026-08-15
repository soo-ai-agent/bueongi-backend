package io.soo.bueongi.storage.db.core.safetyspot.store
import io.soo.bueongi.storage.db.core.safetyspot.dto.SafetySpotCollectionDto
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.core.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.GeoPoint
import io.soo.bueongi.storage.db.core.safetyspot.model.SafePath
import io.soo.bueongi.core.enums.SafetySpotType

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference

@Component
class SafetySpotRepository(
    @Value("\${bueongi.facility.data-dir:../cdn}") private val configuredDir: String,
    private val objectMapper: ObjectMapper,
    private val uploadStore: SafetySpotUploadStore,
    @Value("\${bueongi.facility.cctv-csv:CCTV정보.csv}") private val cctvCsvPath: String = "CCTV정보.csv",
) : SafetySpotSpatialSource, SafetySpotLiveStore {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotRepository::class.java)

    /**
     * 종류별 공간색인. 기록자가 둘이라(라이브 갱신 스케줄러 · 관리자 업로드 요청) 원자적으로 바꾼다.
     * @Volatile 변수에 `map + (k to v)` 를 쓰면 읽기-수정-쓰기가 겹칠 때 한쪽 교체가 유실된다.
     */
    private val indexByType: AtomicReference<Map<SafetySpotType, GridSpatialIndex>> =
        AtomicReference<Map<SafetySpotType, GridSpatialIndex>>(emptyMap())

    @Volatile
    private var _safePaths: List<SafePath> = emptyList()

    @Volatile
    private var _freshness: SafetySpotFreshness? = null

    override val safePaths: List<SafePath> get() = _safePaths

    override val freshness: SafetySpotFreshness? get() = _freshness

    override fun index(type: SafetySpotType): GridSpatialIndex =
        indexByType.get()[type] ?: EMPTY_INDEX

    override fun replace(type: SafetySpotType, points: List<SafetySpotPoint>): Int {
        val replaced: GridSpatialIndex = GridSpatialIndex(points)
        indexByType.updateAndGet { current -> current + (type to replaced) }
        log.info("안심시설 라이브 교체: type={}, count={}", type, points.size)
        return points.size
    }

    @PostConstruct
    fun load() {
        val byType: LinkedHashMap<SafetySpotType, MutableList<SafetySpotPoint>> = LinkedHashMap<SafetySpotType, MutableList<SafetySpotPoint>>()
        val paths: ArrayList<SafePath> = ArrayList<SafePath>()
        var fileCount: Int = 0

        val dir: File? = resolveDataDir()
        if (dir == null) {
            log.warn(
                "안심시설 CDN 데이터 디렉터리를 찾지 못했습니다(configured='{}'). CDN 적재 없이 업로드분만 적재합니다.",
                configuredDir,
            )
        } else {
            fileCount += readSafetySpotFiles(dir, byType)
            fileCount += readSafePathFiles(dir, paths)
            _freshness = readFreshness(dir)
        }

        val csvCctv: List<SafetySpotPoint> = loadCctvCsv()
        if (csvCctv.isNotEmpty()) {
            byType.getOrPut(SafetySpotType.CCTV) { ArrayList() }.addAll(csvCctv)
        }
        val uploadedTypes: Int = overwriteWithUploads(byType)

        indexByType.set(byType.mapValues { (_, pts) -> GridSpatialIndex(pts) })
        _safePaths = paths
        logLoaded(dir, fileCount, uploadedTypes, csvCctv.size, paths.size)
    }

    /** 종류별 CDN 컬렉션 파일을 읽어 [byType] 에 누적한다. 반환 = 읽은 파일 수. */
    private fun readSafetySpotFiles(dir: File, byType: MutableMap<SafetySpotType, MutableList<SafetySpotPoint>>): Int {
        var fileCount: Int = 0
        for (type in SafetySpotType.entries) {
            val typeDir: File = File(dir, type.cdnType)
            if (!typeDir.isDirectory) {
                continue
            }
            for (file in typeDir.listJsonFiles()) {
                val collection: SafetySpotCollectionDto = parseCollection(file) ?: continue
                fileCount++
                val bucket: MutableList<SafetySpotPoint> = byType.getOrPut(type) { ArrayList() }
                for (item in collection.items) {
                    val point: SafetySpotPoint = item.toSafetySpotPoint(type) ?: continue
                    bucket.add(point)
                }
            }
        }
        return fileCount
    }

    /** 안심귀갓길(safepath) 파일을 읽어 [paths] 에 누적한다. 반환 = 읽은 파일 수. */
    private fun readSafePathFiles(dir: File, paths: MutableList<SafePath>): Int {
        val safePathDir: File = File(dir, "safepath")
        if (!safePathDir.isDirectory) {
            return 0
        }
        var fileCount: Int = 0
        for (file in safePathDir.listJsonFiles()) {
            val collection: SafetySpotCollectionDto = parseCollection(file) ?: continue
            fileCount++
            for (item in collection.items) {
                val path: SafePath = item.toSafePath() ?: continue
                paths.add(path)
            }
        }
        return fileCount
    }

    /** manifest.json 의 생성 시각·버전. 없거나 깨졌으면 신선도 표기를 생략한다. */
    private fun readFreshness(dir: File): SafetySpotFreshness? {
        val manifest: File = File(dir, "manifest.json")
        if (!manifest.isFile) {
            return null
        }
        val manifestJson: JsonNode = try {
            objectMapper.readTree(manifest)
        } catch (e: IOException) {
            log.warn("manifest.json 파싱 실패(신선도 표기 생략): {}", e.message)
            return null
        }
        return SafetySpotFreshness(
            generatedAt = manifestJson.get("generatedAt")?.asText()?.takeIf { it.isNotBlank() },
            version = manifestJson.get("version")?.asText()?.takeIf { it.isNotBlank() },
        )
    }

    /** 관리자 업로드분은 CDN 적재분을 대체한다(같은 종류면 업로드가 이긴다). 반환 = 대체된 종류 수. */
    private fun overwriteWithUploads(byType: MutableMap<SafetySpotType, MutableList<SafetySpotPoint>>): Int {
        var uploadedTypes: Int = 0
        for (type in SafetySpotType.entries) {
            val uploaded: List<SafetySpotPoint> = uploadStore.findByType(type) ?: continue
            byType[type] = uploaded.toMutableList()
            uploadedTypes++
        }
        return uploadedTypes
    }

    private fun logLoaded(dir: File?, fileCount: Int, uploadedTypes: Int, csvCctvCount: Int, safePathCount: Int) {
        val loaded: Map<SafetySpotType, GridSpatialIndex> = indexByType.get()
        log.info(
            "안심시설 적재 완료: dir='{}', files={}, uploadedTypes={}, cctv={}(csv={}), lamp={}, bell={}, " +
                "safehouse={}, police={}, pedestrianSignal={}, safepaths={}",
            dir?.path ?: "(없음)",
            fileCount,
            uploadedTypes,
            loaded[SafetySpotType.CCTV]?.size ?: 0,
            csvCctvCount,
            loaded[SafetySpotType.LAMP]?.size ?: 0,
            loaded[SafetySpotType.BELL]?.size ?: 0,
            loaded[SafetySpotType.SAFEHOUSE]?.size ?: 0,
            loaded[SafetySpotType.POLICE]?.size ?: 0,
            loaded[SafetySpotType.PEDESTRIAN_SIGNAL]?.size ?: 0,
            safePathCount,
        )
    }

    private fun loadCctvCsv(): List<SafetySpotPoint> {
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

    private fun parseCollection(file: File): SafetySpotCollectionDto? = try {
        objectMapper.readValue(file, SafetySpotCollectionDto::class.java)
    } catch (e: IOException) {
        log.warn("안심시설 파일 파싱 실패(건너뜀): {} ({})", file.path, e.message)
        null
    }

    private fun File.listJsonFiles(): List<File> =
        listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()

    private companion object {
        val EMPTY_INDEX: GridSpatialIndex = GridSpatialIndex(emptyList())
    }
}

/** 전국 CCTV 표준데이터 설치목적구분 10종 중 방범 성격 4종만 적재 — 부팅 CSV·관리자 업로드 공용 단일 기준. */
val CCTV_SECURITY_PURPOSES: Set<String> = setOf("생활방범", "다목적", "어린이보호", "차량방범")

/** 주소 콤마로 컬럼이 밀리는 행(~8.5%) 때문에 뒤에서 고정 오프셋으로 읽는다(후행 12컬럼은 콤마 없음) — N-13=설치목적, N-12=카메라대수, N-6=위도, N-5=경도. */
internal fun parseCctvCsvLine(line: String): SafetySpotPoint? {
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
    return SafetySpotPoint(
        type = SafetySpotType.CCTV,
        lat = point.lat,
        lng = point.lng,
        name = name,
        purpose = purpose,
        cameraCount = cameraCount,
    )
}

// 자립 계층 원칙상 support:util 을 참조하지 않는 의도적 중복(clients 의 EARTH_RADIUS_M 와 같은 이유).
private val CP949: Charset = Charset.forName("MS949")
