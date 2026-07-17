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
import io.github.raginlundf.racingmanager.application.tenant.TenantPurgeWorker
import io.github.raginlundf.racingmanager.infrastructure.DeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.configureDatabase
import io.github.raginlundf.racingmanager.infrastructure.configureDeploymentMode
import io.github.raginlundf.racingmanager.infrastructure.configureLogging
import io.github.raginlundf.racingmanager.infrastructure.configureWebSockets
import io.github.raginlundf.racingmanager.infrastructure.gateway.ReconfigurableMeasurementGateway
import io.github.raginlundf.racingmanager.infrastructure.gateway.configureMeasurementGateway
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
import io.github.raginlundf.racingmanager.infrastructure.repositories.RaceDeviceSettingsRepository
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
import io.ktor.server.netty.EngineMain
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = EngineMain.main(args)

/** In the "demo" profile, seed a default admin (admin/admin) on first run so the
app is usable out of the box. No-op once any user exists. Switch the profile
(config `racingmanager.profile` or env `RACINGMANAGER_PROFILE`) away from "demo"
to disable. Only applies in [DeploymentMode.LOCAL] — a hosted deployment must
never silently create a shared cloud-wide administrator. */
private fun Application.seedDemoAdmin(authService: AuthService, deploymentMode: DeploymentMode) {
    if (deploymentMode != DeploymentMode.LOCAL) return
    val profile = environment.config.propertyOrNull(path = "racingmanager.profile")?.getString() ?: "demo"
    if (profile != "demo") return
    val setupResult = authService.setupAdmin(username = "admin", password = "admin", displayName = "Administrator")
    if (setupResult is SetupResult.Success) {
        logger.warn { "[demo profile] Seeded default admin 'admin' / 'admin' — change these credentials." }
    }
}

/** Starts the background worker that hard-deletes tenants left in `PENDING_DELETION`
past their retention window. Retention/interval/enabled come from
`racingmanager.tenantPurge` (defaults: 24h retention, 1h sweep, enabled). Set
`racingmanager.tenantPurge.enabled = false` to disable. */
private fun Application.startTenantPurgeWorker(authService: AuthService) {
    val config = environment.config
    val enabled = config.propertyOrNull("racingmanager.tenantPurge.enabled")?.getString()?.toBoolean() ?: true
    if (!enabled) {
        logger.info { "Tenant purge worker disabled via config" }
        return
    }
    val retention = config.propertyOrNull("racingmanager.tenantPurge.retention")?.getString()
        ?.let { Duration.parse(it) } ?: 24.hours
    val interval = config.propertyOrNull("racingmanager.tenantPurge.interval")?.getString()
        ?.let { Duration.parse(it) } ?: 1.hours
    TenantPurgeWorker(authService = authService, retention = retention, interval = interval).start()
}

/** Resolves the JWT signing key source for [deploymentMode]. In [DeploymentMode.LOCAL]
a key is generated and persisted on first run; in [DeploymentMode.HOSTED] keys are
read from `racingmanager.jwt.keys` deployment configuration. Never logs key material. */
private fun Application.configureJwtKeyProvider(deploymentMode: DeploymentMode): JwtKeyProvider {
    return when (deploymentMode) {
        DeploymentMode.LOCAL -> {
            val provider = LocalJwtKeyProvider(repository = SigningKeyRepository())
            val key = provider.ensureKeyExists()
            logger.info { "JWT signing key ready (kid=${key.kid})" }
            provider
        }

        DeploymentMode.HOSTED -> HostedJwtKeyProvider.fromConfig(config = environment.config)
    }
}

