#!/usr/bin/env bash
# Запускає лаунчер на емуляторі і збирає все, що допоможе знайти причину падіння.
# Навмисно завжди виходить з кодом 0 — інакше GitHub не завантажить артефакти.

PKG=com.bell.launcher.debug
ACT=com.bell.launcher.MainActivity
OUT=smoke-out

mkdir -p "$OUT"
exec > >(tee -a "$OUT/steps.txt") 2>&1
set -x

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'

adb shell getprop ro.build.version.release > "$OUT/android-version.txt"
adb shell getprop ro.build.version.sdk >> "$OUT/android-version.txt"

APK=$(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)
echo "APK: $APK"
adb install -r -t "$APK"

# ---------- перший запуск ----------
adb logcat -c || true
adb shell am start -W -n "$PKG/$ACT"
sleep 20

adb logcat -d > "$OUT/logcat-run1-full.txt" || true
adb logcat -d -b crash > "$OUT/logcat-run1-crash.txt" || true
adb exec-out screencap -p > "$OUT/screen-run1.png" || true

grep -inE "bell\.launcher|AndroidRuntime|FATAL EXCEPTION|Caused by|ANR in" \
  "$OUT/logcat-run1-full.txt" > "$OUT/logcat-run1-filtered.txt" || true

# файли діагностики самого лаунчера
adb shell run-as "$PKG" cat files/last_crash.txt     > "$OUT/app-last_crash.txt"     || true
adb shell run-as "$PKG" cat files/startup_trace.txt  > "$OUT/app-startup_trace.txt"  || true

# ---------- другий запуск: має показати екран діагностики ----------
adb shell am force-stop "$PKG"
sleep 2
adb logcat -c || true
adb shell am start -W -n "$PKG/$ACT"
sleep 15

adb exec-out screencap -p > "$OUT/screen-run2.png" || true
adb logcat -d > "$OUT/logcat-run2-full.txt" || true
adb shell run-as "$PKG" cat files/last_crash.txt     > "$OUT/app-last_crash-run2.txt"     || true
adb shell run-as "$PKG" cat files/startup_trace.txt  > "$OUT/app-startup_trace-run2.txt"  || true

# ---------- зведення ----------
{
  echo "===== ANDROID ====="
  cat "$OUT/android-version.txt"
  echo
  echo "===== app-last_crash.txt ====="
  cat "$OUT/app-last_crash.txt" 2>/dev/null || echo "(немає)"
  echo
  echo "===== app-startup_trace.txt ====="
  cat "$OUT/app-startup_trace.txt" 2>/dev/null || echo "(немає)"
  echo
  echo "===== logcat crash buffer ====="
  cat "$OUT/logcat-run1-crash.txt" 2>/dev/null || echo "(немає)"
  echo
  echo "===== logcat filtered ====="
  head -300 "$OUT/logcat-run1-filtered.txt" 2>/dev/null || echo "(немає)"
} > "$OUT/SUMMARY.txt"

exit 0
