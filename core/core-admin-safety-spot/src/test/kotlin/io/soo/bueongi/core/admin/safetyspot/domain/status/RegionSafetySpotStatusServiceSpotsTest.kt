package io.soo.bueongi.core.admin.safetyspot.domain.status

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.soo.bueongi.core.admin.safetyspot.api.response.RegionSafetySpotsResponse
import io.soo.bueongi.core.admin.safetyspot.domain.error.InvalidSpotLimitException
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.RegionNameCatalog
import io.soo.bueongi.core.admin.safetyspot.domain.status.service.RegionSafetySpotStatusService
import io.soo.bueongi.core.enums.SafetySpotType
import io.soo.bueongi.storage.db.safetyspot.model.SafetySpotPoint
import io.soo.bueongi.storage.db.safetyspot.store.FakeSafetySpotSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RegionSafetySpotStatusServiceSpotsTest {
    private fun service(points: Map<SafetySpotType, List<SafetySpotPoint>>): RegionSafetySpotStatusService =
        RegionSafetySpotStatusService(FakeSafetySpotSource(points), RegionNameCatalog(jacksonObjectMapper()))

    private fun points(type: SafetySpotType, count: Int, region: String = MAPO): List<SafetySpotPoint> {
        val made: ArrayList<SafetySpotPoint> = ArrayList(count)
        for (i in 0 until count) {
            made.add(SafetySpotPoint.of(type, lat = 37.50 + i * 0.001, lng = 127.00, name = "p$i", regionCode = region))
        }
        return made
    }

    @Test
    fun `자치단체 이름과 시도를 붙여 그 지역 지점만 내준다`() {
        val found: RegionSafetySpotsResponse =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.50, 127.00, name = "마포 사거리", regionCode = MAPO),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 35.54, 128.49, regionCode = CHANGNYEONG),
                        ),
                ),
            ).findByCode(MAPO, limit = 100)

        assertThat(found.name).isEqualTo("마포구")
        assertThat(found.sido).isEqualTo("서울특별시")
        assertThat(found.total).isEqualTo(1)
        assertThat(found.spots.single().name).isEqualTo("마포 사거리")
    }

    @Test
    fun `상한에 걸려 잘려도 전체 건수는 그대로 말한다`() {
        val found: RegionSafetySpotsResponse =
            service(
                mapOf(SafetySpotType.CCTV to points(SafetySpotType.CCTV, 50)),
            ).findByCode(MAPO, limit = 10)

        assertThat(found.total).isEqualTo(50)
        assertThat(found.spots).hasSize(10)
    }

    @Test
    fun `종류가 섞여 있으면 상한을 종류별로 나눠 담는다`() {
        val found: RegionSafetySpotsResponse =
            service(
                mapOf(
                    SafetySpotType.CCTV to points(SafetySpotType.CCTV, 100),
                    SafetySpotType.BELL to points(SafetySpotType.BELL, 3),
                    SafetySpotType.POLICE to points(SafetySpotType.POLICE, 1),
                ),
            ).findByCode(MAPO, limit = 10)

        val byType: Map<SafetySpotType, Int> = found.spots.groupingBy { it.type }.eachCount()
        assertThat(found.spots).hasSize(10)
        assertThat(byType[SafetySpotType.BELL]).isEqualTo(3)
        assertThat(byType[SafetySpotType.POLICE]).isEqualTo(1)
        assertThat(byType[SafetySpotType.CCTV]).isEqualTo(6)
    }

    @Test
    fun `자를 때는 앞에서 자르지 않고 지역 전체에 편다`() {
        val found: RegionSafetySpotsResponse =
            service(
                mapOf(SafetySpotType.CCTV to points(SafetySpotType.CCTV, 10)),
            ).findByCode(MAPO, limit = 2)

        assertThat(found.spots.map { it.name }).containsExactly("p0", "p5")
    }

    @Test
    fun `상한을 넘겨 요청하면 상한까지만 보낸다`() {
        val found: RegionSafetySpotsResponse =
            service(
                mapOf(SafetySpotType.CCTV to points(SafetySpotType.CCTV, 6_000)),
            ).findByCode(MAPO, limit = 9_999)

        assertThat(found.total).isEqualTo(6_000)
        assertThat(found.spots).hasSize(5_000)
    }

    @Test
    fun `표시 개수가 0 이하면 거부한다`() {
        val service: RegionSafetySpotStatusService =
            service(
                mapOf(SafetySpotType.CCTV to points(SafetySpotType.CCTV, 3)),
            )

        assertThatThrownBy { service.findByCode(MAPO, limit = 0) }
            .isInstanceOf(InvalidSpotLimitException::class.java)
    }

    @Test
    fun `없는 자치단체는 코드를 그대로 돌려주고 빈 목록을 낸다`() {
        val found: RegionSafetySpotsResponse =
            service(
                mapOf(SafetySpotType.CCTV to points(SafetySpotType.CCTV, 3)),
            ).findByCode("9999999", limit = 100)

        assertThat(found.code).isEqualTo("9999999")
        assertThat(found.total).isEqualTo(0)
        assertThat(found.spots).isEmpty()
    }

    @Test
    fun `지역을 모르는 지점은 unknown 으로 조회된다`() {
        val found: RegionSafetySpotsResponse =
            service(
                mapOf(
                    SafetySpotType.CCTV to
                        listOf(
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.50, 127.00),
                            SafetySpotPoint.of(SafetySpotType.CCTV, 37.51, 127.01, regionCode = MAPO),
                        ),
                ),
            ).findByCode("unknown", limit = 100)

        assertThat(found.total).isEqualTo(1)
        assertThat(found.spots.single().lat).isEqualTo(37.50)
    }

    private companion object {
        const val MAPO: String = "3130000"
        const val CHANGNYEONG: String = "5410000"
    }
}
