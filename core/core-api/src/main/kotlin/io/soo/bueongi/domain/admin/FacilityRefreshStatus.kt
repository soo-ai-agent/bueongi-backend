package io.soo.bueongi.domain.admin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import io.soo.bueongi.core.enums.FacilityType
import io.soo.bueongi.storage.db.core.facility.FacilityFreshness
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshRecord
import io.soo.bueongi.storage.db.core.facility.FacilityRefreshStatusStore
import io.soo.bueongi.storage.db.core.facility.FacilitySpatialSource
import org.springframework.stereotype.Service

/** [wire] 가 와이어 계약([JsonValue]) — lastRefreshStatus 가 정확히 replaced|kept|never 를 내야 한다. */
enum class FacilityRefreshStatus(@get:JsonValue val wire: String) {
    NEVER("never"),
    REPLACED("replaced"),
    KEPT("kept"),
    ;

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String): FacilityRefreshStatus = entries.first { it.wire == value }
    }
}
