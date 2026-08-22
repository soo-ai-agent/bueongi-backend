package io.soo.bueongi.core.safetyspot.domain.status.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.safetyspot.domain.status.model.RegionName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * 자치단체 코드를 사람이 읽는 이름으로 옮긴다.
 *
 * 표는 원천 CSV(CCTV·보안등·비상벨)의 `개방자치단체코드`+`관리기관명` 에서 뽑았다 —
 * 코드 체계에서 시도를 **추론하지 않는다**(앞자리가 시도와 1:1 이 아니라 실제로 충돌한다).
 * 모르는 코드는 이름 대신 코드를 그대로 보여 준다.
 */
@Component
class RegionNameCatalog(objectMapper: ObjectMapper) {
    private val log: Logger = LoggerFactory.getLogger(RegionNameCatalog::class.java)

    private val names: Map<String, RegionName> = load(objectMapper)

    fun findByCode(code: String): RegionName? = names[code]

    private fun load(objectMapper: ObjectMapper): Map<String, RegionName> {
        val resource = ClassPathResource(RESOURCE_PATH)
        if (!resource.exists()) {
            log.warn("자치단체 이름표를 찾지 못했습니다({}) — 코드로만 표시합니다.", RESOURCE_PATH)
            return emptyMap()
        }
        return try {
            val type = objectMapper.typeFactory
                .constructMapType(LinkedHashMap::class.java, String::class.java, RegionName::class.java)
            val loaded: Map<String, RegionName> = resource.inputStream.use { objectMapper.readValue(it, type) }
            withCurrentSidoNames(loaded)
        } catch (e: Exception) {
            log.warn("자치단체 이름표를 읽지 못했습니다(코드로만 표시): {}", e.message)
            emptyMap()
        }
    }

    /**
     * 원천이 적어 준 시도를 현행 법정 시도로 맞춘다. 두 가지를 푼다 —
     * 옛 이름(화천군만 '강원도')과, 두 시도를 묶어 부른 이름('전남광주통합특별시').
     *
     * **시도를 안 적어 준 곳은 그대로 비워 둔다** — 이름으로 짐작해 채우지 않는다.
     */
    private fun withCurrentSidoNames(loaded: Map<String, RegionName>): Map<String, RegionName> {
        val normalized: LinkedHashMap<String, RegionName> = LinkedHashMap()
        for ((code, region) in loaded) {
            normalized[code] = region.copy(sido = currentSidoOf(region))
        }
        return normalized
    }

    /**
     * 원천이 광주와 전남을 한 이름으로 묶어 적은 것은 개명이 아니라 **두 시도를 합쳐 부른 것**이라,
     * 이름표를 바꾸는 것만으로는 못 푼다. 자치단체 이름으로 갈라 현재 법정 시도에 돌려 놓는다.
     */
    private fun currentSidoOf(region: RegionName): String {
        if (region.sido == MERGED_GWANGJU_JEONNAM) {
            if (GWANGJU_DISTRICTS.contains(region.name)) {
                return "광주광역시"
            }
            return "전라남도"
        }
        return SIDO_RENAMES[region.sido] ?: region.sido
    }

    private companion object {
        const val RESOURCE_PATH: String = "region-names.json"

        /** 통합 논의 때 쓰던 이름 — 현행 법정 시도가 아니고, 이 표에서 22곳만 이렇게 적혀 있었다. */
        const val MERGED_GWANGJU_JEONNAM: String = "전남광주통합특별시"

        /**
         * 묶인 이름 안에서 광주 쪽인 자치구. 이름이 흔해도(동구·서구…) 이 묶음 안에서만 보므로
         * 다른 시도의 같은 이름과 섞이지 않는다. 광산구는 원천이 이미 광주광역시로 적어 여기 없다.
         */
        val GWANGJU_DISTRICTS: Set<String> = setOf("동구", "서구", "남구", "북구", "광산구")

        /** 옛 이름 → 현재 법정 이름(강원 2023-06-11, 전북 2024-01-18 개편). */
        val SIDO_RENAMES: Map<String, String> = mapOf(
            "강원도" to "강원특별자치도",
            "전라북도" to "전북특별자치도",
        )
    }
}
