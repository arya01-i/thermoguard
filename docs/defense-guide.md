# Live Defense Guide — ThermoGuard

## 1. Two-minute demo script
1. Show the Wokwi circuit / real board: "This is the inside of a server cabinet. The DHT sensor measures the air, and the servo vent, the relay-driven fan, the RGB light and the buzzer are the outputs."
2. Start the Java app → pick the COM port → **Connect**. Point at the log: `HELLO;ThermoGuard;v2.0`, then `DATA;…` every second.
3. Warm the sensor (in Wokwi drag the temperature above 28 °C) → vent opens, **fan starts**, LED red, beep, GUI shows **ALARM**.
4. Cool it down → vent closes, LED green, and point out that the **fan keeps running** until the temperature drops 1.5 °C below the limit. Say the word *hysteresis*.
5. Switch to **MANUAL** → move the vent slider, click **Fan on / Fan off**, click LED colours, toggle the buzzer. Each click shows `> command` and `< ACK;…`.
6. Press **▶ Demo** to run the scripted scenario.
7. Unplug USB → status "Device disconnected" / "Reconnecting…" → plug in → reconnects automatically.

## 2. How Java and Arduino communicate (say this)
- USB Serial (COM port), 9600 baud, 8N1, plain ASCII text.
- **One message = one line** ending with `\n`. Fields separated by `;`, values as `key=value`.
- Arduino *pushes* telemetry periodically; Java *sends* commands; Arduino *always replies* `ACK;<cmd>` or `ERR;<code>`.
- Why text? Easy to debug in the Serial Monitor and in the GUI log, and easy to extend.

## 3. Code structure (say this)
| File | Layer | Job |
|---|---|---|
| `thermoguard.ino` | Arduino | line buffer → `handle()`; timer for telemetry; beep without `delay()`; relay + hysteresis |
| `SerialService.java` | Serial | open/close port (jSerialComm), background reader thread, `send()` |
| `Telemetry.java` | Logic | parse `DATA;…` into a record |
| `CsvLogger.java` | Logic | save readings to CSV |
| `ClimateFrame.java` | GUI | Swing window, controls, chart, log, watchdog, demo mode |

Key points:
- **Why a background thread?** Reading the port blocks; on the GUI thread the window would freeze. Results go back with `SwingUtilities.invokeLater`.
- **Why `millis()` not `delay()`?** `delay()` stops the loop, so commands would be ignored while the board is waiting.
- **Why a relay for the fan?** A fan draws far more current than an Arduino pin can give. The relay is a switch: the Arduino only tells the relay "on" or "off", and the fan's own supply goes through the relay contact.
- **What is hysteresis?** The fan starts at the limit but stops 1.5 °C below it. Without that gap the relay would click on and off every second while the temperature sits on the limit.
- **Error handling:** wrong/busy port → dialog; unplugged → status + auto-reconnect; no data 6 s → timeout message (PING after 3 s); bad command → `ERR;UNKNOWN_CMD` / `BAD_ANGLE` / `FAN_IN_AUTO`…; malformed lines ignored by the parser.

## 4. Likely questions
- *What is baud rate?* Bits per second on the serial line; both sides must match (9600).
- *Why ACK?* So Java knows the command arrived and was valid.
- *What happens in AUTO if I send `FAN ON`?* `ERR;FAN_IN_AUTO` — in AUTO the Arduino owns the actuators, otherwise the GUI and the automatic rule would fight each other.
- *Why does the fan stay on after the alarm stops?* Hysteresis — it must cool down properly, and it protects the relay from chattering.
- *Why is the LED behind resistors?* To limit current and protect the LED and the pins.
- *Difference DHT11 vs DHT22?* DHT22 is more precise; Wokwi only has DHT22, the lab has DHT11 — change `DHT_TYPE`.
- *How would you add a new command?* One `if` in `handle()` on Arduino + one button calling `send()` in Java + one row in the protocol table.

## 5. Practice "small change on the fly" (5–15 min)
| Request | Where to change |
|---|---|
| Change alarm limit default to 30 °C | `.ino`: `float tempLimit = 30.0;` and the Java spinner default |
| Make telemetry every 2 s | send `RATE 2000` or change `rateMs` |
| Change the fan hysteresis to 3 °C | `.ino`: `float fanOffGap = 3.0;` |
| Add "Yellow" LED button | `ClimateFrame`: `colorBtn("Yellow", "LED 255 255 0")` |
| Add command `BEEP <ms>` | `.ino` `handle()`: `tone(BUZZER_PIN,1000,ms); ack(cmd);` + a button |
| Show °F too | `ClimateFrame.onLine`: `t.temp()*9/5+32` |
| Add a second sensor on A0 | `analogRead(A0)` → add `;L=` to DATA, parse it in `Telemetry` |
| Drive the fan with the L9110 module instead | KS0168 on two pins, replace `setFan()` with `digitalWrite` on those pins |

## 6. Checklist before the defense
- [ ] Sketch uploaded with `DHT_TYPE DHT11`
- [ ] Java app runs (`mvn compile exec:java`) on your laptop, correct COM port
- [ ] Arduino IDE Serial Monitor **closed** (only one program can own the port)
- [ ] Screenshots + hardware photo added to `docs/images/`
- [ ] GitHub Pages link opens
- [ ] Backup demo video (30–60 s)
