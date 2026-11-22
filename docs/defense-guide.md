# Live Defense Guide — ClimaLink

## 1. Two-minute demo script
1. Show the Wokwi circuit / real board: "DHT sensor in, servo, RGB LED and buzzer out."
2. Start the Java app → pick COM port → **Connect**. Point at the log: `HELLO;ClimaLink;v1.0` then `DATA;…` every second.
3. Breathe on / warm the sensor (in Wokwi drag the temperature above 28 °C) → window opens, LED red, beep, GUI shows **ALARM**.
4. Switch to **MANUAL** → move the window slider, click LED colours, toggle buzzer. Each click shows `> command` and `< ACK;…`.
5. Press **▶ Demo** to run the scripted scenario.
6. Unplug USB → status "Device disconnected" / "Reconnecting…" → plug in → reconnects automatically.

## 2. How Java and Arduino communicate (say this)
- USB Serial (COM port), 9600 baud, 8N1, plain ASCII text.
- **One message = one line** ending with `\n`. Fields separated by `;`, values as `key=value`.
- Arduino *pushes* telemetry periodically; Java *sends* commands; Arduino *always replies* `ACK;<cmd>` or `ERR;<code>`.
- Why text? Easy to debug in the Serial Monitor and the GUI log, easy to extend.

## 3. Code structure (say this)
| File | Layer | Job |
|---|---|---|
| `climalink.ino` | Arduino | line buffer → `handle()`; timer for telemetry; beep without `delay()` |
| `SerialService.java` | Serial | open/close port (jSerialComm), background reader thread, `send()` |
| `Telemetry.java` | Logic | parse `DATA;…` into a record |
| `CsvLogger.java` | Logic | save readings to CSV |
| `ClimateFrame.java` | GUI | Swing window, controls, chart, log, watchdog, demo mode |

Key points:
- **Why a background thread?** Reading the port blocks; on the GUI thread the window would freeze. Results go back with `SwingUtilities.invokeLater`.
- **Why `millis()` not `delay()`?** `delay()` stops the loop, so commands would be ignored while waiting.
- **Error handling:** wrong/busy port → dialog; unplugged → status + auto-reconnect; no data 6 s → timeout message (PING after 3 s); bad command → `ERR;UNKNOWN_CMD` / `BAD_ANGLE`…; malformed lines ignored by parser.

## 4. Likely questions
- *What is baud rate?* Bits per second on the serial line; both sides must match (9600).
- *Why ACK?* So Java knows the command arrived and was valid.
- *What happens in AUTO if I send `WIN 90`?* `ERR;WIN_IN_AUTO` — Arduino owns the actuators in AUTO.
- *Difference DHT11 vs DHT22?* DHT22 is more precise; Wokwi only has DHT22, the lab has DHT11 — change `DHT_TYPE`.
- *Why is the LED behind resistors?* To limit current and protect the LED and pins.
- *How would you add a new command?* One `if` in `handle()` on Arduino + one button calling `send()` in Java + one row in the protocol table.

## 5. Practice "small change on the fly" (5–15 min)
| Request | Where to change |
|---|---|
| Change alarm limit default to 30 °C | `.ino`: `float tempLimit = 30.0;` and Java spinner default |
| Make telemetry every 2 s | send `RATE 2000` or change `rateMs` |
| Add "Yellow" LED button | `ClimateFrame`: `colorBtn("Yellow", "LED 255 255 0")` |
| Add command `BEEP <ms>` | `.ino` `handle()`: `tone(BUZZER_PIN,1000,ms); ack(cmd);` + a button |
| Show °F too | `ClimateFrame.onLine`: `t.temp()*9/5+32` |
| Add a fan (L9110, KS0168) on humidity | new pin + `FAN ON/OFF` command, auto when `h > humLimit` |
| Send light level from a potentiometer/LDR | `analogRead(A0)` → add `;L=` to DATA, parse in `Telemetry` |

## 6. Checklist before the defense
- [ ] Sketch uploaded with `DHT_TYPE DHT11`
- [ ] Java app runs (`mvn compile exec:java`) on your laptop, correct COM port
- [ ] Arduino IDE Serial Monitor **closed** (only one program can own the port)
- [ ] Screenshots + hardware photo added to `docs/images/`
- [ ] GitHub Pages link opens
- [ ] Backup demo video (30–60 s)
