package io.github.raginlundf.racingmanager.api.auth

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.LoginRequestModel
import io.github.raginlundf.racingmanager.api.auth.models.LoginResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.SessionResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.SetupRequestModel
import io.github.raginlundf.racingmanager.api.auth.models.SetupResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.SetupStatusResponseModel
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.LoginResult
import io.github.raginlundf.racingmanager.application.auth.SessionResult
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.authRoutes(authService: AuthService) {
    get("/api/v1/auth/setup-status") {
        val firstRun = authService.isFirstRun()
        call.respond(SetupStatusResponseModel(firstRun = firstRun))
    }

    post("/api/v1/auth/setup") {
        val request = call.receive<SetupRequestModel>()
        when (val result = authService.setupAdmin(request.username, request.password, request.displayName)) {
            is SetupResult.Success -> {
                call.respond(
                    status = HttpStatusCode.Created,
                    message = SetupResponseModel(
                        userId = result.user.id.toString(),
                        username = result.user.username,
                        displayName = result.user.displayName,
                    ),
                )
            }
            is SetupResult.AlreadySetup -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel(
                        code = "ALREADY_SETUP",
                        message = "System has already been set up",
                    ),
                )
            }
        }
    }

    post("/api/v1/auth/login") {
        val request = call.receive<LoginRequestModel>()
        val correlationId = call.request.headers["X-Correlation-Id"]
        when (val result = authService.login(request.username, request.password, correlationId)) {
            is LoginResult.Success -> {
                call.respond(
                    LoginResponseModel(
                        sessionId = result.session.id.toString(),
                        userId = result.user.id.toString(),
                        username = result.user.username,
                        displayName = result.user.displayName,
                        role = result.user.role.name,
                    ),
                )
            }
            is LoginResult.InvalidCredentials -> {
                call.respond(
                    status = HttpStatusCode.Unauthorized,
                    message = ErrorResponseModel(
                        code = "INVALID_CREDENTIALS",
                        message = "Invalid username or password",
                    ),
                )
            }
        }
    }

    get("/api/v1/auth/session") {
        val sessionId = call.request.headers["X-Session-Id"]
            ?: return@get call.respond(
                status = HttpStatusCode.Unauthorized,
                message = ErrorResponseModel(
                    code = "MISSING_SESSION",
                    message = "Session ID is required",
                ),
            )

        val result = authService.getSession(UUID.fromString(sessionId))
        when (result) {
            is SessionResult.Valid -> {
                call.respond(
                    SessionResponseModel(
                        userId = result.user.id.toString(),
                        username = result.user.username,
                        displayName = result.user.displayName,
                        role = result.user.role.name,
                    ),
                )
            }
            is SessionResult.Expired -> {
                call.respond(
                    status = HttpStatusCode.Unauthorized,
                    message = ErrorResponseModel(
                        code = "SESSION_EXPIRED",
                        message = "Session has expired",
                    ),
                )
            }
            is SessionResult.NotFound -> {
                call.respond(
                    status = HttpStatusCode.Unauthorized,
                    message = ErrorResponseModel(
                        code = "SESSION_NOT_FOUND",
                        message = "Session not found",
                    ),
                )
            }
        }
    }

    post("/api/v1/auth/logout") {
        val sessionId = call.request.headers["X-Session-Id"]
        if (sessionId != null) {
            val correlationId = call.request.headers["X-Correlation-Id"]
            authService.logout(UUID.fromString(sessionId), correlationId)
        }
        call.respond(status = HttpStatusCode.NoContent, message = Unit)
    }
}
