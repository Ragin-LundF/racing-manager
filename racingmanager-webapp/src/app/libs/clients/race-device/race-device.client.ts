import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import { RaceDeviceSettings, RaceDeviceTestRequest, RaceDeviceTestResult, SerialPort } from './race-device.models';

@Injectable({ providedIn: 'root' })
export class RaceDeviceClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getSettings(): Observable<RaceDeviceSettings> {
    return this.http.get<RaceDeviceSettings>(`${this.baseUrl}/api/v1/racedevice/settings`);
  }

  updateSettings(request: RaceDeviceSettings): Observable<RaceDeviceSettings> {
    return this.http.put<RaceDeviceSettings>(`${this.baseUrl}/api/v1/racedevice/settings`, request);
  }

  testConnection(request: RaceDeviceTestRequest): Observable<RaceDeviceTestResult> {
    return this.http.post<RaceDeviceTestResult>(`${this.baseUrl}/api/v1/racedevice/test`, request);
  }

  /** Serial ports of the machine running the backend, for the Arduino port picker. */
  listSerialPorts(): Observable<SerialPort[]> {
    return this.http.get<SerialPort[]>(`${this.baseUrl}/api/v1/racedevice/serialports`);
  }
}
