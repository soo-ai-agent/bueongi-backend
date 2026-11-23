package io.soo.bueongi.core.admin.auth.domain.config

import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

fun parseJwtPublicKey(pem: String): RSAPublicKey {
    val stripped: String =
        pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
    val decoded: ByteArray = Base64.getDecoder().decode(stripped)
    val keySpec: X509EncodedKeySpec = X509EncodedKeySpec(decoded)
    return KeyFactory.getInstance("RSA").generatePublic(keySpec) as RSAPublicKey
}
