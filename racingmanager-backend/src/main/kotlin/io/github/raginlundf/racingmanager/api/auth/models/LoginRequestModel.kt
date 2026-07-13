package io.github.raginlundf.racingmanager.api.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestModel(
    val username: String,
    val password: String,
    /** Disambiguates a username that collides across tenants. Not needed while
        the username happens to be unique (e.g. a single-tenant local install). */
    val tenantSlug: String? = null,
)
