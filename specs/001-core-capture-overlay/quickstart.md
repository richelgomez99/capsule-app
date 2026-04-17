# Quickstart: Core Capture Overlay (Phase 1)

**Feature Branch**: `001-core-capture-overlay`

---

## Prerequisites

- Android Studio Ladybug (2024.2+) or later
- JDK 17+
- Physical Android device running **Android 13+** (API 33+)
- USB debugging enabled on device
- Device connected via USB or wireless debugging

> **Emulator Warning**: The clipboard focus hack relies on `TYPE_APPLICATION_OVERLAY`
> focus behavior that may not replicate accurately on emulators. Always test on
> a physical device.

---

## 1. Clone & Checkout

```bash
git clone <repo-url> Capsule
cd Capsule
git checkout 001-core-capture-overlay
```

---

## 2. Build

```bash
./gradlew assembleDebug
```

Or in Android Studio: **Build → Make Project** (Ctrl+F9).

---

## 3. Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or in Android Studio: **Run → Run 'app'** (Shift+F10).

---

## 4. Grant Permissions

### Overlay Permission (SYSTEM_ALERT_WINDOW)

The app will prompt on first launch. If needed manually:

```bash
adb shell appops set com.capsule.app SYSTEM_ALERT_WINDOW allow
```

Or: **Settings → Apps → Capsule → Display over other apps → Allow**

### Notification Permission (POST_NOTIFICATIONS)

The app will request at runtime. If needed manually:

```bash
adb shell pm grant com.capsule.app android.permission.POST_NOTIFICATIONS
```

### Battery Optimization

Recommended for testing reliability:

```bash
adb shell dumpsys deviceidle whitelist +com.capsule.app
```

---

## 5. Manual Verification Checklist

### A. Overlay Toggle

1. Open Capsule app
2. Tap the toggle switch → bubble should appear as floating FAB
3. Tap toggle OFF → bubble should disappear
4. Tap toggle ON again → bubble reappears

### B. Clipboard Capture

1. Open any other app (e.g., Chrome, Notes)
2. Copy some text to clipboard
3. Tap the Capsule bubble
4. Capture sheet should expand showing the clipboard text
5. Check Logcat for capture log:
   ```bash
   adb logcat -s CapsuleCapture:D
   ```
6. Tap "Save" → text logged to Logcat with tag `CapsuleCapture`
7. Tap "Discard" → sheet collapses, no log

### C. Focus Hack Verification

This is the **critical verification** that must pass before Phase 2.

1. Copy text in another app
2. Tap the Capsule bubble
3. Verify clipboard text appears in the capture sheet
4. Verify Logcat shows state transitions:
   ```
   ClipboardFocus: IDLE → REQUESTING_FOCUS
   ClipboardFocus: REQUESTING_FOCUS → READING_CLIPBOARD
   ClipboardFocus: READING_CLIPBOARD → RESTORING_FLAGS
   ClipboardFocus: RESTORING_FLAGS → IDLE
   ```
5. Verify the underlying app regains focus after capture (type in the app — input should work)

```bash
adb logcat -s ClipboardFocus:D
```

### D. Drag & Edge Snap

1. Long-press the bubble and drag it across the screen
2. Release — it should snap to the nearest edge (left or right)
3. Verify snap animation completes smoothly

### E. OEM Kill Survival

1. Enable the overlay
2. Remove Capsule from recent apps (swipe away)
3. Wait 5-10 seconds
4. Check if bubble reappears
5. Verify Logcat shows restart:
   ```bash
   adb logcat -s ServiceHealth:D
   ```

### F. Service Health

1. In the Capsule Activity, observe the service health indicator
2. After a kill-restart, it should show DEGRADED briefly, then ACTIVE

---

## 6. Useful ADB Commands

```bash
# View all Capsule logs
adb logcat -s CapsuleCapture:D ClipboardFocus:D ServiceHealth:D BubbleUI:D

# Check if service is running
adb shell dumpsys activity services com.capsule.app

# Check overlay permission status
adb shell appops get com.capsule.app SYSTEM_ALERT_WINDOW

# Force-stop to test restart
adb shell am force-stop com.capsule.app

# Check battery optimization status
adb shell dumpsys deviceidle whitelist | grep capsule

# Check foreground service status
adb shell dumpsys activity services | grep -A 5 CapsuleOverlayService
```

---

## 7. Troubleshooting

| Problem | Solution |
|---|---|
| Bubble doesn't appear | Check overlay permission: `adb shell appops get com.capsule.app SYSTEM_ALERT_WINDOW` |
| "Foreground service not allowed" crash | Ensure `POST_NOTIFICATIONS` is granted. On Android 15: overlay must be visible before `startForeground()`. |
| Clipboard read returns null | Verify focus hack state machine — check `ClipboardFocus` Logcat tag. Try with a different source app. |
| Bubble doesn't reappear after kill | Check AlarmManager: `adb shell dumpsys alarm | grep capsule`. Check battery optimization whitelist. |
| Build fails on Compose version | Ensure Compose BOM version in `libs.versions.toml` matches. Run `./gradlew --refresh-dependencies`. |
