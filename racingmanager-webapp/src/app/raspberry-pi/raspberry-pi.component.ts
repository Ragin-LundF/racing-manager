import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { LocaleSelectorComponent } from '../i18n/locale-selector.component';
import { highlightPython } from './python-highlight';
import { highlightJson } from './json-highlight';

@Component({
  selector: 'app-raspberry-pi',
  standalone: true,
  imports: [RouterLink, TranslatePipe, LocaleSelectorComponent],
  templateUrl: './raspberry-pi.component.html',
  styleUrl: './raspberry-pi.component.scss',
})
export class RaspberryPiComponent {
  protected readonly authService = inject(AuthService);

  /** Which code block last got copied — drives the transient "Copied!" label. */
  protected readonly copied = signal('');

  protected readonly envelopeSample = `{
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
}`;

  protected readonly finishSample = `{
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
}`;

  protected readonly pythonExample = `import asyncio
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

    # TODO: replace wait_for_finishes() with real GPIO edge callbacks. Every
    # lane is timed independently; a lane that never triggers must be reported
    # as a "timeout" result (and must NOT emit finishDetected).
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
    # TODO: wire this to RPi.GPIO / gpiozero. This stub fakes two finishes so
    # the example runs end-to-end without hardware attached.
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
                       deviceId="pi-track-01", firmwareVersion="1.0.0",
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
`;

  /** Syntax-highlighted HTML for display; copy still uses the raw sources. */
  protected readonly pythonHtml = highlightPython(this.pythonExample);
  protected readonly envelopeHtml = highlightJson(this.envelopeSample);
  protected readonly finishHtml = highlightJson(this.finishSample);

  protected copy(id: string, text: string): void {
    navigator.clipboard?.writeText(text).then(() => {
      this.copied.set(id);
      setTimeout(() => this.copied.set(''), 2000);
    });
  }
}
