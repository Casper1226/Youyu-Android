#!/usr/bin/env bash
set -euo pipefail
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
runner_tmp="${RUNNER_TEMP:?This check requires an isolated CI emulator.}"
public_package=io.github.casper1226.youyu
debug_package="$public_package.debug"
tools_dir="$ANDROID_HOME/build-tools/35.0.0"
sample_gallery=build/public-gallery
mkdir -p "$sample_gallery"

# The instrumentation target may have been removed after the connected tests.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class 'com.youyu.android.LedgerUiTest#catalogScreensShowTheSameDemoAccountsAndEntries' \
  "$debug_package.test/androidx.test.runner.AndroidJUnitRunner" > "$runner_tmp/demo-test.txt"
grep -q 'OK (1 test)' "$runner_tmp/demo-test.txt"
for screen in home accounts account; do
  adb exec-out run-as "$debug_package" cat "cache/demo-gallery/$screen.png" > "$sample_gallery/$screen.png"
  file "$sample_gallery/$screen.png" | grep -q 'PNG image data'
done

# Only this disposable emulator copy uses the runner's test key.
"$tools_dir/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
  --out "$runner_tmp/youyu-emulator.apk" app/build/outputs/apk/release/app-release-unsigned.apk
"$tools_dir/apksigner" verify "$runner_tmp/youyu-emulator.apk"
adb install "$runner_tmp/youyu-emulator.apk"
adb shell am start -W -n "$public_package/com.youyu.android.MainActivity"
python3 scripts/smoke_release.py
