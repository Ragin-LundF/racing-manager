package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.api.auth.authRoutes
import io.github.raginlundf.racingmanager.api.health.healthRoutes
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting(authService: AuthService) {
    routing {
        healthRoutes()
        authRoutes(authService)
    }
}
