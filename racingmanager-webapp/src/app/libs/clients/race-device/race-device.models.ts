/** Race-device connection settings. `mode` is 'SIMULATED' or 'HARDWARE';
    `endpoint` is the Raspberry Pi WebSocket URL, used only for 'HARDWARE'. */
export interface RaceDeviceSettings {
  mode: string;
  endpoint: string;
  finishTimeoutMs: number;
}

export interface RaceDeviceTestRequest {
  mode: string;
  endpoint: string;
}

export interface RaceDeviceTestResult {
  ok: boolean;
  pingMs?: number;
  error?: string;
}
