package com.example.racingmanager.api.health

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    get("/api/v1/health") {
        call.respond(HealthResponse(status = "UP"))
    }

    get("/api/v1/build-info") {
        call.respond(
            BuildInfoResponse(
                name = "racingmanager",
                version = "1.0-SNAPSHOT",
            ),
        )
    }
}
