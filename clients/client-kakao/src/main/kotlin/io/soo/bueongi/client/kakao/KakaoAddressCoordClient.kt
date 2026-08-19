package io.soo.bueongi.client.kakao

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Kakao 주소 → 좌표 변환(지오코딩). 좌표 없이 주소만 주는 공공데이터(서울 보안등 등)를
 * 지도·안전 점수에 쓸 수 있게 살린다. 장소검색과 같은 REST 키를 쓴다.
 *
 * **일일 상한을 스스로 지킨다** — 이 키는 사용자 장소검색도 쓰므로, 배경 지오코딩이
 * 쿼터를 다 태우면 검색이 시드 폴백으로 떨어진다. 상한에 닿으면 배치 전체를 실패로
 * 돌려(null) 호출자가 내일 다시 시도하게 한다.
 */
@Component
class KakaoAddressCoordClient(
    @Value("\${bueongi.places.kakao-rest-api-key:}") apiKey: String,
    restClientBuilder: RestClient.Builder,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log: Logger = LoggerFactory.getLogger(KakaoAddressCoordClient::class.java)
    private val apiKey: String = apiKey.trim()

    val isEnabled: Boolean
        get() = apiKey.isNotEmpty()

    private val restClient: RestClient = restClientBuilder.clone()
        .baseUrl(BASE_URL)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK ${this.apiKey}")
        .build()

    private var usedToday: Int = 0

    // 자정을 지나면 계수를 0 부터 다시 센다
    private var usedOn: LocalDate = today()

    /**
     * 주소 여러 건을 좌표로 바꾼다. 반환 = 주소 → 좌표(못 찾은 주소는 빠진다).
     *
     * 계약: null = 배치 실패(일일 상한 도달·통신 오류) — 호출자는 저장하지 말고 나중에
     * 다시 시도해야 한다. 통신 오류에 즉시 중단하는 이유는, 원천이 죽었을 때 남은 수천
     * 건을 타임아웃까지 기다리며 도는 것을 막기 위해서다.
     */
    @Synchronized
    fun geocodeAll(addresses: Collection<String>): Map<String, KakaoAddressPoint>? {
        check(isEnabled) { "Kakao REST API key 미설정 — isEnabled 확인 후 호출해야 한다." }
        rollOverDay()
        if (usedToday + addresses.size > DAILY_LIMIT) {
            log.warn("지오코딩 일일 상한 — 오늘 {}건 사용, {}건 요청은 내일로 미룬다", usedToday, addresses.size)
            return null
        }
        val found: LinkedHashMap<String, KakaoAddressPoint> = LinkedHashMap()
        for (address in addresses) {
            usedToday += 1
            val point: KakaoAddressPoint? = try {
                fetchCoord(address)
            } catch (e: Exception) {
                log.warn("지오코딩 통신 실패 — 배치 중단({}건 변환 뒤): {}", found.size, e.message)
                return null
            }
            if (point != null) {
                found[address] = point
            }
        }
        return found
    }

    private fun fetchCoord(address: String): KakaoAddressPoint? {
        val response: KakaoAddressResponse? = restClient.get()
            .uri { builder ->
                builder.path(ADDRESS_SEARCH_PATH)
                    .queryParam("query", address)
                    .queryParam("size", 1)
                    .build()
            }
            .retrieve()
            .body(KakaoAddressResponse::class.java)
        val document: KakaoAddressDocument = response?.documents?.firstOrNull() ?: return null
        val lat: Double = document.y?.toDoubleOrNull() ?: return null
        val lng: Double = document.x?.toDoubleOrNull() ?: return null
        return KakaoAddressPoint(lat = lat, lng = lng)
    }

    private fun rollOverDay() {
        val now: LocalDate = today()
        if (now != usedOn) {
            usedOn = now
            usedToday = 0
        }
    }

    private fun today(): LocalDate =
        LocalDate.now(clock.withZone(SERVICE_ZONE))

    private companion object {
        const val BASE_URL: String = "https://dapi.kakao.com"

        const val ADDRESS_SEARCH_PATH: String = "/v2/local/search/address.json"

        /** Kakao 일일 쿼터(약 10만) 중 배경 지오코딩 몫 — 사용자 장소검색 몫을 남긴다. */
        const val DAILY_LIMIT: Int = 30_000

        /** 상한의 "하루"는 서비스 시간대 기준이어야 사람이 예측할 수 있다. */
        val SERVICE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
