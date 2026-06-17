---
name: android-device-debugging
description: Use when working in unseal-android and needing to connect a real Android phone, mirror the screen, install APKs, simulate user actions, inspect logs, or measure UI performance and resource usage.
---

# Android Device Debugging

Use this skill for real-device validation in `unseal-android`: USB connection, `scrcpy` mirroring, APK install, mock interaction, logs, frame pacing, CPU, memory, and GPU-related checks.

## Defaults

```bash
export ANDROID_HOME=/usr/local/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

APP_ID=network.unseal.android.debug
APK=app/build/outputs/apk/gplay/debug/app-gplay-universal-debug.apk
```

Prefer the ABI-specific APK when the device ABI is known:

```bash
adb shell getprop ro.product.cpu.abi
# arm64-v8a -> app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk
```

## 1. Connect Phone

```bash
adb kill-server
adb start-server
adb devices -l
```

If the device is missing:

- Confirm USB debugging is enabled on the phone.
- Replug USB and accept the trust prompt.
- Use `adb reconnect device`.
- If multiple devices are connected, set `DEVICE=-s <serial>` and include it in every `adb` command.

```bash
DEVICE=-s 5fd76ce3
adb $DEVICE devices -l
```

## 2. Mirror Screen

Start foreground mirroring:

```bash
scrcpy --stay-awake --turn-screen-off=false
```

Useful variants:

```bash
scrcpy --max-size 1080 --video-bit-rate 8M
scrcpy --record /tmp/unseal-android-room.mp4
scrcpy --no-audio --stay-awake
```

If the user says the screen is not visible, check whether `scrcpy` is still running and bring its window to the foreground. Do not assume mirroring is active just because `adb` is connected.

## 3. Build And Install

Build:

```bash
./gradlew --no-daemon --no-configuration-cache :app:assembleGplayDebug
```

Install and relaunch:

```bash
adb $DEVICE install -r -d "$APK"
adb $DEVICE shell am force-stop "$APP_ID"
adb $DEVICE shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1
```

If install fails with version downgrade, keep `-d`. If install fails with ABI mismatch, use `app-gplay-universal-debug.apk`.

## 4. Simulate User Actions

Use `scrcpy` for visual manual testing when possible. Use `adb input` for repeatable actions:

```bash
adb $DEVICE shell input tap 360 1200
adb $DEVICE shell input swipe 360 1400 360 400 400
adb $DEVICE shell input swipe 360 450 360 1450 400
adb $DEVICE shell input text 'hello%sworld'
adb $DEVICE shell input keyevent KEYCODE_BACK
adb $DEVICE shell input keyevent KEYCODE_ENTER
```

Find UI bounds:

```bash
adb $DEVICE shell uiautomator dump /sdcard/window.xml
adb $DEVICE pull /sdcard/window.xml /tmp/unseal-window.xml
```

Take screenshots:

```bash
adb $DEVICE exec-out screencap -p > /tmp/unseal-android.png
```

For timeline testing, use a fixed sequence: open room, clear logcat, scroll up/down several times, stop, screenshot, then collect frame stats.

## 5. Logs

Clear logs before a focused test:

```bash
adb $DEVICE logcat -c
```

Common focused logs:

```bash
adb $DEVICE logcat -s "AiSdkStreamReducer:*" "AiStreamDbg:*" "AgentStream:*"
adb $DEVICE logcat -s "Timeline:*" "Messages:*" "Room:*"
adb $DEVICE logcat | rg -i "exception|fatal|anr|jank|stream|timeline"
```

When investigating auth or stream bugs, record the request host and route, but never print access tokens or message secrets.

## 6. Frame Rate And Jank

Reset frame stats before interacting:

```bash
adb $DEVICE shell dumpsys gfxinfo "$APP_ID" reset
```

Perform the target interaction, then collect:

```bash
adb $DEVICE shell dumpsys gfxinfo "$APP_ID" framestats > /tmp/unseal-framestats.txt
adb $DEVICE shell dumpsys gfxinfo "$APP_ID" > /tmp/unseal-gfxinfo.txt
```

What to inspect:

- `Janky frames`
- `90th percentile`, `95th percentile`, `99th percentile`
- Long frames near scroll stop usually mean layout shift, image decode, markdown parse, or unstable LazyColumn keys.

For live rendering info:

```bash
adb $DEVICE shell dumpsys SurfaceFlinger --latency-clear
adb $DEVICE shell dumpsys SurfaceFlinger --latency
```

## 7. CPU, Memory, And Processes

CPU:

```bash
adb $DEVICE shell top -H -p "$(adb $DEVICE shell pidof "$APP_ID")"
adb $DEVICE shell dumpsys cpuinfo | rg "$APP_ID|TOTAL"
```

Memory:

```bash
adb $DEVICE shell dumpsys meminfo "$APP_ID" > /tmp/unseal-meminfo.txt
adb $DEVICE shell am dumpheap "$APP_ID" /sdcard/unseal.hprof
adb $DEVICE pull /sdcard/unseal.hprof /tmp/unseal.hprof
```

Process and package:

```bash
adb $DEVICE shell pidof "$APP_ID"
adb $DEVICE shell dumpsys package "$APP_ID" | rg "versionName|versionCode|firstInstallTime|lastUpdateTime"
```

## 8. Timeline Performance Checklist

When the user reports room/timeline jank:

1. Install the latest APK and relaunch.
2. Start `scrcpy` and confirm the mirrored window is visible.
3. Clear `gfxinfo` and `logcat`.
4. Scroll the exact room section that reproduces the issue.
5. Capture screenshot plus `gfxinfo framestats`.
6. Check whether new stream handles, image loads, markdown parsing, or read receipts are triggered during scroll.
7. Fix based on measured cause, then repeat the same scroll path and compare metrics.

Do not claim performance is fixed without a before/after measurement or at least a focused real-device reproduction.
