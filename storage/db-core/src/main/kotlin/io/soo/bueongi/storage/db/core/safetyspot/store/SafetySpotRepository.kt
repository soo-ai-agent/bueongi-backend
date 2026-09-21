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
import java.io.BufferedReader
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.util.concurrent.atomic.AtomicReference

@Component
class SafetySpotRepository(
    @param:Value("\${bueongi.facility.data-dir:../cdn}") private val configuredDir: String,
    private val objectMapper: ObjectMapper,
    private val uploadStore: SafetySpotUploadStore,
    @param:Value("\${bueongi.facility.cctv-csv:data/source/cctv/CCTV정보.csv}") private val cctvCsvPath: String = DEFAULT_CCTV_CSV,
    @param:Value("\${bueongi.facility.lamp-csv-dir:data/source/lamp}")
    private val lampCsvDir: String = DEFAULT_LAMP_CSV_DIR,
    private val regionCache: SafetySpotRegionCache? = null,
) : SafetySpotSpatialSource, SafetySpotLiveStore {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotRepository::class.java)

    // 기록자가 둘이라(라이브 갱신 스케줄러 · 관리자 업로드) @Volatile 교체는 한쪽이 유실된다.
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

    override fun append(type: SafetySpotType, points: List<SafetySpotPoint>): Int {
        if (points.isEmpty()) {
            return index(type).size
        }
        var total: Int = 0
        indexByType.updateAndGet { current ->
            val merged: List<SafetySpotPoint> = current[type].pointsOrEmpty() + points
            total = merged.size
            current + (type to GridSpatialIndex(merged))
        }
        log.info("안심시설 지역 추가: type={}, added={}, total={}", type, points.size, total)
        return total
    }

    override fun replace(type: SafetySpotType, points: List<SafetySpotPoint>): Int {
        val replaced = GridSpatialIndex(points)
        indexByType.updateAndGet { current -> current + (type to replaced) }
        log.info("안심시설 라이브 교체: type={}, count={}", type, points.size)
        return points.size
    }

    @PostConstruct
    fun load() {
        val byType: LinkedHashMap<SafetySpotType, MutableList<SafetySpotPoint>> = LinkedHashMap()
        val paths: ArrayList<SafePath> = ArrayList()
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
        val csvLamp: List<SafetySpotPoint> = loadLampCsv()
        if (csvLamp.isNotEmpty()) {
            byType.getOrPut(SafetySpotType.SECURITY_LAMP) { ArrayList() }.addAll(csvLamp)
        }
        val cachedRegions: Int = addRegionCache(byType)
        val uploadedTypes: Int = overwriteWithUploads(byType)

        indexByType.set(byType.mapValues { (_, pts) -> GridSpatialIndex(pts) })
        _safePaths = paths
        logLoaded(dir, fileCount, uploadedTypes, csvCctv.size, csvLamp.size, cachedRegions, paths.size)
    }

    private fun addRegionCache(byType: MutableMap<SafetySpotType, MutableList<SafetySpotPoint>>): Int {
        var regions: Int = 0
        val cache: SafetySpotRegionCache = regionCache ?: return 0
        for (type in SafetySpotType.entries) {
            val cached: List<SafetySpotPoint> = cache.loadAll(type)
            if (cached.isEmpty()) {
                continue
            }
            byType.getOrPut(type) { ArrayList() }.addAll(cached)
            regions += cache.cachedRegions(type).size
        }
        return regions
    }

    private fun readSafetySpotFiles(dir: File, byType: MutableMap<SafetySpotType, MutableList<SafetySpotPoint>>): Int {
        var fileCount: Int = 0
        for (type in SafetySpotType.entries) {
            val typeDir = File(dir, type.cdnType)
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

    private fun readSafePathFiles(dir: File, paths: MutableList<SafePath>): Int {
        val safePathDir = File(dir, "safepath")
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

    private fun readFreshness(dir: File): SafetySpotFreshness? {
        val manifest = File(dir, "manifest.json")
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

    private fun overwriteWithUploads(byType: MutableMap<SafetySpotType, MutableList<SafetySpotPoint>>): Int {
        var uploadedTypes: Int = 0
        for (type in SafetySpotType.entries) {
            val uploaded: List<SafetySpotPoint> = uploadStore.findByType(type) ?: continue
            byType[type] = uploaded.toMutableList()
            uploadedTypes++
        }
        return uploadedTypes
    }

    private fun logLoaded(
        dir: File?,
        fileCount: Int,
        uploadedTypes: Int,
        csvCctvCount: Int,
        csvLampCount: Int,
        cachedRegions: Int,
        safePathCount: Int,
    ) {
        val loaded: Map<SafetySpotType, GridSpatialIndex> = indexByType.get()
        log.info(
            "안심시설 적재 완료: dir='{}', files={}, uploadedTypes={}, regions={}, cctv={}(csv={}), lamp={}, securityLamp={}(csv={}), bell={}, " +
                "safehouse={}, police={}, pedestrianSignal={}, safepaths={}",
            dir?.path ?: "(없음)",
            fileCount,
            uploadedTypes,
            cachedRegions,
            loaded[SafetySpotType.CCTV]?.size ?: 0,
            csvCctvCount,
            loaded[SafetySpotType.STREET_LAMP]?.size ?: 0,
            loaded[SafetySpotType.SECURITY_LAMP]?.size ?: 0,
            csvLampCount,
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
            openCsv(file).useLines { lines ->
                lines.drop(1)
                    .mapNotNull { parseCctvCsvLine(it) }
                    .toList()
            }
        } catch (e: Exception) {
            log.warn("CCTV CSV 적재 실패(건너뜀): {} ({})", file.path, e.message)
            emptyList()
        }
    }

    // 지자체마다 좌표 제공률이 달라 주소만 올린 원천이 있다 — 좌표 없는 행은 여기서 버린다.
    private fun loadLampCsv(): List<SafetySpotPoint> {
        val dir: File = resolveCsvDir(lampCsvDir, DEFAULT_LAMP_CSV_DIR) ?: return emptyList()
        val points: ArrayList<SafetySpotPoint> = ArrayList()
        for (file in dir.listCsvFiles()) {
            points.addAll(loadLampCsvFile(file))
        }
        return points
    }

    private fun loadLampCsvFile(file: File): List<SafetySpotPoint> {
        return try {
            openCsv(file).useLines { lines ->
                val rows: Iterator<String> = lines.iterator()
                if (!rows.hasNext()) {
                    return@useLines emptyList()
                }
                val columns: LampCsvColumns = LampCsvColumns.of(rows.next()) ?: return@useLines emptyList()
                val points: ArrayList<SafetySpotPoint> = ArrayList()
                for (row in rows) {
                    val point: SafetySpotPoint? = columns.parse(row)
                    if (point != null) {
                        points.add(point)
                    }
                }
                points
            }
        } catch (e: Exception) {
            log.warn("보안등 CSV 적재 실패(건너뜀): {} ({})", file.path, e.message)
            emptyList()
        }
    }

    private fun resolveCctvCsv(): File? {
        if (cctvCsvPath.isBlank()) {
            return null
        }
        val candidates: List<String> = buildList {
            add(cctvCsvPath)
            add("$cctvCsvPath.gz")
            add(DEFAULT_CCTV_CSV)
            add("$DEFAULT_CCTV_CSV.gz")
            add("../backend/$DEFAULT_CCTV_CSV")
            add("backend/$DEFAULT_CCTV_CSV")
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
        val EMPTY_INDEX = GridSpatialIndex(emptyList())
    }
}

val CCTV_SECURITY_PURPOSES: Set<String> = setOf("생활방범", "다목적", "어린이보호", "차량방범")

// 주소 콤마로 컬럼이 밀려 뒤에서 센다: 0=자치단체코드, N-13=설치목적, N-12=카메라대수, N-6=위도, N-5=경도.
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
    val sigungu: String? = parts.firstOrNull()?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }
    return SafetySpotPoint(
        type = SafetySpotType.CCTV,
        lat = point.lat,
        lng = point.lng,
        name = name,
        sigungu = sigungu,
        purpose = purpose,
        cameraCount = cameraCount,
    )
}

private const val DEFAULT_CCTV_CSV: String = "data/source/cctv/CCTV정보.csv"

private const val DEFAULT_LAMP_CSV_DIR: String = "data/source/lamp"

private fun resolveCsvDir(configured: String, default: String): File? {
    if (configured.isBlank()) {
        return null
    }
    return listOf(configured, default).map { File(it) }.firstOrNull { it.isDirectory }
}

private fun File.listCsvFiles(): List<File> =
    listFiles()?.filter { it.isFile && (it.name.endsWith(".csv") || it.name.endsWith(".csv.gz")) }
        ?.sortedBy { it.name }
        .orEmpty()

private fun resolveCsv(configured: String, default: String): File? {
    if (configured.isBlank()) {
        return null
    }
    val candidates: List<String> = listOf(configured, "$configured.gz", default, "$default.gz")
    return candidates.map { File(it) }.firstOrNull { it.isFile }
}

private class LampCsvColumns(
    private val nameAt: Int,
    private val latAt: Int,
    private val lngAt: Int,
    private val sigunguAt: Int,
) {
    fun parse(row: String): SafetySpotPoint? {
        val parts: List<String> = row.split(',')
        if (parts.size <= maxOf(nameAt, latAt, lngAt)) {
            return null
        }
        val point: GeoPoint = GeoPoint.orNull(
            lat = parts[latAt].trim().toDoubleOrNull(),
            lng = parts[lngAt].trim().toDoubleOrNull(),
        ) ?: return null
        val name: String? = parts[nameAt].trim().takeIf { it.isNotEmpty() }
        return SafetySpotPoint(
            type = SafetySpotType.SECURITY_LAMP,
            lat = point.lat,
            lng = point.lng,
            name = name,
            sigungu = valueAt(parts, sigunguAt),
        )
    }

    private fun valueAt(parts: List<String>, at: Int): String? {
        if (at < 0 || at >= parts.size) {
            return null
        }
        return parts[at].trim().takeIf { it.isNotEmpty() }
    }

    companion object {
        fun of(header: String): LampCsvColumns? {
            val columns: List<String> = header.split(',').map { it.trim().removePrefix("\uFEFF") }
            val latAt: Int = columns.indexOf("위도")
            val lngAt: Int = columns.indexOf("경도")
            if (latAt < 0 || lngAt < 0) {
                return null
            }
            return LampCsvColumns(
                nameAt = columns.indexOf("보안등위치명").coerceAtLeast(0),
                latAt = latAt,
                lngAt = lngAt,
                // 제공기관코드가 곧 지역 수집 API 의 instt_code 다.
                sigunguAt = columns.indexOf("제공기관코드"),
            )
        }
    }
}

private fun openCsv(file: File): BufferedReader {
    val charset: Charset = detectCharset(file)
    if (file.name.endsWith(".gz")) {
        return GZIPInputStream(file.inputStream()).bufferedReader(charset)
    }
    return file.bufferedReader(charset)
}

// 전국 표준데이터는 CP949, 지자체 개별 파일은 UTF-8(BOM) 로 온다.
private fun detectCharset(file: File): Charset {
    val head: ByteArray = try {
        val stream = if (file.name.endsWith(".gz")) GZIPInputStream(file.inputStream()) else file.inputStream()
        stream.use { it.readNBytes(HEAD_PROBE_BYTES) }
    } catch (e: Exception) {
        return CP949
    }
    val decoder = Charsets.UTF_8.newDecoder()
    return try {
        decoder.decode(java.nio.ByteBuffer.wrap(head.dropLastWhile { it.toInt() and 0x80 != 0 }.toByteArray()))
        Charsets.UTF_8
    } catch (e: Exception) {
        CP949
    }
}

private const val HEAD_PROBE_BYTES: Int = 4096

private val CP949: Charset = Charset.forName("MS949")

private fun GridSpatialIndex?.pointsOrEmpty(): List<SafetySpotPoint> =
    this?.all.orEmpty()
