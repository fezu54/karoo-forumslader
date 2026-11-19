#!/usr/bin/env python3
"""Analyze Karoo .fit files containing Forumslader developer fields for battery estimation tuning."""

import math
import struct
import sys

BASE_TYPES = {
    0x00: ("B", 1, 0xFF),
    0x01: ("b", 1, 0x7F),
    0x02: ("B", 1, 0xFF),
    0x83: ("h", 2, 0x7FFF),
    0x84: ("H", 2, 0xFFFF),
    0x85: ("i", 4, 0x7FFFFFFF),
    0x86: ("I", 4, 0xFFFFFFFF),
    0x07: ("s", 1, None),
    0x88: ("f", 4, 0xFFFFFFFF),
    0x89: ("d", 8, 0xFFFFFFFFFFFFFFFF),
    0x0A: ("B", 1, 0x00),
    0x8B: ("H", 2, 0x0000),
    0x8C: ("I", 4, 0x00000000),
    0x0D: ("B", 1, 0xFF),
}


def decode_val(raw: bytes, f_type: int, endian: str):
    if f_type == 0x07:
        return raw.split(b"\x00", 1)[0].decode("utf-8", errors="replace")
    info = BASE_TYPES.get(f_type)
    if not info:
        return None
    fmt, elem_size, inv = info
    count = len(raw) // elem_size
    if count == 0:
        return None
    vals = struct.unpack(endian + fmt * count, raw[: count * elem_size])
    if count == 1:
        return None if vals[0] == inv else vals[0]
    return vals


def read_data_msg(data: bytes, pos: int, defn):
    _, endian, fields, dfields = defn
    res_f = {}
    for f_num, f_size, f_type in fields:
        raw = data[pos : pos + f_size]
        pos += f_size
        res_f[f_num] = decode_val(raw, f_type, endian)
    res_d = {}
    for df_num, df_size, dev_idx in dfields:
        raw = data[pos : pos + df_size]
        pos += df_size
        if df_size == 4:
            res_d[(dev_idx, df_num)] = struct.unpack(endian + "f", raw)[0]
        elif df_size == 1:
            res_d[(dev_idx, df_num)] = raw[0]
        else:
            res_d[(dev_idx, df_num)] = raw
    return {"fields": res_f, "dev": res_d}, pos


def parse_fit(path: str):
    with open(path, "rb") as f:
        data = f.read()
    hdr_size = data[0]
    data_size = struct.unpack("<I", data[4:8])[0]
    pos, end = hdr_size, hdr_size + data_size
    local_defs = {}
    dev_fields = {}
    records = []
    sessions = []

    while pos < end:
        hdr = data[pos]
        pos += 1
        if hdr & 0x80:
            defn = local_defs[(hdr >> 5) & 0x03]
            msg, pos = read_data_msg(data, pos, defn)
        elif hdr & 0x40:
            has_dev = bool(hdr & 0x20)
            local_num = hdr & 0x0F
            endian = ">" if data[pos + 1] == 1 else "<"
            g_mesg = struct.unpack(endian + "H", data[pos + 2 : pos + 4])[0]
            n_f = data[pos + 4]
            pos += 5
            fields = [(data[pos + i * 3], data[pos + i * 3 + 1], data[pos + i * 3 + 2]) for i in range(n_f)]
            pos += n_f * 3
            dfields = []
            if has_dev:
                n_d = data[pos]
                pos += 1
                dfields = [(data[pos + i * 3], data[pos + i * 3 + 1], data[pos + i * 3 + 2]) for i in range(n_d)]
                pos += n_d * 3
            local_defs[local_num] = (g_mesg, endian, fields, dfields)
            continue
        else:
            defn = local_defs[hdr & 0x0F]
            msg, pos = read_data_msg(data, pos, defn)

        g_mesg = defn[0]
        if g_mesg == 206:
            dev_fields[(msg["fields"].get(0), msg["fields"].get(1))] = (
                msg["fields"].get(3),
                msg["fields"].get(8),
            )
        elif g_mesg == 20:
            records.append(msg)
        elif g_mesg == 18:
            sessions.append(msg)

    return dev_fields, records, sessions


