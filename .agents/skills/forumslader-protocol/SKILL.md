---
name: forumslader-protocol
description: >-
  Authoritative reference for the Forumslader V5 and V6 NMEA-like BLE ASCII protocol
  ($FL5, $FL6, $FLB, $FLC, $FLP, $FLD telemetry sentences, status bitmasks, scaling factors,
  and $FLT commands). Use this skill when modifying ForumsladerParser, ForumsladerProtocol,
  ForumsladerVersion, alert bitmask rules, or adding support for new Forumslader fields.
---

# Forumslader BLE ASCII Protocol Skill

This skill documents the verified Forumslader V5 / V6 ASCII telemetry and command protocol used by
`ForumsladerParser` and `ForumsladerProtocol`.

- For `$FLT` command framing and consumer output toggle values,
  see [flt-commands.md](../../../docs/ble/flt-commands.md).
- For Garmin reference decoding, see `ref-companion/source/ForumsladerDataManager.mc` and
  `ref-companion/source/ForumsladerView.mc`.

---

## 1. Framing & XOR Checksum

- Sentences start with `$` and end with `*XX\r\n` (or `;\n` without checksum).
- `XX` is a 2-digit uppercase hex XOR parity of every ASCII byte strictly between `$` and `*` (or
  `;`).

---

## 2. Primary Telemetry Sentences (`$FL5` / `$FL6`)

```text
$FL6,<statusHex>,<gear>,<freq>,<cell1mV>,<cell2mV>,<cell3mV>,<battCurrMa>,<consCurrMa>,<overload>,<progConsumer>,<intTemp>,<impulses>*XX
```

*(In `$FL5`, `<impulses>` is at token index `13`; in `$FL6`, it is at token index `12`.)*

### Verified Status Bitmask (`tokens[1]`, Hexadecimal)

Always parse `tokens[1]` as base-16 (with or without `0x` prefix). Do **not** invert these bits:

| Bitmask           | Meaning                                                                                          | Domain Mapping                                     |
|-------------------|--------------------------------------------------------------------------------------------------|----------------------------------------------------|
| `0x200`           | Standby (`"o"`)                                                                                  | `ChargeState.STANDBY`                              |
| `0x100`           | Battery Full (`"*"`)                                                                             | `ChargeState.FULL`                                 |
| `0x8000`          | Discharging (`"-"`)                                                                              | `ChargeState.DISCHARGING`                          |
| None of the above | Charging (`"+"`) when `batteryCurrent > +0.015 A`, Discharging when `< -0.015 A`, else `STANDBY` | `ChargeState.CHARGING` / `DISCHARGING` / `STANDBY` |
| `0x8`             | Short circuit                                                                                    | `ForumsladerAlert.ShortCircuit`                    |
| `0x800000`        | System interrupt                                                                                 | `ForumsladerAlert.SystemInterrupt`                 |

### Electrical Scaling

- `batteryVoltage` = `(cell1 + cell2 + cell3) / 1000f` (Volts, 3S pack ~`10.5–12.6 V`).
- `batteryCurrent` = `tokens[7] / 1000f` (Amps; positive = charging, negative = discharging).
- `consumerCurrent` = `tokens[8] / 1000f` (Amps on the **battery bus rail**, ~12 V; consumer load in
  watts is `batteryVoltage * consumerCurrent`).
- `dynamoPowerWatts` = `(batteryVoltage * (batteryCurrent + consumerCurrent)).coerceAtLeast(0f)`
  when `frequency > 0f`, else `0f`.

### Version Scaling (`ForumsladerVersion`)

- **V5 (`$FL5`)**: `impulseIndex = 13`, `frequencyScale = 1.0f`, `impulseScale = 4096.0`.
- **V6 (`$FL6`)**: `impulseIndex = 12`, `frequencyScale = 0.1f`, `impulseScale = 1.0`.
- `speedMetersPerSecond` = `frequency * (wheelsize / poles / 1000f) * version.frequencyScale`.
- `odometerMeters` = `impulses * (wheelsize / poles / 1000.0) * version.impulseScale`.

---

## 3. Auxiliary Telemetry Sentences

### `$FLB` (Environment)

```text
$FLB,<tempDeciC>,<pressurePa>,<altitudeDeciM>,<...>*XX
```

- `temperatureCelsius` = `tokens[1] / 10f`
- `altitudeMeters` = `tokens[3] / 10f`

### `$FLC` (Counters, Energy & Battery Health)

- **Set 3** (`$FLC,3,<totalEnergy>,<tourEnergyWh>,<tripEnergyWh>,<btSaveCount>,<empty>*XX`):
    - `tourEnergyWattHours` = `tokens[3]`, `tripEnergyWattHours` = `tokens[4]`.
- **Set 5**
  (`$FLC,5,<startCount>,<socStatePct>,<fullChargeCapacityMah>,<cycleCount>,<ccadcValue>*XX`):
    - `batteryLevelPercentage` = `tokens[3]` (sets `hasFineGrainedBatteryLevel = true` so coarse
      `$FLD` levels never overwrite it).
    - `fullChargeCapacityMah` = `tokens[4]` (BMS-measured actual full-charge capacity in mAh;
      credible range `450..6000 mAh`, converts to Wh via `(mAh / 1000f) * 11.1f`).
    - `cycleCount` = `tokens[5]`.
    - `ccadcValue` = `tokens[6]` (coulomb counter ADC; remaining mAh =
      `ccadcValue * acc2mah / 167772.16`).

### `$FLP` (Device Parameters — Requested via `$FLT,5`)

```text
$FLP,<wheelsizeMm>,<poles>,<altOffset>,<dayPulseOffset>,<...>,<tourPulseOffset>,<...>,<acc2mah>*XX
```

- `wheelsize` = `tokens[1]`, `poles` = `tokens[2]`, `dayPulseOffset` = `tokens[4]`,
  `tourPulseOffset` = `tokens[6]`, `acc2mah` = `tokens[8]`.

### `$FLD` (Legacy Fallback)

- `tokens[4]` = frequency, `tokens[5]` = voltage (V), `tokens[6]` = batteryCurrent (A),
  `tokens[7]` = consumerCurrent (A), `tokens[8]` = status char (`+` Charging, `-` Discharging, `V`/
  `*` Full), `tokens[9]` = coarse SoC stage (`0..7` → `5..95%`), `tokens[14]` = trip km.

---

## 4. Verification Checklist When Modifying Parser or Protocol

1. Keep nullable defaults (`= null`) on any new fields added to `ForumsladerMetrics` data classes so
   existing test fixtures remain compatible.
2. Verify all `$FL5`, `$FL6`, `$FLB`, `$FLC`, `$FLP`, and `$FLD` cases in
   `ForumsladerParserTest.kt`.
3. Run `./gradlew testDebugUnitTest checkCoverageBaseline`.
