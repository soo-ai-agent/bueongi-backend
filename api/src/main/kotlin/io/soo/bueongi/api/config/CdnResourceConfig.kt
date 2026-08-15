package io.soo.bueongi.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Path
import java.time.Duration

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
