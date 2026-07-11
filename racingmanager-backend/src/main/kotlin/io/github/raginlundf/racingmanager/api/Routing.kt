package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.api.auth.authRoutes
import io.github.raginlundf.racingmanager.api.event.eventRoutes
import io.github.raginlundf.racingmanager.api.health.healthRoutes
import io.github.raginlundf.racingmanager.api.heat.heatRoutes
import io.github.raginlundf.racingmanager.api.participant.participantRoutes
import io.github.raginlundf.racingmanager.api.qualification.qualificationRoutes
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting(authService: AuthService, eventService: EventService, participantService: ParticipantService, heatService: HeatService, qualificationService: QualificationService) {
    routing {
        healthRoutes()
        authRoutes(authService)
        eventRoutes(authService, eventService)
        participantRoutes(authService, participantService)
        heatRoutes(authService, heatService)
        qualificationRoutes(authService, qualificationService)
    }
}
