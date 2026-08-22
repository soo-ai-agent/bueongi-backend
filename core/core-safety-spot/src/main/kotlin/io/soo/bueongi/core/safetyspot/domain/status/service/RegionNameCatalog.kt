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

    fun findByCode(code: String): RegionName? =
        names[code] ?: standardCodeRegion(code) ?: sidoOnlyRegion(code)

    /**
     * 이름표에도 표준코드에도 없지만 시도만은 아는 코드. 자치단체 이름을 모르니 코드를 그대로 보여 준다.
     */
    private fun sidoOnlyRegion(code: String): RegionName? {
        val sido: String = SIDO_ONLY_CODES[code] ?: return null
        return RegionName(sido = sido, name = code)
    }

    /**
     * 5자리 행정표준코드(시군구코드)는 이름표가 쓰는 7자리 개방자치단체코드와 **다른 체계**라
     * 표에 없다. 앞 2자리가 시도를 뜻하도록 표준이 정해 놓았으니 시도만 채운다 —
     * 자치단체 이름은 원천이 코드만 주므로 코드를 그대로 보여 준다.
     */
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

        const val STANDARD_CODE_LENGTH: Int = 5
        const val SIDO_CODE_LENGTH: Int = 2

        /**
         * 행정표준코드 시군구코드의 앞 2자리 = 시도. 강원(42→51)·전북(45→52) 은 특별자치도로
         * 바뀌며 코드가 늘어, 원천마다 옛 코드와 새 코드가 섞여 와서 둘 다 적는다.
         */
        val STANDARD_SIDO_CODES: Map<String, String> = mapOf(
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

        /**
         * 표준에 없는 `12` 블록은 광주·전남을 묶어 쓰던 코드다 — 27개가 전남 22시군 + 광주 5구와
         * 정확히 맞고, 아래 다섯 개만 좌표 중심이 광주 안(위도 35.12~35.19·경도 126.79~126.93)이다.
         * 나머지 22개는 목포(34.80,126.40)·여수(34.66,127.63)처럼 전남에 흩어져 있다.
         */
        const val MERGED_BLOCK_PREFIX: String = "12"
        val MERGED_BLOCK_GWANGJU: Set<String> = setOf("12210", "12240", "12270", "12300", "12330")

        /**
         * 비상벨 원천만 쓰는 7자리 코드 29개 — 이름표에도 없고, 7자리 코드는 앞자리가 시도와
         * 1:1 이 아니라 접두로도 못 가린다. 지점 좌표의 중심으로 시도를 확인해 적었다.
         *
         * 인천 3(위도 37.47~37.55) · 광주 5(35.10~35.19, 126.78~126.93) ·
         * 전남 21(34.39~35.30, 126.24~127.70). 자치단체 이름은 원천이 주지 않아 채우지 않는다.
         */
        val SIDO_ONLY_CODES: Map<String, String> = buildMap {
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

        /** 옛 이름 → 현재 법정 이름(강원 2023-06-11, 전북 2024-01-18 개편). */
        val SIDO_RENAMES: Map<String, String> = mapOf(
            "강원도" to "강원특별자치도",
            "전라북도" to "전북특별자치도",
        )
    }
}