fun Application.module() {
    // Connect the database first: the race-device gateway reads its persisted
    // settings during construction below, so the connection must already exist.
    val dataSource = configureDatabase()
    val repositories = Repositories()
    val measurementGateway = configureMeasurementGateway(
        settingsRepository = repositories.raceDeviceSettingsRepository,
    )
    val coreServices = CoreServices(repositories = repositories, measurementGateway = measurementGateway)

    configureLogging()
    configureSerialization()
    configureStatusPages()
    configureStaticContent()
    val deploymentMode = configureDeploymentMode()
    val jwtKeyProvider = configureJwtKeyProvider(deploymentMode = deploymentMode)
    val jwtService = JwtService(keyProvider = jwtKeyProvider)
    val securedServices = SecuredServices(
        repositories = repositories,
        dataSource = dataSource,
        jwtService = jwtService,
        jwtKeyProvider = jwtKeyProvider,
    )

    logUnfinishedHeats(diagnosticsService = securedServices.diagnosticsService)
    seedDemoAdmin(authService = securedServices.authService, deploymentMode = deploymentMode)
    startTenantPurgeWorker(authService = securedServices.authService)
    configureWebSockets()
    coreServices.spectatorWebSocketService.start()
    configureAppRouting(
        deploymentMode = deploymentMode,
        jwtService = jwtService,
        repositories = repositories,
        coreServices = coreServices,
        securedServices = securedServices,
        measurementGateway = measurementGateway,
    )
}

/** Startup dependency container: all repositories (no-arg constructors). */
private class Repositories {
    val userRepository = UserRepository()
    val auditRepository = AuditRepository()
    val eventRepository = EventRepository()
    val tenantRepository = TenantRepository()
    val membershipRepository = MembershipRepository()
    val refreshTokenRepository = RefreshTokenRepository()
    val participantRepository = ParticipantRepository()
    val heatRepository = HeatRepository()
    val raceDeviceSettingsRepository = RaceDeviceSettingsRepository()
    val qualificationRepository = QualificationRepository()
    val knockoutRepository = KnockoutRepository()
    val spectatorExchangeCodeRepository = SpectatorExchangeCodeRepository()
    val importedPackageRepository = ImportedPackageRepository()
    val localInstanceRepository = LocalInstanceRepository()
    val pairingCodeRepository = PairingCodeRepository()
    val pairedInstanceRepository = PairedInstanceRepository()
    val syncedResultRepository = SyncedResultRepository()
}

/** Domain services wired from [repositories]; unauthenticated, no JWT/deployment deps. */
private class CoreServices(
    repositories: Repositories,
    measurementGateway: ReconfigurableMeasurementGateway,
) {
    val eventService = EventService(
        eventRepository = repositories.eventRepository,
        participantRepository = repositories.participantRepository,
        auditRepository = repositories.auditRepository
    )
    val participantService = ParticipantService(
        participantRepository = repositories.participantRepository,
        eventRepository = repositories.eventRepository,
        auditRepository = repositories.auditRepository
    )
    val heatService = HeatService(
        heatRepository = repositories.heatRepository,
        eventRepository = repositories.eventRepository,
        participantRepository = repositories.participantRepository,
        auditRepository = repositories.auditRepository,
        measurementGateway = measurementGateway
    )
    val qualificationService = QualificationService(
        qualificationRepository = repositories.qualificationRepository,
        heatRepository = repositories.heatRepository,
        eventRepository = repositories.eventRepository,
        participantRepository = repositories.participantRepository,
        auditRepository = repositories.auditRepository
    )
    val knockoutService = KnockoutService(
        knockoutRepository = repositories.knockoutRepository,
        heatRepository = repositories.heatRepository,
        eventRepository = repositories.eventRepository,
        participantRepository = repositories.participantRepository,
        qualificationRepository = repositories.qualificationRepository,
        auditRepository = repositories.auditRepository
    )
    val resultsService = ResultsService(
        eventRepository = repositories.eventRepository,
        participantRepository = repositories.participantRepository,
        heatRepository = repositories.heatRepository,
        qualificationRepository = repositories.qualificationRepository,
        knockoutRepository = repositories.knockoutRepository,
        auditRepository = repositories.auditRepository
    )
    val auditService = AuditService(auditRepository = repositories.auditRepository)
    val spectatorService = SpectatorService(
        eventRepository = repositories.eventRepository,
        heatRepository = repositories.heatRepository,
        participantRepository = repositories.participantRepository,
        qualificationRepository = repositories.qualificationRepository,
        knockoutRepository = repositories.knockoutRepository
    )
    val spectatorWebSocketService = SpectatorWebSocketService(
        spectatorService = spectatorService,
        heatRepository = repositories.heatRepository,
        heatServiceEvents = heatService.events
    )
}

