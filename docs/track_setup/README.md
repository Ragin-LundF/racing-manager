# Racing track documentation package

Choose a language and begin with its `PROJECT.md`:

- [Deutsch](de/PROJECT.md)
- [English](en/PROJECT.md)

Both language packages contain the same reference design: a Raspberry Pi running Racing Manager, one ESP32 at the start, one ESP32 at the finish, four through-beam sensors, Wi-Fi as the preferred transport, and optional RS485 fallback.

The local Wi-Fi is an internal network for the two ESP32 modules and one registration station that records participants and vehicles. It is not intended as public visitor Wi-Fi.

This package intentionally specifies an integration contract rather than assuming a particular internal framework for `Ragin-LundF/racing-manager`. Before implementation, follow the relevant language's `AGENT_RACING_MANAGER_INTEGRATION.md` inside the actual repository.

## Shared, language-neutral packages

Two parts of the build are the same in both languages and are therefore kept once, in English:

| Folder | Contents |
|---|---|
| [`cad_files/`](cad_files/README.md) | OpenSCAD sources for the two-lane infrared break-beam gate: parametric U-frames for start and finish, cable covers, the glue-on electronics housing, STL export scripts, and print/assembly notes. |
| [`esp32/`](esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md) | Practical ESP32 firmware guide: wiring of the Adafruit ADA2167 break-beam sensor, Arduino IDE board and library setup, the WebSocket event format, and two complete sketches — one for a plain DevKit and one for the board with the integrated LCD. |

The language packages reference both from their materials, wiring, setup, and firmware chapters.

---

**Schnellnavigation:** [Deutsch – Projektüberblick](de/PROJECT.md) · [English – Project overview](en/PROJECT.md) · [Deutsch – Materialliste](de/MATERIALS.md) · [English – Materials](en/MATERIALS.md) · [Deutsch – Architektur](de/ARCHITECTURE.md) · [English – Architecture](en/ARCHITECTURE.md)

**Shared / Gemeinsam:** [CAD files](cad_files/README.md) · [ESP32 firmware guide](esp32/ESP32_SENSOR_FIRMWARE_GUIDE.md)
