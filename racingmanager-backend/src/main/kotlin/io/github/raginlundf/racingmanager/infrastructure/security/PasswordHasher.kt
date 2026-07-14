package io.github.raginlundf.racingmanager.infrastructure.security

import at.favre.lib.crypto.bcrypt.BCrypt

class PasswordHasher {

    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    fun hash(password: String): String {
        return hasher.hashToString(HASH_ITERATIONS, password.toCharArray())
    }

    fun verify(password: String, hash: String): Boolean {
        return verifier.verify(password.toCharArray(), hash).verified
    }

    companion object {
        private const val HASH_ITERATIONS = 12
    }
}
