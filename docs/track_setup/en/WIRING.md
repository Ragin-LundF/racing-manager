# Pinout and wiring

You now connect parts electrically. Work with power supplies switched off, wire just one sensor first, and test it before adding another. A GPIO is a small ESP32 signal input; it may receive a 3.3-V signal only.

## Safety rules

> **Warning — 3.3 V:** ESP32 GPIO is not 5-V tolerant. A 5-V sensor output can destroy the input. Check every output with both its data sheet and a multimeter before connecting it.

> **Warning — power:** Sensors and ESP32 may use the same 5-V supply, but all logically connected devices require a shared ground. Future servos/solenoids need a separate, adequately sized supply.

## Reference pinout

These pins target a standard ESP32 DevKit. Display boards may reserve display pins; change the mapping in firmware configuration in that case.

| Module | Function | ESP32 pin | Connection |
|---|---|---:|---|
| START | Lane 1 start beam (OUT) | GPIO 32 | Sensor output after 3.3-V interface |
| START | Lane 2 start beam (OUT) | GPIO 33 | Sensor output after 3.3-V interface |
| FINISH | Lane 1 finish beam (OUT) | GPIO 32 | Sensor output after 3.3-V interface |
| FINISH | Lane 2 finish beam (OUT) | GPIO 33 | Sensor output after 3.3-V interface |
| both | RS485 RX | GPIO 16 | MAX3485 RO |
| both | RS485 TX | GPIO 17 | MAX3485 DI |
| both | RS485 DE + /RE | GPIO 4 | Tie DE and /RE together |
| START | Future servo L1/L2 reserve | GPIO 25 / 26 | Signal only, later |

GPIO 32/33 are input-only and therefore suitable for sensors. Internal pull-ups do not replace a defined level interface.

This is a **reference pinout**, not an absolute rule. An ESP32 board with built-in display may reserve some pins. In that case select suitable free inputs and change the firmware configuration too.

## Beam sensor at each point

```text
IR transmitter: VCC ─── 3.3 V or the manufacturer-rated voltage
                GND ─── GND

IR receiver:    VCC ─── appropriate supply
                GND ─── shared GND
                OUT ─── [level shifter/optocoupler] ─── GPIO 32 or 33
```

For an open-collector receiver, a pull-up to **3.3 V** is valid only when its data sheet permits it. For a push-pull 5-V output, use a bidirectional level shifter or optocoupler; a generic resistor divider is not the reference event-hardware design.

## Optional RS485 bus

```text
ESP32 A                    CAT5e/CAT6                     ESP32 B
GPIO17 ─ DI  MAX3485                                  MAX3485  DI ─ GPIO17
GPIO16 ─ RO  MAX3485                                  MAX3485  RO ─ GPIO16
GPIO4  ─ DE,/RE MAX3485                              MAX3485 DE,/RE ─ GPIO4
             A ═══════════════════════════════════════════ A
             B ═══════════════════════════════════════════ B
            GND ───────────────────────────────────────── GND (reference)
```

Fit 120 ohms across A–B at both bus ends. CAT cable is merely cable here; do not connect it to a network switch or PoE. Use a 3.3-V transceiver (MAX3485, SP3485, or equivalent), not an unprotected 5-V MAX485 board.

---

**Navigation:** [← Architecture](ARCHITECTURE.md) · [Next: Firmware →](FIRMWARE.md) · [Deutsch](../de/WIRING.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · **Wiring** · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · [Testing](TESTING.md) · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
