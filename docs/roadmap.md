# Karoo Forumslader Extension Roadmap

This document outlines the planned improvements and missing functionalities for the Karoo Forumslader Extension, compared against the Garmin Connect IQ reference companion project (`ref-companion`).

---

## 1. Feature Parity Gaps (Garmin `ref-companion` vs. Karoo Extension)

### Telemetry & Data Fields
* **Unexposed Existing Fields**: `batteryVoltage`, `batteryCurrent`, and `temperatureCelsius` are parsed but not registered as Karoo custom data types or adapter fields.
* **Unparsed/Unexposed Fields**:
  * **Trip & Tour Energy (Wh)**: Derived from the `$FLC` sentence (set 3), indicating generator/consumer energy.
  * **Generator Gear**: Coupled dynamo state/stage (1-10) from the `$FL5`/`$FL6` sentences.
  * **Dynamo Power (W)**: Calculated generator power.
  * **Odometer vs. Tour Distance**: Garmin distinguishes between odometer, tour distance, and day distance; Karoo currently only exposes a single trip distance.
  * **Charge State**: Status indicator showing Standby, Charging, Discharging, or Full.

### BLE Connection Stability & Diagnostics
* **Diagnostics**: Garmin displays a diagnostic field with Stability Index (disconnects/hour), Data Quality (checksum check), and Disconnect count. Karoo does not track these.
* **Device Lock**: Garmin stores the MAC address of the device to perform direct connection on boot. Karoo relies on scanning every time.

---

## 2. Platform-Specific Enhancements (Independent of Garmin)

* **Companion App Settings UI**: Jetpack Compose screen in `MainScreen` allowing:
  * Wheel size and dynamo pole count overrides (so speed/distance can compute instantly without waiting for `$FLP` packets).
  * Lock MAC address selection.
* **System Notifications**: Native Karoo alerts on critical errors (Low battery, high temperature, system interrupt, short circuit).
* **Power-Safe BLE Loop**: Automatically pause scans/connections when the user pauses their ride or the computer suspends.

---

## 3. Prioritized Implementation Roadmap

### Phase 1: Telemetry & Connection Parity (High Importance)
1. **Expose Existing Parsed Telemetry**: Add `batteryVoltage`, `batteryCurrent`, and `temperatureCelsius` as custom data types to [ForumsladerExtension](file:///../../app/src/main/kotlin/org/happycode/karoo/forumslader/extension/ForumsladerExtension.kt).
2. **Parse and Expose Remaining Metrics**: Support Generator Gear, Charging State, Trip/Tour Energy, and Odometer.
3. **Implement Device Lock**: Save the connected MAC address in `SharedPreferences` to skip scan cycles.

### Phase 2: Configuration & Diagnostics (Medium Importance)
4. **Companion App Settings Panel**: Add Compose settings fields in [MainScreen](file:///../../app/src/main/kotlin/org/happycode/karoo/forumslader/screens/MainScreen.kt) to override wheel size/poles.
5. **BLE Diagnostics Tracking**: Track connection stability metrics and display them in a diagnostic custom field.
6. **System Alerts**: Implement popups for safety warnings (high temp, low batt) using the Karoo SDK notification API.

### Phase 3: FIT Recording & Optimization (Low Importance)
7. **FIT Activity Recording**: Write custom telemetry directly to the ride's activity FIT file.
8. **CSV Logging & Export**: Support exporting local telemetry logs.
9. **Comprehensive Integration Tests**: Expand Robolectric tests for BLE disconnect/reconnect loops.
