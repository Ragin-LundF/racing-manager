package io.github.raginlundf.racingmanager.infrastructure.gateway.transport

import io.github.raginlundf.racingmanager.infrastructure.gateway.simulator.FakeRaspberryPiController
import kotlinx.coroutines.flow.Flow

/** In-process transport wiring the adapter directly to a [FakeRaspberryPiController]
    — no socket, no port. Commands are handed straight to the controller; its event
    frames are exposed as [incoming]. Used in the "simulated" race-device mode. */
class LoopbackRaceDeviceTransport(
    private val controller: FakeRaspberryPiController,
) : RaceDeviceTransport {
    override suspend fun connect() {
        // The controller is passive — it reacts to commands. Nothing to start.
    }

    override suspend fun send(text: String) {
        controller.onCommand(text = text)
    }

    override fun incoming(): Flow<String> {
        return controller.outgoing()
    }

    override suspend fun close() {
        // In-process controller lives for the app lifetime; nothing to close.
    }
}
