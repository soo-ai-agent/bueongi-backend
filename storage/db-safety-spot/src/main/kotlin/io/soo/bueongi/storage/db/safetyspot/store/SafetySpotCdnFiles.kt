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

// 배포 이미지에 든 CDN 파일(cdn/{종류}/*.json, safepath/, manifest.json)을 읽는다.
// 시설은 SafetySpotCdnSync 가 DB 로 옮기고, 안심귀갓길과 신선도는 작아서 메모리에 둔다.
@Component
class SafetySpotCdnFiles(
    @param:Value("\${bueongi.facility.data-dir:cdn}") private val configuredDir: String,
    private val objectMapper: ObjectMapper,
) {
    private val log: Logger = LoggerFactory.getLogger(SafetySpotCdnFiles::class.java)

    private var loadedPoints: Map<SafetySpotType, List<SafetySpotPoint>> = emptyMap()
    private var loadedSafePaths: List<SafePath> = emptyList()

    // manifest 가 없으면 신선도를 표기하지 않는다.
    private var loadedFreshness: SafetySpotFreshness? = null

    val pointsByType: Map<SafetySpotType, List<SafetySpotPoint>>
        get() = loadedPoints

    val safePaths: List<SafePath>
        get() = loadedSafePaths

    val freshness: SafetySpotFreshness?
        get() = loadedFreshness

    @PostConstruct
    fun load() {
        val dir: File? = resolveDataDir()
        if (dir == null) {
            log.warn("안심시설 CDN 디렉터리를 찾지 못했습니다(configured='{}'). CDN 파일 없이 동작합니다.", configuredDir)
            return
        }
        loadedPoints = readPoints(dir)
        loadedSafePaths = readSafePaths(dir)
        loadedFreshness = readFreshness(dir)
        log.info("안심시설 CDN 파일 읽음: dir='{}', types={}, safepaths={}", dir.path, pointsByType.keys, safePaths.size)
    }

    private fun readPoints(dir: File): Map<SafetySpotType, List<SafetySpotPoint>> {
        val byType: LinkedHashMap<SafetySpotType, List<SafetySpotPoint>> = LinkedHashMap()
        for (type in SafetySpotType.entries) {
            val typeDir: File = File(dir, type.cdnType)
            if (!typeDir.isDirectory) {
                continue
            }
            val points: ArrayList<SafetySpotPoint> = ArrayList()
            for (file in typeDir.listJsonFiles()) {
                val collection: StoredSafetySpotCollection = parseCollection(file) ?: continue
                for (item in collection.items) {
                    val point: SafetySpotPoint = item.toSafetySpotPoint(type) ?: continue
                    points.add(point)
                }
            }
            byType[type] = points
        }
        return byType
    }

    private fun readSafePaths(dir: File): List<SafePath> {
        val safePathDir: File = File(dir, "safepath")
        if (!safePathDir.isDirectory) {
            return emptyList()
        }
        val paths: ArrayList<SafePath> = ArrayList()
        for (file in safePathDir.listJsonFiles()) {
            val collection: StoredSafetySpotCollection = parseCollection(file) ?: continue
            for (item in collection.items) {
                val path: SafePath = item.toSafePath() ?: continue
                paths.add(path)
            }
        }
        return paths
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
}
