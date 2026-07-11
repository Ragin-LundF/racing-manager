package io.github.raginlundf.racingmanager.api.health

import io.github.raginlundf.racingmanager.api.health.models.BuildInfoResponseModel
import io.github.raginlundf.racingmanager.api.health.models.HealthResponseModel
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    get("/api/v1/health") {
        call.respond(HealthResponseModel(status = "UP"))
    }

    get("/api/v1/build-info") {
        call.respond(
            BuildInfoResponseModel(
                name = "racingmanager",
                version = "1.0-SNAPSHOT",
            ),
        )
    }
}
