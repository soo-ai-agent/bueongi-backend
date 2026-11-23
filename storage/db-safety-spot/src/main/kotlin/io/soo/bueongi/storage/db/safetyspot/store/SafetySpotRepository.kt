package io.soo.bueongi.storage.db.safetyspot.store
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafePath
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotFreshness
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.model.StoredSafetySpotCollection
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

@Component
class SafetySpotRepository(
    @param:Value("\${bueongi.facility.data-dir:cdn}") private val configuredDir: String,
    private val objectMapper: ObjectMapper,
    private val uploadStore: SafetySpotUploadStore,
    private val regionCache: SafetySpotRegionCache? = null,
) : SafetySpotSpatialSource, SafetySpotLiveStore {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotRepository::class.java)

    // 기록자가 둘이라(지역 API 수집 · 관리자 업로드) @Volatile 교체는 한쪽이 유실된다.
    private val sourcesByType: AtomicReference<Map<SafetySpotType, SafetySpotSources>> =
        AtomicReference<Map<SafetySpotType, SafetySpotSources>>(emptyMap())

    @Volatile
    private var _safePaths: List<SafePath> = emptyList()

    @Volatile
    private var _freshness: SafetySpotFreshness? = null

    override val safePaths: List<SafePath> get() = _safePaths

    override val freshness: SafetySpotFreshness? get() = _freshness

    override fun index(type: SafetySpotType): GridSpatialIndex {
        val sources: SafetySpotSources = sourcesByType.get()[type] ?: return EMPTY_INDEX
        return sources.index
    }

    override fun sources(type: SafetySpotType): SafetySpotSources {
        return sourcesByType.get()[type] ?: SafetySpotSources.EMPTY
    }

    override fun replaceFiles(type: SafetySpotType, points: List<SafetySpotPoint>) {
        sourcesByType.updateAndGet { current ->
            val sources: SafetySpotSources = current[type] ?: SafetySpotSources.EMPTY
            current + (type to sources.withFiles(points))
        }
        log.info("안심시설 파일 출처 교체: type={}, count={}, total={}", type, points.size, index(type).size)
    }

    override fun replaceRegion(type: SafetySpotType, regionCode: String, points: List<SafetySpotPoint>) {
        sourcesByType.updateAndGet { current ->
            val sources: SafetySpotSources = current[type] ?: SafetySpotSources.EMPTY
            current + (type to sources.withRegion(regionCode, points))
        }
        log.info("안심시설 지역 교체: type={}, region={}, count={}, total={}", type, regionCode, points.size, index(type).size)
    }

    @PostConstruct
    fun load() {
        val filesByType: LinkedHashMap<SafetySpotType, MutableList<SafetySpotPoint>> = LinkedHashMap()
        val paths: ArrayList<SafePath> = ArrayList()
        var fileCount: Int = 0

        val dir: File? = resolveDataDir()
        if (dir == null) {
            log.warn(
                "안심시설 CDN 데이터 디렉터리를 찾지 못했습니다(configured='{}'). CDN 적재 없이 업로드분만 적재합니다.",
                configuredDir,
            )
        } else {
            fileCount += readFiles(dir, filesByType)
            fileCount += readSafePathFiles(dir, paths)
            _freshness = readFreshness(dir)
        }

        val uploadedTypes: Int = overwriteWithUploads(filesByType)

        val sources: LinkedHashMap<SafetySpotType, SafetySpotSources> = LinkedHashMap()
        for ((type, points) in filesByType) {
            sources[type] = SafetySpotSources.EMPTY.withFiles(points)
        }
        val cachedRegions: Int = addRegionCache(sources)
        sourcesByType.set(sources)
        _safePaths = paths
        logLoaded(dir, fileCount, uploadedTypes, cachedRegions, paths.size)
    }

    private fun addRegionCache(sources: MutableMap<SafetySpotType, SafetySpotSources>): Int {
        var regions: Int = 0
        val cache: SafetySpotRegionCache = regionCache ?: return 0
        for (type in SafetySpotType.entries) {
            for (regionCode in cache.list(type)) {
                val cached: SafetySpotSources = sources[type] ?: SafetySpotSources.EMPTY
                sources[type] = cached.withRegion(regionCode, cache.load(type, regionCode))
                regions++
            }
        }
        return regions
    }

    private fun readFiles(dir: File, byType: MutableMap<SafetySpotType, MutableList<SafetySpotPoint>>): Int {
        var fileCount: Int = 0
        for (type in SafetySpotType.entries) {
            val typeDir: File = File(dir, type.cdnType)
            if (!typeDir.isDirectory) {
                continue
            }
            for (file in typeDir.listJsonFiles()) {
                val collection: StoredSafetySpotCollection = parseCollection(file) ?: continue
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
        val safePathDir: File = File(dir, "safepath")
        if (!safePathDir.isDirectory) {
            return 0
        }
        var fileCount: Int = 0
        for (file in safePathDir.listJsonFiles()) {
            val collection: StoredSafetySpotCollection = parseCollection(file) ?: continue
            fileCount++
            for (item in collection.items) {
                val path: SafePath = item.toSafePath() ?: continue
                paths.add(path)
            }
        }
        return fileCount
    }

    private fun readFreshness(dir: File): SafetySpotFreshness? {
        val manifest: File = File(dir, "manifest.json")
        if (!manifest.isFile) {
            return null
        }
        val manifestJson: JsonNode =
            try {
                objectMapper.readTree(manifest)
            } catch (e: IOException) {
                log.warn("manifest.json 파싱 실패(신선도 표기 생략) | 원인={}", e.javaClass.simpleName)
                return null
            }
        val generatedAt: String = manifestJson.path("generatedAt").asText()
        val version: String = manifestJson.path("version").asText()
        if (generatedAt.isBlank() || version.isBlank()) {
            log.warn("manifest.json 에 generatedAt·version 이 없음(신선도 표기 생략)")
            return null
        }
        return SafetySpotFreshness(generatedAt = generatedAt, version = version)
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
        cachedRegions: Int,
        safePathCount: Int,
    ) {
        log.info(
            "안심시설 적재 완료: dir='{}', files={}, uploadedTypes={}, regions={}, cctv={}, lamp={}, securityLamp={}, bell={}, " +
                "safehouse={}, police={}, pedestrianSignal={}, safepaths={}",
            dir?.path ?: "(없음)",
            fileCount,
            uploadedTypes,
            cachedRegions,
            index(SafetySpotType.CCTV).size,
            index(SafetySpotType.STREET_LAMP).size,
            index(SafetySpotType.SECURITY_LAMP).size,
            index(SafetySpotType.BELL).size,
            index(SafetySpotType.SAFEHOUSE).size,
            index(SafetySpotType.POLICE).size,
            index(SafetySpotType.PEDESTRIAN_SIGNAL).size,
            safePathCount,
        )
    }

    private fun resolveDataDir(): File? {
        val candidates: List<String> = listOf(configuredDir, "cdn", "../../cdn")
        for (candidate in candidates) {
            val dir: File = File(candidate)
            if (dir.isDirectory) {
                return dir.absoluteFile
            }
        }
        return null
    }

    private fun parseCollection(file: File): StoredSafetySpotCollection? {
        return try {
            objectMapper.readValue(file, StoredSafetySpotCollection::class.java)
        } catch (e: IOException) {
            log.warn("안심시설 파일 파싱 실패(건너뜀): {} | 원인={}", file.path, e.javaClass.simpleName)
            null
        }
    }

    private companion object {
        val EMPTY_INDEX: GridSpatialIndex = GridSpatialIndex(emptyList())
    }
}
