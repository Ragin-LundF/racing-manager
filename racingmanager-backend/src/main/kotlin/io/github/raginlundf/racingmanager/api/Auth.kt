package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SessionResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.util.UUID

suspend fun ApplicationCall.authenticateRequest(authService: AuthService): SessionResult.Valid? {
    val sessionId = request.headers["X-Session-Id"]
        ?: return null.also {
            respond(
                status = HttpStatusCode.Unauthorized,
                message = ErrorResponseModel("MISSING_SESSION", "Session ID is required"),
            )
        }

    val result = authService.getSession(UUID.fromString(sessionId))
    if (result !is SessionResult.Valid) {
        respond(
            status = HttpStatusCode.Unauthorized,
            message = ErrorResponseModel("SESSION_EXPIRED", "Session has expired"),
        )
        return null
    }
    return result
}