def find_fl_dev_index(dev_fields: dict) -> int:
    for (dev_idx, _), (name, _) in dev_fields.items():
        if name == "Battery Voltage":
            return dev_idx
    return 2


def analyze_fit(path: str, pack_wh: float = 13.3):
    dev_fields, records, sessions = parse_fit(path)
    fl_idx = find_fl_dev_index(dev_fields)

    print(f"=== {path} ===")
    if sessions:
        sf = sessions[0]["fields"]
        dist_km = (sf.get(9) or 0) / 100000.0
        ascent = sf.get(22)
        descent = sf.get(23)
        print(f"Session: dist={dist_km:.2f} km, ascent={ascent} m, descent={descent} m")

    charge_wh = discharge_wh = dyn_wh = 0.0
    day_wh = night_wh = day_km = night_km = 0.0
    prev_ts = prev_dist = None
    by_grade = {}

    for rec in records:
        ts = rec["fields"].get(253)
        dist = (rec["fields"].get(5) or 0) / 100.0
        grade = (rec["fields"].get(9) or 0) / 100.0
        v = rec["dev"].get((fl_idx, 0))
        i = rec["dev"].get((fl_idx, 1))
        p = rec["dev"].get((fl_idx, 2))
        spd = rec["dev"].get((fl_idx, 4)) or 0.0
        if v is None or i is None or p is None or math.isnan(v) or math.isnan(i) or math.isnan(p):
            continue

        dt = 1.0 if prev_ts is None else max(0.0, min(10.0, float(ts - prev_ts)))
        dd_km = 0.0 if prev_dist is None else max(0.0, (dist - prev_dist) / 1000.0)
        prev_ts = ts
        prev_dist = dist

        p_bat = v * i
        if p_bat > 0:
            charge_wh += p_bat * dt / 3600.0
        else:
            discharge_wh += (-p_bat) * dt / 3600.0
        dyn_wh += p * dt / 3600.0

        ic = (p / v - i) if (v > 0 and p > 0) else (-i if i < 0 else 0.0)
        d_wh = p_bat * dt / 3600.0
        if ic > 0.35:
            night_wh += d_wh
            night_km += dd_km
        else:
            day_wh += d_wh
            day_km += dd_km

        if spd >= 4.3:
            by_grade.setdefault(int(round(grade)), []).append((spd, p, i))

    print(f"Battery Energy: +{charge_wh:.2f} Wh charge, -{discharge_wh:.2f} Wh discharge, net={charge_wh - discharge_wh:+.2f} Wh, dynamo={dyn_wh:.2f} Wh")
    print(f"Daytime/Low load:  {day_km:5.1f} km, net={day_wh:+.2f} Wh ({(day_wh / pack_wh) * 100:+.1f}% of {pack_wh} Wh)")
    night_rate = ((-night_wh / pack_wh) * 100 / night_km) if night_km > 0 else 0.0
    print(f"Night/High load:   {night_km:5.1f} km, net={night_wh:+.2f} Wh ({(night_wh / pack_wh) * 100:+.1f}% of {pack_wh} Wh, rate={night_rate:.2f} %/km)")
    print("Gradient Buckets (>= 60 samples):")
    for g in sorted(by_grade.keys()):
        arr = by_grade[g]
        if len(arr) >= 60:
            avg_spd = sum(x[0] for x in arr) / len(arr)
            avg_p = sum(x[1] for x in arr) / len(arr)
            active = [x[1] for x in arr if x[1] > 0.5]
            avg_active = (sum(active) / len(active)) if active else 0.0
            zero_pct = (len([x for x in arr if x[1] <= 0.1]) / len(arr)) * 100.0
            print(f"  {g:+3d}% ({len(arr):4d}s): spd={avg_spd:4.1f} km/h, P_all={avg_p:4.2f} W, P_active={avg_active:4.2f} W, zero={zero_pct:4.1f}%")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: analyze_fit_telemetry.py <file.fit> [file2.fit ...]")
        sys.exit(1)
    for fit_path in sys.argv[1:]:
        analyze_fit(fit_path)
