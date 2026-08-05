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
    /** 관리자 업로드 저장소 — 부팅 시 CDN 적재분 위에 마지막 업로드분을 종류별로 덮어쓴다(업로드 우선). */
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

    /** 적재된 안심귀갓길 좌표열(불변 스냅샷). */
    override val safePaths: List<SafePath> get() = _safePaths

    /** manifest.json 기반 데이터 신선도(P0-3). manifest 부재/파싱 실패면 null(표기 생략). */
    override val freshness: FacilityFreshness? get() = _freshness

    /** 시설 종류별 공간 인덱스. 미적재 종류는 빈 인덱스를 돌려준다(NPE 방지). */
    override fun index(type: FacilityType): GridSpatialIndex =
        indexByType[type] ?: EMPTY_INDEX

    /**
     * 라이브 갱신(도메인 갱신 서비스 주도) — [type] 인덱스만 새 스냅샷으로 교체한다.
     * copy-on-write + @Volatile 재대입이라 재기동 없이 원자적이고, 조회 스레드는 전/후 어느 한쪽만 본다.
     */
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

    /**
     * 전국 CCTV CSV(CP949/MS949 인코딩, 37만행)를 스트리밍 파싱해 CCTV 포인트 목록으로 적재한다.
     * 파일이 없으면 빈 목록(경고 로그 후 skip — 죽지 않음). 헤더·콤마밀림·좌표불량 행은 조용히 건너뛴다.
     */
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

    /** 설정 경로 → 폴백 후보 순으로 첫 존재하는 CSV 파일을 찾는다. 빈 설정값이면 CSV 적재 비활성(null). */
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

    /** 설정 경로 → 폴백 후보 순으로 첫 존재하는 디렉터리를 찾는다. */
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

/** 전국 CCTV CSV 의 인코딩. CP949(=MS949, EUC-KR 의 윈도 확장 superset). */
private val CP949: Charset = Charset.forName("MS949")

/**
 * 안심 귀가에 의미 있는 **방범 성격** 설치목적구분(=용도)만 적재 대상으로 한다.
 * 전국 CCTV 표준데이터의 설치목적구분 10종 중 — 생활방범/다목적/어린이보호/차량방범(약 84%)은 유지,
 * 교통단속·시설물관리·재난재해·쓰레기단속·교통정보수집·기타(약 16%)는 안심 시설로 부적합하여 제외.
 *
 * 부팅 CSV 적재([parseCctvCsvLine])와 관리자 업로드 파서(domain FacilityCsvParser)가 같은 기준을
 * 쓰도록 단일 출처로 공개한다(규칙 복붙 드리프트 방지).
 */
val CCTV_SECURITY_PURPOSES: Set<String> = setOf("생활방범", "다목적", "어린이보호", "차량방범")

/**
 * 전국 CCTV CSV 한 줄을 [FacilityPoint](type=CCTV)로 파싱한다. 헤더·빈줄·좌표불량·비방범 행은 null.
 *
 * 표준 18컬럼이나 약 8.5% 행은 주소에 콤마가 섞여 컬럼이 밀린다(따옴표가 있어도 단순 split 으로 깨짐).
 * 따라서 앞 인덱스 대신 **뒤에서 고정 오프셋**으로 읽는다 — 뒤 13컬럼
 * (설치목적구분,카메라대수,화소수,촬영방면,보관일수,설치연월,전화,위도,경도,기준일자,갱신구분,갱신시점,수정시점)
 * 중 후행 12컬럼은 콤마가 없어 안정적이다.
 *   - lat = parts[N-6], lng = parts[N-5]  (N = split 결과 크기)
 *   - 설치목적구분(용도) = parts[N-13]; name = parts[2] (관리기관명; 앞 2컬럼은 숫자코드라 안정적)
 * 방범 성격([CCTV_SECURITY_PURPOSES])이 아니면 적재 제외(null). 후행 필드에 콤마가 섞여
 * N-13 이 어긋나는 약 0.5% 행은 목적을 신뢰할 수 없어 보수적으로 제외된다(좌표 자체는 영향 없음).
 * lat/lng 는 Double 파싱 + 좌표 불변식([GeoPoint.orNull]) 검증, 실패 시 null(조용히 skip).
 */
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
