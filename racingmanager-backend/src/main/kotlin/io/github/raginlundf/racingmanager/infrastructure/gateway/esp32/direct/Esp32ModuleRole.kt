package io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct

/** The `role` a module plays for its lane — `PROTOCOL.md`'s `role`/`position`
    values. [wireValue] is the exact string used in `device.register.role` and
    `sensor.event.role`; the wire layer
    ([io.github.raginlundf.racingmanager.infrastructure.gateway.esp32.direct.protocol.Esp32Message])
    keeps these as raw strings, this enum is the gateway's internal, validated form. */
enum class Esp32ModuleRole(val wireValue: String) {
    START("start"),
    FINISH("finish"),
    ;

    companion object {
        fun from(value: String): Esp32ModuleRole? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}
