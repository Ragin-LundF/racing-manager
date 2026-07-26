# Race Device Integration Specification

**Protocol version 1**

## Overview

This document explains how an external race device — a Raspberry Pi, an Arduino
or ESP32 with networking, or any controller that can run a WebSocket server —
talks to the racing-manager backend, so you can build your own controller.

The backend handles the user interface, race selection, live display, and
persistence. Your device handles the starting gate, both independent lane
sensors, and high-resolution timing, and reports results in real time.

Communication is a small JSON protocol over a single WebSocket connection. The
backend connects to your device as a client, so **your controller runs the
WebSocket server**.

Nothing in this protocol depends on a particular board, language, or GPIO
library. If it can serve WebSocket and read two sensors, it can run a race.

## How it connects

```text
  ┌──────────────────┐        ┌────────────────────────┐        ┌───────────────────────┐
  │   Operator UI    │  HTTP  │     racing-manager     │        │      Race device      │
  │     (browser)    │───/WS──│        backend         │        │   (WebSocket server)  │
  └──────────────────┘        │      ws:// client      │        │  ┌─────────────────┐  │
                              │                        │───────▶│  │  Shared gate    │  │
                              │                        │ commands  ├─────────────────┤  │
                              │                        │◀───────│  │  Lane 1 sensor  │  │
                              │                        │  events   ├─────────────────┤  │
                              │                        │        │  │  Lane 2 sensor  │  │
                              └────────────────────────┘        │  └─────────────────┘  │
                                                                └───────────────────────┘
```

The backend is the WebSocket client and connects out to your device, which runs
the WebSocket server. Commands flow right, events flow left.

## Connection & message envelope

The backend opens one persistent WebSocket connection to your device and keeps
it open. Commands flow from the backend; events flow back from the device the
moment they happen.

The endpoint is configured in the app, for example
`ws://<device-host>:8080/race` — `ws://raspberrypi.local:8080/race` where mDNS
is available, or a fixed address such as `ws://192.168.1.100:8080/race` for a
board without name resolution.

Every message — in both directions — is a JSON object with the same envelope.
The `payload` object carries a `type` discriminator plus the fields for that
message.

```json
{
  "protocolVersion": 1,
  "messageId": "b3f2a1e0-...-uuid",
  "timestamp": "2026-07-13T15:30:12.123Z",
  "raceId": "race-123",
  "payload": {
    "type": "prepareRace",
    "lanes": [1, 2],
    "startMode": "shared-gate",
    "finishTimeoutMs": 30000
  }
}
```

### Envelope fields

| Field | Meaning |
|---|---|
| `protocolVersion` | Contract version. This document describes version 1. |
| `messageId` | Unique id of this message; use it to detect and drop duplicates. |
| `timestamp` | Wall-clock time the message was sent (ISO-8601). Not the authoritative race time. |
| `raceId` | Correlates every message of one race; equals the heat id. |
| `payload` | The typed body: a `type` field plus the fields for that message. |

## Commands (backend → device)

The backend sends these commands to your controller. Answer `prepareRace` and
`startRace` with the matching events.

| Type | Payload fields | Description |
|---|---|---|
| `hello` | — | Sent right after connecting. The device replies with `helloAck` (identity, firmware, capabilities, lanes). |
| `prepareRace` | `lanes`, `startMode`, `finishTimeoutMs` | Close and arm the gate, enable the given lanes, and verify the sensors — without releasing the cars. Reply with `raceReady`, or `error` if a sensor is already blocked. |
| `startRace` | — | Release the shared gate once, take the monotonic start timestamp, and reply with `raceStarted`. Accept it only once per race. |
| `abortRace` | — | Abort the current race, close the gate where possible, and ignore any later finishes for it. |
| `reset` | — | Return the device to its initial state. A reset during a race aborts it first. |
| `ping` | — | Connection and latency check; reply with `pong`. |

## Events (device → backend)

Your controller sends these events back. Race times come from the device's
monotonic clock in nanoseconds.

