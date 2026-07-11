package com.example.racingmanager

import com.example.racingmanager.api.configureRouting
import com.example.racingmanager.api.configureSerialization
import com.example.racingmanager.api.configureStatusPages
import com.example.racingmanager.application.auth.AuthService
import com.example.racingmanager.infrastructure.configureDatabase
import com.example.racingmanager.infrastructure.configureLogging
import com.example.racingmanager.infrastructure.repositories.AuditRepository
import com.example.racingmanager.infrastructure.repositories.SessionRepository
import com.example.racingmanager.infrastructure.repositories.UserRepository
import com.example.racingmanager.infrastructure.security.PasswordHasher
import io.ktor.server.application.Application

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val userRepository = UserRepository()
    val sessionRepository = SessionRepository()
    val auditRepository = AuditRepository()
    val passwordHasher = PasswordHasher()
    val authService = AuthService(userRepository, sessionRepository, auditRepository, passwordHasher)

    configureLogging()
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureRouting(authService)
}
