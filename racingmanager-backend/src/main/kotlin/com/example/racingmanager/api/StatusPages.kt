package com.example.racingmanager.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class ProblemDetail(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ProblemDetail(
                    type = "about:blank",
                    title = "Internal Server Error",
                    status = 500,
                ),
            )
        }
    }
}
