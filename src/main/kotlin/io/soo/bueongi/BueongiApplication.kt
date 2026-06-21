package io.soo.bueongi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.runApplication

/**
 * 안전 귀가 앱(bueongi) 백엔드 진입점.
 *
 * CCTV·비상벨·밝은 길 중심으로 안심 귀가 경로를 안내하는 MVP 의 API 서버.
 * 프론트(포트 3619)가 mock(클라이언트 사이드)으로 쓰던 장소검색/경로비교/안심시설을
 * 동일 계약(시그니처)으로 교체할 수 있게 실제 엔드포인트를 제공한다. 독립 실행 포트 8119.
 *
 * [RedisAutoConfiguration] 제외: 부트 기본 자동설정은 `bueongi.share.redis-url` 미지정 시에도 `localhost:6379` 로
 * 연결을 시도한다. 위치 공유 저장소는 [io.soo.bueongi.share.ShareStoreConfig] 가 URL 주입 시에만 명시적으로
 * Redis 접속 팩토리를 만들고, 미주입(로컬/CI/MVP)이면 인메모리로 폴백하므로 자동설정을 끈다.
 */
@SpringBootApplication(exclude = [RedisAutoConfiguration::class])
class BueongiApplication

fun main(args: Array<String>) {
    runApplication<BueongiApplication>(*args)
}
