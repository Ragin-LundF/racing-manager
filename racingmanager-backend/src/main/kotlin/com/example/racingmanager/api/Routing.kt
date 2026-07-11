package com.example.racingmanager.api

import com.example.racingmanager.api.health.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        healthRoutes()
    }
}
