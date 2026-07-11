package io.github.raginlundf.racingmanager.api

import io.github.raginlundf.racingmanager.api.models.ProblemDetailModel
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ProblemDetailModel(
                    type = "about:blank",
                    title = "Internal Server Error",
                    status = 500,
                ),
            )
        }
    }
}
