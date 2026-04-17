# Overlay Service Contract: Core Capture Overlay (Phase 1)

**Feature Branch**: `001-core-capture-overlay`
**Date**: 2026-04-15

---

## 1. Service Intent Actions

### Start Service

**Action**: `com.capsule.app.action.START_OVERLAY`
**Extras**: None
**Caller**: `MainActivity` (user toggles ON)
**Preconditions**:
- `Settings.canDrawOverlays(context)` returns `true`
- `POST_NOTIFICATIONS` permission granted (API 33+)
**Postconditions**:
- Foreground notification posted on channel `capsule_overlay`
- Overlay window added to WindowManager with `TYPE_APPLICATION_OVERLAY`
- OverlayLifecycleOwner moved to `ON_RESUME`

### Stop Service

**Action**: `com.capsule.app.action.STOP_OVERLAY`
**Extras**: None
**Caller**: `MainActivity` (user toggles OFF) or foreground notification action
**Postconditions**:
- Overlay window removed from WindowManager
- OverlayLifecycleOwner moved to `ON_DESTROY`, ViewModelStore cleared
- Service calls `stopSelf()`

### Restart from AlarmManager

**Action**: `com.capsule.app.action.RESTART_OVERLAY`
**Extras**: None
**Caller**: `RestartReceiver` (BroadcastReceiver triggered by AlarmManager)
**Preconditions**:
- `SharedPreferences["service_enabled"] == true`
- `Settings.canDrawOverlays(context)` returns `true`
**Postconditions**:
- Same as START_OVERLAY
- `ServiceHealth.restartCount` incremented
- `ServiceHealth.status` set to `DEGRADED`

---

## 2. Notification Channel

**Channel ID**: `capsule_overlay`
**Name**: `Capsule Overlay`
**Importance**: `NotificationManager.IMPORTANCE_LOW`
**Description**: `Keeps Capsule's floating overlay running`
**Sound**: None
**Vibrate**: None
**Badge**: Hidden

### Foreground Notification

**ID**: `1` (constant — single notification)
**Content Title**: `Capsule Active`
**Content Text**: `Tap bubble to capture clipboard`
**Small Icon**: `R.drawable.ic_capsule_notification` (placeholder in Phase 1)
**Actions**:
- `Stop` → sends `STOP_OVERLAY` intent to service

---

## 3. Overlay Window Parameters

```kotlin
WindowManager.LayoutParams(
    width = WRAP_CONTENT,
    height = WRAP_CONTENT,
    type = TYPE_APPLICATION_OVERLAY,
    flags = FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS,
    format = PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = restoredX
    y = restoredY
}
```

**Flag toggling during clipboard read**:
- `REQUESTING_FOCUS` state: Remove `FLAG_NOT_FOCUSABLE`, call `updateViewLayout()`
- `RESTORING_FLAGS` state: Add `FLAG_NOT_FOCUSABLE`, call `updateViewLayout()`

---

## 4. OverlayViewModel API Surface

```kotlin
class OverlayViewModel : ViewModel() {
    // State
    val bubbleState: StateFlow<BubbleState>
    val capturedContent: StateFlow<CapturedContent?>
    val clipboardFocusState: StateFlow<ClipboardFocusState>

    // User Actions
    fun onBubbleTap()           // Trigger clipboard read if COLLAPSED, toggle if EXPANDED
    fun onBubbleDragStart()
    fun onBubbleDrag(dx: Int, dy: Int)
    fun onBubbleDragEnd()
    fun onSaveCapture()         // Phase 1: Log to Logcat
    fun onDiscardCapture()      // Clear capturedContent, collapse sheet

    // Service Callbacks
    fun onClipboardReadResult(content: CapturedContent?)
    fun onFocusStateChanged(state: ClipboardFocusState)
}
```

---

## 5. Permission Dependencies

| Permission | Type | Required For | Fallback |
|---|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Special (Settings intent) | Overlay window | Cannot function — block toggle |
| `FOREGROUND_SERVICE` | Normal (manifest) | Foreground service | N/A — auto-granted |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Normal (manifest) | `specialUse` FGS type | N/A — auto-granted |
| `POST_NOTIFICATIONS` | Runtime (API 33+) | Notification channel | Service cannot start foreground |
| `RECEIVE_BOOT_COMPLETED` | Normal (manifest) | Auto-restart on boot | Service won't auto-start |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Normal (manifest) | Battery optimization prompt | Service more likely to be killed |

---

## 6. Error Conditions

| Condition | Response |
|---|---|
| `canDrawOverlays()` returns `false` | Block service start, show permission request in Activity |
| `POST_NOTIFICATIONS` denied | Show rationale, re-request. Cannot start foreground service without it. |
| Clipboard is empty on tap | Show brief "Nothing to capture" feedback in overlay, auto-collapse |
| Clipboard has non-text MIME | Show "Text only in Phase 1" feedback, auto-collapse |
| Focus hack times out (500ms) | Force-restore `FLAG_NOT_FOCUSABLE`, show "Capture failed" feedback |
| Service killed by OEM | AlarmManager restart → DEGRADED state → recover bubble position |
| `ForegroundServiceStartNotAllowedException` | Log error, schedule AlarmManager retry, show KILLED in Activity |
