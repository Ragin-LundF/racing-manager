#include <Arduino.h>
#include <WiFi.h>
#include <WebSocketsClient.h>
#include <ArduinoJson.h>
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>

// ===== Configuration: adapt for each measuring module =====
constexpr char WIFI_SSID[] = "RacingManager";
constexpr char WIFI_PASSWORD[] = "race-4-life";
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
constexpr uint32_t DISPLAY_POLL_INTERVAL_MS = 50;

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
bool screenStaticDrawn = false;
bool beamStateDrawn = false;
bool wifiStatusDrawn = false;
bool websocketStatusDrawn = false;
bool eventCountDrawn = false;
bool lastBeamBroken = false;
bool lastWifiDisplayed = false;
bool lastWebsocketDisplayed = false;
uint32_t lastEventCountDisplayed = 0;

void drawStatus();

void drawCenteredText(const String &text, int16_t y, uint8_t size, uint16_t color,
                      uint16_t background = ST77XX_BLACK) {
  display.setTextSize(size);
  display.setTextColor(color, background);
  const int16_t width = text.length() * 6 * size;
  int16_t x = (display.width() - width) / 2;
  if (x < 0) x = 0;
  display.setCursor(x, y);
  display.print(text);
}

void drawTextCenteredInBox(const String &text, int16_t x, int16_t width, int16_t y,
                           uint8_t size, uint16_t color) {
  display.setTextSize(size);
  display.setTextColor(color, ST77XX_BLACK);
  const int16_t textWidth = text.length() * 6 * size;
  int16_t cursorX = x + (width - textWidth) / 2;
  if (cursorX < x + 2) cursorX = x + 2;
  display.setCursor(cursorX, y);
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

void drawStaticLayout() {
  display.fillScreen(ST77XX_BLACK);
  display.setTextWrap(false);

  display.fillRect(0, 0, display.width(), 34, ST77XX_BLUE);
  drawCenteredText(MODULE_ID, 9, 2, ST77XX_WHITE, ST77XX_BLUE);
  drawCenteredText(String("Lane ") + LANE_NUMBER + " / " + MODULE_POSITION, 43, 1, ST77XX_CYAN);

  const uint16_t dividerColor = display.color565(70, 70, 70);
  display.drawFastHLine(8, 176, display.width() - 16, dividerColor);
  display.drawFastHLine(8, 236, display.width() - 16, dividerColor);
  drawCenteredText("EVENTS", 247, 1, display.color565(170, 170, 170));

  beamStateDrawn = false;
  wifiStatusDrawn = false;
  websocketStatusDrawn = false;
  eventCountDrawn = false;
  screenStaticDrawn = true;
}

void drawBeamState(bool beamBroken) {
  if (beamStateDrawn && beamBroken == lastBeamBroken) return;

  lastBeamBroken = beamBroken;
  beamStateDrawn = true;

  const uint16_t sensorColor = beamBroken ? ST77XX_RED : ST77XX_GREEN;
  const int16_t centerX = display.width() / 2;
  const int16_t centerY = 110;

  display.fillRect(0, 66, display.width(), 102, ST77XX_BLACK);
  drawCenteredText("BEAM", 72, 1, display.color565(170, 170, 170));
  display.fillRect(20, centerY - 2, display.width() - 40, 5, sensorColor);
  display.fillCircle(centerX, centerY, 16, sensorColor);
  display.drawCircle(centerX, centerY, 18, ST77XX_WHITE);
  drawCenteredText(beamBroken ? "BLOCKED" : "CLEAR", 138, 3, sensorColor);
}

void drawStatusTile(int16_t x, const String &label, bool connected, bool &drawn, bool &lastValue) {
  if (drawn && connected == lastValue) return;

  drawn = true;
  lastValue = connected;

  constexpr int16_t tileY = 190;
  constexpr int16_t tileWidth = 72;
  constexpr int16_t tileHeight = 38;
  const uint16_t color = connected ? ST77XX_GREEN : ST77XX_YELLOW;

  display.fillRect(x, tileY, tileWidth, tileHeight, ST77XX_BLACK);
  display.drawRoundRect(x, tileY, tileWidth, tileHeight, 5, color);
  drawTextCenteredInBox(label, x, tileWidth, tileY + 6, 1, display.color565(190, 190, 190));
  drawTextCenteredInBox(connected ? "OK" : "--", x, tileWidth, tileY + 20, 2, color);
}

void drawEventCount() {
  if (eventCountDrawn && eventCount == lastEventCountDisplayed) return;

  eventCountDrawn = true;
  lastEventCountDisplayed = eventCount;

  const String countText = String(eventCount);
  const uint8_t textSize = countText.length() <= 3 ? 4 : (countText.length() <= 5 ? 3 : 2);

  display.fillRect(0, 262, display.width(), 40, ST77XX_BLACK);
  drawCenteredText(countText, 266, textSize, ST77XX_WHITE);
}

void drawStatus() {
  if (!screenStaticDrawn) drawStaticLayout();

  const bool beamBroken = digitalRead(SENSOR_PIN) == SENSOR_ACTIVE_LEVEL;
  drawBeamState(beamBroken);
  drawStatusTile(8, "WIFI", wifiConnected, wifiStatusDrawn, lastWifiDisplayed);
  drawStatusTile(90, "SERVER", websocketConnected, websocketStatusDrawn, lastWebsocketDisplayed);
  drawEventCount();
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
  // Use flipped portrait orientation so the mounted board is readable.
  display.setRotation(2);
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

  if (millis() - lastScreenUpdateMs >= DISPLAY_POLL_INTERVAL_MS) {
    lastScreenUpdateMs = millis();
    drawStatus();
  }
}
