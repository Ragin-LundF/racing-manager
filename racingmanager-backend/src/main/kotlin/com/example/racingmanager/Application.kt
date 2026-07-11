package com.example.racingmanager

import com.example.racingmanager.api.configureRouting
import com.example.racingmanager.api.configureSerialization
import com.example.racingmanager.api.configureStatusPages
import com.example.racingmanager.infrastructure.configureDatabase
import com.example.racingmanager.infrastructure.configureLogging
import io.ktor.server.application.Application

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    configureLogging()
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureRouting()
}
