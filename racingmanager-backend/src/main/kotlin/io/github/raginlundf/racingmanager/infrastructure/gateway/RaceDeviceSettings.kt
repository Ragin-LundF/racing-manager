package io.github.raginlundf.racingmanager.infrastructure.gateway

/** The user-configurable race-device connection settings. Persisted (single row)
    so a local install can point the app at a real Raspberry Pi from the UI instead
    of only via startup parameters. [endpoint] is only used when [mode] is
    [RaceDeviceMode.HARDWARE]. */
data class RaceDeviceSettings(
    val mode: RaceDeviceMode,
    val endpoint: String,
    val finishTimeoutMs: Long,
)
