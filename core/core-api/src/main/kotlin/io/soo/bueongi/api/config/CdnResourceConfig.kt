package io.soo.bueongi.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Path
import java.time.Duration

/**
 * 안심시설 정적 JSON(CDN 산출물)을 백엔드가 `/cdn` 하위 경로로 직접 서빙한다.
 *
 * 동기(배포 단순화): 프론트는 `VITE_CDN_BASE_URL` 로 police/all.json 등 정적 데이터를 읽는데,
 * dev 는 vite 미들웨어가 `../cdn` 을 서빙하지만 운영 컨테이너에는 그 미들웨어가 없다.
 * 시설 데이터 디렉터리(`bueongi.facility.data-dir`)는 어차피 백엔드 이미지에 들어가므로,
 * 같은 디렉터리를 HTTP 로도 노출하면 별도 CDN 인프라 없이 nginx `/cdn` 프록시 한 줄로
 * 단일 출처(데이터 파일 = 점수 계산 입력 = 프론트 표시 데이터)가 유지된다.
 *
 * - 데이터는 전부 공개 공공데이터 산출물이라 노출 자체는 무해하다.
 * - 경로 탈출은 Spring PathResourceResolver 가 기본 차단한다.
 * - 월 1회 갱신 데이터이므로 짧은 public 캐시(30분)를 준다.
 */
@Configuration
class CdnResourceConfig(
    @Value("\${bueongi.facility.data-dir:../cdn}") private val dataDir: String,
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val root: Path = Path.of(dataDir).toAbsolutePath().normalize()
        val rootUri: String = root.toUri().toString()
        val location: String = if (rootUri.endsWith("/")) {
            rootUri
        } else {
            "$rootUri/"
        }
        registry.addResourceHandler("/cdn/**")
            .addResourceLocations(location)
            .setCacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic())
    }
}
