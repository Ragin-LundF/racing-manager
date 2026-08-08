package io.github.raginlundf.racingmanager.api.auth

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
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.LoginResult
import io.github.raginlundf.racingmanager.application.auth.RefreshResult
import io.github.raginlundf.racingmanager.application.auth.RegisterResult
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.authRoutes(authService: AuthService, jwtService: JwtService, deploymentMode: DeploymentMode) {
    registerRoutes(authService = authService, deploymentMode = deploymentMode)
    setupRoutes(authService = authService, deploymentMode = deploymentMode)
    loginRoutes(authService = authService)
    sessionRoutes(authService = authService, jwtService = jwtService)
}

/** Public tenant registration. Always open in [DeploymentMode.HOSTED].
    In [DeploymentMode.LOCAL] it is the first-run alternative to
    `/api/v1/auth/setup`: a freshly installed offline instance carries no data
    at all, so refusing registration outright would leave no way in. It closes
    permanently once the first user exists — a local instance is normally
    reachable from the whole LAN (spectator view, race device), and nobody on
    that network may self-register an administrator after the fact. Further
    users then come from `/api/v1/tenant/users`. */
private fun Route.registerRoutes(authService: AuthService, deploymentMode: DeploymentMode) {
    post("/api/v1/register") {
        if (deploymentMode == DeploymentMode.LOCAL && !authService.isFirstRun()) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "ALREADY_SETUP",
                    message = "Local registration is only available before the first user is created"
                ),
            )
            return@post
        }
        val request = call.receive<RegisterRequestModel>()
        when (val result = authService.register(
            tenantDisplayName = request.tenantName,
            tenantSlug = request.tenantSlug,
            username = request.username,
            password = request.password,
            displayName = request.displayName
        )) {
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
                    message = ErrorResponseModel(
                        code = "TENANT_SLUG_TAKEN",
                        message = "A tenant with this slug already exists"
                    ),
                )
            }
        }
    }
}

private fun Route.setupRoutes(authService: AuthService, deploymentMode: DeploymentMode) {
    get("/api/v1/auth/setup-status") {
        call.respond(SetupStatusResponseModel(firstRun = authService.isFirstRun(), mode = deploymentMode.name))
    }

    post("/api/v1/auth/setup") {
        if (deploymentMode != DeploymentMode.LOCAL) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(
                    code = "NOT_LOCAL_MODE",
                    message = "Admin setup is only available in local mode"
                ),
            )
            return@post
        }
        val request = call.receive<SetupRequestModel>()
        when (val result = authService.setupAdmin(
            username = request.username,
            password = request.password,
            displayName = request.displayName
        )) {
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
}

private fun Route.loginRoutes(authService: AuthService) {
    post("/api/v1/auth/login") {
        val request = call.receive<LoginRequestModel>()
        val correlationId = call.request.headers["X-Correlation-Id"]
        when (val result = authService.login(
            username = request.username,
            password = request.password,
            tenantSlug = request.tenantSlug,
            correlationId = correlationId
        )) {
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

            is LoginResult.TenantDisabled -> {
                call.respond(
                    status = HttpStatusCode.Unauthorized,
                    message = ErrorResponseModel(
                        code = "TENANT_DISABLED",
                        message = "This tenant has been deactivated",
                    ),
                )
            }
        }
    }
}

private fun Route.sessionRoutes(authService: AuthService, jwtService: JwtService) {
    post("/api/v1/auth/refresh") {
        val request = call.receive<RefreshRequestModel>()
        when (val result = authService.refresh(refreshToken = request.refreshToken)) {
            is RefreshResult.Success -> {
                call.respond(
                    RefreshResponseModel(
                        accessToken = result.accessToken,
                        expiresIn = result.expiresInSeconds
                    )
                )
            }

            is RefreshResult.Invalid -> {
                call.respond(
                    status = HttpStatusCode.Unauthorized,
                    message = ErrorResponseModel(
                        code = "INVALID_REFRESH_TOKEN",
                        message = "Refresh token is invalid, expired, or revoked"
                    ),
                )
            }
        }
    }

    get("/api/v1/auth/session") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        val user = authService.currentUser(userId = principal.userId)
            ?: return@get call.respond(
                status = HttpStatusCode.Unauthorized,
                message = ErrorResponseModel(code = "USER_NOT_FOUND", message = "User no longer exists"),
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
        val request = runCatching {
            call.receive<LogoutRequestModel>()
        }.getOrDefault(defaultValue = LogoutRequestModel())
        val correlationId = call.request.headers["X-Correlation-Id"]
        authService.logout(refreshToken = request.refreshToken, correlationId = correlationId)
        call.respond(status = HttpStatusCode.NoContent, message = Unit)
    }
}
