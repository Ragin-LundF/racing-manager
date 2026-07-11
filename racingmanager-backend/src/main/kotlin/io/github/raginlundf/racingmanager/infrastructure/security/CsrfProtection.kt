package io.github.raginlundf.racingmanager.infrastructure.security

import java.security.SecureRandom
import java.util.Base64

class CsrfProtection {

    private val secureRandom = SecureRandom()

    fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
