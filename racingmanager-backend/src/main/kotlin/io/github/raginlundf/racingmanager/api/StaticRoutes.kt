package io.github.raginlundf.racingmanager.api

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.routing.routing

fun Application.configureStaticContent() {
    install(Compression) {
        gzip()
    }

    routing {
        staticResources(remotePath = "", basePackage = "webapp")
    }
}
