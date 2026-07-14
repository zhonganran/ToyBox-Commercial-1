#!/bin/bash
# Phase 2c Round 2 — interior/ambient light + DRL write validation.
#
# Writes each candidate light fid via probe.dex (app_process shell uid, tx=6 setInt
# = identical transact to the shipping helper daemon). For each fid: write ON,
# wait for operator to observe the car, write OFF, print both RESULT lines.
#
# Values are RAW (already account for BYD setter conversions, e.g. ambient +1 shift).
# Source: docs/native-data-stack/oncar-light-validation-2026-05-28.md
#
# PASS = status >= 0 AND light physically changes. FAIL = status < 0 / SecurityException.
#
# Run while car is parked, IGN ON, charge >=30%. Operator watches the cabin/exterior.

set -u
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
SERIAL="${SERIAL:-192.168.2.68:5555}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROBE_DIR="$(dirname "$SCRIPT_DIR")"
DEX_LOCAL="$PROBE_DIR/probe.dex"
DEX_REMOTE="/data/local/tmp/probe.dex"

if [ ! -f "$DEX_LOCAL" ]; then
  echo "ERR probe.dex not found at $DEX_LOCAL. Run $PROBE_DIR/build.sh first." >&2
  exit 2
fi

echo "[round2] push probe.dex -> $DEX_REMOTE"
"$ADB" -s "$SERIAL" push "$DEX_LOCAL" "$DEX_REMOTE" >/dev/null

write_probe () {
  local dev="$1" fid="$2" val="$3"
  "$ADB" -s "$SERIAL" shell \
    "CLASSPATH=$DEX_REMOTE app_process /system/bin --nice-name=wjx_probe \
     com.byd.wjx.probe.WriteProbe 6 $dev $fid $val" </dev/null 2>&1
}

# test: label dev fid on_val off_val watch_text
run_test () {
  local label="$1" dev="$2" fid="$3" on="$4" off="$5" watch="$6"
  echo
  echo "════════════════════════════════════════════════════════════"
  echo "  $label   (dev=$dev fid=$fid)"
  echo "  Observe: $watch"
  echo "════════════════════════════════════════════════════════════"
  echo "[ON  val=$on]"
  write_probe "$dev" "$fid" "$on"
  read -r -p "  >> Did the light change? Enter to turn it off..." _
  echo "[OFF val=$off]"
  write_probe "$dev" "$fid" "$off"
  read -r -p "  >> Result recorded? Enter for next test..." _
}

echo
echo "=== PRIORITY: interior light + ambient ==="
run_test "#1 Interior light"  1023 1330643002 2 1 "interior lamps on->off"
run_test "#2 Ambient brightness"     1023 1069547536 5 0 "ambient brighter->off"

echo
read -r -p "Test additional ambient items (#3-#6)? [y/N] " more
if [ "${more:-N}" = "y" ] || [ "${more:-N}" = "Y" ]; then
  echo
  echo "=== #3 Ambient zone: writing 1, 2, 3; observe which zone changes ==="
  for z in 1 2 3; do
    echo "[zone=$z]"; write_probe 1023 1069547540 "$z"
    read -r -p "  >> Which zone? Enter to continue..." _
  done
  echo
  echo "=== #4 Ambient color: 5(cool blue) 26(red) 14(yellow) ==="
  for c in 5 26 14; do
    echo "[color=$c]"; write_probe 1023 1069547528 "$c"
    read -r -p "  >> Which color? Enter to continue..." _
  done
  run_test "#5 Interior light timeout" 1023 1043333154 4 1 "turn-off timeout (status>=0 is enough)"
  run_test "#6 Welcome light"         1023 1330643013 1 2 "welcome light (may not work in P)"
fi

echo
echo "════════════════════════════════════════════════════════════"
echo "  #7 DRL — dev=1004 BANNED namespace"
echo "  This is the pre-unban snap. Confirm that you are ready to write dev=1004."
echo "════════════════════════════════════════════════════════════"
read -r -p "Test DRL (dev=1004)? [y/N] " drl
if [ "${drl:-N}" = "y" ] || [ "${drl:-N}" = "Y" ]; then
  run_test "#7 DRL" 1004 1125122118 1 2 "daytime running lights on->off"
fi

echo
echo "[round2] DONE. Record PASS/FAIL + status in"
echo "         docs/native-data-stack/oncar-light-validation-2026-05-28.md"