| Type | Payload fields | Description |
|---|---|---|
| `helloAck` | `deviceId`, `firmwareVersion`, `protocolVersion`, `capabilities`, `lanes` | Answer to `hello`: device id, firmware and protocol version, capabilities, and available lanes. |
| `raceReady` | `lanes`, `gateState` | The device is prepared and the gate is closed; the listed lanes are armed. |
| `raceStarted` | `startedLanes`, `controllerMonotonicNs` | The gate was released. `controllerMonotonicNs` is the shared reference all lane times are measured against. |
| `finishDetected` | `lane`, `finishSequence`, `finishMonotonicNs`, `elapsedNs`, `sensorState` | One lane crossed the finish. Emitted per lane, independently. `elapsedNs` is the authoritative time since the start. |
| `raceFinished` | `results[]`, `completionReason` | All active lanes have finished or the timeout elapsed. `results` lists each lane's status; a lane that never finished appears with status `"timeout"`. |
| `error` | `code`, `message` | A problem occurred; carries a machine-readable code (see below) and a message. |
| `pong` | — | Answer to `ping`. |

Each entry in `results[]` is `{ "lane": 1, "status": "finished", "elapsedMs": 3287.1 }`.
`status` is one of `finished`, `timeout`, `not-started`, `aborted`, `invalid`;
`elapsedMs` may be omitted for a lane that has no time.

```json
{
  "protocolVersion": 1,
  "messageId": "9c1d...-uuid",
  "timestamp": "2026-07-13T15:30:15.410Z",
  "raceId": "race-123",
  "payload": {
    "type": "finishDetected",
    "lane": 1,
    "finishSequence": 1,
    "finishMonotonicNs": 1234567893400,
    "elapsedNs": 3287100,
    "sensorState": "blocked"
  }
}
```

## Timing & correctness rules

- Race times must come from a monotonic clock in nanoseconds — never wall-clock time.
- Measure both lanes independently; never assume they finish in the same order or at the same time.
- Emit at most one accepted finish per lane and race.
- Decide the winner from the measured durations and `finishSequence`, never from the order messages arrive.
- Make commands idempotent: a repeated `startRace` must not start a second race — answer with a `DUPLICATE_COMMAND` error instead.

The monotonic source is platform-specific — `time.monotonic_ns()` in Python,
`System.nanoTime()` on the JVM, `esp_timer_get_time()` on ESP32, `micros()` on
AVR. The wire unit is always **nanoseconds**: multiply microsecond counters by
1000, and on 32-bit counters mind the rollover (`micros()` wraps after ~70
minutes — take the difference in the native unsigned type before converting).

## Error codes

| Code | Meaning |
|---|---|
| `INVALID_STATE` | The command is not valid in the device's current state (e.g. `startRace` before `prepareRace`). |
| `UNKNOWN_RACE` | The `raceId` is not known to the device. |
| `HARDWARE_FAILURE` | A gate or sensor could not be operated or read. |
| `DUPLICATE_COMMAND` | A command was received again that must not repeat (e.g. a second `startRace`). |
| `SENSOR_STUCK` | A finish sensor is blocked when it should be clear. |

## Reference implementation

One minimal WebSocket server, in Python, that answers every command and emits
the events. Any language or board that can serve WebSocket works just as well —
this is a reference, not a requirement. Replace the sensor stub with your real
sensor reads.

`pip install websockets` — install the one dependency, then run the script on
your device.

