package io.github.raginlundf.racingmanager.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.routing

private val logger = KotlinLogging.logger {}

/** Paths owned by the backend. Everything below them must keep failing as a
    real 404 instead of being answered with the SPA shell, so a client calling
    a mistyped endpoint sees an error rather than a 200 full of HTML. */
private const val API_PREFIX = "/api"

fun Application.configureStaticContent() {
    install(Compression) {
        gzip()
    }

    val indexHtml = loadIndexHtml()

    routing {
        staticResources(remotePath = "", basePackage = "webapp") {
            // The Angular router owns paths like /setup, /login and
            // /racemanager/<id>/results, none of which exist as files. Without
            // this fallback they only work while navigating inside the running
            // app — a hard reload, a bookmark or a pasted deep link 404s.
            fallback { _, call ->
                val path = call.request.path()
                when {
                    path.startsWith(API_PREFIX) -> call.respond(HttpStatusCode.NotFound)
                    // A missing asset (…/main-ABC123.js) must stay a 404: answering it
                    // with the shell would turn a broken build into a silent white page.
                    path.substringAfterLast('/').contains('.') -> call.respond(HttpStatusCode.NotFound)
                    indexHtml == null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respondBytes(bytes = indexHtml, contentType = ContentType.Text.Html)
                }
            }
        }
    }
}

/** Reads the built SPA shell once at startup. Absent when the backend runs
    without a bundled web UI (the Angular dev server serves it instead), in
    which case deep links simply keep 404-ing on the API port. */
private fun Application.loadIndexHtml(): ByteArray? {
    val bytes = this::class.java.classLoader
        ?.getResourceAsStream("webapp/index.html")
        ?.use { it.readBytes() }
    if (bytes == null) {
        logger.info { "No bundled web UI found (webapp/index.html) — SPA deep-link fallback disabled" }
    }
    return bytes
}
