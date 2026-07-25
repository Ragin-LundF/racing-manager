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

/** Race-device connection settings. `mode` is 'SIMULATED', 'HARDWARE' or
    'ARDUINO_TWO_LANE'; `endpoint` is the Raspberry Pi WebSocket URL, used only for
    'HARDWARE'; `arduino` is used only for 'ARDUINO_TWO_LANE'. */
export interface RaceDeviceSettings {
  mode: string;
  endpoint: string;
  finishTimeoutMs: number;
  arduino: ArduinoSettings | null;
}

export interface RaceDeviceTestRequest {
  mode: string;
  endpoint: string;
  arduino: ArduinoSettings | null;
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
