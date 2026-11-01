#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Karoo 2 / Karoo 3 In-Ride Cockpit Screenshot Capture Script
# ==============================================================================
# Captures pixel-accurate PNG screenshots directly from a connected Hammerhead
# Karoo cycling computer over ADB (`adb exec-out screencap -p`) and provides
# guidance for replaying GPX routes to showcase live navigation and Forumslader
# telemetry fields.
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

OUTPUT_DIR="${REPO_ROOT}/docs/images/cockpit"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
FILENAME_STEM="karoo-cockpit-${TIMESTAMP}"
DEVICE_SERIAL=""
SHOW_GUIDE=false

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Captures a full-screen cockpit screenshot from a connected Hammerhead Karoo 2/3.

Options:
  -s, --serial <serial>       ADB device serial (optional if only one device is attached)
  -o, --output-dir <path>     Output directory (default: docs/images/cockpit)
  -n, --name <stem>           Output filename stem without .png (default: karoo-cockpit-<timestamp>)
  -g, --guide                 Print GPX route replay & mock navigation setup instructions
  -h, --help                  Show this help message and exit

Examples:
  $(basename "$0") -n cockpit-navigation-surplus
  $(basename "$0") -s KAROO3-123456 -n cockpit-charging
  $(basename "$0") --guide
EOF
}

print_gpx_guide() {
    cat <<'EOF'
================================================================================
Karoo Live Navigation & GPX Replay Guide for Cockpit Screenshots
================================================================================

To capture the full Karoo in-ride cockpit with the proprietary Hammerhead map,
elevation profile, turn-by-turn cue banner, and Forumslader data fields:

1. Enable Developer Options & USB Debugging on Karoo:
   - Open Settings -> About -> tap "Build Number" 7 times.
   - Go back to Settings -> Developer Options -> enable "USB Debugging".
   - Connect the Karoo via USB-C and authorize the computer prompt.

2. Configure Ride Profile Data Fields:
   - Open Profiles -> select or create a profile (e.g. "Bikepacking").
   - Add a Data Page containing:
     * Map + Elevation Graph or Data Grid
     * Forumslader "Battery & Range" graphical field (60x15 or 30x15)
     * Optional telemetry fields: Dynamo Power (W), Charge State, Consumer Current

3. Load a Navigation Route:
   - Sync a route from Hammerhead Dashboard or import a GPX file via:
     adb push sample-route.gpx /sdcard/Download/
   - Select the route on the Karoo so the yellow/chevron route line and remaining
     distance/ascent metrics are active.

4. Simulate Movement / GPX Track Replay (if stationary indoors):
   - Option A (Built-in Karoo Demo / Indoor Ride Mode):
     Start a ride with GPS disabled or indoor trainer simulation active while
     a route is pinned.
   - Option B (Mock Location App over ADB):
     Install a GPX mock location provider (e.g. Lockito or GPS JoyStick) and
     grant mock location permission via ADB:
     adb shell appops set <mock.package.name> android:mock_location allow
     Replay a GPX track along your loaded Karoo route to trigger live
     turn-by-turn banners and climb segments.

5. Capture the Cockpit Frame:
   - Once the desired Forumslader state and navigation banner are visible:
     ./scripts/capture-karoo-screenshots.sh -n cockpit-navigation-surplus
================================================================================
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial)
            DEVICE_SERIAL="${2:-}"
            shift 2
            ;;
        -o|--output-dir)
            OUTPUT_DIR="${2:-}"
            shift 2
            ;;
        -n|--name)
            FILENAME_STEM="${2:-}"
            shift 2
            ;;
        -g|--guide)
            SHOW_GUIDE=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Error: Unknown option '$1'" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [[ "${SHOW_GUIDE}" == "true" ]]; then
    print_gpx_guide
    exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "Error: 'adb' command not found in PATH. Install Android Platform Tools first." >&2
    exit 1
fi

ADB_ARGS=()
if [[ -n "${DEVICE_SERIAL}" ]]; then
    ADB_ARGS+=("-s" "${DEVICE_SERIAL}")
fi

CONNECTED_COUNT="$(adb "${ADB_ARGS[@]}" get-state 2>/dev/null | grep -c "device" || true)"
if [[ "${CONNECTED_COUNT}" -eq 0 ]]; then
    echo "Error: No connected/authorized Karoo ADB device detected." >&2
    echo "Run 'adb devices' to verify connection, or pass '--guide' for setup instructions." >&2
    exit 1
fi

mkdir -p "${OUTPUT_DIR}"
TARGET_FILE="${OUTPUT_DIR}/${FILENAME_STEM}.png"

echo "Capturing screen from Karoo device..."
adb "${ADB_ARGS[@]}" exec-out screencap -p > "${TARGET_FILE}"

if [[ ! -s "${TARGET_FILE}" ]]; then
    rm -f "${TARGET_FILE}"
    echo "Error: Captured screenshot was empty." >&2
    exit 1
fi

FILE_SIZE="$(du -h "${TARGET_FILE}" | cut -f1)"
echo "Saved cockpit screenshot (${FILE_SIZE}): ${TARGET_FILE}"
