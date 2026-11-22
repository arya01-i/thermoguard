# Step 1 — Project Proposal: ThermoGuard (Server Cabinet Overheat Guard)

**Student:** Arya Venu · **Course:** Course Projects 2: Java (RNU) · **Deadline:** 1 October 2026

## Idea
A server or network cabinet traps heat, and when the air inside gets too hot the equipment inside throttles or fails. ThermoGuard is a small guard station for such a cabinet: an Arduino measures the temperature and humidity inside it and reacts on its own — it opens a vent flap, switches on a cooling fan, shows the status on a light and sounds an alarm. A Java Swing desktop app on the desk shows live readings, a chart and a message log, and lets the user take manual control.

Two modes:
- **AUTO** — the Arduino decides by itself. Above the limit: vent open, fan running, red LED, buzzer. Below it: vent closed, fan off, green LED.
- **MANUAL** — the user drives the vent, the fan, the LED and the buzzer from the GUI.

## Components (all from the Laboratory Inventory, room 5-101)
| Part | Lab code | Qty |
|---|---|---|
| Arduino UNO R3 | KS0001 | 1 |
| DHT11 temperature & humidity sensor | KS0034 | 1 |
| Micro servo 9G | KS0209 | 1 |
| Single relay 5V module (Keyestudio) | — | 1 |
| 12 V DC cooling fan | — | 1 |
| RGB LED module | KS0032 | 1 |
| Passive buzzer alarm module | KS0019 | 1 |
| Breadboard, jumper wires, 3 × 220 Ω, USB cable | — | — |

## What Arduino does
- Reads the DHT sensor every second without blocking (`millis()`, no `delay()`).
- Sends one telemetry line per reading.
- In AUTO mode: temperature above limit → vent open, **fan on**, red LED, beep. Cools down → vent closed, green LED; the fan keeps running until the temperature is 1.5 °C below the limit (hysteresis, so the relay does not chatter).
- Switches the fan through a relay, so the fan's current never goes through an Arduino pin.
- Executes commands from Java and answers `ACK` or `ERR`.

## What the Java app does
- Lists COM ports, connects / disconnects.
- Shows temperature, humidity, vent angle, fan state, alarm state, a live chart and a message log.
- Controls: AUTO/MANUAL, vent slider, temperature limit, fan on/off, LED colour, buzzer, demo mode, CSV logging.

## Draft communication protocol (USB Serial, 9600 baud, one message = one line)
Arduino → Java
```
HELLO;ThermoGuard;v2.0
DATA;T=24.5;H=52.0;WIN=0;FAN=0;MODE=AUTO;ALARM=0
ACK;<command>
ERR;<code>
```
Java → Arduino
```
PING                  -> PONG
MODE AUTO | MODE MANUAL
WIN OPEN | WIN CLOSE | WIN <0-180>
FAN ON | FAN OFF
LED <r> <g> <b>
BUZZ ON | BUZZ OFF
THR T <celsius> | THR H <percent>
RATE <200-10000>
```

## Visible result
The vent flap opens, the fan starts, the LED turns red and the buzzer beeps — while the live numbers, the chart and the message log move in the Java window.
