package io.soo.bueongi.core.route.stats.domain.service

import io.soo.bueongi.core.enums.RouteType
import io.soo.bueongi.core.enums.SafetyPreference
import io.soo.bueongi.core.route.stats.domain.model.PreferenceCount
import io.soo.bueongi.core.route.stats.domain.model.RouteChoice
import io.soo.bueongi.core.route.stats.domain.model.RouteChoiceSummary
import io.soo.bueongi.core.route.stats.domain.model.RouteTypeCount
import io.soo.bueongi.storage.db.core.routestats.ROUTE_CHOICE_CAP
import io.soo.bueongi.storage.db.core.routestats.RouteChoiceLog
import io.soo.bueongi.storage.db.core.routestats.RouteChoiceStore
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.EnumMap

@Service
class RouteChoiceService(
    private val routeChoiceStore: RouteChoiceStore,
    private val clock: Clock,
) {
    fun record(choice: RouteChoice) {
        routeChoiceStore.record(
            RouteChoiceLog(
                atEpochMs = clock.millis(),
                chosenType = choice.chosenType,
                candidateCount = choice.candidateCount,
                score = choice.score,
                distanceMeters = choice.distanceMeters,
                durationSeconds = choice.durationSeconds,
                preference = choice.preference,
                destinationName = choice.destinationName,
            ),
        )
    }

    fun summarize(): RouteChoiceSummary {
        val logs: List<RouteChoiceLog> = routeChoiceStore.findRecent(ROUTE_CHOICE_CAP)
        return RouteChoiceSummary(
            total = logs.size,
            windowCap = ROUTE_CHOICE_CAP,
            byType = byType(logs),
            byPreference = byPreference(logs),
        )
    }

    private fun byType(logs: List<RouteChoiceLog>): List<RouteTypeCount> {
        val counts: MutableMap<RouteType, Int> = EnumMap(RouteType::class.java)
        for (log in logs) {
            counts[log.chosenType] = (counts[log.chosenType] ?: 0) + 1
        }
        val rows: ArrayList<RouteTypeCount> = ArrayList()
        for (type in RouteType.entries) {
            rows.add(RouteTypeCount(type = type, count = counts[type] ?: 0))
        }
        return rows
    }

    private fun byPreference(logs: List<RouteChoiceLog>): List<PreferenceCount> {
        val counts: MutableMap<SafetyPreference, Int> = EnumMap(SafetyPreference::class.java)
        for (log in logs) {
            counts[log.preference] = (counts[log.preference] ?: 0) + 1
        }
        val rows: ArrayList<PreferenceCount> = ArrayList()
        for (preference in SafetyPreference.entries) {
            rows.add(PreferenceCount(preference = preference, count = counts[preference] ?: 0))
        }
        return rows
    }
}
