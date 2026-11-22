// ThermoGuard — Server Cabinet Overheat Guard (Arduino UNO)
// Reads temperature/humidity inside an equipment cabinet, streams them over Serial,
// and reacts: vent flap (servo), status light (RGB), alarm (buzzer), cooling fan (relay).
// Libraries: "DHT sensor library" (Adafruit), Servo
#include <DHT.h>
#include <Servo.h>

#define DHT_PIN 2
#define DHT_TYPE DHT22   // Wokwi has no DHT11; change to DHT11 for the lab KS0034 module
#define SERVO_PIN 9      // vent flap
#define BUZZER_PIN 8
#define LED_R 5
#define LED_G 6
#define LED_B 3
#define RELAY_PIN 7      // cooling fan through the relay module

// The lab's SINGLE RELAY 5V module is triggered LOW. Set to 0 if your module needs HIGH.
#define RELAY_ACTIVE_LOW 1

DHT dht(DHT_PIN, DHT_TYPE);
Servo ventServo;

bool autoMode = true;
float tempLimit = 28.0;      // above -> vent opens, fan runs, alarm
float humLimit = 70.0;
float fanOffGap = 1.5;       // hysteresis: fan stops only 1.5 °C below the limit
int ventAngle = 0;           // 0 = flap closed, 90 = flap open
bool buzzerManual = false;
bool fanOn = false;
bool fanManual = false;      // set by FAN ON / FAN OFF while in MANUAL mode
unsigned long rateMs = 1000, lastSend = 0, lastBeep = 0;
String line;

void setLed(int r, int g, int b) { analogWrite(LED_R, r); analogWrite(LED_G, g); analogWrite(LED_B, b); }
void setVent(int a) { ventAngle = constrain(a, 0, 180); ventServo.write(ventAngle); }
void setFan(bool on) {
  fanOn = on;
  digitalWrite(RELAY_PIN, RELAY_ACTIVE_LOW ? !fanOn : fanOn);
}
void ack(const String& c) { Serial.print("ACK;"); Serial.println(c); }
void err(const String& m) { Serial.print("ERR;"); Serial.println(m); }

void handle(String cmd) {
  cmd.trim(); cmd.toUpperCase();
  if (cmd.length() == 0) return;
  if (cmd == "PING") { Serial.println("PONG"); return; }
  if (cmd == "MODE AUTO")   { autoMode = true;  fanManual = false; ack(cmd); return; }
  if (cmd == "MODE MANUAL") { autoMode = false; ack(cmd); return; }
  if (cmd.startsWith("WIN ")) {
    if (autoMode) { err("WIN_IN_AUTO"); return; }
    String v = cmd.substring(4);
    if (v == "OPEN") setVent(90); else if (v == "CLOSE") setVent(0);
    else { int a = v.toInt(); if (a < 0 || a > 180 || (a == 0 && v != "0")) { err("BAD_ANGLE"); return; } setVent(a); }
    ack(cmd); return;
  }
  if (cmd.startsWith("LED ")) {
    if (autoMode) { err("LED_IN_AUTO"); return; }
    int r, g, b;
    if (sscanf(cmd.c_str() + 4, "%d %d %d", &r, &g, &b) != 3) { err("BAD_LED"); return; }
    setLed(constrain(r,0,255), constrain(g,0,255), constrain(b,0,255)); ack(cmd); return;
  }
  if (cmd == "BUZZ ON")  { buzzerManual = true;  ack(cmd); return; }
  if (cmd == "BUZZ OFF") { buzzerManual = false; noTone(BUZZER_PIN); ack(cmd); return; }
  if (cmd == "FAN ON")   { if (autoMode) { err("FAN_IN_AUTO"); return; } fanManual = true;  setFan(true);  ack(cmd); return; }
  if (cmd == "FAN OFF")  { if (autoMode) { err("FAN_IN_AUTO"); return; } fanManual = false; setFan(false); ack(cmd); return; }
  if (cmd.startsWith("THR T ")) {
    float v = cmd.substring(6).toFloat();
    if (v < 10 || v > 50) { err("BAD_LIMIT"); return; }
    tempLimit = v; ack(cmd); return;
  }
  if (cmd.startsWith("THR H ")) {
    float v = cmd.substring(6).toFloat();
    if (v < 10 || v > 95) { err("BAD_LIMIT"); return; }
    humLimit = v; ack(cmd); return;
  }
  if (cmd.startsWith("RATE ")) {
    long r = cmd.substring(5).toInt();
    if (r < 200 || r > 10000) { err("BAD_RATE"); return; }
    rateMs = r; ack(cmd); return;
  }
  err("UNKNOWN_CMD");
}

void setup() {
  Serial.begin(9600);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(LED_R, OUTPUT); pinMode(LED_G, OUTPUT); pinMode(LED_B, OUTPUT);
  dht.begin();
  ventServo.attach(SERVO_PIN);
  setVent(0);
  setFan(false);
  setLed(0, 0, 255);
  Serial.println("HELLO;ThermoGuard;v2.0");
}

void loop() {
  // 1) Non-blocking serial read: one command = one line
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') { handle(line); line = ""; }
    else if (c != '\r' && line.length() < 40) line += c;
  }

  unsigned long now = millis();
  // 2) Periodic telemetry
  if (now - lastSend >= rateMs) {
    lastSend = now;
    float t = dht.readTemperature(), h = dht.readHumidity();
    if (isnan(t) || isnan(h)) { err("SENSOR_READ"); return; }
    bool alarm = t > tempLimit || h > humLimit;
    if (autoMode) {
      setVent(alarm ? 90 : 0);
      // Hysteresis on the fan: it starts at the limit and stops 1.5 °C below it,
      // so the relay does not click on and off every second around the limit.
      if (t > tempLimit) setFan(true);
      else if (t < tempLimit - fanOffGap) setFan(false);
      if (alarm) setLed(255, 0, 0); else if (t < 18) setLed(0, 0, 255); else setLed(0, 255, 0);
    }
    Serial.print("DATA;T="); Serial.print(t, 1);
    Serial.print(";H="); Serial.print(h, 1);
    Serial.print(";WIN="); Serial.print(ventAngle);
    Serial.print(";FAN="); Serial.print(fanOn ? 1 : 0);
    Serial.print(";MODE="); Serial.print(autoMode ? "AUTO" : "MANUAL");
    Serial.print(";ALARM="); Serial.println(alarm ? 1 : 0);
    if (alarm && autoMode) lastBeep = now;
  }
  // 3) Buzzer: short beep pattern without delay()
  bool beepWanted = buzzerManual || fanManual || (autoMode && now - lastBeep < 300);
  if (beepWanted && (now / 250) % 2 == 0) tone(BUZZER_PIN, 1000); else noTone(BUZZER_PIN);
}
