package io.github.raginlundf.racingmanager

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.infrastructure.configureDatabase
import io.github.raginlundf.racingmanager.infrastructure.configureLogging
import io.github.raginlundf.racingmanager.infrastructure.configureWebSockets
import io.github.raginlundf.racingmanager.infrastructure.gateway.SimulationMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.KnockoutRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SessionRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import io.ktor.server.application.Application

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

/** In the "demo" profile, seed a default admin (admin/admin) on first run so the
    app is usable out of the box. No-op once any user exists. Switch the profile
    (config `racingmanager.profile` or env `RACINGMANAGER_PROFILE`) away from "demo"
    to disable. */
private fun Application.seedDemoAdmin(authService: AuthService) {
    val profile = environment.config.propertyOrNull("racingmanager.profile")?.getString() ?: "demo"
    if (profile != "demo") return
    when (authService.setupAdmin(username = "admin", password = "admin", displayName = "Administrator")) {
        is SetupResult.Success ->
            logger.warn { "[demo profile] Seeded default admin 'admin' / 'admin' — change these credentials." }
        SetupResult.AlreadySetup -> Unit
    }
}

fun Application.module() {
    val userRepository = UserRepository()
    val sessionRepository = SessionRepository()
    val auditRepository = AuditRepository()
    val eventRepository = EventRepository()
    val participantRepository = ParticipantRepository()
    val heatRepository = HeatRepository()
    val passwordHasher = PasswordHasher()
    val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)
    val eventService = EventService(eventRepository, participantRepository, auditRepository)
    val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)
    val measurementGateway = SimulationMeasurementGateway()
    val heatService = HeatService(heatRepository, eventRepository, participantRepository, auditRepository, measurementGateway)
    val qualificationRepository = QualificationRepository()
    val qualificationService = QualificationService(qualificationRepository, heatRepository, eventRepository, participantRepository, auditRepository)
    val knockoutRepository = KnockoutRepository()
    val knockoutService = KnockoutService(knockoutRepository, heatRepository, eventRepository, participantRepository, qualificationRepository, auditRepository)
    val resultsService = ResultsService(eventRepository, participantRepository, heatRepository, qualificationRepository, knockoutRepository, auditRepository)
    val auditService = AuditService(auditRepository)
    val spectatorService = SpectatorService(eventRepository, heatRepository, participantRepository, qualificationRepository, knockoutRepository)
    val spectatorWebSocketService = SpectatorWebSocketService(spectatorService, heatRepository, heatService.events)

    configureLogging()
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    seedDemoAdmin(authService)
    configureWebSockets()
    spectatorWebSocketService.start()
    configureRouting(authService, eventService, participantService, heatService, qualificationService, knockoutService, resultsService, spectatorService, eventRepository, spectatorWebSocketService, auditService)
}
