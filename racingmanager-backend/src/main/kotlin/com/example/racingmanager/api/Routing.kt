package com.example.racingmanager.api

import com.example.racingmanager.api.auth.authRoutes
import com.example.racingmanager.api.health.healthRoutes
import com.example.racingmanager.application.auth.AuthService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting(authService: AuthService) {
    routing {
        healthRoutes()
        authRoutes(authService)
    }
}
