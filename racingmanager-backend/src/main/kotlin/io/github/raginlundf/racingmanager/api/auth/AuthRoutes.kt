package io.github.raginlundf.racingmanager.api.auth

import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.LoginRequestModel
import io.github.raginlundf.racingmanager.api.auth.models.LoginResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.LogoutRequestModel
import io.github.raginlundf.racingmanager.api.auth.models.RefreshRequestModel
import io.github.raginlundf.racingmanager.api.auth.models.RefreshResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.RegisterRequestModel
import io.github.raginlundf.racingmanager.api.auth.models.RegisterResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.SessionResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.SetupRequestModel
import io.github.raginlundf.racingmanager.api.auth.models.SetupResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.SetupStatusResponseModel
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.LoginResult
import io.github.raginlundf.racingmanager.application.auth.RefreshResult
import io.github.raginlundf.racingmanager.application.auth.RegisterResult
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.authRoutes(authService: AuthService, jwtService: JwtService, deploymentMode: DeploymentMode) {
    post("/api/v1/register") {
        if (deploymentMode != DeploymentMode.HOSTED) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel("NOT_HOSTED", "Tenant registration is only available in hosted mode"),
            )
            return@post
        }
        val request = call.receive<RegisterRequestModel>()
        when (val result = authService.register(request.tenantName, request.tenantSlug, request.username, request.password, request.displayName)) {
            is RegisterResult.Success -> {
                call.respond(
                    status = HttpStatusCode.Created,
                    message = RegisterResponseModel(
                        tenantId = result.tenant.id.toString(),
                        tenantSlug = result.tenant.slug ?: request.tenantSlug,
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken,
                        expiresIn = result.expiresInSeconds,
                        scopes = result.scopes.toList(),
                        userId = result.user.id.toString(),
                        username = result.user.username,
                        displayName = result.user.displayName,
                        role = result.user.role.name,
                    ),
                )
            }
            is RegisterResult.SlugTaken -> {
                call.respond(
                    status = HttpStatusCode.Conflict,
                    message = ErrorResponseModel("TENANT_SLUG_TAKEN", "A tenant with this slug already exists"),
                )
            }
        }
    }

    get("/api/v1/auth/setup-status") {
        call.respond(SetupStatusResponseModel(firstRun = authService.isFirstRun(), mode = deploymentMode.name))
    }

    post("/api/v1/auth/setup") {
        if (deploymentMode != DeploymentMode.LOCAL) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel("NOT_LOCAL_MODE", "Admin setup is only available in local mode"),
            )
            return@post
        }
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
        when (val result = authService.login(request.username, request.password, request.tenantSlug, correlationId)) {
            is LoginResult.Success -> {
                call.respond(
                    LoginResponseModel(
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken,
                        expiresIn = result.expiresInSeconds,
                        tenantId = result.tenantId.toString(),
                        scopes = result.scopes.toList(),
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

    post("/api/v1/auth/refresh") {
        val request = call.receive<RefreshRequestModel>()
        when (val result = authService.refresh(request.refreshToken)) {
            is RefreshResult.Success -> {
                call.respond(RefreshResponseModel(accessToken = result.accessToken, expiresIn = result.expiresInSeconds))
            }
            is RefreshResult.Invalid -> {
                call.respond(
                    status = HttpStatusCode.Unauthorized,
                    message = ErrorResponseModel("INVALID_REFRESH_TOKEN", "Refresh token is invalid, expired, or revoked"),
                )
            }
        }
    }

    get("/api/v1/auth/session") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        val user = authService.currentUser(principal.userId)
            ?: return@get call.respond(
                status = HttpStatusCode.Unauthorized,
                message = ErrorResponseModel("USER_NOT_FOUND", "User no longer exists"),
            )
        call.respond(
            SessionResponseModel(
                userId = user.id.toString(),
                username = user.username,
                displayName = user.displayName,
                role = user.role.name,
            ),
        )
    }

    post("/api/v1/auth/logout") {
        val request = runCatching { call.receive<LogoutRequestModel>() }.getOrDefault(LogoutRequestModel())
        val correlationId = call.request.headers["X-Correlation-Id"]
        authService.logout(request.refreshToken, correlationId)
        call.respond(status = HttpStatusCode.NoContent, message = Unit)
    }
}
