package io.soo.bueongi.api

import io.jsonwebtoken.Jwts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Date

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthGateTest(
    @param:Autowired private val restTemplate: TestRestTemplate,
    @param:LocalServerPort private val port: Int,
) {

    private fun url(path: String) = "http://localhost:$port$path"

    private fun get(path: String, token: String? = null): org.springframework.http.ResponseEntity<String> {
        val headers = HttpHeaders()
        if (token != null) {
            headers.setBearerAuth(token)
        }
        return restTemplate.exchange(url(path), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
    }

    @Test
    fun `토큰 없이 admin 접근하면 401`() {
        val response = get("/api/admin/me")

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(response.body).contains("\"code\":\"UNAUTHORIZED\"")
    }

    @Test
    fun `ROLE_USER 토큰으로 admin 접근하면 403`() {
        val response = get("/api/admin/me", token(roles = listOf("ROLE_USER")))

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(response.body).contains("\"code\":\"FORBIDDEN\"")
    }

    @Test
    fun `ROLE_ADMIN 토큰으로 admin 접근하면 200 과 principal`() {
        val response = get("/api/admin/me", token(subject = "7", roles = listOf("ROLE_ADMIN")))

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("\"userId\":7")
        assertThat(response.body).contains("ROLE_ADMIN")
    }

    @Test
    fun `public health 는 토큰 없이 200 유지된다`() {
        val response = get("/api/health")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("\"status\":\"UP\"")
    }

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        @JvmStatic
        @DynamicPropertySource
        fun jwtKey(registry: DynamicPropertyRegistry) {
            registry.add("bueongi.jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
            registry.add("bueongi.jwt.private-key") { Base64.getEncoder().encodeToString(keyPair.private.encoded) }
            registry.add("bueongi.admin-auth.login-id") { "admin" }
            registry.add("bueongi.admin-auth.password-hash") {
                "\$2y\$10\$COO/27/JNmvhln4FMxHDveBYG3xOHtG8Bh/uP2LUEUAgt9X.8r06e"
            }
        }

        private fun token(subject: String = "1", roles: List<String>): String =
            Jwts.builder()
                .issuer("bueongi")
                .subject(subject)
                .claim("roles", roles)
                .claim("tokenKind", "ACCESS")
                .issuedAt(Date())
                .expiration(Date(System.currentTimeMillis() + 60_000))
                .signWith(keyPair.private, Jwts.SIG.RS256)
                .compact()
    }
}
