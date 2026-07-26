# Racing track documentation package

Choose a language and begin with its `PROJECT.md`:

- [Deutsch](de/PROJECT.md)
- [English](en/PROJECT.md)

Both language packages contain the same reference design: a Raspberry Pi running Racing Manager, one ESP32 at the start, one ESP32 at the finish, four through-beam sensors, Wi-Fi as the preferred transport, and optional RS485 fallback.

The local Wi-Fi is an internal network for the two ESP32 modules and one registration station that records participants and vehicles. It is not intended as public visitor Wi-Fi.

This package intentionally specifies an integration contract rather than assuming a particular internal framework for `Ragin-LundF/racing-manager`. Before implementation, follow the relevant language's `AGENT_RACING_MANAGER_INTEGRATION.md` inside the actual repository.

---

**Schnellnavigation:** [Deutsch – Projektüberblick](de/PROJECT.md) · [English – Project overview](en/PROJECT.md) · [Deutsch – Materialliste](de/MATERIALS.md) · [English – Materials](en/MATERIALS.md) · [Deutsch – Architektur](de/ARCHITECTURE.md) · [English – Architecture](en/ARCHITECTURE.md)
