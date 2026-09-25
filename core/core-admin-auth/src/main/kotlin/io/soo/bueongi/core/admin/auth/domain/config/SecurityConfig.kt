package io.soo.bueongi.core.admin.auth.domain.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.admin.auth.domain.model.JwtKeyMaterial
import io.soo.bueongi.core.admin.auth.domain.service.AdminAuthService
import io.soo.bueongi.core.admin.auth.domain.service.AdminTokenService
import io.soo.bueongi.core.admin.auth.domain.service.JwtTokenVerifier
import io.soo.bueongi.core.admin.auth.domain.service.createJwtKeyMaterial
import io.soo.bueongi.core.admin.auth.domain.service.writeAuthProblem
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

// 필터체인 2개 — @Order(1) 전 경로 permitAll 체인이 없으면 spring-security 기본 필터체인이 전체 앱을 잠근다.
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JwtProperties::class, AdminAuthProperties::class)
class SecurityConfig(private val objectMapper: ObjectMapper) {
    @Bean
    fun jwtKeyMaterial(props: JwtProperties, environment: Environment): JwtKeyMaterial {
        val isLocalProfile: Boolean = environment.activeProfiles.contains("local")
        return createJwtKeyMaterial(props, isLocalProfile)
    }

    @Bean
    fun jwtTokenVerifier(keys: JwtKeyMaterial): JwtTokenVerifier = JwtTokenVerifier(keys.publicKey, keys.issuer)

    @Bean
    fun adminTokenService(keys: JwtKeyMaterial, auth: AdminAuthProperties): AdminTokenService = AdminTokenService(keys, auth)

    @Bean
    fun adminAuthService(auth: AdminAuthProperties, tokens: AdminTokenService): AdminAuthService = AdminAuthService(auth, tokens)

    @Bean
    @Order(0)
    fun adminSecurityFilterChain(http: HttpSecurity, verifier: JwtTokenVerifier): SecurityFilterChain {
        http
            .securityMatcher("/api/admin/**")
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().hasRole("ADMIN") }
            .addFilterBefore(
                JwtAuthenticationFilter(verifier, objectMapper),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .exceptionHandling {
                it.authenticationEntryPoint(unauthorizedEntryPoint())
                it.accessDeniedHandler(forbiddenHandler())
            }
        return http.build()
    }

    @Bean
    @Order(1)
    fun publicSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    private fun unauthorizedEntryPoint() =
        AuthenticationEntryPoint { _, response, _ ->
            writeAuthProblem(
                response = response,
                objectMapper = objectMapper,
                status = HttpStatus.UNAUTHORIZED,
                code = "UNAUTHORIZED",
                title = "Unauthorized",
                detail = "인증 토큰이 필요합니다.",
            )
        }

    private fun forbiddenHandler() =
        AccessDeniedHandler { _, response, _ ->
            writeAuthProblem(
                response = response,
                objectMapper = objectMapper,
                status = HttpStatus.FORBIDDEN,
                code = "FORBIDDEN",
                title = "Forbidden",
                detail = "관리자 권한이 필요합니다.",
            )
        }
}
