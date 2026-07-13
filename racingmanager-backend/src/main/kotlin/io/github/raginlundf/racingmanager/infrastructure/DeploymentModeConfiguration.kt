package io.github.raginlundf.racingmanager.infrastructure

import io.ktor.server.application.Application

/** Reads `racingmanager.mode` (env `RACINGMANAGER_MODE`), defaulting to [DeploymentMode.LOCAL]
    so existing offline installations keep working unless explicitly switched to hosted. */
fun Application.configureDeploymentMode(): DeploymentMode {
    val configured = environment.config.propertyOrNull("racingmanager.mode")?.getString() ?: "local"
    return DeploymentMode.from(value = configured)
}
