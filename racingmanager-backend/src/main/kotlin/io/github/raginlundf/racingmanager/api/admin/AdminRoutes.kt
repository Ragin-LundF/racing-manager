package io.github.raginlundf.racingmanager.api.admin

import io.github.raginlundf.racingmanager.api.admin.models.DeleteTenantRequestModel
import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.SetupRequestModel
import io.github.raginlundf.racingmanager.api.auth.models.SetupResponseModel
import io.github.raginlundf.racingmanager.api.auth.models.SetupStatusResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.tenant.models.TenantResponseModel
import io.github.raginlundf.racingmanager.api.tenant.models.UpdateTenantRequestModel
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.DeleteTenantResult
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.domain.tenant.TenantEntity
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

/** Hosted-platform supervisor APIs (design §3/§7): `rm:supervisor` is a
platform-level scope, never a tenant role, and these routes only ever
touch tenant **metadata** — never race data. `/api/v1/admin/setup*` is the
one-time supervisor bootstrap, gated to hosted mode like `/api/v1/register`. */
fun Route.adminRoutes(jwtService: JwtService, authService: AuthService, deploymentMode: DeploymentMode) {
    adminSetupRoutes(authService = authService, deploymentMode = deploymentMode)
    adminTenantRoutes(jwtService = jwtService, authService = authService)
    adminTenantDeleteRoutes(jwtService = jwtService, authService = authService)
}

private fun Route.adminSetupRoutes(authService: AuthService, deploymentMode: DeploymentMode) {
    get("/api/v1/admin/setup-status") {
        call.respond(
            message = SetupStatusResponseModel(
                firstRun = authService.isFirstSupervisorRun(),
                mode = deploymentMode.name
            )
        )
    }

    post("/api/v1/admin/setup") {
        if (deploymentMode != DeploymentMode.HOSTED) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(
                    code = "NOT_HOSTED",
                    message = "Supervisor setup is only available in hosted mode"
                )
            )
            return@post
        }
        val request = call.receive<SetupRequestModel>()
        when (val result = authService.setupSupervisor(
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
                        message = "A supervisor has already been set up"
                    )
                )
            }
        }
    }
}

private fun Route.adminTenantRoutes(jwtService: JwtService, authService: AuthService) {
    get("/api/v1/admin/tenants") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.SUPERVISOR)) return@get
        call.respond(message = authService.listAllTenants().map { it.toResponseModel() })
    }

    put("/api/v1/admin/tenants/{id}") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@put
        if (!call.requireScope(principal, Scopes.SUPERVISOR)) return@put
        val tenantId = UUID.fromString(call.parameters["id"])
        val request = call.receive<UpdateTenantRequestModel>()
        val tenant = authService.updateTenant(
            tenantId = tenantId,
            displayName = request.displayName,
            settings = request.settings
        ) ?: return@put call.respond(
            status = HttpStatusCode.NotFound,
            message = ErrorResponseModel(code = "TENANT_NOT_FOUND", message = "Tenant not found")
        )
        call.respond(tenant.toResponseModel())
    }

    post("/api/v1/admin/tenants/{id}/deactivate") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.SUPERVISOR)) return@post
        val tenantId = UUID.fromString(call.parameters["id"])
        val tenant = authService.deactivateTenant(tenantId = tenantId, supervisorId = principal.userId)
            ?: return@post call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "TENANT_NOT_FOUND", message = "Tenant not found")
            )
        call.respond(tenant.toResponseModel())
    }

    post("/api/v1/admin/tenants/{id}/reactivate") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.SUPERVISOR)) return@post
        val tenantId = UUID.fromString(call.parameters["id"])
        val tenant = authService.reactivateTenant(tenantId = tenantId, supervisorId = principal.userId)
            ?: return@post call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "TENANT_NOT_FOUND", message = "Tenant not found")
            )
        call.respond(tenant.toResponseModel())
    }
}

private fun Route.adminTenantDeleteRoutes(jwtService: JwtService, authService: AuthService) {
    delete("/api/v1/admin/tenants/{id}") {
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@delete
        if (!call.requireScope(principal, Scopes.SUPERVISOR)) return@delete
        val tenantId = UUID.fromString(call.parameters["id"])
        val request = call.receive<DeleteTenantRequestModel>()

        when (val result = authService.requestTenantDeletion(
            tenantId = tenantId,
            confirmSlug = request.confirmSlug,
            supervisorId = principal.userId
        )) {
            is DeleteTenantResult.Success -> call.respond(result.tenant.toResponseModel())
            is DeleteTenantResult.NotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "TENANT_NOT_FOUND", message = "Tenant not found")
            )

            is DeleteTenantResult.ConfirmationMismatch -> call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(
                    code = "CONFIRMATION_MISMATCH",
                    message = "confirmSlug does not match this tenant"
                ),
            )
        }
    }
}

private fun TenantEntity.toResponseModel(): TenantResponseModel {
    return TenantResponseModel(
        id = id.toString(),
        slug = slug,
        displayName = displayName,
        status = status.name,
        settings = settings,
    )
}
