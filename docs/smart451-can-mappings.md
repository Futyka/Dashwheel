# Smart Fortwo 451 (2010, 0.8 CDI) – CAN mapping notes

This is a **field-observation and candidate mapping register**, not a list of universally validated CAN specifications. Intended for native CANable2/SLCAN support in Dashwheel (see [draft PR #2](https://github.com/Futyka/Dashwheel/pull/2) and [upstream request #165](https://github.com/deviloufr-ai/Dashwheel/issues/165)).

## Hardware and receive mode

- Vehicle: 2010 Smart Fortwo 451 0.8 CDI.
- CANable2 via USB serial, connected to CAN-H / CAN-L / signal ground.
- CAN bitrate reported working by vehicle owner: **500 kbit/s** (SLCAN `S6`).
- **Receive-only** monitoring; firmware-specific silent/listen-only semantics must be verified before opening the bus. Never send CAN data frames just to discover values.
- Byte convention: **D0 is the first payload byte** (formula name `A`), D1=`B`, D2=`C`, etc.
- Distinguish signals verified on *this* 2010 CDI from third-party references for other Smart variants.

## Owner-observed / earlier project findings

| Signal | CAN ID | Payload | Observation / current confidence |
|---|---|---|---|
| Accelerator pedal | `0x210` | D2 (`C`) | **Direct vehicle test** with ignition ON, engine OFF. Released raw 0; fully depressed around 250; gradual change with pedal. `C * 100 / 250` only a *preliminary* scaling formula, not a calibrated percent. |
| Parking brake | `0x200` | D0, mask `0x02` | **Owner states confirmed** through apply/release test: released `0x00`, engaged `0x02`. Check bit 1 (`A & 2`). Archive a raw paired capture for upstream validation. |
| Fuel gauge | `0x408` | D0 (`A`) | Previously extracted from Smart451 logger v0.3.6. In that app, raw `0x14` was treated as 2/8 bars and `0x29` as 5/8; >41 was displayed as >=5/8. **The 0–100% conversion and 8/8 endpoint are not established.** No claim that raw byte directly represents percent. |

## Candidate mappings – require validation on owner's vehicle

Some of the following came from external Smart 451 references including a 2013 petrol car, and/or earlier exploration. **Do not treat them as confirmed for the 2010 CDI without correlated logs.**

| Signal | Candidate CAN ID | Candidate byte(s) | Check required |
|---|---|---|---|
| Engine RPM | `0x212` | D2–D3 | Compare to OBD RPM and engine-off/idle/rev recordings; check endianness and scale. Also recent logger observations involved `0x312 D5` and `0x218 D3`. |
| Coolant temperature | `0x608` | D0, potential raw - 40 | Correlate cold start/warm-up against OBD coolant temperature. |
| Intake air temperature | `0x608` | D1, potential raw - 40 | Correlate with OBD IAT. |
| Vehicle speed | `0x23A` | D0–D1 | Compare parked / GPS / OBD speed under safe test conditions. |
| Gear / reverse | `0x418` | Data-dependent | Observe N/R/forward transitions while stationary and safely secured. |
| Doors | `0x423` | D2 | Observe driver/passenger door toggles. |
| Position lights / headlights | `0x423` | D3 | Observe switch changes. |
| Indicators / hazard | `0x423` | D1 | Observe left/right/hazard transitions. |
| Brake pedal | `0x208` | D2–D3 | Correlate brake press/release; mapping may be pressure rather than simple Boolean. |
| Other engine/raw signals | `0x308 D5`, `0x312 D5`, `0x218 D3` | As logged | Changes observed with time/engine speed; meanings not established. |

Standard OBD readings already reported working separately via Carista include RPM, coolant, MAF, speed, battery voltage, and engine load. Standard fuel level service `012F` did **not** return a value. The upstream maintainer suggested exploring manufacturer-specific Mode 21/22 requests to the instrument cluster; cluster header and request remain unknown.

## Intended Dashwheel integration

1. Native USB CANable2/SLCAN adapter path alongside the existing ELM327 path; keep USB selection inside Settings rather than a new activity / launcher.
2. Configurable CAN bitrate, device type, read-only monitoring, and a safe USB lifecycle.
3. Configurable CAN ID + byte fields/bit masks + expression (and optionally preset mappings) feeding **My readings**, including both numeric and Boolean values.
4. Reuse existing gauges, door/light/reverse/parking-brake status and dashboard widgets when supported by Dashwheel's vehicle bus. Do not substitute accelerator pedal for actual throttle plate reading.
5. Offer lightweight raw capture for correlation with vehicle state; future percent/scale formulas must be validated.
6. Do not modify unrelated CI version numbering or the Pi pairing QR code.

## Evidence still needed

- Timestamped raw SLCAN capture at **500 kbit/s**, parked, showing 0x200 brake applied/released, 0x210 pedal sweep, and 0x408 fuel.
- Fuel frames after filling and after consumption / at known dash bar counts; calibrate monotonic byte mapping and end points.
- For each candidate signal, paired *before/after* captures under known conditions.
- Confirm CANable2 firmware's exact listen-only command and acknowledgment handling before integrating into the driver.

**Status of PR #2:** foundation only (frame parser / formula decoder / tests), not a working USB adapter or connected My readings publisher yet.