```python
import asyncio
import json
import time
import uuid
from datetime import datetime, timezone

import websockets  # pip install websockets

PROTOCOL_VERSION = 1
LANES = [1, 2]


def envelope(msg_type, race_id=None, **payload):
    """Wrap a payload in the protocol v1 envelope and return a JSON string."""
    return json.dumps({
        "protocolVersion": PROTOCOL_VERSION,
        "messageId": str(uuid.uuid4()),
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "raceId": race_id,
        "payload": {"type": msg_type, **payload},
    })


async def send(ws, msg_type, race_id=None, **payload):
    await ws.send(envelope(msg_type, race_id, **payload))


async def run_race(ws, race_id, lanes):
    # Release the shared gate here (open the ramp / drop the servo), then take
    # the monotonic reference the whole race is measured against.
    start_ns = time.monotonic_ns()
    await send(ws, "raceStarted", race_id,
               startedLanes=lanes, controllerMonotonicNs=start_ns)

    # TODO: replace wait_for_finishes() with real sensor reads (GPIO edge
    # callbacks, pin-change interrupts, …). Every lane is timed independently;
    # a lane that never triggers must be reported as a "timeout" result (and
    # must NOT emit finishDetected).
    results = []
    finishes = await wait_for_finishes(lanes, start_ns)
    for seq, finish in enumerate(finishes, start=1):
        elapsed_ns = finish["finish_ns"] - start_ns
        await send(ws, "finishDetected", race_id,
                   lane=finish["lane"], finishSequence=seq,
                   finishMonotonicNs=finish["finish_ns"],
                   elapsedNs=elapsed_ns, sensorState="blocked")
        results.append({"lane": finish["lane"], "status": "finished",
                        "elapsedMs": elapsed_ns / 1_000_000})

    await send(ws, "raceFinished", race_id,
               results=results, completionReason="all-lanes-finished")


async def wait_for_finishes(lanes, start_ns):
    # TODO: wire this to your sensor input. This stub fakes two finishes so the
    # example runs end-to-end without hardware attached.
    await asyncio.sleep(1.0)
    return [{"lane": lane, "finish_ns": start_ns + lane * 500_000_000}
            for lane in lanes]


async def handler(ws):
    prepared = {}  # raceId -> lanes
    async for raw in ws:
        msg = json.loads(raw)
        payload = msg.get("payload", {})
        mtype = payload.get("type")
        race_id = msg.get("raceId")

        if mtype == "hello":
            await send(ws, "helloAck",
                       deviceId="track-01", firmwareVersion="1.0.0",
                       protocolVersion=PROTOCOL_VERSION,
                       capabilities=["shared-gate", "two-lane"], lanes=LANES)
        elif mtype == "ping":
            await send(ws, "pong", race_id)
        elif mtype == "prepareRace":
            lanes = payload.get("lanes", LANES)
            # Close the gate and verify every sensor is clear before READY.
            prepared[race_id] = lanes
            await send(ws, "raceReady", race_id, lanes=lanes, gateState="closed")
        elif mtype == "startRace":
            lanes = prepared.get(race_id)
            if lanes is None:
                await send(ws, "error", race_id,
                           code="INVALID_STATE", message="race not prepared")
                continue
            asyncio.create_task(run_race(ws, race_id, lanes))
        elif mtype in ("abortRace", "reset"):
            # Close the gate and ignore any later finishes for this race.
            prepared.pop(race_id, None)


async def main():
    # The backend connects to ws://<this-host>:8080/race — websockets.serve
    # accepts every path, so the handler receives the /race connection.
    async with websockets.serve(handler, "0.0.0.0", 8080):
        print("Race device listening on ws://0.0.0.0:8080/race")
        await asyncio.Future()  # run forever


if __name__ == "__main__":
    asyncio.run(main())
```

## Implementation checklist

- Serve WebSocket and accept the configured path; answer `hello` with `helloAck`.
- Keep per-`raceId` state: `IDLE → PREPARED → RUNNING → FINISHED`.
- Emit at most one accepted `finishDetected` per lane and race.
- Reject a second `startRace` for the same race with `DUPLICATE_COMMAND`.
- After `finishTimeoutMs`, close the race and report every lane that never
  triggered as `status: "timeout"` in `raceFinished` — without a
  `finishDetected` for it.
- Reply to `ping` with `pong` so the backend can see the link is alive.
- Drop repeated `messageId`s instead of acting on them twice.
