package io.soo.bueongi.storage.db.core.facility

import io.soo.bueongi.core.enums.FacilityType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
                FacilityType.CCTV to 372,
                FacilityType.POLICE to null,
            ),
            atEpochMs = 1_700_000_000_000L,
        )

        assertThat(store.findByType(FacilityType.CCTV))
            .isEqualTo(FacilityRefreshRecord(atEpochMs = 1_700_000_000_000L, replacedCount = 372))
        assertThat(store.findByType(FacilityType.POLICE))
            .isEqualTo(FacilityRefreshRecord(atEpochMs = 1_700_000_000_000L, replacedCount = null))
        assertThat(store.findByType(FacilityType.LAMP)).isNull()
    }

    @Test
    fun `다시 기록하면 최근 1회분으로 교체된다`() {
        val store: FacilityRefreshStatusStore = FacilityRefreshStatusStore()

        store.record(mapOf(FacilityType.CCTV to 100), atEpochMs = 1L)
        store.record(mapOf(FacilityType.BELL to 5), atEpochMs = 2L)

        assertThat(store.findByType(FacilityType.CCTV)).isNull()
        assertThat(store.findByType(FacilityType.BELL))
            .isEqualTo(FacilityRefreshRecord(atEpochMs = 2L, replacedCount = 5))
    }
}
