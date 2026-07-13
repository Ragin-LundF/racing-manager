package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.api.admin.adminRoutes
import io.github.raginlundf.racingmanager.api.audit.auditRoutes
import io.github.raginlundf.racingmanager.api.auth.authRoutes
import io.github.raginlundf.racingmanager.api.bootstrap.bootstrapRoutes
import io.github.raginlundf.racingmanager.api.event.eventRoutes
import io.github.raginlundf.racingmanager.api.health.healthRoutes
import io.github.raginlundf.racingmanager.api.heat.heatRoutes
import io.github.raginlundf.racingmanager.api.knockout.knockoutRoutes
import io.github.raginlundf.racingmanager.api.participant.participantRoutes
import io.github.raginlundf.racingmanager.api.qualification.qualificationRoutes
import io.github.raginlundf.racingmanager.api.results.resultsRoutes
import io.github.raginlundf.racingmanager.api.spectator.spectatorRoutes
import io.github.raginlundf.racingmanager.api.sync.syncRoutes
import io.github.raginlundf.racingmanager.api.tenant.tenantRoutes
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.application.sync.SyncService
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SpectatorExchangeCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting(authService: AuthService, jwtService: JwtService, eventService: EventService, participantService: ParticipantService, heatService: HeatService, qualificationService: QualificationService, knockoutService: KnockoutService, resultsService: ResultsService, spectatorService: SpectatorService, eventRepository: EventRepository, webSocketService: SpectatorWebSocketService, auditService: AuditService, diagnosticsService: DiagnosticsService, deploymentMode: DeploymentMode, spectatorExchangeCodeRepository: SpectatorExchangeCodeRepository, localPackageService: LocalPackageService, syncService: SyncService) {
    routing {
        healthRoutes(diagnosticsService, jwtService)
        authRoutes(authService, jwtService, deploymentMode)
        tenantRoutes(jwtService, authService)
        adminRoutes(jwtService, authService, deploymentMode)
        eventRoutes(jwtService, eventService, eventRepository)
        participantRoutes(jwtService, participantService, eventRepository)
        heatRoutes(jwtService, heatService, eventRepository)
        qualificationRoutes(jwtService, qualificationService, eventRepository)
        knockoutRoutes(jwtService, knockoutService, eventRepository)
        resultsRoutes(jwtService, resultsService, eventService, eventRepository)
        auditRoutes(jwtService, auditService, eventRepository)
        spectatorRoutes(jwtService, spectatorService, eventRepository, webSocketService, spectatorExchangeCodeRepository)
        bootstrapRoutes(jwtService, localPackageService, deploymentMode)
        syncRoutes(jwtService, syncService, deploymentMode)
    }
}
