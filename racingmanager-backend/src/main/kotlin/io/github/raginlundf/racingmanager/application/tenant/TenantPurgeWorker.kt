package io.github.raginlundf.racingmanager.application.tenant

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.raginlundf.racingmanager.application.auth.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Periodically purges tenants that have sat in `PENDING_DELETION` longer than
 * [retention]. Follows the [SpectatorWebSocketService][io.github.raginlundf.racingmanager.infrastructure.spectator.SpectatorWebSocketService]
 * pattern: owns its own scope, started explicitly from `Application.module()`.
 *
 * ponytail: naive while+delay sweep, runs on every instance. Fine for a
 * single-instance deployment. If this ever runs multi-instance, add leader
 * election or move the sweep to a real scheduler so tenants aren't purged twice.
 */
class TenantPurgeWorker(
    private val authService: AuthService,
    private val retention: Duration,
    private val interval: Duration,
) {
    private val scope = CoroutineScope(context = Dispatchers.Default + SupervisorJob())

    fun start() {
        logger.info { "Tenant purge worker started (retention=$retention, interval=$interval)" }
        scope.launch {
            while (isActive) {
                runCatching { authService.purgeExpiredTenants(retention) }
                    .onSuccess { purged -> if (purged > 0) logger.info { "Purged $purged expired tenant(s)" } }
                    .onFailure { logger.error(throwable = it) { "Tenant purge sweep failed" } }
                delay(duration = interval)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
