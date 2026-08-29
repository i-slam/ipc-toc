#!/usr/bin/env bash
# Installs the minified release APK on a running emulator and checks that it actually launches.
# R8 failures show up at runtime, not at build time, so this is the gate that catches them.
set -euo pipefail

APK="${1:-artifacts/ipc-solution-poc-swiss-army-slim.apk}"
PACKAGE="com.aistudio.ipcsolution.poc"
ACTIVITY="com.example.MainActivity"

echo "== waiting for device =="
adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'

echo "== installing $APK =="
adb install -r "$APK"

echo "== launching $PACKAGE/$ACTIVITY =="
adb logcat -c
adb shell am start -W -n "$PACKAGE/$ACTIVITY"
sleep 12

echo "== checking the activity survived =="
if ! adb shell dumpsys activity activities | grep -q "$PACKAGE/$ACTIVITY"; then
  echo "FAIL: $ACTIVITY is not in the activity stack — it never started or died on launch."
  adb logcat -d | tail -200
  exit 1
fi

echo "== checking for crashes =="
crash_log="$(adb logcat -d -b crash || true)"
if [ -n "$crash_log" ]; then
  echo "FAIL: crash buffer is not empty:"
  echo "$crash_log"
  exit 1
fi

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  echo "FAIL: FATAL EXCEPTION in the main log buffer:"
  adb logcat -d | grep -A 40 "FATAL EXCEPTION" | head -80
  exit 1
fi

# ClassNotFoundException / NoSuchMethodError here almost always means an over-aggressive
# R8 rule rather than a genuine bug.
if adb logcat -d | grep -Eq "ClassNotFoundException|NoSuchMethodError|NoClassDefFoundError"; then
  echo "FAIL: missing class or method at runtime — check the keep rules in proguard-rules.pro:"
  adb logcat -d | grep -E -B 5 -A 20 "ClassNotFoundException|NoSuchMethodError|NoClassDefFoundError" | head -80
  exit 1
fi

echo "PASS: the minified APK installs, launches and stays up."
