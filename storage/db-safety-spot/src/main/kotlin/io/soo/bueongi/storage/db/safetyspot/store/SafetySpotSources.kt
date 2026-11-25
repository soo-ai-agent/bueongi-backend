package io.soo.bueongi.storage.db.safetyspot.store

import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import java.util.Collections
import java.util.IdentityHashMap

// 한 종류의 시설을 출처별 조각으로 나눠 든다 — 업로드가 API 로 받은 지역 데이터를 지우지 않게 하려는 것.
class SafetySpotSources(
    private val files: List<SafetySpotPoint>,
    private val regions: Map<String, List<SafetySpotPoint>>,
) {
    // ponytail: 조각 하나만 바뀌어도 종류 전체 색인을 다시 짓는다(보안등 9.8만 건 51ms) — 지역 수집이 잦아지면 칸 단위 증분 색인으로.
    val index: GridSpatialIndex = GridSpatialIndex(regionsBeforeFiles())

    // 값이 같은 점이 파일·API 양쪽에 있을 수 있어 동일 객체 여부로 가린다 — 색인이 남긴 바로 그 점의 출처를 묻는 것이라서다.
    private val apiPoints: MutableSet<SafetySpotPoint> = Collections.newSetFromMap(IdentityHashMap())

    /** 색인(겹침 제거 후)에서 파일(CDN·업로드) 출처인 점의 수. */
    val fileOriginCount: Int

    /** 색인(겹침 제거 후)에서 외부 API 지역 수집 출처인 점의 수. fileOriginCount 와 더하면 index.size 다. */
    val apiOriginCount: Int

    init {
        for (regionPoints in regions.values) {
            apiPoints.addAll(regionPoints)
        }
        var fileCount = 0
        var apiCount = 0
        for (point in index.all) {
            if (apiPoints.contains(point)) {
                apiCount++
            } else {
                fileCount++
            }
        }
        fileOriginCount = fileCount
        apiOriginCount = apiCount
    }

    /** 색인에 담긴 이 점이 외부 API 지역 수집에서 왔는가 — 아니면 파일(CDN·업로드) 출처다. */
    fun isCollectedFromApi(point: SafetySpotPoint): Boolean {
        return apiPoints.contains(point)
    }

    fun withFiles(points: List<SafetySpotPoint>): SafetySpotSources {
        return SafetySpotSources(points, regions)
    }

    fun withRegion(regionCode: String, points: List<SafetySpotPoint>): SafetySpotSources {
        return SafetySpotSources(files, regions + (regionCode to points))
    }

    // 좌표가 겹치면 색인은 먼저 들어온 점을 남긴다 — API 가 파일보다 최신이라 앞에 둔다.
    // 파일(CDN·업로드)에 이미 있는 지역의 API 조각은 넣지 않는다 — 같은 시설이 좌표만 조금 달라 두 번 세지 않게.
    private fun regionsBeforeFiles(): List<SafetySpotPoint> {
        val fileRegions: Set<String> = files.mapNotNullTo(HashSet()) { it.regionCodeOrNull() }
        val points: ArrayList<SafetySpotPoint> = ArrayList()
        for ((regionCode, regionPoints) in regions) {
            if (regionCode in fileRegions) {
                continue
            }
            points.addAll(regionPoints)
        }
        points.addAll(files)
        return points
    }

    companion object {
        val EMPTY: SafetySpotSources = SafetySpotSources(emptyList(), emptyMap())
    }
}
