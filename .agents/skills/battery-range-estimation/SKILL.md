---
name: battery-range-estimation
description: >-
  Guides AI agents modifying, debugging, or analyzing the Forumslader battery range estimation,
  self-learning rider gradient profile, headwind compensation, and FIT/telemetry energy balance.
  Use this skill when working on files in domain/estimation, application/estimation,
  or analyzing ride .fit/.csv telemetry for range accuracy.
---

# Battery Range Estimation & Self-Learning Skill

This skill defines the architecture, physical invariants, Karoo battery-saving constraints, and FIT
analysis workflow for the battery range estimation system in `karoo-forumslader`.

- For Mermaid class, pipeline, and lifecycle diagrams, read
  [battery-range-estimation.md](../../../docs/estimation/battery-range-estimation.md).
- For Forumslader NMEA sentence formats and `$FL5`/`$FL6` status bitmasks, read the
  [forumslader-protocol](../forumslader-protocol/SKILL.md) skill.

---

## 1. Object-Oriented Component Map

Keep estimation logic cleanly decomposed into single-responsibility domain objects inside
`app/src/main/kotlin/org/happycode/karoo/forumslader/domain/estimation/` (pure Kotlin, zero
Android/Room imports per `AGENTS.md`):

| Component                   | Layer       | Responsibility                                                                                                                                                                                             |
|-----------------------------|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `BatteryEstimator`          | Domain      | Thin coordinator composing the four models below into `BatteryEstimate`                                                                                                                                    |
| `DischargeWindow`           | Domain      | 20 km virtual discharging sliding window, voltage-bounce guard, EMA smoothing, and window-averaged baselines (`baselineConsumerPowerWatts`, `baselineHeadwindSpeedMs`)                                     |
| `ConsumerLoadModel`         | Domain      | Consumer power in watts at battery bus voltage (~12 V) + bounded rate adjustment (`maxNegativeRelief = -45%` of base rate)                                                                                 |
| `HeadwindCompensator`       | Domain      | Incremental headwind penalty above `baselineHeadwindSpeedMs`, weighted by `nonClimbRouteFraction`                                                                                                          |
| `RouteEnergyForecaster`     | Domain      | Sequential climb drain + inter-climb flat dynamo recovery simulation; exposes `RouteEnergyForecast.adjustedCapacityPct(...)` deducting only excess climb deficit above `bottleneckClimbKm * effectiveRate` |
| `ClimbSegment`              | Domain      | Remaining climb slicing and net energy balance prediction (`Wh`)                                                                                                                                           |
| `GradientProfile`           | Domain      | Per-1% gradient learned speed, frequency, and dynamo power (fixed `0.05f` distance-gated EMA, nullable hysteresis `measuredDynamoWatts`, speed-scaled headwind penalty)                                    |
| `DynamoPowerCurve`          | Domain      | Scales learned dynamo power (`>= 0.25 W` credible threshold) or falls back to frequency curve                                                                                                              |
| `RecordRiderProfileUseCase` | Application | Distance-gated (`50 m`) in-memory profile learning with unconditional trip tracking, hysteresis cutoff exclusion, and batched `flush()`                                                                    |

---

## 2. Critical Domain & Hardware Invariants (Do Not Break)

1. **`ChargeState` Accuracy (`ForumsladerParser`)**:
    - Follow the verified `$FL5`/`$FL6` status bitmask (`0x200 = STANDBY`, `0x100 = FULL`,
      `0x8000 = DISCHARGING`) with `batteryCurrent` (`±0.015 A` deadband) fallback when none of
      those flags are set. See [forumslader-protocol](../forumslader-protocol/SKILL.md).
2. **12 V Bus Consumer Power (`ConsumerLoadModel`)**:
    - `$FL6` `consumerCurrent` measures total consumer draw at battery bus voltage (`~12 V`, e.g.,
      `~0.8 A ≈ 9.6 W` for a 12 V headlight + taillight via converter), **not** `5 V` USB rail.
      (Note: `LightLoadDetector` still uses `5 V / 0.90` for its USB-vs-12V heuristic—do not
      conflate the two.)
    - Fall back to `-batteryCurrent * busVoltage` during `DISCHARGING` if a direct-wired load
      bypasses `consumerCurrent`, and keep negative rate adjustments bounded (`-45%` of `baseRate`)
      so turning off lights/USB cannot clamp `effectiveRate` to `0.15 %/km`.
