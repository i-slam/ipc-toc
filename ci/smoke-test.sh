#!/usr/bin/env bash
# Installs an APK on a running emulator and checks that it launches and stays up.
#
# R8 breakage surfaces at runtime, not at build time, so the minified APK gets this gate before
# it is published. The unminified debug APK is checked first as a control: if both fail the same
# way, the fault is in this harness rather than in the shrinker.
set -uo pipefail

PACKAGE="com.aistudio.ipcsolution.poc"
ACTIVITY="com.example.MainActivity"

wait_for_device() {
  adb wait-for-device
  adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
}

# Everything the log knows about our package, whichever buffer it landed in.
dump_app_log() {
  # head, not tail: the exception type and message are at the TOP of a crash dump, and that is
  # the only part that names the cause.
  echo "---- crash buffer ----"
  adb logcat -d -b crash 2>/dev/null | head -120
  echo "---- app / activity-manager lines ----"
  adb logcat -d -b main,system 2>/dev/null \
    | grep -iE "$PACKAGE|AndroidRuntime|FATAL|ActivityManager|ActivityTaskManager|lowmemorykiller|libprocessgroup" \
    | tail -80
}

# Returns 0 when the app is up: process alive and the activity present in the stack.
check_running() {
  local pid stack
  pid="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')"
  stack="$(adb shell dumpsys activity activities 2>/dev/null | grep -F "$PACKAGE/$ACTIVITY")"
  echo "pid='$pid'"
  echo "stack entries:"
  echo "${stack:-  (none)}"
  [ -n "$pid" ] && [ -n "$stack" ]
}

smoke_test_apk() {
  local apk="$1" label="$2"

  echo
  echo "=================================================================="
  echo "== $label: $apk"
  echo "=================================================================="

  adb uninstall "$PACKAGE" >/dev/null 2>&1 || true

  echo "-- installing"
  if ! adb install -r "$apk"; then
    echo "RESULT[$label]: FAIL - install rejected"
    return 1
  fi

  echo "-- launching $PACKAGE/$ACTIVITY"
  adb logcat -c || true
  adb shell am start -W -S -n "$PACKAGE/$ACTIVITY"

  # Give Compose time to inflate on a cold, software-rendered emulator.
  local attempt
  for attempt in 1 2 3 4 5 6; do
    sleep 5
    echo "-- probe $attempt"
    if check_running; then
      echo "-- still up after $((attempt * 5))s"
      if [ "$attempt" -ge 3 ]; then
        echo "RESULT[$label]: PASS - launched and stayed up"
        return 0
      fi
    else
      echo "-- not running on probe $attempt"
      dump_app_log
      echo "RESULT[$label]: FAIL - process or activity gone after $((attempt * 5))s"
      return 1
    fi
  done

  echo "RESULT[$label]: PASS - launched and stayed up"
  return 0
}

# The floating rail is a SYSTEM_ALERT_WINDOW overlay, so a broken window setup shows up as an
# addView failure at runtime and nowhere else. Grant the appop and check the window attaches.
smoke_test_floating_rail() {
  echo
  echo "== floating rail overlay =="

  # Pre-grant everything the arm chain would otherwise ask a human for, so it runs unattended.
  adb shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow || true
  adb shell pm grant "$PACKAGE" android.permission.READ_CALL_LOG || true
  adb shell pm grant "$PACKAGE" android.permission.READ_PHONE_STATE || true
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
  adb shell dumpsys deviceidle whitelist "+$PACKAGE" || true

  adb logcat -c || true

  # The service is exported="false" on purpose, so adb cannot start it directly. Go through the
  # arm shortcut, which is exported - that also exercises the real one-tap path end to end.
  adb shell am start -a com.example.action.QUICK_ARM \
    -n "$PACKAGE/com.example.QuickActionActivity"
  sleep 12

  local log
  log="$(adb logcat -d 2>/dev/null | grep -E "FloatingRailService|QuickActionActivity|AndroidRuntime" || true)"
  echo "$log" | tail -30

  # Logged by OverlayWindowService under the subclass's tag once addView succeeds.
  if echo "$log" | grep -q "Overlay window attached"; then
    if adb logcat -d -b crash | grep -q "FATAL EXCEPTION"; then
      echo "RESULT[floating rail]: FAIL - the overlay attached but something crashed"
      adb logcat -d -b crash | head -60
      return 1
    fi
    echo "RESULT[floating rail]: PASS - overlay window attached"
    return 0
  fi

  echo "RESULT[floating rail]: FAIL - the overlay never attached"
  adb logcat -d -b crash | head -60
  return 1
}

