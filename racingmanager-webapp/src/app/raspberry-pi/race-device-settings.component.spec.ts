import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { Esp32DeviceStatus, RaceDeviceSettings } from '../libs/clients/race-device/race-device.models';
import { RaceDeviceSettingsComponent } from './race-device-settings.component';

const SETTINGS_URL = 'http://localhost:8080/api/v1/racedevice/settings';
const PORTS_URL = 'http://localhost:8080/api/v1/racedevice/serialports';
const TEST_URL = 'http://localhost:8080/api/v1/racedevice/test';
const ESP32_DEVICES_URL = 'http://localhost:8080/api/v1/racedevice/esp32/devices';

const SIMULATED: RaceDeviceSettings = {
  mode: 'SIMULATED',
  endpoint: 'ws://raspberrypi.local:8080/race',
  finishTimeoutMs: 30000,
  arduino: null,
  esp32: null,
};

describe('RaceDeviceSettingsComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RaceDeviceSettingsComponent],
      providers: [provideTestTranslate(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  /** Creates the component and answers the three requests it fires on construction.
      ngModel writes its values in a microtask, so the fixture is settled before the
      caller inspects the form. */
  async function render(
    settings: RaceDeviceSettings = SIMULATED,
    ports: { name: string; description: string }[] = [],
    esp32Devices: Esp32DeviceStatus[] = [],
  ): Promise<ComponentFixture<RaceDeviceSettingsComponent>> {
    const fixture = TestBed.createComponent(RaceDeviceSettingsComponent);
    fixture.detectChanges();
    httpTesting.expectOne(SETTINGS_URL).flush(settings);
    httpTesting.expectOne(PORTS_URL).flush(ports);
    httpTesting.expectOne(ESP32_DEVICES_URL).flush(esp32Devices);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  async function selectMode(fixture: ComponentFixture<RaceDeviceSettingsComponent>, mode: string): Promise<void> {
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select[name="mode"]');
    select.value = mode;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('offers the Arduino two-lane device and the ESP32 direct-connect device as modes', async () => {
    const fixture = await render();

    const options: HTMLOptionElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('select[name="mode"] option'),
    );

    expect(options.map((option) => option.value)).toEqual([
      'SIMULATED',
      'HARDWARE',
      'ARDUINO_TWO_LANE',
      'ESP32_WEBSOCKET_DIRECT',
    ]);
  });

  it('shows the serial fields with the spec defaults when the Arduino mode is picked', async () => {
    const fixture = await render();

    await selectMode(fixture, 'ARDUINO_TWO_LANE');

    const value = (name: string): string => fixture.nativeElement.querySelector(`[name="${name}"]`).value;
    expect(value('portName')).toBe('');
    expect(value('baudRate')).toBe('115200');
    expect(value('readyTimeoutMs')).toBe('10000');
    expect(value('falseStartWindowMs')).toBe('0');
    expect(value('finishSemantics')).toBe('ELAPSED');
    expect(value('rawLogPath')).toBe('raw-timing.log');
    // The WebSocket-only endpoint field must not be part of this mode.
    expect(fixture.nativeElement.querySelector('[name="endpoint"]')).toBeNull();
  });

  it('offers the detected serial ports for selection', async () => {
    const fixture = await render(SIMULATED, [{ name: '/dev/tty.usbmodem1101', description: 'Arduino Mega 2560' }]);

    await selectMode(fixture, 'ARDUINO_TWO_LANE');

    const options: HTMLOptionElement[] = Array.from(fixture.nativeElement.querySelectorAll('datalist option'));
    expect(options.map((option) => option.value)).toEqual(['/dev/tty.usbmodem1101']);
    expect(fixture.nativeElement.querySelector('.hint')?.textContent).toContain('The port the Arduino is on');
  });

  it('hints that no port was found when the host has none', async () => {
    const fixture = await render();

    await selectMode(fixture, 'ARDUINO_TWO_LANE');

    expect(fixture.nativeElement.querySelector('.hint')?.textContent).toContain('No serial ports detected');
  });

  it('loads stored serial options into the form', async () => {
    const fixture = await render({
      mode: 'ARDUINO_TWO_LANE',
      endpoint: 'ws://unused',
      finishTimeoutMs: 45000,
      arduino: {
        portName: '/dev/ttyACM0',
        baudRate: 57600,
        readyTimeoutMs: 8000,
        falseStartWindowMs: 400,
        finishSemantics: 'ELAPSED',
        rawLogPath: 'logs/raw.log',
      },
      esp32: null,
    });

    const value = (name: string): string => fixture.nativeElement.querySelector(`[name="${name}"]`).value;
    expect(value('portName')).toBe('/dev/ttyACM0');
    expect(value('baudRate')).toBe('57600');
    expect(value('finishSemantics')).toBe('ELAPSED');
    expect(value('rawLogPath')).toBe('logs/raw.log');
  });

  it('saves the serial options with the settings', async () => {
    const fixture = await render({
      mode: 'ARDUINO_TWO_LANE',
      endpoint: 'ws://unused',
      finishTimeoutMs: 30000,
      arduino: {
        portName: '/dev/ttyACM0',
        baudRate: 115200,
        readyTimeoutMs: 10000,
        falseStartWindowMs: 250,
        finishSemantics: 'TIMESTAMP',
        rawLogPath: 'raw-timing.log',
      },
      esp32: null,
    });

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpTesting.expectOne(SETTINGS_URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.mode).toBe('ARDUINO_TWO_LANE');
    expect(request.request.body.arduino.portName).toBe('/dev/ttyACM0');
    request.flush(SIMULATED);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.success')?.textContent).toContain('saved');
  });

  it('shows the ESP32 fields with the concrete-case defaults when that mode is picked', async () => {
    const fixture = await render();

    await selectMode(fixture, 'ESP32_WEBSOCKET_DIRECT');

    const value = (name: string): string => fixture.nativeElement.querySelector(`[name="${name}"]`).value;
    expect(value('esp32DeviceIds')).toBe('lane-1-start, lane-1-finish, lane-2-start, lane-2-finish');
    expect(value('esp32RegisterTimeoutMs')).toBe('10000');
    expect(value('esp32RawLogPath')).toBe('raw-esp32-timing.log');
    expect(fixture.nativeElement.querySelector('[name="esp32UseHeartbeat"]').checked).toBe(true);
  });

  it('loads stored esp32 options into the form', async () => {
    const fixture = await render({
      mode: 'ESP32_WEBSOCKET_DIRECT',
      endpoint: 'ws://unused',
      finishTimeoutMs: 30000,
      arduino: null,
      esp32: {
        expectedDeviceIds: ['lane-1-start', 'lane-1-finish'],
        registerTimeoutMs: 5000,
        useRaceControlHandshake: false,
        useTimeSync: false,
        useDeviceHeartbeat: false,
        heartbeatTimeoutMs: 3000,
        armTimeoutMs: 5000,
        timeSyncRounds: 5,
        rawLogPath: 'logs/esp32.log',
      },
    });

    const value = (name: string): string => fixture.nativeElement.querySelector(`[name="${name}"]`).value;
    expect(value('esp32DeviceIds')).toBe('lane-1-start, lane-1-finish');
    expect(value('esp32RegisterTimeoutMs')).toBe('5000');
    expect(value('esp32RawLogPath')).toBe('logs/esp32.log');
    expect(fixture.nativeElement.querySelector('[name="esp32UseHeartbeat"]').checked).toBe(false);
    // heartbeatTimeoutMs is only shown while useDeviceHeartbeat is on.
    expect(fixture.nativeElement.querySelector('[name="esp32HeartbeatTimeoutMs"]')).toBeNull();
  });

  it('saves the esp32 options with the settings', async () => {
    const fixture = await render({
      mode: 'ESP32_WEBSOCKET_DIRECT',
      endpoint: 'ws://unused',
      finishTimeoutMs: 30000,
      arduino: null,
      esp32: {
        expectedDeviceIds: ['lane-1-start', 'lane-1-finish', 'lane-2-start', 'lane-2-finish'],
        registerTimeoutMs: 10000,
        useRaceControlHandshake: false,
        useTimeSync: false,
        useDeviceHeartbeat: true,
        heartbeatTimeoutMs: 5000,
        armTimeoutMs: 5000,
        timeSyncRounds: 5,
        rawLogPath: 'raw-esp32-timing.log',
      },
    });

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = httpTesting.expectOne(SETTINGS_URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.mode).toBe('ESP32_WEBSOCKET_DIRECT');
    expect(request.request.body.esp32.expectedDeviceIds).toEqual([
      'lane-1-start',
      'lane-1-finish',
      'lane-2-start',
      'lane-2-finish',
    ]);
    request.flush(SIMULATED);
    httpTesting.expectOne(ESP32_DEVICES_URL).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.success')?.textContent).toContain('saved');
  });

  it('renders the connected and not-connected ESP32 devices', async () => {
    const fixture = await render(
      {
        mode: 'ESP32_WEBSOCKET_DIRECT',
        endpoint: 'ws://unused',
        finishTimeoutMs: 30000,
        arduino: null,
        esp32: null,
      },
      [],
      [
        { deviceId: 'lane-1-start', connected: true, online: true, lane: 1, role: 'START', lastHeartbeatAt: 'now' },
        { deviceId: 'lane-1-finish', connected: false, online: false, lane: 1, role: 'FINISH', lastHeartbeatAt: null },
      ],
    );

    const rows: HTMLTableRowElement[] = Array.from(fixture.nativeElement.querySelectorAll('.esp32-devices tbody tr'));
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('lane-1-start');
    expect(rows[0].textContent).toContain('Connected');
    expect(rows[1].textContent).toContain('lane-1-finish');
    expect(rows[1].textContent).toContain('Not connected');
  });

  it('blocks saving and testing until a serial port is named', async () => {
    const fixture = await render();

    await selectMode(fixture, 'ARDUINO_TWO_LANE');

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.actions button'));
    expect(buttons.map((button) => button.disabled)).toEqual([true, true]);
  });

  it('reports a failed connection test with the device error', async () => {
    const fixture = await render({
      mode: 'ARDUINO_TWO_LANE',
      endpoint: 'ws://unused',
      finishTimeoutMs: 30000,
      arduino: {
        portName: '/dev/ttyACM0',
        baudRate: 115200,
        readyTimeoutMs: 10000,
        falseStartWindowMs: 250,
        finishSemantics: 'TIMESTAMP',
        rawLogPath: 'raw-timing.log',
      },
      esp32: null,
    });
    const testButton: HTMLButtonElement = fixture.nativeElement.querySelectorAll('.actions button')[1];

    testButton.click();

    const request = httpTesting.expectOne(TEST_URL);
    expect(request.request.body.arduino).not.toBeNull();
    request.flush({ ok: false, error: 'No ready banner within 10000 ms' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No ready banner within 10000 ms');
  });
});
