# Step 1 — Project Proposal: ClimaLink (Smart Climate Station)

**Student:** Arya Venu · **Course:** Course Projects 2: Java (RNU) · **Deadline:** 1 October 2026

## Idea
A small climate station that measures room temperature and humidity. A Java Swing desktop app shows the live values and a chart, and lets the user control a ventilation "window" (servo), a status light (RGB LED) and an alarm (buzzer). In AUTO mode the Arduino reacts by itself when the climate passes a limit; in MANUAL mode the user controls everything from the GUI.

## Components (all from the Laboratory Inventory, room 5-101)
| Part | Inventory code | Qty |
|---|---|---|
| Arduino UNO R3 | KS0001 | 1 |
| DHT11 temperature & humidity sensor | KS0034 | 1 |
| Micro servo 9G | KS0209 | 1 |
| RGB LED module | KS0032 | 1 |
| Passive buzzer module | KS0019 | 1 |
| Breadboard, jumper wires, USB cable | — | — |

## What Arduino does
- Reads the DHT sensor every second (non-blocking, `millis()`).
- Sends one telemetry line per reading.
- In AUTO mode: temperature/humidity above limit → open window, red LED, beep. Below → close window, green LED (blue when cold).
- Executes commands from Java and answers `ACK` or `ERR`.

## What the Java app does
- Lists COM ports, connects / disconnects.
- Shows temperature, humidity, window angle, alarm state, a live chart and a message log.
- Controls: AUTO/MANUAL, window slider, temperature limit, LED colour, buzzer.

## Draft communication protocol (USB Serial, 9600 baud, one message = one line)
Arduino → Java
```
HELLO;ClimaLink;v1.0
DATA;T=24.5;H=52.0;WIN=0;MODE=AUTO;ALARM=0
ACK;<command>
ERR;<code>
```
Java → Arduino
```
PING                  -> PONG
MODE AUTO | MODE MANUAL
WIN OPEN | WIN CLOSE | WIN <0-180>
LED <r> <g> <b>
BUZZ ON | BUZZ OFF
THR T <celsius> | THR H <percent>
RATE <200-10000>
```

## Visible result
The servo window moves, the LED changes colour and the buzzer beeps, while live readings appear in the GUI.
