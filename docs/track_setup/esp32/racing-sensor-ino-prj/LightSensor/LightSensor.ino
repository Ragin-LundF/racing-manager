#include <Arduino.h>
#include <WiFi.h>
#include <WebSocketsClient.h>
#include <ArduinoJson.h>

// ===== Configuration: adapt for each measuring module =====
constexpr char WIFI_SSID[] = "RacingManager";
constexpr char WIFI_PASSWORD[] = "race-4-life";
constexpr char MODULE_ID[] = "lane-1-start";
constexpr uint8_t LANE_NUMBER = 1;
constexpr char MODULE_POSITION[] = "start"; // "start" or "finish"

constexpr char WEBSOCKET_HOST[] = "192.168.10.1";
constexpr uint16_t WEBSOCKET_PORT = 8080;
constexpr char WEBSOCKET_PATH[] = "/hardware/esp32/ws";

constexpr uint8_t SENSOR_PIN = 16;
constexpr uint8_t SENSOR_ACTIVE_LEVEL = LOW;
constexpr uint32_t DEBOUNCE_US = 20'000;
constexpr uint32_t WIFI_RETRY_INTERVAL_MS = 10'000;
constexpr uint32_t WEBSOCKET_RECONNECT_MS = 3'000;
constexpr uint32_t HEARTBEAT_INTERVAL_MS = 1'000;
// PROTOCOL.md's message contract; this sketch only ever sends v=1 frames.
constexpr uint8_t PROTOCOL_VERSION = 1;

WebSocketsClient webSocket;
volatile bool sensorEventPending = false;
volatile uint32_t sensorEventTimestampUs = 0;
volatile uint32_t lastInterruptUs = 0;
bool websocketConnected = false;
uint32_t eventCount = 0;
uint32_t lastHeartbeatMs = 0;
// Changes every boot so the backend can tell a reconnect from a reset; two
// random words are enough entropy to be unique among the 4 modules on one Pi.
String bootId;

void IRAM_ATTR onBeamBroken() {
  const uint32_t nowUs = micros();
  if (nowUs - lastInterruptUs < DEBOUNCE_US) return;
  lastInterruptUs = nowUs;
  sensorEventTimestampUs = nowUs;
  sensorEventPending = true;
}

void sendDeviceRegister() {
  StaticJsonDocument<256> message;
  message["v"] = PROTOCOL_VERSION;
  message["type"] = "device.register";
  message["device_id"] = MODULE_ID;
  message["boot_id"] = bootId;
  message["role"] = MODULE_POSITION;
  message["firmware"] = "1.0.0";
  JsonArray capabilities = message.createNestedArray("capabilities");
  capabilities.add("beam_sensor");
  capabilities.add("wifi");

  String text;
  serializeJson(message, text);
  Serial.println(text);
  webSocket.sendTXT(text);
}

void sendHeartbeat() {
  StaticJsonDocument<256> message;
  message["v"] = PROTOCOL_VERSION;
  message["type"] = "device.heartbeat";
  message["device_id"] = MODULE_ID;
  message["boot_id"] = bootId;
  message["uptime_ms"] = millis();
  message["transport"] = "wifi";
  JsonObject sensors = message.createNestedObject("sensors");
  const bool beamBroken = digitalRead(SENSOR_PIN) == SENSOR_ACTIVE_LEVEL;
  sensors[String("lane_") + LANE_NUMBER] = beamBroken ? "blocked" : "clear";

  String text;
  serializeJson(message, text);
  webSocket.sendTXT(text);
}

void onWebSocketEvent(WStype_t type, uint8_t *payload, size_t length) {
  if (type == WStype_CONNECTED) {
    websocketConnected = true;
    Serial.println("WebSocket connected");
    sendDeviceRegister();
  } else if (type == WStype_DISCONNECTED) {
    websocketConnected = false;
    Serial.println("WebSocket disconnected");
  }
}

void sendSensorEvent(uint32_t timestampUs) {
  eventCount++;
  StaticJsonDocument<256> event;
  event["v"] = PROTOCOL_VERSION;
  event["type"] = "sensor.event";
  event["message_id"] = bootId + "-" + String(eventCount);
  event["device_id"] = MODULE_ID;
  event["boot_id"] = bootId;
  event["sequence"] = eventCount;
  event["role"] = MODULE_POSITION;
  event["lane"] = LANE_NUMBER;
  event["event"] = "beam_broken";
  event["local_timestamp_us"] = timestampUs;

  String message;
  serializeJson(event, message);
  Serial.println(message);
  if (websocketConnected) webSocket.sendTXT(message);
}

void connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);

  Serial.println("[WiFi] Scanning for visible networks...");
  const int found = WiFi.scanNetworks();
  bool targetSeen = false;
  for (int i = 0; i < found; i++) {
    Serial.printf("[WiFi]  - '%s' ch=%d rssi=%ddBm enc=%d%s\n",
                  WiFi.SSID(i).c_str(), WiFi.channel(i), WiFi.RSSI(i), WiFi.encryptionType(i),
                  WiFi.SSID(i) == WIFI_SSID ? "  <-- target" : "");
    if (WiFi.SSID(i) == WIFI_SSID) targetSeen = true;
  }
  Serial.printf("[WiFi] Target SSID '%s' seen in scan: %s\n", WIFI_SSID, targetSeen ? "YES" : "NO");

  Serial.printf("[WiFi] Connecting to SSID '%s'...\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

void setup() {
  Serial.begin(115200);
  pinMode(SENSOR_PIN, INPUT_PULLUP);
  bootId = String(esp_random(), HEX) + String(esp_random(), HEX);

  connectWifi();

  webSocket.begin(WEBSOCKET_HOST, WEBSOCKET_PORT, WEBSOCKET_PATH);
  webSocket.onEvent(onWebSocketEvent);
  webSocket.setReconnectInterval(WEBSOCKET_RECONNECT_MS);

  const int edge = SENSOR_ACTIVE_LEVEL == LOW ? FALLING : RISING;
  attachInterrupt(digitalPinToInterrupt(SENSOR_PIN), onBeamBroken, edge);
}

void loop() {
  webSocket.loop();

  static wl_status_t lastWifiStatus = WL_IDLE_STATUS;
  const wl_status_t currentWifiStatus = WiFi.status();
  if (currentWifiStatus != lastWifiStatus) {
    lastWifiStatus = currentWifiStatus;
    if (currentWifiStatus == WL_CONNECTED) {
      Serial.printf("[WiFi] Connected. IP=%s RSSI=%ddBm\n", WiFi.localIP().toString().c_str(), WiFi.RSSI());
    } else {
      Serial.printf("[WiFi] status changed to %d\n", currentWifiStatus);
    }
  }

  static uint32_t lastWifiRetryMs = 0;
  if (currentWifiStatus != WL_CONNECTED && millis() - lastWifiRetryMs > WIFI_RETRY_INTERVAL_MS) {
    lastWifiRetryMs = millis();
    WiFi.reconnect();
  }

  uint32_t timestampUs = 0;
  bool eventReady = false;
  noInterrupts();
  if (sensorEventPending) {
    timestampUs = sensorEventTimestampUs;
    sensorEventPending = false;
    eventReady = true;
  }
  interrupts();

  if (eventReady) sendSensorEvent(timestampUs);

  if (websocketConnected && millis() - lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS) {
    lastHeartbeatMs = millis();
    sendHeartbeat();
  }
}
