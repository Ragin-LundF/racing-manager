package io.github.raginlundf.racingmanager

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.api.configureRouting
import io.github.raginlundf.racingmanager.api.configureSerialization
import io.github.raginlundf.racingmanager.api.configureStaticContent
import io.github.raginlundf.racingmanager.api.configureStatusPages
import io.github.raginlundf.racingmanager.application.audit.AuditService
import io.github.raginlundf.racingmanager.application.auth.AuthService
import io.github.raginlundf.racingmanager.application.auth.SetupResult
import io.github.raginlundf.racingmanager.application.bootstrap.LocalPackageService
import io.github.raginlundf.racingmanager.application.diagnostics.DiagnosticsService
import io.github.raginlundf.racingmanager.application.event.EventService
import io.github.raginlundf.racingmanager.application.heat.HeatService
import io.github.raginlundf.racingmanager.application.knockout.KnockoutService
import io.github.raginlundf.racingmanager.application.participant.ParticipantService
import io.github.raginlundf.racingmanager.application.qualification.QualificationService
import io.github.raginlundf.racingmanager.application.results.ResultsService
import io.github.raginlundf.racingmanager.application.spectator.SpectatorService
import io.github.raginlundf.racingmanager.application.sync.SyncService
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.configureDatabase
import io.github.raginlundf.racingmanager.infrastructure.configureDeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.configureLogging
import io.github.raginlundf.racingmanager.infrastructure.configureWebSockets
import io.github.raginlundf.racingmanager.infrastructure.gateway.SimulationMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.repositories.AuditRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.EventRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.HeatRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ImportedPackageRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.KnockoutRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.LocalInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.MembershipRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairedInstanceRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.PairingCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.ParticipantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.QualificationRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.RefreshTokenRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SigningKeyRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SpectatorExchangeCodeRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.SyncedResultRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.TenantRepository
import io.github.raginlundf.racingmanager.infrastructure.repositories.UserRepository
import io.github.raginlundf.racingmanager.infrastructure.security.HostedJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.security.JwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.security.JwtService
import io.github.raginlundf.racingmanager.infrastructure.security.LocalJwtKeyProvider
import io.github.raginlundf.racingmanager.infrastructure.security.PasswordHasher
import io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService
import io.ktor.server.application.Application

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

/** In the "demo" profile, seed a default admin (admin/admin) on first run so the
    app is usable out of the box. No-op once any user exists. Switch the profile
    (config `racingmanager.profile` or env `RACINGMANAGER_PROFILE`) away from "demo"
    to disable. Only applies in [DeploymentMode.LOCAL] — a hosted deployment must
    never silently create a shared cloud-wide administrator. */
private fun Application.seedDemoAdmin(authService: AuthService, deploymentMode: DeploymentMode) {
    if (deploymentMode != DeploymentMode.LOCAL) return
    val profile = environment.config.propertyOrNull("racingmanager.profile")?.getString() ?: "demo"
    if (profile != "demo") return
    val setupResult = authService.setupAdmin(username = "admin", password = "admin", displayName = "Administrator")
    if (setupResult is SetupResult.Success) {
        logger.warn { "[demo profile] Seeded default admin 'admin' / 'admin' — change these credentials." }
    }
}

/** Resolves the JWT signing key source for [deploymentMode]. In [DeploymentMode.LOCAL]
    a key is generated and persisted on first run; in [DeploymentMode.HOSTED] keys are
    read from `racingmanager.jwt.keys` deployment configuration. Never logs key material. */
private fun Application.configureJwtKeyProvider(deploymentMode: DeploymentMode): JwtKeyProvider =
    when (deploymentMode) {
        DeploymentMode.LOCAL -> {
            val provider = LocalJwtKeyProvider(SigningKeyRepository())
            val key = provider.ensureKeyExists()
            logger.info { "JWT signing key ready (kid=${key.kid})" }
            provider
        }
        DeploymentMode.HOSTED -> HostedJwtKeyProvider.fromConfig(environment.config)
    }

fun Application.module() {
    val userRepository = UserRepository()
    val auditRepository = AuditRepository()
    val eventRepository = EventRepository()
    val tenantRepository = TenantRepository()
    val membershipRepository = MembershipRepository()
    val refreshTokenRepository = RefreshTokenRepository()
    val participantRepository = ParticipantRepository()
    val heatRepository = HeatRepository()
    val passwordHasher = PasswordHasher()
    val eventService = EventService(eventRepository, participantRepository, auditRepository)
    val participantService = ParticipantService(participantRepository, eventRepository, auditRepository)
    val measurementGateway = SimulationMeasurementGateway()
    val heatService = HeatService(heatRepository, eventRepository, participantRepository, auditRepository, measurementGateway)
    val qualificationRepository = QualificationRepository()
    val qualificationService = QualificationService(qualificationRepository, heatRepository, eventRepository, participantRepository, auditRepository)
    val knockoutRepository = KnockoutRepository()
    val knockoutService = KnockoutService(knockoutRepository, heatRepository, eventRepository, participantRepository, qualificationRepository, auditRepository)
    val resultsService = ResultsService(eventRepository, participantRepository, heatRepository, qualificationRepository, knockoutRepository, auditRepository)
    val auditService = AuditService(auditRepository)
    val spectatorService = SpectatorService(eventRepository, heatRepository, participantRepository, qualificationRepository, knockoutRepository)
    val spectatorWebSocketService = SpectatorWebSocketService(spectatorService, heatRepository, heatService.events)
    val spectatorExchangeCodeRepository = SpectatorExchangeCodeRepository()
    val importedPackageRepository = ImportedPackageRepository()
    val localInstanceRepository = LocalInstanceRepository()
    val pairingCodeRepository = PairingCodeRepository()
    val pairedInstanceRepository = PairedInstanceRepository()
    val syncedResultRepository = SyncedResultRepository()

    configureLogging()
    configureSerialization()
    configureStatusPages()
    configureStaticContent()
    val deploymentMode = configureDeploymentMode()
    val dataSource = configureDatabase()
    val jwtKeyProvider = configureJwtKeyProvider(deploymentMode)
    val jwtService = JwtService(jwtKeyProvider)
    val authService = AuthService(userRepository, tenantRepository, membershipRepository, refreshTokenRepository, auditRepository, passwordHasher, jwtService)
    val localPackageService = LocalPackageService(eventRepository, participantRepository, tenantRepository, importedPackageRepository, localInstanceRepository, jwtKeyProvider)
    val syncService = SyncService(pairingCodeRepository, pairedInstanceRepository, syncedResultRepository, eventRepository, auditRepository)
    val diagnosticsService = DiagnosticsService(dataSource, eventRepository, participantRepository, heatRepository)
    diagnosticsService.findUnfinishedHeats().let { unfinished ->
        if (unfinished.isNotEmpty()) {
            logger.warn { "Found ${unfinished.size} unfinished heat(s) on startup — recovery recommended" }
            unfinished.forEach { uf ->
                logger.warn { "  Heat #${uf.heat.heatNumber} (${uf.heat.id}) in event '${uf.event.name}' has status ${uf.heat.status}" }
            }
        }
    }
    seedDemoAdmin(authService, deploymentMode)
    configureWebSockets()
    spectatorWebSocketService.start()
    configureRouting(authService, jwtService, eventService, participantService, heatService, qualificationService, knockoutService, resultsService, spectatorService, eventRepository, spectatorWebSocketService, auditService, diagnosticsService, deploymentMode, spectatorExchangeCodeRepository, localPackageService, syncService)
}
