package io.soo.bueongi.api.auth

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `Authorization: Bearer <jwt>` 를 검증해 SecurityContext 를 채운다. SecurityConfig 의 admin 필터체인
 * (securityMatcher `/api/admin` 하위)에만 등록되므로 별도 shouldNotFilter 는 불필요하다.
 *
 * roles 클레임을 그대로 authority 로 매핑한다(ROLE_ 하드코딩 금지). auth-service 가 `roles=["ROLE_ADMIN"]`
 * 을 발급하면 SecurityConfig 의 `hasRole("ADMIN")` 이 이 authority 와 매칭된다.
 * Bearer 부재·검증 실패 시 bueongi 표준 401(problem+json)로 체인을 중단한다.
 */
class JwtAuthenticationFilter(
    private val verifier: JwtTokenVerifier,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "인증 토큰이 필요합니다.")
            return
        }

        val verified = try {
            verifier.verify(header.removePrefix("Bearer ").trim())
        } catch (e: ExpiredTokenException) {
            writeUnauthorized(response, "인증 토큰이 만료되었습니다.")
            return
        } catch (e: InvalidTokenException) {
            writeUnauthorized(response, "유효하지 않은 인증 토큰입니다.")
            return
        }

        val authorities = verified.roles.map { SimpleGrantedAuthority(it) }
        val authentication = UsernamePasswordAuthenticationToken(verified.userId, null, authorities)
        SecurityContextHolder.getContext().authentication = authentication
        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun writeUnauthorized(response: HttpServletResponse, detail: String) {
        AuthProblemWriter.write(
            response = response,
            objectMapper = objectMapper,
            status = HttpStatus.UNAUTHORIZED,
            code = "UNAUTHORIZED",
            title = "Unauthorized",
            detail = detail,
        )
    }
}
