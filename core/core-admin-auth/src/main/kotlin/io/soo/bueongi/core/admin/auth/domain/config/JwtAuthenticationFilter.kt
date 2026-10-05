package io.soo.bueongi.core.admin.auth.domain.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.bueongi.core.admin.auth.domain.error.ExpiredTokenException
import io.soo.bueongi.core.admin.auth.domain.error.InvalidTokenException
import io.soo.bueongi.core.admin.auth.domain.error.MissingTokenException
import io.soo.bueongi.core.admin.auth.domain.service.JwtTokenVerifier
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
            writeAuthProblem(response, objectMapper, MissingTokenException())
            return
        }

        val verified: JwtTokenVerifier.VerifiedToken =
            try {
                verifier.verify(header.removePrefix("Bearer ").trim())
            } catch (e: ExpiredTokenException) {
                writeAuthProblem(response, objectMapper, e)
                return
            } catch (e: InvalidTokenException) {
                writeAuthProblem(response, objectMapper, e)
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
}
