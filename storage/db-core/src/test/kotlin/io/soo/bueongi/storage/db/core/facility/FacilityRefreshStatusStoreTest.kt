package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [FacilityRefreshStatusStore] — refreshAll 반환 Map 을 그대로 보관하고 종류별로 되돌려준다.
 * 미기록 종류는 null(=never), 교체 성공은 count 보존, 실패/0건 유지는 count=null 로 구분된다.
 */
class FacilityRefreshStatusStoreTest {

    @Test
    fun `기록 전에는 어떤 종류도 null 이다`() {
        val store: FacilityRefreshStatusStore = FacilityRefreshStatusStore()

        assertThat(store.findByType(FacilityType.CCTV)).isNull()
    }

    @Test
    fun `기록한 결과를 종류별로 되돌려주고 미포함 종류는 null 이다`() {
        val store: FacilityRefreshStatusStore = FacilityRefreshStatusStore()

        store.record(
            mapOf(
                FacilityType.CCTV to 372,   // 교체 성공
                FacilityType.POLICE to null, // 수집 실패·0건 유지
            ),
            atEpochMs = 1_700_000_000_000L,
        )

        assertThat(store.findByType(FacilityType.CCTV))
            .isEqualTo(FacilityRefreshRecord(atEpochMs = 1_700_000_000_000L, replacedCount = 372))
        assertThat(store.findByType(FacilityType.POLICE))
            .isEqualTo(FacilityRefreshRecord(atEpochMs = 1_700_000_000_000L, replacedCount = null))
        // 갱신 대상이 아니던 lamp 는 기록되지 않는다.
        assertThat(store.findByType(FacilityType.LAMP)).isNull()
    }

    @Test
    fun `다시 기록하면 최근 1회분으로 교체된다`() {
        val store: FacilityRefreshStatusStore = FacilityRefreshStatusStore()

        store.record(mapOf(FacilityType.CCTV to 100), atEpochMs = 1L)
        store.record(mapOf(FacilityType.BELL to 5), atEpochMs = 2L)

        assertThat(store.findByType(FacilityType.CCTV)).isNull() // 직전 스냅샷은 대체됨
        assertThat(store.findByType(FacilityType.BELL))
            .isEqualTo(FacilityRefreshRecord(atEpochMs = 2L, replacedCount = 5))
    }
}
