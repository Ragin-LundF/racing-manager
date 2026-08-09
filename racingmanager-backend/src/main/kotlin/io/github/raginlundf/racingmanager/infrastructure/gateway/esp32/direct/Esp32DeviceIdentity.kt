package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct

/** The lane and role encoded in a module's `device_id` — `lane-<n>-<start|finish>`,
    the naming convention `ESP32_SENSOR_FIRMWARE_GUIDE.md` §6 assigns to each of the
    4 modules. `device.register` carries `role` but not `lane`, so this is the
    authoritative way the gateway learns which lane a socket belongs to. */
data class Esp32DeviceIdentity(
    val lane: Int,
    val role: Esp32ModuleRole,
) {
    companion object {
        private val DEVICE_ID_PATTERN = Regex(pattern = """^lane-(\d+)-(start|finish)$""")

        fun parse(deviceId: String): Esp32DeviceIdentity? {
            val match = DEVICE_ID_PATTERN.matchEntire(input = deviceId) ?: return null
            val lane = match.groupValues[1].toIntOrNull() ?: return null
            val role = Esp32ModuleRole.from(value = match.groupValues[2]) ?: return null
            return Esp32DeviceIdentity(lane = lane, role = role)
        }
    }
}
