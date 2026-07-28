package io.soo.bueongi.domain.route

import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.domain.facility.FakeFacilitySource
import io.soo.bueongi.storage.db.core.facility.FacilityPoint
import io.soo.bueongi.storage.db.core.facility.GeoPoint
import io.soo.bueongi.support.monitoring.AlertNotifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [RouteMarkerBuilder] 의 시설 데이터 제공 실패 알림 테스트 — 핵심은 "장애 0 vs 정상 0" 구분이다:
 * 인덱스 총 적재량이 0(적재 실패/부재)일 때만 알리고, 적재량>0인데 특정 회랑만 0인 정직한 0에는 알리지 않는다.
 * 스로틀 억제(같은 key 로 폭주 방지)는 [io.soo.bueongi.support.monitoring.SlackAlertNotifierTest] 가 실제로 검증하므로,
 * 여기서는 반복 호출이 **같은 상수 key** 를 쓰는지(=스로틀 대상인지)만 단언한다.
 */
class RouteMarkerBuilderAlertTest {

    private val origin: GeoPoint = GeoPoint(37.5, 127.000)
    private val destination: GeoPoint = GeoPoint(37.5, 127.020)

    private class RecordingNotifier : AlertNotifier {
        val keys: ArrayList<String> = ArrayList<String>()
        override val isEnabled: Boolean = true
        override fun notify(key: String, title: String, detail: String) {
            keys.add(key)
        }
    }

    private fun build(source: FakeFacilitySource, notifier: AlertNotifier?) =
        RouteMarkerBuilder(source, notifier).buildMarkers(origin, destination, listOf(origin, destination))

    @Test
    fun `장애 - 시설 인덱스가 통째로 비면(총 적재 0) 데이터 제공 실패로 알린다`() {
        val notifier: RecordingNotifier = RecordingNotifier()

        val markers = build(FakeFacilitySource(), notifier)

        // 인덱스 적재 실패라도 마커 생성은 계속(start/end 유지) — 알림은 부수효과.
        assertThat(markers.map { it.type }).containsExactlyInAnyOrder(FacilityPoiType.START, FacilityPoiType.END)
        assertThat(notifier.keys).containsExactly("facility-index-empty")
    }

    @Test
    fun `정상 - 시설이 적재됐고 회랑에만 없으면(적재량 GT 0, 회랑 0) 알리지 않는다`() {
        val notifier: RecordingNotifier = RecordingNotifier()
        // 시설은 적재됐지만(size>0) 경로 회랑(30m)에서 멀리 떨어져 실측 0 인 소스 — 정직한 0.
        val farAway = FakeFacilitySource(
            mapOf(FacilityType.CCTV to listOf(FacilityPoint(FacilityType.CCTV, 38.0, 128.0))),
        )

        val markers = build(farAway, notifier)

        assertThat(markers.map { it.type }).containsExactlyInAnyOrder(FacilityPoiType.START, FacilityPoiType.END)
        assertThat(notifier.keys).isEmpty()
    }

    @Test
    fun `스로틀 - 빈 인덱스로 반복 호출해도 항상 같은 상수 key 를 써서 폭주가 억제된다`() {
        val notifier: RecordingNotifier = RecordingNotifier()
        val source: FakeFacilitySource = FakeFacilitySource()

        repeat(3) { build(source, notifier) }

        // 실제 억제(창당 1회)는 SlackAlertNotifier 스로틀이 수행한다. 여기선 그 대상이 되는 단일 key 를 단언.
        assertThat(notifier.keys).allMatch { it == "facility-index-empty" }
        assertThat(notifier.keys.toSet()).containsExactly("facility-index-empty")
    }

    @Test
    fun `알림기 미주입(null)이어도 마커 생성은 정상 동작한다`() {
        val markers = build(FakeFacilitySource(), notifier = null)
        assertThat(markers.map { it.type }).containsExactlyInAnyOrder(FacilityPoiType.START, FacilityPoiType.END)
    }
}
