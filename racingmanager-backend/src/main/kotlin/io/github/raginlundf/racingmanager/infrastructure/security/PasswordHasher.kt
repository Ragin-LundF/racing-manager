package io.github.raginlundf.racingmanager.infrastructure.security

import at.favre.lib.crypto.bcrypt.BCrypt

class PasswordHasher {

    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    fun hash(password: String): String =
        hasher.hashToString(12, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        verifier.verify(password.toCharArray(), hash).verified
}
