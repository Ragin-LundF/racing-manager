/** Arduino two-lane serial options, read only when the mode is 'ARDUINO_TWO_LANE'.
    `finishSemantics` is 'TIMESTAMP' or 'ELAPSED' — how the device's FINISH value is
    interpreted, which is unresolved in the device protocol and therefore a setting. */
export interface ArduinoSettings {
  portName: string;
  baudRate: number;
  readyTimeoutMs: number;
  falseStartWindowMs: number;
  finishSemantics: string;
  rawLogPath: string;
}

/** ESP32 WebSocket Direct Connect options, read only when the mode is
    'ESP32_WEBSOCKET_DIRECT'. `useRaceControlHandshake`/`useTimeSync` are not
    implemented yet by the backend gateway and must stay `false` — surfaced here
    read-only so the UI can explain why, not as editable toggles. */
export interface Esp32Settings {
  expectedDeviceIds: string[];
  registerTimeoutMs: number;
  useRaceControlHandshake: boolean;
  useTimeSync: boolean;
  useDeviceHeartbeat: boolean;
  heartbeatTimeoutMs: number;
  armTimeoutMs: number;
  timeSyncRounds: number;
  rawLogPath: string;
}

/** Race-device connection settings. `mode` is 'SIMULATED', 'HARDWARE',
    'ARDUINO_TWO_LANE' or 'ESP32_WEBSOCKET_DIRECT'; `endpoint` is the Raspberry Pi
    WebSocket URL, used only for 'HARDWARE'; `arduino` is used only for
    'ARDUINO_TWO_LANE'; `esp32` is used only for 'ESP32_WEBSOCKET_DIRECT'. */
export interface RaceDeviceSettings {
  mode: string;
  endpoint: string;
  finishTimeoutMs: number;
  arduino: ArduinoSettings | null;
  esp32: Esp32Settings | null;
}

export interface RaceDeviceTestRequest {
  mode: string;
  endpoint: string;
  arduino: ArduinoSettings | null;
  esp32: Esp32Settings | null;
}

export interface RaceDeviceTestResult {
  ok: boolean;
  pingMs?: number;
  error?: string;
}

/** A serial port found on the machine running the backend. */
export interface SerialPort {
  name: string;
  description: string;
}

/** Live status of one expected ESP32 module — there is nothing to "test-dial" in
    an inbound-only mode, so this is the closest equivalent to a connection test. */
export interface Esp32DeviceStatus {
  deviceId: string;
  connected: boolean;
  online: boolean;
  lane: number | null;
  role: string | null;
  lastHeartbeatAt: string | null;
}
