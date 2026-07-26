package io.github.raginlundf.racingmanager.infrastructure.gateway

import io.github.raginlundf.racingmanager.application.heat.CloseableMeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.GatewayArmResult
import io.github.raginlundf.racingmanager.application.heat.GatewayCancelResult
import io.github.raginlundf.racingmanager.application.heat.MeasurementGateway
import io.github.raginlundf.racingmanager.application.heat.MeasurementGatewayEvent
import io.github.raginlundf.racingmanager.domain.event.MeasurementType
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
    private val buildDelegate: (RaceDeviceSettings) -> CloseableMeasurementGateway,
    private val scope: CoroutineScope = CoroutineScope(context = Dispatchers.Default),
) : MeasurementGateway {
    private val events = MutableSharedFlow<MeasurementGatewayEvent>(extraBufferCapacity = 64)
    private val reconfigureLock = Mutex()

    @Volatile
    private var settings: RaceDeviceSettings = initialSettings

    @Volatile
    private var delegate: CloseableMeasurementGateway = buildDelegate(initialSettings)

    private var forwardJob: Job = startForwarding(source = delegate)

    /** In-process simulator kept alongside the configured device, so SIMULATED
        events never reach real hardware. Built on first use — an install that only
        runs hardware events never pays for it. */
    @Volatile
    private var simulator: CloseableMeasurementGateway? = null
    private val simulatorLock = Any()

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

    /** Routes a SIMULATED event to the simulator even when this instance is wired to
        a Raspberry Pi or an Arduino: choosing simulated timing on the event is the
        operator saying "no hardware", so a missing board must not fail the heat.
        Everything else runs on the configured device. */
    override fun forMeasurementType(measurementType: MeasurementType): MeasurementGateway {
        if (measurementType != MeasurementType.SIMULATED || settings.mode == RaceDeviceMode.SIMULATED) {
            return delegate
        }
        return simulator ?: synchronized(simulatorLock) {
            simulator ?: buildDelegate(settings.copy(mode = RaceDeviceMode.SIMULATED)).also {
                logger.info { "Simulated event on a ${settings.mode} instance — using the in-process simulator" }
                simulator = it
                // Share the stable event stream, so simulated measurements reach the
                // same HeatService subscriber as hardware ones.
                startForwarding(source = it)
            }
        }
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
            // A device that is already broken (board unplugged, port gone) must never
            // stop the operator from switching to another mode — least of all back to
            // the simulator, which is how they recover.
            runCatching { delegate.close() }
                .onFailure { logger.warn(it) { "Ignoring failure while closing the previous race device" } }
            val next = buildDelegate(newSettings)
            delegate = next
            settings = newSettings
            forwardJob = startForwarding(source = next)
        }
    }

    private fun startForwarding(source: CloseableMeasurementGateway): Job {
        return scope.launch {
            source.events().collect { event -> events.emit(value = event) }
        }
    }
}
