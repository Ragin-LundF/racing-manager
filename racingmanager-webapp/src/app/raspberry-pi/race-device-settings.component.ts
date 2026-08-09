import { ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { RaceDeviceClient } from '../libs/clients/race-device/race-device.client';
import {
  ArduinoSettings,
  Esp32DeviceStatus,
  Esp32Settings,
  SerialPort,
} from '../libs/clients/race-device/race-device.models';

/** Defaults from the device integration spec (.plan/Adruino-impl.md §7.1), with the
    two values that a real capture resolved: the finish value is an elapsed time, and
    the false-start window is off because the board starts on arming. */
const ARDUINO_DEFAULTS: ArduinoSettings = {
  portName: '',
  baudRate: 115200,
  readyTimeoutMs: 10000,
  falseStartWindowMs: 0,
  finishSemantics: 'ELAPSED',
  rawLogPath: 'raw-timing.log',
};

/** Defaults matching the backend's Esp32WebSocketDirectSettings — the concrete
    deployment (start-beam to finish-beam timing), handshake and time-sync off. */
const ESP32_DEFAULTS: Esp32Settings = {
  expectedDeviceIds: ['lane-1-start', 'lane-1-finish', 'lane-2-start', 'lane-2-finish'],
  registerTimeoutMs: 10000,
  useRaceControlHandshake: false,
  useTimeSync: false,
  useDeviceHeartbeat: true,
  heartbeatTimeoutMs: 5000,
  armTimeoutMs: 5000,
  timeSyncRounds: 5,
  rawLogPath: 'raw-esp32-timing.log',
};

@Component({
  selector: 'app-race-device-settings',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './race-device-settings.component.html',
  styleUrl: './race-device-settings.component.scss',
})
export class RaceDeviceSettingsComponent {
  private readonly client = inject(RaceDeviceClient);
  private readonly translate = inject(TranslateService);
  // Zoneless CD: ngModel fields set in an async callback need an explicit nudge.
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly testResult = signal('');
  protected readonly testOk = signal(false);
  protected readonly testing = signal(false);
  protected readonly serialPorts = signal<SerialPort[]>([]);
  protected readonly esp32Devices = signal<Esp32DeviceStatus[]>([]);

  protected mode = 'SIMULATED';
  protected endpoint = 'ws://raspberrypi.local:8080/race';
  protected finishTimeoutMs = 30000;
  protected arduino: ArduinoSettings = { ...ARDUINO_DEFAULTS };
  protected esp32: Esp32Settings = { ...ESP32_DEFAULTS };

  constructor() {
    this.client.getSettings().subscribe({
      next: (settings) => {
        this.mode = settings.mode;
        this.endpoint = settings.endpoint;
        this.finishTimeoutMs = settings.finishTimeoutMs;
        this.arduino = settings.arduino ?? { ...ARDUINO_DEFAULTS };
        this.esp32 = settings.esp32 ?? { ...ESP32_DEFAULTS };
        this.cdr.markForCheck();
      },
      error: () => this.error.set(this.translate.instant('raspberryPi.config.loadError')),
    });
    // A machine with no adapter attached simply offers no ports; the field stays
    // editable so an operator can still type a name.
    this.client.listSerialPorts().subscribe({
      next: (ports) => this.serialPorts.set(ports),
      error: () => this.serialPorts.set([]),
    });
    this.refreshEsp32Devices();
  }

  /** The device-id list as a comma-separated field for the form; there is
      nothing simpler than free text for an operator to edit a short list. */
  protected get esp32DeviceIdsText(): string {
    return this.esp32.expectedDeviceIds.join(', ');
  }

  protected set esp32DeviceIdsText(value: string) {
    this.esp32.expectedDeviceIds = value
      .split(',')
      .map((id) => id.trim())
      .filter((id) => id.length > 0);
  }

  /** Empty when the race device is not currently in ESP32_WEBSOCKET_DIRECT mode —
      that is not an error, just nothing to show. */
  protected refreshEsp32Devices(): void {
    this.client.listEsp32Devices().subscribe({
      next: (devices) => this.esp32Devices.set(devices),
      error: () => this.esp32Devices.set([]),
    });
  }

  protected onSave(): void {
    this.error.set('');
    this.success.set('');
    this.testResult.set('');
    this.client
      .updateSettings({
        mode: this.mode,
        endpoint: this.endpoint,
        finishTimeoutMs: this.finishTimeoutMs,
        arduino: this.arduino,
        esp32: this.esp32,
      })
      .subscribe({
        next: () => {
          this.success.set(this.translate.instant('raspberryPi.config.saved'));
          if (this.mode === 'ESP32_WEBSOCKET_DIRECT') this.refreshEsp32Devices();
        },
        error: () => this.error.set(this.translate.instant('raspberryPi.config.saveError')),
      });
  }

  protected onTest(): void {
    this.error.set('');
    this.success.set('');
    this.testResult.set('');
    this.testing.set(true);
    this.client
      .testConnection({ mode: this.mode, endpoint: this.endpoint, arduino: this.arduino, esp32: this.esp32 })
      .subscribe({
        next: (result) => {
          this.testing.set(false);
          this.testOk.set(result.ok);
          this.testResult.set(
            result.ok
              ? this.translate.instant('raspberryPi.config.testSuccess', { ms: result.pingMs ?? 0 })
              : this.translate.instant('raspberryPi.config.testFailed', { message: result.error ?? '' }),
          );
          if (this.mode === 'ESP32_WEBSOCKET_DIRECT') this.refreshEsp32Devices();
        },
        error: (err) => {
          this.testing.set(false);
          this.testOk.set(false);
          this.testResult.set(this.translate.instant('raspberryPi.config.testFailed', { message: err.message }));
        },
      });
  }
}
