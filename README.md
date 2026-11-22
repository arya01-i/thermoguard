# ThermoGuard — Server Cabinet Overheat Guard
A Java Swing desktop app that watches the climate inside an equipment cabinet over USB Serial and reacts to overheating: it opens a vent flap (servo), turns on a cooling fan (relay), shows the status on an RGB LED and sounds a buzzer alarm.

## The problem
A server or network cabinet traps heat. If the air inside gets too hot, the equipment throttles or dies. ThermoGuard measures the air inside the cabinet and reacts on its own: above the limit the vent opens and the fan runs; when it cools down again the fan stops. A Java app on the desk shows live readings, a chart and a log, and lets the user take manual control.

## Components (all from the Laboratory Inventory, room 5-101)
| Part | Lab code | Qty | Role |
|---|---|---|---|
| Arduino UNO R3 | KS0001 | 1 | Controller, USB Serial |
| DHT11 temperature & humidity sensor | KS0034 | 1 | Input: air inside the cabinet |
| Micro servo 9G | KS0209 | 1 | Vent flap, 0–180° |
| Single relay 5V module (Keyestudio) | — | 1 | Switches the cooling fan |
| 12 V DC cooling fan | — | 1 | Output: removes hot air |
| RGB LED module | KS0032 | 1 | Status: blue cold · green OK · red alarm |
| Passive buzzer alarm module | KS0019 | 1 | Audible alarm |
| Breadboard, jumper wires, 3 × 220 Ω, USB cable | — | — | Connections |

The relay switches the fan's power line, so the fan never hangs on the Arduino pin. In Wokwi the fan is modelled by an LED switched through the relay, because Wokwi has no DC motor part; on the real board the relay's normally-open contact sits in the fan's supply wire. If a DC fan is not available, the lab's L9110 Fan Motor Control Module (KS0168) drives one instead.

## Repository Structure
- `/arduino` — `thermoguard.ino` sketch + Wokwi `diagram.json`
- `/java-app` — Maven project (Swing GUI, jSerialComm)
- `/docs` — GitHub Pages report (`index.html`), `proposal.md` (Step 1), `defense-guide.md`, `images/`

## How to Run
1. Wokwi: create a new UNO project, paste `thermoguard.ino` and `diagram.json`, add library "DHT sensor library", press Play.
2. Real board: set `DHT_TYPE DHT11`, upload with Arduino IDE. If the relay does not react, set `RELAY_ACTIVE_LOW 0`.
3. Requires JDK 17+ and Maven. `cd java-app && mvn compile exec:java`
4. Pick the COM port, press **Connect**.

## Serial Protocol (9600 baud, one message = one line, `\n`)
| Direction | Message | Meaning |
|---|---|---|
| A → J | `HELLO;ThermoGuard;v2.0` | Boot |
| A → J | `DATA;T=24.5;H=52.0;WIN=0;FAN=0;MODE=AUTO;ALARM=0` | Telemetry every RATE ms |
| A → J | `ACK;<cmd>` / `ERR;<code>` | Reply |
| J → A | `PING` → `PONG` | Health check |
| J → A | `MODE AUTO` / `MODE MANUAL` | Automatic or GUI control |
| J → A | `WIN OPEN` / `WIN CLOSE` / `WIN <0-180>` | Servo vent (manual only) |
| J → A | `FAN ON` / `FAN OFF` | Cooling fan (manual only) |
| J → A | `LED <r> <g> <b>` | RGB 0-255 (manual only) |
| J → A | `BUZZ ON` / `BUZZ OFF` | Buzzer |
| J → A | `THR T <°C>` / `THR H <%>` | Alarm limits |
| J → A | `RATE <200-10000>` | Telemetry interval |

### Automatic rule and hysteresis
Above the temperature limit (default 28 °C) or the humidity limit (70 %): vent opens to 90°, LED red, buzzer beeps, **fan on**. When it cools, the vent closes and the LED goes green — but the fan keeps running until the temperature falls **1.5 °C below** the limit. That gap (hysteresis) stops the relay from clicking on and off every second when the temperature sits right on the limit.

Extra features: auto-reconnect after unplugging, timeout/PING watchdog, ▶ Demo mode, CSV logging (`thermoguard-log.csv`).

Errors: `UNKNOWN_CMD`, `BAD_ANGLE`, `BAD_LED`, `BAD_RATE`, `BAD_LIMIT`, `WIN_IN_AUTO`, `LED_IN_AUTO`, `FAN_IN_AUTO`, `SENSOR_READ`.

## Documentation
GitHub Pages report: https://arya01-i.github.io/thermoguard/

## Screenshots
![Wokwi](docs/images/wokwi-circuit.png)
![GUI](docs/images/gui.png)
![Hardware](docs/images/hardware.jpg)
