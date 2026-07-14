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
import io.github.raginlundf.racingmanager.api.racedevice.raceDeviceRoutes
import io.github.raginlundf.racingmanager.api.results.resultsRoutes
import io.github.raginlundf.racingmanager.api.spectator.spectatorRoutes
import io.github.raginlundf.racingmanager.api.sync.syncRoutes
import io.github.raginlundf.racingmanager.api.tenant.tenantRoutes
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.application.sync.SyncService
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.gateway.ReconfigurableMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RaceDeviceSettingsRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SpectatorExchangeCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

@Suppress("LongParameterList")
fun Application.configureRouting(
    authService: AuthService,
    jwtService: JwtService,
    eventService: EventService,
    participantService: ParticipantService,
    heatService: HeatService,
    qualificationService: QualificationService,
    knockoutService: KnockoutService,
    resultsService: ResultsService,
    spectatorService: SpectatorService,
    eventRepository: EventRepository,
    webSocketService: SpectatorWebSocketService,
    auditService: AuditService,
    diagnosticsService: DiagnosticsService,
    deploymentMode: DeploymentMode,
    spectatorExchangeCodeRepository: SpectatorExchangeCodeRepository,
    localPackageService: LocalPackageService,
    syncService: SyncService,
    raceDeviceGateway: ReconfigurableMeasurementGateway,
    raceDeviceSettingsRepository: RaceDeviceSettingsRepository
) {
    routing {
        healthRoutes(diagnosticsService = diagnosticsService, jwtService = jwtService)
        authRoutes(authService = authService, jwtService = jwtService, deploymentMode = deploymentMode)
        tenantRoutes(jwtService = jwtService, authService = authService)
        adminRoutes(jwtService = jwtService, authService = authService, deploymentMode = deploymentMode)
        eventRoutes(jwtService = jwtService, eventService = eventService, eventRepository = eventRepository)
        participantRoutes(
            jwtService = jwtService,
            participantService = participantService,
            eventRepository = eventRepository
        )
        heatRoutes(jwtService = jwtService, heatService = heatService, eventRepository = eventRepository)
        qualificationRoutes(
            jwtService = jwtService,
            qualificationService = qualificationService,
            eventRepository = eventRepository
        )
        knockoutRoutes(jwtService = jwtService, knockoutService = knockoutService, eventRepository = eventRepository)
        resultsRoutes(
            jwtService = jwtService,
            resultsService = resultsService,
            eventService = eventService,
            eventRepository = eventRepository
        )
        auditRoutes(jwtService = jwtService, auditService = auditService, eventRepository = eventRepository)
        spectatorRoutes(
            jwtService,
            spectatorService,
            eventRepository,
            webSocketService,
            spectatorExchangeCodeRepository
        )
        bootstrapRoutes(
            jwtService = jwtService,
            localPackageService = localPackageService,
            deploymentMode = deploymentMode
        )
        syncRoutes(jwtService = jwtService, syncService = syncService, deploymentMode = deploymentMode)
        raceDeviceRoutes(
            jwtService = jwtService,
            gateway = raceDeviceGateway,
            settingsRepository = raceDeviceSettingsRepository,
            deploymentMode = deploymentMode
        )
    }
}