# Photographs the bubble actually drawn over the screen. The overlay either renders or it does
# not, and a log line saying addView returned is weaker evidence than the pixels.
capture_bubble_screenshots() {
  local width height tap_x tap_y size
  mkdir -p artifacts

  # Home screen first, so the shot shows the bubble over the launcher rather than over its own app.
  adb shell input keyevent KEYCODE_HOME
  sleep 3
  adb exec-out screencap -p > artifacts/bubble-on-screen.png 2>/dev/null || true

  # Tap the bubble to open the panel. It sits at the top-right, 4dp in, ~52dp across.
  size="$(adb shell wm size 2>/dev/null | tr -d '\r' | awk -F': ' '/Physical size/ {print $2}')"
  width="${size%x*}"
  height="${size#*x}"
  if [ -n "${width:-}" ] && [ -n "${height:-}" ]; then
    tap_x=$((width - 70))
    tap_y=$((320 + 70))
    echo "-- tapping the bubble at ${tap_x},${tap_y} (screen ${width}x${height})"
    adb shell input tap "$tap_x" "$tap_y"
    sleep 4
    adb exec-out screencap -p > artifacts/bubble-expanded.png 2>/dev/null || true
  fi

  ls -l artifacts/*.png 2>/dev/null || echo "(no screenshots captured)"
}

# The standalone floating-button app: its own package, and its whole point is the overlay, so
# install it, grant the appop, launch it and require the window to attach.
smoke_test_bubble_app() {
  local apk="artifacts/floating-button.apk"
  local pkg="com.aistudio.ipcsolution.bubble"

  echo
  echo "== standalone floating button app =="

  if [ ! -f "$apk" ]; then
    echo "RESULT[bubble app]: FAIL - $apk was not built"
    return 1
  fi

  adb uninstall "$pkg" >/dev/null 2>&1 || true
  if ! adb install -r "$apk"; then
    echo "RESULT[bubble app]: FAIL - install rejected"
    return 1
  fi

  adb shell appops set "$pkg" SYSTEM_ALERT_WINDOW allow || true
  adb shell pm grant "$pkg" android.permission.READ_CALL_LOG || true
  adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS || true

  adb logcat -c || true

  # The overlay service is exported="false", so drive it through the activity's SHOW action -
  # the same entry point a launcher shortcut or automation would use.
  adb shell am start -a com.example.bubble.action.SHOW -n "$pkg/com.example.bubble.BubbleActivity"
  sleep 12

  local log
  log="$(adb logcat -d 2>/dev/null | grep -E "BubbleOverlayService|BubbleActivity|AndroidRuntime" || true)"
  echo "$log" | tail -30

  if adb logcat -d -b crash | grep -q "FATAL EXCEPTION"; then
    echo "RESULT[bubble app]: FAIL - crashed"
    adb logcat -d -b crash | head -60
    return 1
  fi

  if echo "$log" | grep -q "Overlay window attached"; then
    capture_bubble_screenshots
    echo "RESULT[bubble app]: PASS - launcher screen and overlay window both up"
    return 0
  fi

  # The activity alone still proves the app installs and starts; say so precisely.
  if adb shell dumpsys activity activities | grep -q "$pkg"; then
    echo "RESULT[bubble app]: FAIL - app started but the overlay never attached"
  else
    echo "RESULT[bubble app]: FAIL - app did not start"
  fi
  adb logcat -d -b crash | head -60
  return 1
}

wait_for_device

DEBUG_APK="artifacts/ipc-solution-poc-swiss-army-debug.apk"
SLIM_APK="artifacts/ipc-solution-poc-swiss-army-slim.apk"

debug_status=skipped
if [ -f "$DEBUG_APK" ]; then
  if smoke_test_apk "$DEBUG_APK" "control (debug, unminified)"; then
    debug_status=pass
  else
    debug_status=fail
  fi
fi

slim_status=fail
if smoke_test_apk "$SLIM_APK" "minified release"; then
  slim_status=pass
fi

# Runs against whatever is installed, which at this point is the minified build.
floating_status=fail
if [ "$slim_status" = pass ] && smoke_test_floating_rail; then
  floating_status=pass
fi

bubble_status=fail
if smoke_test_bubble_app; then
  bubble_status=pass
fi

echo
echo "=================================================================="
echo "control (debug) : $debug_status"
echo "minified release: $slim_status"
echo "floating rail   : $floating_status"
echo "bubble app      : $bubble_status"
echo "=================================================================="

if [ "$slim_status" = pass ] && [ "$floating_status" = pass ] && [ "$bubble_status" = pass ]; then
  exit 0
fi

if [ "$debug_status" = fail ]; then
  echo "Both builds failed the same check - suspect the emulator or this harness, not R8."
fi
exit 1