3. **Default Battery Pack Capacity & `$FLC,5` Dynamic Capacity**:
    - `BatteryEstimator.DEFAULT_BATTERY_CAPACITY_WH` is `13.3f` (3S 1.2 Ah Ahead V6 Li-Ion pack at
      `11.1 V` nominal).
    - When integrating `$FLC,5` `fullChargeCapacityMah` (`tokens[4]`), keep nullable defaults
      (`= null`) on `ForumsladerMetrics.Power`, validate within credible bounds (`450..6000 mAh` →
      `5.0..66.6 Wh` via `(mAh / 1000f) * 11.1f`) before overriding `batteryCapacityWh`, and fall
      back to `13.3f` when absent or `0`.
4. **Full-Battery Hysteresis `0 W` Guard (`RecordRiderProfileUseCase`, `GradientProfile`,
   `DynamoPowerCurve`)**:
    - When `generatorGear == 0` or `SoC >= 98%`, the Forumslader disconnects the generator while
      coasting through its hysteresis band (`0 W` even at 25 km/h).
    - Pass `measuredDynamoWatts = null` to `GradientProfile.updatedWith(...)` during hysteresis
      cutoffs so learned dynamo power is preserved, while still allowing genuine `0 W` readings
      below cut-in to blend downward. Require `>= 0.25 W` in `DynamoPowerCurve` before overriding
      `predictFromFrequency()`.
    - Track `lastTripMeters` unconditionally in `RecordRiderProfileUseCase` before the
      `isRecordable` guard so distance ridden while `FULL` or stationary does not bleed into the
      next climb bucket.
5. **Climb Recovery & Excess Climb Deficit (`RouteEnergyForecaster`)**:
    - Walk climbs sequentially with inter-climb flat/descent recovery when
      `predictedFlatPower > currentConsumerPowerWatts` (active during daytime; `0` at night when
      lighting load exceeds flat dynamo output).
    - Deduct only the **excess** climb deficit above `bottleneckClimbKm * effectiveRatePctPerKm` so
      climb kilometers are never double-counted at night.
6. **Headwind Non-Double-Counting (`HeadwindCompensator`, `GradientProfile`)**:
    - Apply only excess headwind above `baselineHeadwindSpeedMs`, weighted by
      `nonClimbRouteFraction`.
    - Scale aerodynamic speed reduction on climbs by
      `(speedMetersPerSecond / 5.0f).coerceIn(0.35f, 1.2f)` so steep slow climbs are not pushed
      artificially below the `12 Hz` dynamo cut-in.
7. **Karoo Battery-Saving Constraints & Non-Destructive Room Migrations**:
    - Never run Room SQLite reads/writes on every 1 Hz BLE packet. Keep `RecordRiderProfileUseCase`
      in memory (`minDistanceStepMeters = 50.0`, `persistImmediately = false`) and call `flush()`
      only on `RideState.Paused`, `RideState.Idle`, BLE `DISCONNECTED`, teardown, or 5-minute
      intervals.
    - Always provide an explicit non-destructive Room `Migration` when modifying
      `ForumsladerDatabase` so `rider_gradient_profile` is never wiped on upgrade.
    - Keep `CsvLogger` writes buffered in memory (`flushBatchSize = 20`) with cached file size and
      header state, flushing on pause/idle/disconnect/export.

---

## 3. Analyzing Real Ride `.fit` Files

When investigating range accuracy against real Karoo `.fit` recordings, run the bundled analyzer
script:

```bash
python3 .agents/skills/battery-range-estimation/scripts/analyze_fit_telemetry.py <path/to/ride.fit>
```

`ForumsladerFitRecorder` logs `Battery Voltage`, `Battery Current`, `Dynamo Power`, `Temperature`,
`Speed`, and `Trip Energy`; during active dynamo generation, `consumerCurrent` is reconstructed via
`(Dynamo Power / Battery Voltage) - Battery Current`.

---

## 4. Verification Workflow After Any Algorithm Edit

1. Update or add unit tests in
   `app/src/test/kotlin/org/happycode/karoo/forumslader/domain/estimation/` and
   `RecordRiderProfileUseCaseTest.kt`.
2. Update `docs/estimation/battery-range-estimation.md` if architecture, formulas, or Mermaid flows
   changed.
3. Run unit tests and Kover coverage baseline check (must exit `0` and remain `>=`
   `.github/coverage-baseline.txt`):

   ```bash
   ./gradlew testDebugUnitTest checkCoverageBaseline
   ```