/** Services that depend on the resolved JWT/deployment/database context. */
private class SecuredServices(
    repositories: Repositories,
    dataSource: DataSource,
    jwtService: JwtService,
    jwtKeyProvider: JwtKeyProvider,
) {
    val authService = AuthService(
        userRepository = repositories.userRepository,
        tenantRepository = repositories.tenantRepository,
        membershipRepository = repositories.membershipRepository,
        refreshTokenRepository = repositories.refreshTokenRepository,
        auditRepository = repositories.auditRepository,
        passwordHasher = PasswordHasher(),
        jwtService = jwtService
    )
    val localPackageService = LocalPackageService(
        eventRepository = repositories.eventRepository,
        participantRepository = repositories.participantRepository,
        tenantRepository = repositories.tenantRepository,
        importedPackageRepository = repositories.importedPackageRepository,
        localInstanceRepository = repositories.localInstanceRepository,
        jwtKeyProvider = jwtKeyProvider
    )
    val syncService = SyncService(
        pairingCodeRepository = repositories.pairingCodeRepository,
        pairedInstanceRepository = repositories.pairedInstanceRepository,
        syncedResultRepository = repositories.syncedResultRepository,
        eventRepository = repositories.eventRepository,
        auditRepository = repositories.auditRepository
    )
    val diagnosticsService = DiagnosticsService(
        dataSource = dataSource,
        eventRepository = repositories.eventRepository,
        participantRepository = repositories.participantRepository,
        heatRepository = repositories.heatRepository
    )
}

/** Warn on any unfinished heats found at startup (recovery guidance only). */
private fun Application.logUnfinishedHeats(diagnosticsService: DiagnosticsService) {
    diagnosticsService.findUnfinishedHeats().let { unfinished ->
        if (unfinished.isNotEmpty()) {
            logger.warn { "Found ${unfinished.size} unfinished heat(s) on startup — recovery recommended" }
            unfinished.forEach { uf ->
                logger.warn {
                    "  Heat #${uf.heat.heatNumber} " +
                            "(${uf.heat.id}) in event '${uf.event.name}' " +
                            "has status ${uf.heat.status}"
                }
            }
        }
    }
}

/** Registers all HTTP/WebSocket routes, threading the wired dependency containers. */
private fun Application.configureAppRouting(
    deploymentMode: DeploymentMode,
    jwtService: JwtService,
    repositories: Repositories,
    coreServices: CoreServices,
    securedServices: SecuredServices,
    measurementGateway: ReconfigurableMeasurementGateway,
) {
    configureRouting(
        authService = securedServices.authService,
        jwtService = jwtService,
        eventService = coreServices.eventService,
        participantService = coreServices.participantService,
        heatService = coreServices.heatService,
        qualificationService = coreServices.qualificationService,
        knockoutService = coreServices.knockoutService,
        resultsService = coreServices.resultsService,
        spectatorService = coreServices.spectatorService,
        eventRepository = repositories.eventRepository,
        webSocketService = coreServices.spectatorWebSocketService,
        auditService = coreServices.auditService,
        diagnosticsService = securedServices.diagnosticsService,
        deploymentMode = deploymentMode,
        spectatorExchangeCodeRepository = repositories.spectatorExchangeCodeRepository,
        localPackageService = securedServices.localPackageService,
        syncService = securedServices.syncService,
        raceDeviceGateway = measurementGateway,
        raceDeviceSettingsRepository = repositories.raceDeviceSettingsRepository
    )
}
