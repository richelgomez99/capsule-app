# Phase 0 Research: Core Capture Overlay

**Feature Branch**: `001-core-capture-overlay`
**Researched**: 2026-04-15
**Sources**: Android Developer Docs (2026-03-30 rev), dontkillmyapp.com, Android 15 behavior changes

---

## Table of Contents

1. [Foreground Service Type Decision](#1-foreground-service-type-decision)
2. [Clipboard Access on Android 13-15](#2-clipboard-access-on-android-13-15)
3. [SYSTEM_ALERT_WINDOW + FGS on Android 15](#3-system_alert_window--fgs-on-android-15)
4. [Compose-in-Overlay Lifecycle Wiring](#4-compose-in-overlay-lifecycle-wiring)
5. [OEM Battery Kill Survival](#5-oem-battery-kill-survival)
6. [Edge-to-Edge Enforcement (Android 15)](#6-edge-to-edge-enforcement-android-15)
7. [Key Decisions & Recommendations](#7-key-decisions--recommendations)

---

## 1. Foreground Service Type Decision

### The Problem

Android 14 requires a `foregroundServiceType` in the manifest. The spec currently
declares `dataSync`. Research reveals critical restrictions introduced in
Android 15 (API 35) that affect this choice.

### `dataSync` — Findings

| Property | Value |
|---|---|
| Permission | `FOREGROUND_SERVICE_DATA_SYNC` |
| Runtime prerequisites | None |
| Valid use cases | Data upload/download, backup/restore, import/export, fetch, local file processing, cloud transfer |
| Android 15 timeout | **6 hours total in a 24-hour period** (shared across all `dataSync` services). Timer resets when user brings app to foreground. |
| BOOT_COMPLETED | **BLOCKED on Android 15** — cannot launch `dataSync` from BOOT_COMPLETED receiver |
| onTimeout | Must implement `Service.onTimeout(int, int)` and call `stopSelf()` within seconds, or system throws `RemoteServiceException` |

**Critical issue**: Capsule's overlay service is *always-on* — it's not performing
"data sync". The 6-hour timeout means the foreground service will be force-stopped
if the user doesn't interact with the app in the foreground for 6 hours. This
is **unacceptable** for a persistent overlay.

### `specialUse` — Findings

| Property | Value |
|---|---|
| Permission | `FOREGROUND_SERVICE_SPECIAL_USE` |
| Runtime prerequisites | None |
| Timeout | **None** — no system-imposed time limit |
| BOOT_COMPLETED | Not in the blocked list (only `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone` are blocked) |
| Google Play review | Requires `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>` with free-form justification reviewed at submission |

**Manifest declaration**:
```xml
<service android:name=".service.CapsuleOverlayService"
    android:foregroundServiceType="specialUse">
  <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
      android:value="Persistent user-interactive floating overlay for explicit clipboard capture triggered by user tap. Requires continuous foreground presence to render TYPE_APPLICATION_OVERLAY window."/>
</service>
```

### Other Alternatives Evaluated

| Type | Why rejected |
|---|---|
| `shortService` | 3-minute timeout — far too short for always-on overlay |
| `connectedDevice` | Requires Bluetooth/NFC/USB permissions — not applicable |
| `mediaProcessing` | 6-hour timeout same as `dataSync`, wrong semantic |
| WorkManager | Cannot maintain a persistent overlay window |

### **DECISION: Use `specialUse`**

**Rationale**: Capsule's overlay is not data sync — it's a persistent
user-interactive surface. `specialUse` has no timeout, can launch from
BOOT_COMPLETED, and the justification ("persistent floating overlay for
user-initiated clipboard capture") is a legitimate use case. Google Play
review is required but this is a valid use of the type.

**Impact**: Spec FR-017 must be updated from `dataSync` to `specialUse`.
Constitution section "Foreground Service Type" must be amended at plan
completion.

---

## 2. Clipboard Access on Android 13-15

### Reading the Clipboard

The clipboard is accessed via `ClipboardManager.getPrimaryClip()`. Key behavior
across API levels:

| API Level | Behavior |
|---|---|
| 31+ (Android 12) | System shows toast "APP pasted from your clipboard" when `getPrimaryClip()` is called. Toast suppressed for same-app clips and repeated reads from same source. |
| 33+ (Android 13) | System shows a **visual confirmation UI** (not just a toast) when content is added to clipboard. Apps should avoid duplicate notifications. |
| 33+ (Android 13) | `POST_NOTIFICATIONS` runtime permission required for foreground service notification channel. |

### Key API Surface for Capsule

```kotlin
val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

// Check before reading
if (clipboard.hasPrimaryClip()) {
    val description = clipboard.primaryClipDescription
    if (description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
        val item = clipboard.primaryClip?.getItemAt(0)
        val text = item?.text?.toString()
        // OR for coercion: item?.coerceToText(context)?.toString()
    }
}
```

### The Focus Hack — Why It's Necessary

`TYPE_APPLICATION_OVERLAY` windows are created with `FLAG_NOT_FOCUSABLE` to avoid
stealing input from the underlying app. However, `getPrimaryClip()` on some
Android versions / OEM skins returns null or empty data when called from an
unfocused window context.

The **ClipboardFocusStateMachine** temporarily:
1. Removes `FLAG_NOT_FOCUSABLE` from the overlay layout params
2. Calls `WindowManager.updateViewLayout()` to acquire focus
3. Reads clipboard via `getPrimaryClip()`
4. Restores `FLAG_NOT_FOCUSABLE`

The 500ms timeout guard prevents the overlay from permanently stealing focus
if the read hangs.

### Sensitive Content Flag

Android 13 introduced `ClipDescription.EXTRA_IS_SENSITIVE`. Capsule should check
this flag and indicate when captured content was marked sensitive by the source
app, but should still allow capture (user explicitly tapped).

### `getPrimaryClipDescription()` — No Toast

Calling `getPrimaryClipDescription()` (metadata only, no data) does NOT trigger
the "pasted from clipboard" toast. Capsule should use this to check clipboard
state before committing to a full read.

---

## 3. SYSTEM_ALERT_WINDOW + FGS on Android 15

### New Restriction (targetSdk 35)

Previously, holding `SYSTEM_ALERT_WINDOW` was sufficient to start a foreground
service from the background. **Android 15 narrows this exemption**:

> The app now needs to have the `SYSTEM_ALERT_WINDOW` permission **AND** have a
> **visible** overlay window. The app needs to first launch a
> `TYPE_APPLICATION_OVERLAY` window and the window needs to be **visible** before
> you start a foreground service.

**Verification methods**:
- `View.getWindowVisibility()` — check if overlay is currently visible
- `View.onWindowVisibilityChanged()` — callback when visibility changes

**If requirements not met**: System throws `ForegroundServiceStartNotAllowedException`.

### Impact on Capsule Architecture

This **aligns well** with Capsule's design:

1. User toggles overlay ON in the Activity (foreground context)
2. Activity creates the overlay window via WindowManager (visible)
3. Activity starts the foreground service
4. Service takes ownership of the overlay window

The critical order of operations:
```
Activity.onCreate() → check permissions → add overlay to WindowManager
→ verify overlay visible → startForegroundService() → Service.onStartCommand()
→ Service.startForeground()
```

**For restart-after-kill scenarios**: The service's `onCreate()` must add the
overlay window FIRST, then call `startForeground()`. Since the service itself
has SYSTEM_ALERT_WINDOW, it can add the overlay directly without needing a
visible window first (the exemption is about starting FGS from background —
if the service IS the FGS, it can add windows).

**Clarification**: When the system restarts the service via `START_STICKY` or
AlarmManager, the service is already in the foreground service lifecycle. The
SYSTEM_ALERT_WINDOW + visible window restriction applies specifically to
*starting* a new FGS from the background. A restarting sticky service is not
blocked by this (it's the system restarting it, not the app starting from
background).

---

## 4. Compose-in-Overlay Lifecycle Wiring

### The Problem

Jetpack Compose requires a `LifecycleOwner`, `ViewModelStoreOwner`, and
`SavedStateRegistryOwner` to function. Normally an Activity provides these.
An overlay service has none.

### Solution: OverlayLifecycleOwner

A custom class implementing all three interfaces:

```kotlin
class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
```

### Wiring to ComposeView

```kotlin
val composeView = ComposeView(context).apply {
    setViewTreeLifecycleOwner(overlayLifecycleOwner)
    setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
    setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
}
```

### Lifecycle Events Synchronization

| Service Event | Lifecycle Event |
|---|---|
| `Service.onCreate()` + overlay added to WindowManager | `ON_CREATE` → `ON_START` → `ON_RESUME` |
| Overlay removed from WindowManager | `ON_PAUSE` → `ON_STOP` |
| `Service.onDestroy()` | `ON_DESTROY` + `ViewModelStore.clear()` |

### Critical Gotcha: LifecycleService

`LifecycleService` (from `androidx.lifecycle:lifecycle-service`) already implements
`LifecycleOwner`. The overlay's `OverlayLifecycleOwner` is a SEPARATE lifecycle
from the service's lifecycle. The `ComposeView` must be attached to the
`OverlayLifecycleOwner`, not to the service's own lifecycle, because the overlay
window has a different visibility lifecycle than the service itself.

---

## 5. OEM Battery Kill Survival

### Severity Rankings (dontkillmyapp.com, April 2026)

| Rank | Manufacturer | Severity |
|---|---|---|
| #1 | **Huawei** (except Nexus 6P) | Most aggressive |
| #2 | **Xiaomi** (except Android One) | Very aggressive |
| #3 | **OnePlus** | Aggressive |
| #4 | **Samsung** (especially after Android P) | Aggressive |
| #5 | Meizu | Aggressive |
| #6 | Asus | Moderate |
| #7 | Wiko | Moderate |
| #8 | Lenovo | Moderate |
| #9 | **Oppo** | Aggressive |
| #10 | **Vivo** | Aggressive |
| #11 | **Realme** | Aggressive |
| #12 | Motorola | Moderate |

### Survival Strategy (Multi-Layer)

**Layer 1: START_STICKY + Foreground Service**
- `onStartCommand()` returns `START_STICKY` — system will attempt restart
- Foreground notification keeps process priority elevated
- SYSTEM_ALERT_WINDOW grants additional process importance

**Layer 2: AlarmManager Restart**
- `onTaskRemoved()` schedules `AlarmManager.setExactAndAllowWhileIdle()`
- Restart intent targets a BroadcastReceiver that starts the service
- Alarm fires even in Doze mode (exact + allowWhileIdle)

**Layer 3: SharedPreferences State Recovery**
- Bubble position (x, y) persisted on every drag end
- Service state (active/stopped) persisted on toggle
- Restart count incremented on each `onCreate()` after unexpected kill
- On restart: read persisted state → restore position → resume

**Layer 4: Manufacturer-Specific User Guidance**
- Detect OEM via `Build.MANUFACTURER`
- Show targeted instructions (e.g., "Samsung: Settings → Battery → Background usage limits → Add Capsule to Never sleeping apps")
- Link to manufacturer-specific battery settings intents where possible

### Manufacturers Capsule Must Cover (from spec FR-013)

Samsung, Xiaomi, Huawei, OnePlus, Oppo, Vivo, Realme

### Key Insight from dontkillmyapp.com

App developers can now report device-specific issues to Google through an
IssueTracker template. CTS-D (Compatibility Test Suite - D) is Google's tool
for tracking OEM compliance. This is a positive signal — Google is actively
pushing back against aggressive OEM battery kills.

---

## 6. Edge-to-Edge Enforcement (Android 15)

Apps targeting SDK 35 are **edge-to-edge by default**. Key impacts:

- Status bar is transparent by default
- Navigation bar (gesture) is transparent by default
- `layoutInDisplayCutoutMode` must be `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`

**Impact on Capsule Phase 1**: Minimal for the overlay itself (overlays don't
have status/nav bars), but the **main Activity** (toggle screen) must handle
insets properly. Using Material 3 Compose components (which auto-handle insets)
mitigates this. The bubble positioning calculation must account for display
cutouts when calculating screen bounds for edge-snap.

---

## 7. Key Decisions & Recommendations

### Decision 1: Foreground Service Type → `specialUse`

**Status**: RECOMMENDED — replaces `dataSync` from original spec.
**Reason**: No 6-hour timeout, no BOOT_COMPLETED restriction, semantically
correct for a persistent interactive overlay.
**Action**: Update spec FR-017, amend constitution FGS type reference.

### Decision 2: Clipboard Read Strategy

**Status**: CONFIRMED — focus hack approach is correct.
**Strategy**:
1. Check `hasPrimaryClip()` + `primaryClipDescription` first (no toast)
2. If text MIME available, remove `FLAG_NOT_FOCUSABLE`
3. Call `getPrimaryClip()` (triggers Android 12+ toast — expected per VII)
4. Restore `FLAG_NOT_FOCUSABLE` within 500ms hard timeout
5. Check `EXTRA_IS_SENSITIVE` flag and indicate in UI if set

### Decision 3: Service Start Order (Android 15 Safe)

**Status**: CONFIRMED — Capsule's architecture naturally satisfies the new
requirement.
**Order**: Show overlay window → verify visible → start FGS (or, for service
restarts via START_STICKY, service creates overlay in `onCreate()` before
`startForeground()`).

### Decision 4: OEM Survival — 4-Layer Defense

**Status**: CONFIRMED per Constitution Principle III.
**Layers**: START_STICKY + AlarmManager + SharedPreferences recovery +
User-facing OEM guides.

### Decision 5: Compose-in-Overlay — Separate OverlayLifecycleOwner

**Status**: CONFIRMED per Constitution Principle VI.
**Key**: OverlayLifecycleOwner is SEPARATE from LifecycleService's lifecycle.
ComposeView tree owners point to OverlayLifecycleOwner, not to the service.

---

## Open Questions for Implementation

1. **`specialUse` Google Play approval**: The justification is strong, but
   approval is not guaranteed. Fallback plan: Use `dataSync` with an
   `onTimeout()` implementation that restarts the service (user must bring app
   to foreground every 6 hours to reset timer). This is degraded but functional.

2. **BOOT_COMPLETED restart**: With `specialUse`, the service CAN be started
   from BOOT_COMPLETED. Should Capsule auto-start on boot? Recommendation:
   Yes, if the user had the service enabled when device was shut down. Persisted
   flag in SharedPreferences controls this.

3. **Android 16 preview**: No breaking changes to overlay or FGS APIs have been
   announced as of April 2026. Monitor developer previews.
