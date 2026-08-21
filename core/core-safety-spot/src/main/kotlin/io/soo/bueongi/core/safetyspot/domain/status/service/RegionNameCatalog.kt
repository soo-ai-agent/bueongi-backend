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
            resource.inputStream.use { objectMapper.readValue(it, type) }
        } catch (e: Exception) {
            log.warn("자치단체 이름표를 읽지 못했습니다(코드로만 표시): {}", e.message)
            emptyMap()
        }
    }

    private companion object {
        const val RESOURCE_PATH: String = "region-names.json"
    }
}
