package io.github.raginlundf.racingmanager.infrastructure

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("io.github.raginlundf.racingmanager.Logging")

fun Application.configureLogging() {
    install(CallLogging) {
        format { call: ApplicationCall ->
            val correlationId = call.request.headers["X-Correlation-Id"] ?: UUID.randomUUID().toString()
            "${call.request.httpMethod.value} ${call.request.uri} [correlationId=$correlationId]"
        }
    }

    logger.info("Logging configured with correlation ID support")
}
