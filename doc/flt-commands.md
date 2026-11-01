# Forumslader `$FLT` NMEA Commands (Reverse-Engineered Reference)

This document summarizes the `$FLT` command protocol reverse-engineered from the official Android application (`com.duringsoft.forumslader`) and the Garmin Connect IQ companion reference (`ForumsladerSettings.mc` / `ForumsladerDeviceManager.mc`).

---

## 1. Command Framing & Checksum

All commands sent to the Forumslader start with `$FLT,` followed by a numeric command ID and optional comma-separated parameters.

### Without Checksum
The official Android app constructs raw command strings terminated by `;\n`:
```text
$FLT,<cmd>[,<param1>,...];\n
```

### With NMEA XOR Checksum
When the device sends telemetry frames with an NMEA CRC (`*XX`), `DataParser` (`Lcom/duringsoft/forumslader/b;->o()Z`) marks CRC mode as active and transforms outgoing commands via `b->b(String)` before sending:
- Every ASCII character strictly between `$` and `;` (or `*`) is XORed together (`0x00` initial value).
- The 8-bit result is formatted as a 2-digit uppercase hexadecimal string prefixed with `*`.
- Example for `FLT,2`:
  - `'F' (0x46) ^ 'L' (0x4C) ^ 'T' (0x54) ^ ',' (0x2C) ^ '2' (0x32) = 0x40`
  - Framed command: `$FLT,2*40;\n` (or `$FLT,2*40\r\n`)

---

## 2. Complete `$FLT` Command Table

| Command ID | Raw Command | Example with Checksum | Source | Description |
|---|---|---|---|---|
| **2** | `$FLT,2;\n` | `$FLT,2*40\r\n` | Android APK (`Forumslader$m$f->onClick`, `Forumslader$h->run`) | **Toggle consumer / 5V USB output state** (`FL_PROGCONSUMER`). Cycles/toggles the consumer output when the Battery tab button (`id/akkuProgCell`) is pressed. |
| **4** | `$FLT,4;\n` | `$FLT,4*46\r\n` | Android APK (`Forumslader$h->run`) | **Request firmware version (`$FLV`)**. Sent until `$FLV` is received. |
| **5** | `$FLT,5;\n` | `$FLT,5*47\r\n` | Android APK & Garmin CIQ | **Request device parameters (`$FLP`)**. Returns wheel size, pole pairs, altitude offset, day/tour counters, and calibration values. |
| **6** | `$FLT,6;\n` | `$FLT,6*44\r\n` | Android APK & Garmin CIQ | **Reset Tour counters**. Resets tour distance, tour time, and tour energy counters. |
| **7** | `$FLT,7;\n` | `$FLT,7*45\r\n` | Android APK & Garmin CIQ | **Reset Day / Trip counters**. Resets day distance, day time, and day energy counters. |
| **9** | `$FLT,9,<wheelsize>,<poles>;\n` | `$FLT,9,2100,20*4A\r\n` | Android APK & Garmin CIQ | **Set wheel circumference and dynamo pole pairs**. `<wheelsize>` in mm, `<poles>` as pole pairs (e.g. `14` for 28-pole hub dynamo). |
| **10** | `$FLT,10,<offset>;\n` | `$FLT,10,0*43\r\n` | Android APK (`h$b->a`, `b->f0`) | **Set altitude calibration offset**. `<offset>` in meters (reported in `$FLP` token 3 as `offset * 10` decimeters). |
| **12** | `$FLT,12,<seconds>;\n` | `$FLT,12,180*7D\r\n` | Garmin CIQ (`ForumsladerSettings.mc`) | **Set auto-poweroff timeout (`offtime`)**. `<seconds>` until the Forumslader turns off when stationary. |
| **13** | `$FLT,13,<ramp>,<offset>;\n` | — | Android APK (`h$b->a`, `b->f0`) | **Calibrate consumer current sensor**. `<ramp>` (`current_consumer_ramp`, key `509`) and `<offset>` (`current_consumer_offset`, key `510`) in mA. |
| **14** | `$FLT,14;\n` | `$FLT,14*75\r\n` | Android APK (`Forumslader$h->run`) | **Switch telemetry protocol (`Array_FL_PROTOCOL`, key `511`)**. Sent when the active telemetry sentence format does not match the configured protocol. |
| **15** | `$FLT,15,<type>;\n` | `$FLT,15,0*68\r\n` / `$FLT,15,1*69\r\n` | Android APK (`h$b->a`, `outputType`) | **Configure FL V6 auxiliary output type (`outputTypeFL6`)**:<br>• `$FLT,15,0;\n` → **12V output** (`:12V Ausgang`)<br>• `$FLT,15,1;\n` → **Light output** (`:Licht Ausgang`) |
| **99** | `$FLT,99;\n` | `$FLT,99*72\r\n` | Android APK (`h$c->a`, `FirmwareUpdate`) | **Enter DFU / Firmware Update bootloader mode**. |

