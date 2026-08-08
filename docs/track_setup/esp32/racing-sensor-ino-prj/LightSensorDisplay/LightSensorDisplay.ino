#include <Arduino.h>
#include <WiFi.h>
#include <WebSocketsClient.h>
#include <ArduinoJson.h>
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>

// ===== Configuration: adapt for each measuring module =====
constexpr char WIFI_SSID[] = "RacingManager";
constexpr char WIFI_PASSWORD[] = "YOUR-WIFI-PASSWORD";
constexpr char MODULE_ID[] = "lane-1-start";
constexpr uint8_t LANE_NUMBER = 1;
constexpr char MODULE_POSITION[] = "start"; // "start" or "finish"

constexpr char WEBSOCKET_HOST[] = "192.168.10.1";
constexpr uint16_t WEBSOCKET_PORT = 8080;
constexpr char WEBSOCKET_PATH[] = "/ws";

constexpr uint8_t SENSOR_PIN = 16;
constexpr uint8_t SENSOR_ACTIVE_LEVEL = LOW;
constexpr uint32_t DEBOUNCE_US = 20'000;
constexpr uint32_t WIFI_CONNECT_TIMEOUT_MS = 15'000;
constexpr uint32_t WIFI_RETRY_INTERVAL_MS = 10'000;
constexpr uint32_t WEBSOCKET_RECONNECT_MS = 3'000;

// Hard-wired pins of the built-in ST7789 display.
constexpr uint8_t LCD_MOSI = 23;
constexpr uint8_t LCD_SCLK = 18;
constexpr uint8_t LCD_CS = 15;
constexpr uint8_t LCD_DC = 2;
constexpr uint8_t LCD_RST = 4;
constexpr uint8_t LCD_BLK = 32;

Adafruit_ST7789 display(LCD_CS, LCD_DC, LCD_RST);
WebSocketsClient webSocket;
volatile bool sensorEventPending = false;
volatile uint32_t sensorEventTimestampUs = 0;
volatile uint32_t lastInterruptUs = 0;
bool wifiConnected = false;
bool websocketConnected = false;
uint32_t eventCount = 0;
uint32_t lastScreenUpdateMs = 0;

void drawStatus();

void drawCenteredText(const String &text, int16_t y, uint8_t size, uint16_t color) {
  display.setTextSize(size);
  display.setTextColor(color, ST77XX_BLACK);
  const int16_t width = text.length() * 6 * size;
  display.setCursor((display.width() - width) / 2, y);
  display.print(text);
}

void IRAM_ATTR onBeamBroken() {
  const uint32_t nowUs = micros();
  if (nowUs - lastInterruptUs < DEBOUNCE_US) return;
  lastInterruptUs = nowUs;
  sensorEventTimestampUs = nowUs;
  sensorEventPending = true;
}

void connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  const uint32_t startedAt = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startedAt < WIFI_CONNECT_TIMEOUT_MS) {
    delay(100);
  }
  wifiConnected = WiFi.status() == WL_CONNECTED;
}

void onWebSocketEvent(WStype_t type, uint8_t *payload, size_t length) {
  if (type == WStype_CONNECTED) websocketConnected = true;
  if (type == WStype_DISCONNECTED) websocketConnected = false;
  drawStatus();
}

void sendSensorEvent(uint32_t timestampUs) {
  eventCount++;
  StaticJsonDocument<256> event;
  event["type"] = "sensor_event";
  event["moduleId"] = MODULE_ID;
  event["lane"] = LANE_NUMBER;
  event["position"] = MODULE_POSITION;
  event["timestampUs"] = timestampUs;
  event["sequence"] = eventCount;

  String message;
  serializeJson(event, message);
  Serial.println(message);
  if (websocketConnected) webSocket.sendTXT(message);
  drawStatus();
}

void drawLine(const String &text, uint16_t color) {
  display.setTextSize(2);
  display.setTextColor(color, ST77XX_BLACK);
  display.println(text);
}

void drawStatus() {
  display.fillScreen(ST77XX_BLACK);
  display.setTextWrap(false);
  const bool beamBroken = digitalRead(SENSOR_PIN) == SENSOR_ACTIVE_LEVEL;
  const uint16_t sensorColor = beamBroken ? ST77XX_RED : ST77XX_GREEN;

  display.fillRect(0, 0, display.width(), 34, ST77XX_BLUE);
  drawCenteredText(MODULE_ID, 9, 2, ST77XX_WHITE);
  drawCenteredText(String("Lane ") + LANE_NUMBER + " / " + MODULE_POSITION, 43, 1, ST77XX_CYAN);

  const int16_t centerX = display.width() / 2;
  const int16_t centerY = 102;
  display.drawCircle(centerX, centerY, 43, ST77XX_WHITE);
  display.fillCircle(centerX, centerY, 39, sensorColor);
  display.fillCircle(centerX - 13, centerY - 13, 10, beamBroken ? ST77XX_ORANGE : ST77XX_YELLOW);
  drawCenteredText(beamBroken ? "INTERRUPTED" : "CLEAR LANE", 151, 2, sensorColor);

  display.drawFastHLine(8, 178, display.width() - 16, display.color565(70, 70, 70));
  display.setCursor(12, 188);
  drawLine(wifiConnected ? "WIFI  OK" : "WIFI  ...", wifiConnected ? ST77XX_GREEN : ST77XX_YELLOW);
  display.setCursor(12, 211);
  drawLine(websocketConnected ? "SERVER OK" : "SERVER ...",
           websocketConnected ? ST77XX_GREEN : ST77XX_YELLOW);
  display.setCursor(100, 199);
  display.setTextColor(ST77XX_WHITE, ST77XX_BLACK);
  display.setTextSize(1);
  display.print("Events");
  display.setCursor(100, 212);
  display.setTextSize(2);
  display.print(eventCount);
}

void setup() {
  Serial.begin(115200);
  pinMode(SENSOR_PIN, INPUT_PULLUP);

  // On this board, initialise Wi-Fi before the display.
  connectWifi();
  pinMode(LCD_BLK, OUTPUT);
  digitalWrite(LCD_BLK, HIGH);
  pinMode(LCD_DC, OUTPUT);
  digitalWrite(LCD_DC, HIGH);
  SPI.begin(LCD_SCLK, -1, LCD_MOSI, LCD_CS);
  display.init(170, 320);
  display.setRotation(0);
  drawStatus();

  webSocket.begin(WEBSOCKET_HOST, WEBSOCKET_PORT, WEBSOCKET_PATH);
  webSocket.onEvent(onWebSocketEvent);
  webSocket.setReconnectInterval(WEBSOCKET_RECONNECT_MS);

  const int edge = SENSOR_ACTIVE_LEVEL == LOW ? FALLING : RISING;
  attachInterrupt(digitalPinToInterrupt(SENSOR_PIN), onBeamBroken, edge);
}

void loop() {
  webSocket.loop();

  const bool currentWifiStatus = WiFi.status() == WL_CONNECTED;
  if (currentWifiStatus != wifiConnected) {
    wifiConnected = currentWifiStatus;
    drawStatus();
  }

  static uint32_t lastWifiRetryMs = 0;
  if (!wifiConnected && millis() - lastWifiRetryMs > WIFI_RETRY_INTERVAL_MS) {
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

  if (millis() - lastScreenUpdateMs >= 250) {
    lastScreenUpdateMs = millis();
    drawStatus();
  }
}
