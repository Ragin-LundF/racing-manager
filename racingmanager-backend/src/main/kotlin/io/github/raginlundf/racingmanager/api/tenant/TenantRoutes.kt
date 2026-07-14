package io.github.raginlundf.racingmanager.api.tenant

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.api.tenant.models.CreateTenantUserRequestModel
import io.github.raginlundf.racingmanager.api.tenant.models.TenantResponseModel
import io.github.raginlundf.racingmanager.api.tenant.models.TenantUserResponseModel
import io.github.raginlundf.racingmanager.api.tenant.models.UpdateTenantRequestModel
import io.github.raginlundf.racingmanager.api.tenant.models.UpdateTenantUserRequestModel
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.CreateTenantUserResult
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.auth.UpdateTenantUserResult
import io.github.raginlundf.racingmanager.domain.tenant.MembershipStatus
import io.github.raginlundf.racingmanager.domain.user.UserRole
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.util.UUID

/** Tenant administrator self-service (design §4/§11) — `rm:admin`-only, and
    always scoped to the caller's own tenant (`principal.tenantId`), never a
    tenant id from the request. */
fun Route.tenantRoutes(jwtService: JwtService, authService: AuthService) {
    tenantSelfRoutes(jwtService, authService)
    tenantUserRoutes(jwtService, authService)
    tenantUserUpdateRoutes(jwtService, authService)
}

private fun Route.tenantSelfRoutes(jwtService: JwtService, authService: AuthService) {
    get("/api/v1/tenant") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN)) return@get
        val tenant = authService.getTenant(principal.tenantId)
            ?: return@get call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "TENANT_NOT_FOUND", message = "Tenant not found"),
            )
        call.respond(
            TenantResponseModel(
                id = tenant.id.toString(),
                slug = tenant.slug,
                displayName = tenant.displayName,
                status = tenant.status.name,
                settings = tenant.settings,
            ),
        )
    }

    put("/api/v1/tenant") {
        val principal = call.authenticateRequest(jwtService) ?: return@put
        if (!call.requireScope(principal, Scopes.ADMIN)) return@put
        val request = call.receive<UpdateTenantRequestModel>()
        val tenant = authService.updateTenant(principal.tenantId, request.displayName, request.settings)
            ?: return@put call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "TENANT_NOT_FOUND", message = "Tenant not found"),
            )
        call.respond(
            TenantResponseModel(
                id = tenant.id.toString(),
                slug = tenant.slug,
                displayName = tenant.displayName,
                status = tenant.status.name,
                settings = tenant.settings,
            ),
        )
    }
}

private fun Route.tenantUserRoutes(jwtService: JwtService, authService: AuthService) {
    get("/api/v1/tenant/users") {
        val principal = call.authenticateRequest(jwtService) ?: return@get
        if (!call.requireScope(principal, Scopes.ADMIN)) return@get
        val members = authService.listTenantUsers(principal.tenantId)
        call.respond(
            members.map { member ->
                TenantUserResponseModel(
                    userId = member.user.id.toString(),
                    username = member.user.username,
                    displayName = member.user.displayName,
                    role = member.membership.role.name,
                    status = member.membership.status.name,
                )
            },
        )
    }

    post("/api/v1/tenant/users") {
        val principal = call.authenticateRequest(jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN)) return@post
        val request = call.receive<CreateTenantUserRequestModel>()
        val role = runCatching { UserRole.valueOf(request.role) }.getOrElse {
            return@post call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(code = "INVALID_ROLE", message = "Role must be ADMIN or DIRECTOR"),
            )
        }

        val result = authService.createTenantUser(
            principal.tenantId,
            request.username,
            request.password,
            request.displayName,
            role,
        )
        call.respondCreateTenantUser(result)
    }
}

private fun Route.tenantUserUpdateRoutes(jwtService: JwtService, authService: AuthService) {
    put("/api/v1/tenant/users/{userId}") {
        val principal = call.authenticateRequest(jwtService) ?: return@put
        if (!call.requireScope(principal, Scopes.ADMIN)) return@put
        val userId = UUID.fromString(call.parameters["userId"])
        val request = call.receive<UpdateTenantUserRequestModel>()
        val role = request.role?.let {
            runCatching { UserRole.valueOf(it) }.getOrElse {
                return@put call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = ErrorResponseModel(code = "INVALID_ROLE", message = "Role must be ADMIN or DIRECTOR"),
                )
            }
        }
        val status = request.status?.let {
            runCatching { MembershipStatus.valueOf(it) }.getOrElse {
                return@put call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = ErrorResponseModel(
                        code = "INVALID_STATUS",
                        message = "Status must be ACTIVE or DISABLED",
                    ),
                )
            }
        }

        call.respondTenantUserUpdate(authService.updateTenantUser(principal.tenantId, userId, role, status))
    }

    delete("/api/v1/tenant/users/{userId}") {
        val principal = call.authenticateRequest(jwtService) ?: return@delete
        if (!call.requireScope(principal, Scopes.ADMIN)) return@delete
        val userId = UUID.fromString(call.parameters["userId"])

        call.respondTenantUserUpdate(
            authService.updateTenantUser(principal.tenantId, userId, status = MembershipStatus.DISABLED),
        )
    }
}

private suspend fun ApplicationCall.respondCreateTenantUser(result: CreateTenantUserResult) {
    when (result) {
        is CreateTenantUserResult.Success -> {
            respond(
                status = HttpStatusCode.Created,
                message = TenantUserResponseModel(
                    userId = result.user.id.toString(),
                    username = result.user.username,
                    displayName = result.user.displayName,
                    role = result.user.role.name,
                    status = MembershipStatus.ACTIVE.name,
                ),
            )
        }
        is CreateTenantUserResult.UsernameTaken -> {
            respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponseModel(
                    code = "USERNAME_TAKEN",
                    message = "Username already exists in this tenant",
                ),
            )
        }
    }
}

private suspend fun ApplicationCall.respondTenantUserUpdate(result: UpdateTenantUserResult) {
    when (result) {
        is UpdateTenantUserResult.Success -> {
            respond(
                TenantUserResponseModel(
                    userId = result.user.id.toString(),
                    username = result.user.username,
                    displayName = result.user.displayName,
                    role = result.membership.role.name,
                    status = result.membership.status.name,
                ),
            )
        }
        is UpdateTenantUserResult.NotFound -> {
            respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(code = "USER_NOT_FOUND", message = "User not found in this tenant"),
            )
        }
    }
}
