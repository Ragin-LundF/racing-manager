package io.github.raginlundf.racingmanager.api.bootstrap

import io.github.raginlundf.racingmanager.api.auth.models.ErrorResponseModel
import io.github.raginlundf.racingmanager.api.authenticateRequest
import io.github.raginlundf.racingmanager.api.bootstrap.models.LocalPackageImportRequestModel
import io.github.raginlundf.racingmanager.api.bootstrap.models.LocalPackageImportResponseModel
import io.github.raginlundf.racingmanager.api.bootstrap.models.LocalPackageRequestModel
import io.github.raginlundf.racingmanager.api.requireScope
import io.github.raginlundf.racingmanager.application.auth.Scopes
import io.github.raginlundf.racingmanager.application.bootstrap.ImportResult
import io.github.raginlundf.racingmanager.application.bootstrap.IssueResult
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID

/** Hosted→local bootstrap package (design §H): issuance runs in `hosted`
mode against the caller's own tenant only; import runs in `local` mode
against the caller's own (already-authenticated) tenant only. Neither
operation ever takes a tenant-selection parameter, so there is no
cross-tenant surface to guard on either side. */
fun Route.bootstrapRoutes(
    jwtService: JwtService,
    localPackageService: LocalPackageService,
    deploymentMode: DeploymentMode
) {
    post("/api/v1/tenant/local-packages") {
        if (deploymentMode != DeploymentMode.HOSTED) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(
                    code = "NOT_HOSTED",
                    message = "Local packages can only be issued in hosted mode"
                )
            )
            return@post
        }
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN)) return@post

        val request = call.receive<LocalPackageRequestModel>()
        val eventIds = request.eventIds.map { UUID.fromString(it) }
        when (val result = localPackageService.issue(tenantId = principal.tenantId, eventIds = eventIds)) {
            is IssueResult.Success -> call.respond(status = HttpStatusCode.Created, message = result.artifact)
            is IssueResult.EventNotFound -> call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponseModel(
                    code = "EVENT_NOT_FOUND",
                    message = "One or more events were not found in this tenant"
                )
            )
        }
    }

    post("/api/v1/tenant/local-packages/import") {
        if (deploymentMode != DeploymentMode.LOCAL) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponseModel(
                    code = "NOT_LOCAL",
                    message = "Local packages can only be imported in local mode"
                )
            )
            return@post
        }
        val principal = call.authenticateRequest(jwtService = jwtService) ?: return@post
        if (!call.requireScope(principal, Scopes.ADMIN)) return@post

        val request = call.receive<LocalPackageImportRequestModel>()
        when (val result =
            localPackageService.import(
                artifact = request.artifact,
                targetTenantId = principal.tenantId,
                importedByUserId = principal.userId,
                dryRun = request.dryRun
            )) {
            is ImportResult.Success -> call.respond(
                status = HttpStatusCode.Created,
                message = LocalPackageImportResponseModel(
                    localInstanceId = result.localInstanceId.toString(),
                    tenantId = result.tenantId.toString(),
                    importedEventIds = result.importedEventIds.map { it.toString() },
                    alreadyImported = result.alreadyImported,
                    dryRun = false,
                    originTenantDisplayName = result.originTenantDisplayName,
                ),
            )

            is ImportResult.Preview -> call.respond(
                LocalPackageImportResponseModel(
                    localInstanceId = "",
                    tenantId = principal.tenantId.toString(),
                    importedEventIds = result.importedEventIds.map { it.toString() },
                    alreadyImported = result.alreadyImported,
                    dryRun = true,
                    originTenantDisplayName = result.originTenantDisplayName,
                ),
            )

            is ImportResult.InvalidArtifact -> call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(code = "INVALID_ARTIFACT", message = "The package artifact is malformed")
            )

            is ImportResult.InvalidSignature -> call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(
                    code = "INVALID_SIGNATURE",
                    message = "The package artifact failed integrity verification"
                )
            )

            is ImportResult.Expired -> call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponseModel(code = "PACKAGE_EXPIRED", message = "This package has expired")
            )
        }
    }
}
