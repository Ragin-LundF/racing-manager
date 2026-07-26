# Testing and event checklist

These tests do more than show that “something happens”: they verify that every sensor belongs to the correct lane and that a fault becomes visible. Complete the table before the first use and before every event.

## Acceptance tests

| Test | Expected outcome |
|---|---|
| Block one sensor | Exactly one matching health change and diagnostic event |
| Leave sensor clear for 30 s | No false event |
| Both start beams | Lanes stay independent |
| Lane 1 only | Only lane 1 starts/finishes; lane 2 remains unscored |
| Two-lane race | Each lane receives exactly one start and finish |
| Disconnect Wi-Fi | ESP buffers, reconnects, server deduplicates |
| Enable RS485 | Same protocol semantics, visible error counters |
| Reboot one ESP during a race | UI marks race interrupted/invalid; never silently scores it |

## Verify measurement quality

Run 20 controlled reference passes and record elapsed time, `sync_uncertainty_us`, transport, and outliers. Define an acceptable tolerance before the event. If it is not met, use RS485 or score from the common gate-release reference.

## Immediately-before-event checklist

- [ ] Pi, power supplies, sensor brackets, and spare cables checked
- [ ] Local Wi-Fi and all operator devices connected
- [ ] Both modules online and firmware versions match
- [ ] All sensors clear and labeled correctly
- [ ] One- and two-lane test runs passed
- [ ] Time-quality warning is visible in UI
- [ ] Result export/backup tested

## Fault patterns

| Symptom | Likely cause | Action |
|---|---|---|
| Always `blocked` | Misaligned beam / wrong logic level | Check optics and `active_level` |
| Random events | Sunlight, loose wire, no debounce | Add shade, strain relief, filtering |
| ESP offline | AP range/power issue | Check distance, supply, antenna, logs |
| Implausible times | Clock uncertainty or wrong order | Check sync quality; invalidate race |

**Next step for developers:** [ESP_AGENT_GUIDE.md](ESP_AGENT_GUIDE.md). For the Racing Manager connection, follow [AGENT_RACING_MANAGER_INTEGRATION.md](AGENT_RACING_MANAGER_INTEGRATION.md).

---

**Navigation:** [← Setup](SETUP.md) · [Next: ESP agent brief →](ESP_AGENT_GUIDE.md) · [Deutsch](../de/TESTING.md)

**All topics:** [Project overview](PROJECT.md) · [Materials](MATERIALS.md) · [Architecture](ARCHITECTURE.md) · [Wiring](WIRING.md) · [Firmware](FIRMWARE.md) · [Protocol](PROTOCOL.md) · [Setup](SETUP.md) · **Testing** · [ESP agent brief](ESP_AGENT_GUIDE.md) · [Racing Manager integration](AGENT_RACING_MANAGER_INTEGRATION.md)
