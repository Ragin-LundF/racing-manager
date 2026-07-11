package io.github.raginlundf.racingmanager

import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.infrastructure.configureDatabase
import io.github.raginlundf.racingmanager.infrastructure.configureLogging
import io.github.raginlundf.racingmanager.infrastructure.configureWebSockets
import io.github.raginlundf.racingmanager.infrastructure.gateway.SimulationMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SessionRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import io.ktor.server.application.Application

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val userRepository = UserRepository()
    val sessionRepository = SessionRepository()
    val auditRepository = AuditRepository()
    val eventRepository = EventRepository()
    val participantRepository = ParticipantRepository()
    val heatRepository = HeatRepository()
    val passwordHasher = PasswordHasher()
    val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)
    val eventService = EventService(eventRepository, auditRepository)
    val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)
    val measurementGateway = SimulationMeasurementGateway()
    val heatService = HeatService(heatRepository, eventRepository, participantRepository, auditRepository, measurementGateway)
    val qualificationRepository = QualificationRepository()
    val qualificationService = QualificationService(qualificationRepository, heatRepository, eventRepository, participantRepository, auditRepository)

    configureLogging()
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureWebSockets()
    configureRouting(authService, eventService, participantService, heatService, qualificationService)
}
