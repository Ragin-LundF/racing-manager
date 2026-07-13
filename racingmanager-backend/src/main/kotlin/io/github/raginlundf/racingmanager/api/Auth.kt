package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.application.auth.RequestPrincipal
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

private const val BEARER_PREFIX = "Bearer "

/** The single choke point every protected route calls to resolve the caller's
    [RequestPrincipal] from an `Authorization: Bearer <token>` header. Responds
    with 401 and returns null on any missing/invalid/expired token — callers
    treat that uniformly via `?: return@handler`. */
suspend fun ApplicationCall.authenticateRequest(jwtService: JwtService): RequestPrincipal? {
    val header = request.headers["Authorization"]
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
        respond(
            status = HttpStatusCode.Unauthorized,
            message = ErrorResponseModel(code = "MISSING_TOKEN", message = "A Bearer access token is required"),
        )
        return null
    }

    val principal = jwtService.verifyAccessToken(header.removePrefix(BEARER_PREFIX))
    if (principal == null) {
        respond(
            status = HttpStatusCode.Unauthorized,
            message = ErrorResponseModel(code = "INVALID_TOKEN", message = "Access token is invalid or expired"),
        )
        return null
    }
    return principal
}
