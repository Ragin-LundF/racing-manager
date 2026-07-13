package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.api.audit.auditRoutes
import io.github.raginlundf.racingmanager.api.auth.authRoutes
import io.github.raginlundf.racingmanager.api.event.eventRoutes
import io.github.raginlundf.racingmanager.api.health.healthRoutes
import io.github.raginlundf.racingmanager.api.heat.heatRoutes
import io.github.raginlundf.racingmanager.api.knockout.knockoutRoutes
import io.github.raginlundf.racingmanager.api.participant.participantRoutes
import io.github.raginlundf.racingmanager.api.qualification.qualificationRoutes
import io.github.raginlundf.racingmanager.api.results.resultsRoutes
import io.github.raginlundf.racingmanager.api.spectator.spectatorRoutes
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting(authService: AuthService, eventService: EventService, participantService: ParticipantService, heatService: HeatService, qualificationService: QualificationService, knockoutService: KnockoutService, resultsService: ResultsService, spectatorService: SpectatorService, eventRepository: EventRepository, webSocketService: SpectatorWebSocketService, auditService: AuditService, diagnosticsService: DiagnosticsService) {
    routing {
        healthRoutes(diagnosticsService)
        authRoutes(authService)
        eventRoutes(authService, eventService)
        participantRoutes(authService, participantService)
        heatRoutes(authService, heatService)
        qualificationRoutes(authService, qualificationService)
        knockoutRoutes(authService, knockoutService)
        resultsRoutes(authService, resultsService, eventService)
        auditRoutes(authService, auditService)
        spectatorRoutes(spectatorService, eventRepository, webSocketService)
    }
}
