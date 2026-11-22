# ClimaLink — Smart Climate Station
A Java Swing desktop app that monitors temperature and humidity from an Arduino over USB Serial and controls a servo "window", RGB status LED and buzzer alarm.

## Components
- Arduino UNO R3 (KS0001)
- DHT11 temperature/humidity sensor (KS0034) — DHT22 in Wokwi
- Micro servo 9G (KS0209) — window flap
- RGB LED module (KS0032) — status colour
- Passive buzzer (KS0019) — alarm
- Breadboard, jumper wires, USB cable

## Repository Structure
- `/arduino` — `climalink.ino` sketch + Wokwi `diagram.json`
- `/java-app` — Maven project (Swing GUI, jSerialComm)
- `/docs` — GitHub Pages report (`index.html`), `proposal.md` (Step 1), `defense-guide.md`, `images/`

## How to Run
1. Wokwi: create a new UNO project, paste `climalink.ino` and `diagram.json`, add library "DHT sensor library", press Play.
2. Real board: set `DHT_TYPE DHT11`, upload with Arduino IDE.
3. Requires JDK 17+ and Maven. `cd java-app && mvn compile exec:java`
4. Pick the COM port, press **Connect**.

## Serial Protocol (9600 baud, one message = one line, `\n`)
| Direction | Message | Meaning |
|---|---|---|
| A → J | `HELLO;ClimaLink;v1.0` | Boot |
| A → J | `DATA;T=24.5;H=52.0;WIN=0;MODE=AUTO;ALARM=0` | Telemetry every RATE ms |
| A → J | `ACK;<cmd>` / `ERR;<code>` | Reply |
| J → A | `PING` → `PONG` | Health check |
| J → A | `MODE AUTO` / `MODE MANUAL` | Automatic or GUI control |
| J → A | `WIN OPEN` / `WIN CLOSE` / `WIN <0-180>` | Servo (manual only) |
| J → A | `LED <r> <g> <b>` | RGB 0-255 (manual only) |
| J → A | `BUZZ ON` / `BUZZ OFF` | Buzzer |
| J → A | `THR T <°C>` / `THR H <%>` | Alarm limits |
| J → A | `RATE <200-10000>` | Telemetry interval |

Extra features: auto-reconnect after unplugging, timeout/PING watchdog, ▶ Demo mode, CSV logging (`climalink-log.csv`).

Errors: `UNKNOWN_CMD`, `BAD_ANGLE`, `BAD_LED`, `BAD_RATE`, `WIN_IN_AUTO`, `LED_IN_AUTO`, `SENSOR_READ`.

## Documentation
GitHub Pages report: https://arya01-i.github.io/climalink/

## Screenshots
![Wokwi](docs/images/wokwi-circuit.png)
![GUI](docs/images/gui.png)
![Hardware](docs/images/hardware.jpg)