---

## 3. Consumer Output Control (`$FLT,2`) & Telemetry (`FL_PROGCONSUMER`)

### How the Official App Toggles Consumer Output
1. On the Battery page (`LS/q;`), tapping the consumer status cell (`id/akkuProgCell`, `0x7f090058`) invokes `Lcom/duringsoft/forumslader/Forumslader$m$f;->onClick`.
2. The handler:
   - Reads the current integer value of `FL_PROGCONSUMER` (internal metric key `610`) via `d->d(610)` and stores it in `Forumslader.P0`.
   - Sets `Forumslader.K0 = true` (pending toggle flag) and `Forumslader.B0 = 10` (retry counter).
   - Sends `$FLT,2;\n` (with checksum if `b->o()` is enabled).
3. In the periodic background runnable (`Lcom/duringsoft/forumslader/Forumslader$h;->run`), while `K0 == true`, the app re-sends `$FLT,2;\n` until an incoming telemetry frame updates key `610` to a value different from `P0` (or the retry counter expires).

### `FL_PROGCONSUMER` Values in Telemetry (`b->f0`)
The Forumslader reports the current consumer output state in:
- **`$FL5` / `$FL6`**: token index `10` (`tokens[10]`, immediately after `overload` at `tokens[9]`)
- **`$FLD`**: token index `11` (`tokens[11]`)

| Raw Value (`tokens[10]`) | Android Resource | Display String (EN / DE) | Meaning |
|---|---|---|---|
| `0` | `string/fl_prog_auto` (`0x7f1101a9`) | `Auto` / `Auto` | Automatic consumer switching |
| `1` | `string/fl_prog_on` (`0x7f1101ab`) | `On` / `Ein` | 5V USB / consumer output **ON** |
| `2` | `string/fl_prog_off` (`0x7f1101aa`) | `Off` / `Aus` | 5V USB / consumer output **OFF** |
| `3` | `string/fl_prog_power` (`0x7f1101ac`) | `Power` / `Netz` | External power / mains mode |
| `11` *(FL6 only)* | `fl_prog_on` + `out12V` (`0x7f110234`) | `On:12V output` / `Ein:12V Ausgang` | Output **ON** (12V output mode) |
| `12` *(FL6 only)* | `fl_prog_off` + `out12V` (`0x7f110234`) | `Off:12V output` / `Aus:12V Ausgang` | Output **OFF** (12V output mode) |
| `21` *(FL6 only)* | `fl_prog_on` + `outLight` (`0x7f110235`) | `On:Light output` / `Ein:Licht Ausgang` | Output **ON** (Light output mode) |
| `22` *(FL6 only)* | `fl_prog_off` + `outLight` (`0x7f110235`) | `Off:Light output` / `Aus:Licht Ausgang` | Output **OFF** (Light output mode) |
