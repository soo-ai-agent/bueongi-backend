package io.soo.bueongi.core.safetyspot.domain.status.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.safetyspot.domain.status.model.RegionName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class RegionNameCatalog(objectMapper: ObjectMapper) {
    private val log: Logger = LoggerFactory.getLogger(RegionNameCatalog::class.java)

    private val names: Map<String, RegionName> = load(objectMapper)

    fun findByCode(code: String): RegionName? = names[code] ?: standardCodeRegion(code) ?: sidoOnlyRegion(code)

    private fun sidoOnlyRegion(code: String): RegionName? {
        val sido: String = SIDO_ONLY_CODES[code] ?: return null
        return RegionName(sido = sido, name = code)
    }

    // 5자리 행정표준코드(시군구코드)는 이름표가 쓰는 7자리 개방자치단체코드와 다른 체계라 표에 없다.
    private fun standardCodeRegion(code: String): RegionName? {
        if (code.length != STANDARD_CODE_LENGTH) {
            return null
        }
        if (code.startsWith(MERGED_BLOCK_PREFIX)) {
            return RegionName(sido = mergedBlockSido(code), name = code)
        }
        val sido: String = STANDARD_SIDO_CODES[code.take(SIDO_CODE_LENGTH)] ?: return null
        return RegionName(sido = sido, name = code)
    }

    private fun mergedBlockSido(code: String): String {
        if (MERGED_BLOCK_GWANGJU.contains(code)) {
            return "광주광역시"
        }
        return "전라남도"
    }

    private fun load(objectMapper: ObjectMapper): Map<String, RegionName> {
        val resource = ClassPathResource(RESOURCE_PATH)
        if (!resource.exists()) {
            log.warn("자치단체 이름표를 찾지 못했습니다({}) — 코드로만 표시합니다.", RESOURCE_PATH)
            return emptyMap()
        }
        return try {
            val type =
                objectMapper.typeFactory
                    .constructMapType(LinkedHashMap::class.java, String::class.java, RegionName::class.java)
            val loaded: Map<String, RegionName> = resource.inputStream.use { objectMapper.readValue(it, type) }
            withCurrentSidoNames(loaded)
        } catch (e: Exception) {
            log.warn("자치단체 이름표를 읽지 못했습니다(코드로만 표시): {}", e.message)
            emptyMap()
        }
    }

    private fun withCurrentSidoNames(loaded: Map<String, RegionName>): Map<String, RegionName> {
        val normalized: LinkedHashMap<String, RegionName> = LinkedHashMap()
        for ((code, region) in loaded) {
            normalized[code] = region.copy(sido = currentSidoOf(region))
        }
        return normalized
    }

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

        // 광주·전남 통합 논의 때 쓰던 이름 — 현행 법정 시도가 아닌데 원천 이름표에 그대로 실려 온다.
        const val MERGED_GWANGJU_JEONNAM: String = "전남광주통합특별시"

        val GWANGJU_DISTRICTS: Set<String> = setOf("동구", "서구", "남구", "북구", "광산구")

        const val STANDARD_CODE_LENGTH: Int = 5
        const val SIDO_CODE_LENGTH: Int = 2

        // 강원(42→51)·전북(45→52)은 특별자치도 개편으로 코드가 바뀌어 원천마다 옛/새 코드가 섞여 온다.
        val STANDARD_SIDO_CODES: Map<String, String> =
            mapOf(
                "11" to "서울특별시",
                "26" to "부산광역시",
                "27" to "대구광역시",
                "28" to "인천광역시",
                "29" to "광주광역시",
                "30" to "대전광역시",
                "31" to "울산광역시",
                "36" to "세종특별자치시",
                "41" to "경기도",
                "42" to "강원특별자치도",
                "51" to "강원특별자치도",
                "43" to "충청북도",
                "44" to "충청남도",
                "45" to "전북특별자치도",
                "52" to "전북특별자치도",
                "46" to "전라남도",
                "47" to "경상북도",
                "48" to "경상남도",
                "50" to "제주특별자치도",
            )

        // 행정표준코드에 없는 `12` 블록은 광주 5구 + 전남 22시군을 묶어 쓰던 코드다.
        const val MERGED_BLOCK_PREFIX: String = "12"
        val MERGED_BLOCK_GWANGJU: Set<String> = setOf("12210", "12240", "12270", "12300", "12330")

        // 비상벨 원천만 쓰는 7자리 코드 — 앞자리가 시도와 1:1이 아니라 지점 좌표 중심으로 시도를 확인해 적었다.
        val SIDO_ONLY_CODES: Map<String, String> =
            buildMap {
                for (code in listOf("3490000", "3500000", "3560000")) {
                    put(code, "인천광역시")
                }
                for (code in listOf("3590000", "3600000", "3610000", "3620000", "3630000")) {
                    put(code, "광주광역시")
                }
                for (code in 480..500) {
                    put("${code}0000", "전라남도")
                }
            }

        // 강원 2023-06-11, 전북 2024-01-18 개편으로 바뀐 법정 이름.
        val SIDO_RENAMES: Map<String, String> =
            mapOf(
                "강원도" to "강원특별자치도",
                "전라북도" to "전북특별자치도",
            )
    }
}
