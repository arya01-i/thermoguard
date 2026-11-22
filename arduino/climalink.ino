// ClimaLink — Smart Climate Station (Arduino UNO)
// Sends DHT readings over Serial, receives commands from the Java Swing app.
// Libraries: "DHT sensor library" (Adafruit), Servo
#include <DHT.h>
#include <Servo.h>

#define DHT_PIN 2
#define DHT_TYPE DHT22   // Wokwi uses DHT22; change to DHT11 for the lab KS0034 module
#define SERVO_PIN 9
#define BUZZER_PIN 8
#define LED_R 5
#define LED_G 6
#define LED_B 3

DHT dht(DHT_PIN, DHT_TYPE);
Servo windowServo;

bool autoMode = true;
float tempLimit = 28.0;     // above -> open window + alarm
float humLimit = 70.0;
int windowAngle = 0;         // 0 closed, 90 open
bool buzzerManual = false;
unsigned long rateMs = 1000, lastSend = 0, lastBeep = 0;
String line;

void setLed(int r, int g, int b) { analogWrite(LED_R, r); analogWrite(LED_G, g); analogWrite(LED_B, b); }
void setWindow(int a) { windowAngle = constrain(a, 0, 180); windowServo.write(windowAngle); }
void ack(const String& c) { Serial.print("ACK;"); Serial.println(c); }
void err(const String& m) { Serial.print("ERR;"); Serial.println(m); }

void handle(String cmd) {
  cmd.trim(); cmd.toUpperCase();
  if (cmd.length() == 0) return;
  if (cmd == "PING") { Serial.println("PONG"); return; }
  if (cmd == "MODE AUTO")   { autoMode = true;  ack(cmd); return; }
  if (cmd == "MODE MANUAL") { autoMode = false; ack(cmd); return; }
  if (cmd.startsWith("WIN ")) {
    if (autoMode) { err("WIN_IN_AUTO"); return; }
    String v = cmd.substring(4);
    if (v == "OPEN") setWindow(90); else if (v == "CLOSE") setWindow(0);
    else { int a = v.toInt(); if (a < 0 || a > 180 || (a == 0 && v != "0")) { err("BAD_ANGLE"); return; } setWindow(a); }
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
  if (cmd.startsWith("THR T ")) { tempLimit = cmd.substring(6).toFloat(); ack(cmd); return; }
  if (cmd.startsWith("THR H ")) { humLimit = cmd.substring(6).toFloat(); ack(cmd); return; }
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
  pinMode(LED_R, OUTPUT); pinMode(LED_G, OUTPUT); pinMode(LED_B, OUTPUT);
  dht.begin();
  windowServo.attach(SERVO_PIN);
  setWindow(0);
  setLed(0, 0, 255);
  Serial.println("HELLO;ClimaLink;v1.0");
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
      setWindow(alarm ? 90 : 0);
      if (alarm) setLed(255, 0, 0); else if (t < 18) setLed(0, 0, 255); else setLed(0, 255, 0);
    }
    Serial.print("DATA;T="); Serial.print(t, 1);
    Serial.print(";H="); Serial.print(h, 1);
    Serial.print(";WIN="); Serial.print(windowAngle);
    Serial.print(";MODE="); Serial.print(autoMode ? "AUTO" : "MANUAL");
    Serial.print(";ALARM="); Serial.println(alarm ? 1 : 0);
    if (alarm && autoMode) lastBeep = now;
  }
  // 3) Buzzer: short beep pattern without delay()
  bool beepWanted = buzzerManual || (autoMode && now - lastBeep < 300);
  if (beepWanted && (now / 250) % 2 == 0) tone(BUZZER_PIN, 1000); else noTone(BUZZER_PIN);
}
