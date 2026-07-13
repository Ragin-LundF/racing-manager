package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.heat.HeatEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

private val logger = KotlinLogging.logger {}

/** A [MeasurementGateway] whose underlying device connection can be swapped at
    runtime (device mode / Raspberry Pi endpoint / finish timeout) without
    replacing the gateway object or its [events] flow — both are captured once at
    startup by HeatService and the spectator feed. Saving new settings from the UI
    calls [reconfigure], which tears down the old delegate and builds a new one
    while the stable event stream keeps flowing to existing subscribers. */
class ReconfigurableMeasurementGateway(
    initialSettings: RaceDeviceSettings,
    private val buildDelegate: (RaceDeviceSettings) -> RaspberryPiMeasurementGateway,
    private val scope: CoroutineScope = CoroutineScope(context = Dispatchers.Default),
) : MeasurementGateway {
    private val events = MutableSharedFlow<MeasurementGatewayEvent>(extraBufferCapacity = 64)
    private val reconfigureLock = Mutex()

    @Volatile
    private var settings: RaceDeviceSettings = initialSettings

    @Volatile
    private var delegate: RaspberryPiMeasurementGateway = buildDelegate(initialSettings)

    private var forwardJob: Job = startForwarding(source = delegate)

    override fun events(): Flow<MeasurementGatewayEvent> {
        return events.asSharedFlow()
    }

    override suspend fun arm(heat: HeatEntity): GatewayArmResult {
        return delegate.arm(heat = heat)
    }

    override suspend fun start(heat: HeatEntity) {
        delegate.start(heat = heat)
    }

    override suspend fun cancel(heatId: UUID): GatewayCancelResult {
        return delegate.cancel(heatId = heatId)
    }

    /** The currently active settings, for the settings read endpoint. */
    fun current(): RaceDeviceSettings {
        return settings
    }

    /** Swaps the live device connection to [newSettings]: stops forwarding, closes
        the old delegate (and its reconnect loop), builds a new delegate, and
        resumes forwarding — all under a lock so overlapping saves serialize. */
    suspend fun reconfigure(newSettings: RaceDeviceSettings) {
        reconfigureLock.withLock {
            logger.info { "Reconfiguring race device: mode=${newSettings.mode} endpoint=${newSettings.endpoint}" }
            forwardJob.cancelAndJoin()
            delegate.close()
            val next = buildDelegate(newSettings)
            delegate = next
            settings = newSettings
            forwardJob = startForwarding(source = next)
        }
    }

    private fun startForwarding(source: RaspberryPiMeasurementGateway): Job {
        return scope.launch {
            source.events().collect { event -> events.emit(value = event) }
        }
    }
}
