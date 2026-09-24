package io.soo.bueongi.core.admin.auth.domain.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.admin.auth.domain.error.ExpiredTokenException
import io.soo.bueongi.core.admin.auth.domain.error.InvalidTokenException
import io.soo.bueongi.core.admin.auth.domain.service.JwtTokenVerifier
import io.soo.bueongi.core.admin.auth.domain.service.writeAuthProblem
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val verifier: JwtTokenVerifier,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header: String? = request.getHeader("Authorization")
        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "인증 토큰이 필요합니다.")
            return
        }

        val verified: JwtTokenVerifier.VerifiedToken =
            try {
                verifier.verify(header.removePrefix("Bearer ").trim())
            } catch (_: ExpiredTokenException) {
                writeUnauthorized(response, "인증 토큰이 만료되었습니다.")
                return
            } catch (_: InvalidTokenException) {
                writeUnauthorized(response, "유효하지 않은 인증 토큰입니다.")
                return
            }

        val authorities: List<SimpleGrantedAuthority> = verified.roles.map { SimpleGrantedAuthority(it) }
        val authentication = UsernamePasswordAuthenticationToken(verified.userId, null, authorities)
        SecurityContextHolder.getContext().authentication = authentication
        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun writeUnauthorized(response: HttpServletResponse, detail: String) {
        writeAuthProblem(
            response = response,
            objectMapper = objectMapper,
            status = HttpStatus.UNAUTHORIZED,
            code = "UNAUTHORIZED",
            title = "Unauthorized",
            detail = detail,
        )
    }
}
