package io.soo.bueongi.api.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * 관리자 페이지 인증 필터체인. `/api/admin` 하위만 `ROLE_ADMIN` 으로 보호하고, 나머지 경로
 * (기존 `/api`·`/share` 등)는 무인증으로 100% 보존한다.
 *
 * 필터체인 2개:
 *  - @Order(0) admin: `/api/admin` 하위 → STATELESS + `hasRole("ADMIN")` + JWT 필터. 401·403 은 bueongi 표준 JSON.
 *  - @Order(1) 나머지: 전 경로 permitAll. **이게 없으면 spring-security 기본 필터체인이 전체 앱을 잠근다.**
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JwtProperties::class, AdminAuthProperties::class)
class SecurityConfig(
    private val objectMapper: ObjectMapper,
) {

    /** JWT 검증기는 이 설정 안에서만 생성한다(공개키 fail-fast 가 웹 컨텍스트로 국한되도록). */
    @Bean
    fun jwtTokenVerifier(props: JwtProperties): JwtTokenVerifier = JwtTokenVerifier(props)

    @Bean
    fun adminTokenService(jwt: JwtProperties, auth: AdminAuthProperties): AdminTokenService =
        AdminTokenService(jwt, auth)

    @Bean
    fun adminAuthService(auth: AdminAuthProperties, tokens: AdminTokenService): AdminAuthService =
        AdminAuthService(auth, tokens)

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

    /** 인증 실패(토큰 없음/무효) → 401. 필터가 대개 먼저 처리하지만 방어적으로 둔다. */
    private fun unauthorizedEntryPoint() = AuthenticationEntryPoint { _, response, _ ->
        AuthProblemWriter.write(
            response = response,
            objectMapper = objectMapper,
            status = HttpStatus.UNAUTHORIZED,
            code = "UNAUTHORIZED",
            title = "Unauthorized",
            detail = "인증 토큰이 필요합니다.",
        )
    }

    /** 인증됐으나 ROLE_ADMIN 이 아님 → 403. */
    private fun forbiddenHandler() = AccessDeniedHandler { _, response, _ ->
        AuthProblemWriter.write(
            response = response,
            objectMapper = objectMapper,
            status = HttpStatus.FORBIDDEN,
            code = "FORBIDDEN",
            title = "Forbidden",
            detail = "관리자 권한이 필요합니다.",
        )
    }
}
