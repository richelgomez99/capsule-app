# Capsule – Phased Spec Kit: Implementation Roadmap

**Project**: Capsule – Proactive PKM Memory Agent for Android 13+  
**Created**: 2026-04-15  
**Status**: Ratified  
**Input Sources**: Architectural Feasibility Study, UI Information Architecture  
**Execution Rule**: No phase may begin code implementation until all prior phases are compiled, deployed to a physical device, and manually verified.

---

## Table of Contents

1. [Global Architecture Map](#1-global-architecture-map)
2. [Complete File Structure (All Phases)](#2-complete-file-structure-all-phases)
3. [Phase 1: Core Capture Infrastructure](#3-phase-1-core-capture-infrastructure)
4. [Phase 2: Local Persistence & Vector Foundation](#4-phase-2-local-persistence--vector-foundation)
5. [Phase 3: The Inbox & UI Workspace](#5-phase-3-the-inbox--ui-workspace)
6. [Phase 4: The Intelligence Engine](#6-phase-4-the-intelligence-engine)
7. [Dependency Graph](#7-dependency-graph)
8. [Verification Gates](#8-verification-gates)

---

## 1. Global Architecture Map

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         CAPSULE SYSTEM ARCHITECTURE                        │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     PHASE 1: OVERLAY SURFACE                        │   │
│  │                                                                     │   │
│  │  CapsuleOverlayService (LifecycleService)                          │   │
│  │    ├── OverlayLifecycleOwner (custom)                              │   │
│  │    ├── WindowManager + ComposeView                                 │   │
│  │    ├── BubbleUI (Draggable FAB)                                    │   │
│  │    ├── CaptureSheetUI (Expanded Card)                              │   │
│  │    └── ClipboardFocusStateMachine                                  │   │
│  │         Idle → RequestFocus → ReadClipboard → RestoreFlags         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │ captured text                               │
│                              ▼                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     PHASE 2: PERSISTENCE LAYER                      │   │
│  │                                                                     │   │
│  │  MemoryEvent (Room Entity) ──► IngestionPipeline                   │   │
│  │    ├── id, timestamp, source, sourceAppPackage                     │   │
│  │    ├── rawText, summaryText, tags                                  │   │
│  │    └── metadataJson                                                │   │
│  │                                                                     │   │
│  │  Embedding (Room Entity)                                           │   │
│  │    ├── id (FK → MemoryEvent)                                       │   │
│  │    ├── vector (BLOB, 384-dim FLOAT32)                              │   │
│  │    └── modelId                                                      │   │
│  │                                                                     │   │
│  │  CapsuleDatabase (Room + sqlite-vss extension)                     │   │
│  │  MemoryRepository (single source of truth)                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │ query/write                                 │
│                              ▼                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                  PHASE 3: INBOX & UI WORKSPACE                      │   │
│  │                                                                     │   │
│  │  MainActivity (Compose Navigation)                                 │   │
│  │    ├── InboxScreen (Timeline Feed + Search Bar)                    │   │
│  │    ├── MemoryDetailScreen (Raw + Summary + Chat)                   │   │
│  │    ├── SettingsScreen (Service Health + Privacy + AI Tier)         │   │
│  │    └── ManualCaptureSheet (Bottom Sheet FAB)                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │ semantic queries                            │
│                              ▼                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                  PHASE 4: INTELLIGENCE ENGINE                       │   │
│  │                                                                     │   │
│  │  EmbeddingEngine (ONNX Runtime Mobile)                             │   │
│  │    ├── MiniLM quantized model (~22 MB, 384-dim)                    │   │
│  │    ├── NNAPI / XNNPACK acceleration                                │   │
│  │    └── Batched background embedding via WorkManager                │   │
│  │                                                                     │   │
│  │  DeviceTierClassifier (A/B/C profiling)                            │   │
│  │  RAGOrchestrator (retrieve → rank → synthesize)                    │   │
│  │  GlobalChatScreen (Full RAG chat interface)                        │   │
│  │    ├── Citation Pills                                              │   │
│  │    ├── Agentic Thought Transparency                                │   │
│  │    └── Local Vault / Internet+Vault mode toggle                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Complete File Structure (All Phases)

```
Capsule/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   └── models/                          ← Phase 4
│       │   │       └── minilm-quantized.onnx
│       │   ├── java/com/capsule/app/
│       │   │   │
│       │   │   ├── CapsuleApplication.kt            ← Phase 1 (init)
│       │   │   │
│       │   │   ├── service/                          ← Phase 1
│       │   │   │   ├── CapsuleOverlayService.kt
│       │   │   │   ├── OverlayLifecycleOwner.kt
│       │   │   │   ├── ClipboardFocusStateMachine.kt
│       │   │   │   ├── ForegroundNotificationManager.kt
│       │   │   │   └── ServiceHealthMonitor.kt
│       │   │   │
│       │   │   ├── overlay/                          ← Phase 1
│       │   │   │   ├── BubbleUI.kt
│       │   │   │   ├── CaptureSheetUI.kt
│       │   │   │   ├── OverlayViewModel.kt
│       │   │   │   └── BubbleState.kt
│       │   │   │
│       │   │   ├── permission/                       ← Phase 1
│       │   │   │   ├── OverlayPermissionHelper.kt
│       │   │   │   └── BatteryOptimizationGuide.kt
│       │   │   │
│       │   │   ├── data/                             ← Phase 2
│       │   │   │   ├── local/
│       │   │   │   │   ├── CapsuleDatabase.kt
│       │   │   │   │   ├── MemoryEventDao.kt
│       │   │   │   │   ├── EmbeddingDao.kt
│       │   │   │   │   └── VectorSearchHelper.kt
│       │   │   │   ├── model/
│       │   │   │   │   ├── MemoryEvent.kt
│       │   │   │   │   ├── Embedding.kt
│       │   │   │   │   ├── MemorySource.kt
│       │   │   │   │   └── CaptureMetadata.kt
│       │   │   │   ├── repository/
│       │   │   │   │   └── MemoryRepository.kt
│       │   │   │   └── ingestion/
│       │   │   │       ├── IngestionPipeline.kt
│       │   │   │       ├── TextNormalizer.kt
│       │   │   │       └── DeduplicationEngine.kt
│       │   │   │
│       │   │   ├── ui/                               ← Phase 3
│       │   │   │   ├── MainActivity.kt
│       │   │   │   ├── navigation/
│       │   │   │   │   └── CapsuleNavGraph.kt
│       │   │   │   ├── inbox/
│       │   │   │   │   ├── InboxScreen.kt
│       │   │   │   │   ├── InboxViewModel.kt
│       │   │   │   │   ├── MemoryCardItem.kt
│       │   │   │   │   └── SearchBar.kt
│       │   │   │   ├── detail/
│       │   │   │   │   ├── MemoryDetailScreen.kt
│       │   │   │   │   └── MemoryDetailViewModel.kt
│       │   │   │   ├── settings/
│       │   │   │   │   ├── SettingsScreen.kt
│       │   │   │   │   ├── SettingsViewModel.kt
│       │   │   │   │   └── ServiceDashboard.kt
│       │   │   │   └── theme/
│       │   │   │       ├── CapsuleTheme.kt
│       │   │   │       ├── Color.kt
│       │   │   │       └── Type.kt
│       │   │   │
│       │   │   ├── ai/                               ← Phase 4
│       │   │   │   ├── DeviceTierClassifier.kt
│       │   │   │   ├── EmbeddingEngine.kt
│       │   │   │   ├── OnnxSessionManager.kt
│       │   │   │   ├── RAGOrchestrator.kt
│       │   │   │   └── SLMInferenceEngine.kt
│       │   │   │
│       │   │   ├── chat/                             ← Phase 4
│       │   │   │   ├── GlobalChatScreen.kt
│       │   │   │   ├── GlobalChatViewModel.kt
│       │   │   │   ├── CitationPill.kt
│       │   │   │   └── AgenticThoughtAccordion.kt
│       │   │   │
│       │   │   └── di/                               ← Evolves each phase
│       │   │       └── AppModule.kt
│       │   │
│       │   └── res/
│       │       ├── drawable/
│       │       │   ├── ic_capsule_bubble.xml
│       │       │   ├── ic_capsule_notification.xml
│       │       │   └── ic_service_health_dot.xml
│       │       ├── values/
│       │       │   ├── strings.xml
│       │       │   ├── colors.xml
│       │       │   └── themes.xml
│       │       └── xml/
│       │           └── backup_rules.xml
│       │
│       └── test/                                     ← Tests per phase
│           └── java/com/capsule/app/
│               ├── service/
│               │   └── ClipboardFocusStateMachineTest.kt
│               ├── data/
│               │   ├── IngestionPipelineTest.kt
│               │   └── MemoryRepositoryTest.kt
│               └── ai/
│                   └── DeviceTierClassifierTest.kt
│
├── build.gradle.kts                                  ← Root build file
├── settings.gradle.kts
├── gradle.properties
└── gradle/
    └── libs.versions.toml                            ← Version catalog
```

---

## 3. Phase 1: Core Capture Infrastructure

**Codename**: "The Catcher's Mitt"  
**Goal**: A foreground service renders a draggable Compose bubble overlay. Tapping the bubble steals focus, reads clipboard, displays content in a Capture Sheet, and offers a dummy "Save" action (logs to Logcat). Service survives OEM kills.  
**Blocking Dependencies**: None (foundation phase).  
**Completion Criteria**: APK installed on physical device. Bubble renders over other apps. Tap reads clipboard. Capture Sheet appears. Dummy save logs text to Logcat. Service persists across screen-off and app-switch.

### 3.1 File: `build.gradle.kts` (Root)

**Purpose**: Multi-module Gradle setup with Kotlin, Compose compiler, and version catalog.

```
Plugins: android-application, kotlin-android, compose-compiler (BOM-aligned)
No Hilt/DI yet — Phase 1 uses manual construction to reduce moving parts.
```

**Key dependencies for Phase 1** (via `libs.versions.toml`):
| Dependency | Version | Purpose |
|---|---|---|
| `androidx.core:core-ktx` | 1.15+ | Kotlin extensions |
| `androidx.lifecycle:lifecycle-service` | 2.8+ | LifecycleService base class |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8+ | ViewModel for overlay |
| `androidx.compose.ui:ui` | BOM 2025.01+ | Compose UI |
| `androidx.compose.material3:material3` | BOM-aligned | Material 3 components |
| `androidx.compose.foundation:foundation` | BOM-aligned | Gesture/pointerInput |
| `androidx.activity:activity-compose` | 1.9+ | Compose Activity support |

### 3.2 File: `AndroidManifest.xml`

**Purpose**: Declare all permissions, services, and the main activity entry point.

**Declarations**:

```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Application -->
<application android:name=".CapsuleApplication" ...>

    <!-- Main Activity (minimal in Phase 1 — just a toggle switch) -->
    <activity android:name=".ui.MainActivity"
        android:exported="true"
        android:theme="@style/Theme.Capsule">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <!-- Overlay Foreground Service -->
    <service android:name=".service.CapsuleOverlayService"
        android:foregroundServiceType="dataSync"
        android:exported="false" />

</application>
```

**Notes**:
- `minSdk = 33` (Android 13), `targetSdk = 35`.
- `FOREGROUND_SERVICE_DATA_SYNC` required for Android 14+ runtime compliance.
- `POST_NOTIFICATIONS` required for Android 13+ notification channel.

### 3.3 File: `CapsuleApplication.kt`

**Purpose**: Application class. In Phase 1, creates the notification channel for the foreground service.

**Responsibilities**:
- `onCreate()`: Create `NotificationChannel("capsule_overlay", "Capsule Overlay", IMPORTANCE_LOW)` and register it with `NotificationManager`.
- No DI framework yet. Manual singleton for shared prefs if needed for bubble position persistence.

### 3.4 File: `service/CapsuleOverlayService.kt`

**Purpose**: The heart of Phase 1. A `LifecycleService` that owns the WindowManager overlay, manages the ComposeView lifecycle, and orchestrates the clipboard focus hack.

**Responsibilities**:

1. **`onStartCommand()`**:
   - Call `startForeground(NOTIFICATION_ID, buildNotification())` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
   - Check `Settings.canDrawOverlays(this)`. If false, stop self and broadcast failure.
   - Instantiate `OverlayLifecycleOwner`.
   - Create `ComposeView(this)`.
   - Attach lifecycle/savedstate/viewmodelstore owners to the ComposeView via `ViewTreeLifecycleOwner.set()`, `ViewTreeSavedStateRegistryOwner.set()`, `ViewTreeViewModelStoreOwner.set()`.
   - Call `composeView.setContent { BubbleOverlayRoot(viewModel, clipboardStateMachine) }`.
   - Create `WindowManager.LayoutParams`:
     ```
     type = TYPE_APPLICATION_OVERLAY
     flags = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_IN_SCREEN
     format = PixelFormat.TRANSLUCENT
     gravity = Gravity.TOP or Gravity.START
     width = WRAP_CONTENT
     height = WRAP_CONTENT
     x = restoredX   // from SharedPreferences
     y = restoredY
     ```
   - `windowManager.addView(composeView, params)`.
   - Drive lifecycle: `overlayLifecycleOwner.handleLifecycleEvent(ON_CREATE)` → `ON_START` → `ON_RESUME`.

2. **`onDestroy()`**:
   - Drive lifecycle: `ON_PAUSE` → `ON_STOP` → `ON_DESTROY`.
   - `windowManager.removeView(composeView)`.
   - Persist bubble position to SharedPreferences.

3. **Notification**: Low-priority persistent notification. Tap opens `MainActivity`. Action button to stop service.

4. **`onTaskRemoved()`**: Re-schedule service restart via `AlarmManager.setExactAndAllowWhileIdle()` for OEM survival.

### 3.5 File: `service/OverlayLifecycleOwner.kt`

**Purpose**: Custom implementation of `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner` for the overlay ComposeView.

**Implementation**:

```
class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val viewModelStore = ViewModelStore()

    val lifecycle: Lifecycle get() = lifecycleRegistry
    val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    val viewModelStore get() = store

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun performRestore(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }
}
```

**Critical**: Must call `performRestore(null)` before `ON_CREATE` to initialize the saved state registry.

### 3.6 File: `service/ClipboardFocusStateMachine.kt`

**Purpose**: Encapsulates the `FLAG_NOT_FOCUSABLE` toggling logic for clipboard access. This is the "clipboard focus hack" — the most critical and fragile component of Phase 1.

**State Machine**:

```
enum class ClipboardFocusState {
    IDLE,              // FLAG_NOT_FOCUSABLE is set. Normal overlay behavior.
    REQUESTING_FOCUS,  // FLAG_NOT_FOCUSABLE removed. Waiting for window focus.
    READING_CLIPBOARD, // Window has focus. Reading ClipboardManager.
    RESTORING_FLAGS    // Re-applying FLAG_NOT_FOCUSABLE. Transitioning to IDLE.
}
```

**Flow**:

1. **`onBubbleTapped()`** (called from BubbleUI gesture):
   - Assert state == `IDLE`.
   - Transition to `REQUESTING_FOCUS`.
   - Remove `FLAG_NOT_FOCUSABLE` from `LayoutParams.flags`.
   - Call `windowManager.updateViewLayout(composeView, params)`.
   - Post a delayed runnable (50ms safety) to `READING_CLIPBOARD`.

2. **`readClipboard()`**:
   - Transition to `READING_CLIPBOARD`.
   - `val clip = clipboardManager.primaryClip`
   - Extract `clip?.getItemAt(0)?.text?.toString()`.
   - Emit result to `OverlayViewModel` via callback/flow.
   - Transition to `RESTORING_FLAGS`.

3. **`restoreFlags()`**:
   - Re-add `FLAG_NOT_FOCUSABLE` to `LayoutParams.flags`.
   - Call `windowManager.updateViewLayout(composeView, params)`.
   - Transition to `IDLE`.

4. **Timeout guard**: If stuck in `REQUESTING_FOCUS` for >500ms, force-restore flags and transition to `IDLE` to prevent the overlay from permanently stealing focus.

**Error handling**:
- `SecurityException` on clipboard read → log and return null.
- `BadTokenException` on `updateViewLayout` → overlay permission revoked; stop service.

### 3.7 File: `service/ForegroundNotificationManager.kt`

**Purpose**: Build and manage the persistent foreground notification.

**Specification**:
- Channel: `capsule_overlay`, importance LOW (no sound/vibration).
- Notification: Small icon, "Capsule is active" text, ongoing, not dismissible.
- Content intent: Opens `MainActivity`.
- Action: "Stop" button that calls `stopSelf()` on the service.

### 3.8 File: `service/ServiceHealthMonitor.kt`

**Purpose**: Track service lifecycle events and expose health state as a `StateFlow<ServiceHealth>`.

**Responsibilities**:
- Track `lastStartTime`, `restartCount`, `isActive`.
- On each `onStartCommand`, increment `restartCount` if time since last start < threshold (indicates OEM kill+restart).
- Expose `Build.MANUFACTURER`-based battery optimization guide text.
- Persist health state to SharedPreferences.

### 3.9 File: `overlay/BubbleState.kt`

**Purpose**: Data classes for overlay UI state.

```kotlin
data class BubbleState(
    val x: Int = 0,
    val y: Int = 100,
    val isExpanded: Boolean = false,   // false = bubble, true = capture sheet
    val isDragging: Boolean = false,
    val serviceHealth: ServiceHealth = ServiceHealth.ACTIVE
)

data class CapturedContent(
    val text: String,
    val sourcePackage: String?,        // UsageStatsManager or null
    val timestamp: Long
)

enum class ServiceHealth { ACTIVE, DEGRADED, KILLED }
```

### 3.10 File: `overlay/OverlayViewModel.kt`

**Purpose**: ViewModel scoped to `OverlayLifecycleOwner`. Holds all overlay state.

**State**:
- `bubbleState: MutableStateFlow<BubbleState>`
- `capturedContent: MutableStateFlow<CapturedContent?>`
- `clipboardFocusState: StateFlow<ClipboardFocusState>` (observed from state machine)

**Actions**:
- `onBubbleTapped()` → triggers clipboard state machine → on result, sets `capturedContent` and `bubbleState.isExpanded = true`.
- `onDrag(dx, dy)` → updates `bubbleState.x/y` → caller must also `updateViewLayout`.
- `onSaveClicked()` → Phase 1: Logcat `Log.d("Capsule", "SAVED: ${capturedContent.value?.text}")`. Phase 2: routes to IngestionPipeline.
- `onDiscardClicked()` → clears `capturedContent`, collapses bubble.
- `onDragEnd()` → snap bubble to nearest screen edge.

### 3.11 File: `overlay/BubbleUI.kt`

**Purpose**: The draggable floating bubble composable.

**Specification**:

```
@Composable
fun BubbleUI(
    state: BubbleState,
    onTap: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit
)
```

**Visual Design**:
- Circular FAB, 56dp diameter.
- Material 3 tonal surface color with elevation shadow.
- Health indicator dot: 8dp circle at top-right of bubble. Green = ACTIVE, Yellow = DEGRADED, Red = KILLED.
- Semi-transparent (alpha 0.7) when idle, opaque (alpha 1.0) when dragging.

**Gesture handling**:
- `Modifier.pointerInput(Unit)` with `detectDragGesturesAfterLongPress` for drag.
- Short tap detected via `detectTapGestures { onTap() }`.
- Combine with `Modifier.offset { IntOffset(state.x, state.y) }`.
- On drag, emit `onDrag(dx, dy)`. On drag end, emit `onDragEnd()`.

**Edge snapping**: On drag end, animate `x` to 0 (left edge) or `screenWidth - bubbleSize` (right edge), whichever is closer.

### 3.12 File: `overlay/CaptureSheetUI.kt`

**Purpose**: The expanded capture card that appears over the current app when the user taps the bubble.

**Specification**:

```
@Composable
fun CaptureSheetUI(
    content: CapturedContent,
    onSave: () -> Unit,
    onTag: () -> Unit,          // Phase 2+: no-op in Phase 1
    onSummarize: () -> Unit,    // Phase 4+: no-op in Phase 1
    onDiscard: () -> Unit
)
```

**Layout** (from UI IA §2):
- **Top Bar**: Capsule icon (16dp), source attribution text (e.g., "Clipboard"), timestamp (relative: "Just now").
- **Content Body**: `TextField` (read-only in Phase 1) showing captured text. Scrollable. Max height 200dp.
- **Action Row** (horizontal, evenly spaced):
  - Save & Close: `FilledTonalButton`, primary emphasis.
  - Tag: `OutlinedButton`, disabled in Phase 1 (grayed).
  - Summarize: `OutlinedButton`, disabled in Phase 1 (grayed).
  - Discard: `IconButton` with trash icon.
- **Feedback Footer**: Small `Text` composable. Phase 1 shows "Ready" or "Saved ✓".

**Dimensions**: Width = screen width - 32dp margin. Positioned center-screen vertically. Elevated card with 8dp corner radius.

**Animation**: `AnimatedVisibility` with `expandVertically` + `fadeIn` when transitioning from bubble to sheet.

### 3.13 File: `permission/OverlayPermissionHelper.kt`

**Purpose**: Helper to check and request `SYSTEM_ALERT_WINDOW` permission.

**Flow**:
- `canDrawOverlays()` → `Settings.canDrawOverlays(context)`.
- `requestOverlayPermission()` → Launch `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))`.
- Expose result as a callback or `StateFlow<Boolean>`.

### 3.14 File: `permission/BatteryOptimizationGuide.kt`

**Purpose**: Detect OEM and present manufacturer-specific battery optimization instructions.

**Logic**:
- Read `Build.MANUFACTURER`.
- Match against known aggressive OEMs: Samsung, Xiaomi, Huawei, OnePlus, Oppo, Vivo, Realme.
- Return a data class with:
  - `manufacturer: String`
  - `instructions: List<String>` (step-by-step text)
  - `settingsIntent: Intent?` (deep link to battery settings if available)
  - `dontkillmyappUrl: String` (fallback URL)

### 3.15 File: `ui/MainActivity.kt` (Phase 1 Minimal)

**Purpose**: Minimal launcher activity. In Phase 1, this is just a toggle to start/stop the overlay service and check overlay permission.

**UI**:
- Single screen with:
  - "Capsule" title.
  - `Switch` to enable/disable overlay service.
  - If overlay permission not granted: Show card with "Grant Overlay Permission" button.
  - If service is running: Show green status indicator + "Bubble is active".
  - Service health card (from `ServiceHealthMonitor`).
  - Battery optimization guide card (from `BatteryOptimizationGuide`).

### 3.16 Phase 1 Verification Checklist

| # | Test | Pass Criteria |
|---|------|---------------|
| 1 | APK installs on physical Android 13+ device | No install errors |
| 2 | Overlay permission request flow | Settings screen opens, permission grantable |
| 3 | Toggle switch starts foreground service | Persistent notification appears |
| 4 | Bubble renders over other apps | Visible FAB over Chrome, Settings, etc. |
| 5 | Bubble is draggable | Follows finger, snaps to edge on release |
| 6 | Tap bubble → clipboard read | System toast "Capsule pasted from clipboard" appears (Android 13+) |
| 7 | Capture Sheet displays clipboard text | Card expands with correct text content |
| 8 | "Save & Close" logs to Logcat | `adb logcat -s Capsule` shows saved text |
| 9 | "Discard" collapses back to bubble | Smooth collapse animation |
| 10 | Service survives screen-off (5 min) | Bubble reappears on screen-on |
| 11 | Service survives app-switch | Bubble persists over all apps |
| 12 | Service survives recents-clear (Samsung) | Service restarts within 10s |
| 13 | Bubble position persists across restart | Same edge and vertical position |

> **GATE**: All 13 checks must pass before ANY Phase 2 code is written.

---

## 4. Phase 2: Local Persistence & Vector Foundation

**Codename**: "The Vault"  
**Goal**: Replace Phase 1's Logcat dummy save with a real local database. Captured text is persisted, deduplicated, and queryable via Room. Vector column is defined but not populated until Phase 4.  
**Blocking Dependencies**: Phase 1 fully verified on device.  
**Completion Criteria**: Tapping "Save" on the Capture Sheet writes a `MemoryEvent` to Room/SQLite. Events are readable via a debug query. Duplicate detection prevents saving identical text within 30s.

### 4.1 File: `data/model/MemorySource.kt`

**Purpose**: Enum defining capture sources.

```kotlin
enum class MemorySource {
    CLIPBOARD,
    SHARE_INTENT,
    MANUAL_NOTE,
    OVERLAY_NOTE,
    ACCESSIBILITY_ASSIST   // Future: gated behind policy review
}
```

### 4.2 File: `data/model/CaptureMetadata.kt`

**Purpose**: Structured metadata attached to each capture event.

```kotlin
data class CaptureMetadata(
    val sourceAppPackage: String? = null,
    val sourceAppLabel: String? = null,
    val browserUrl: String? = null,      // Only via explicit share, never scraped
    val userAnnotation: String? = null
)
```
Serialized to/from JSON via `kotlinx.serialization` for storage in `metadataJson` column.

### 4.3 File: `data/model/MemoryEvent.kt`

**Purpose**: Room entity for captured memories.

```kotlin
@Entity(tableName = "memory_events",
        indices = [Index("timestamp"), Index("source_app_package")])
data class MemoryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,                           // epoch millis
    @Enumerated(EnumType.STRING) val source: MemorySource,
    @ColumnInfo(name = "source_app_package") val sourceAppPackage: String?,
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "summary_text") val summaryText: String? = null,  // Phase 4
    val tags: String? = null,                      // Comma-separated, Phase 4
    @ColumnInfo(name = "metadata_json") val metadataJson: String? = null,
    @ColumnInfo(name = "content_hash") val contentHash: String,          // SHA-256 for dedup
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false      // Soft delete
)
```

### 4.4 File: `data/model/Embedding.kt`

**Purpose**: Room entity for vector embeddings. Defined in Phase 2 but populated in Phase 4.

```kotlin
@Entity(tableName = "embeddings",
        foreignKeys = [ForeignKey(
            entity = MemoryEvent::class,
            parentColumns = ["id"],
            childColumns = ["memory_event_id"],
            onDelete = ForeignKey.CASCADE
        )])
data class Embedding(
    @PrimaryKey val memoryEventId: Long,
    val vector: ByteArray,                // BLOB: 384-dim FLOAT32 = 1536 bytes
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

### 4.5 File: `data/local/MemoryEventDao.kt`

**Purpose**: Room DAO for memory event CRUD.

**Queries**:
```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insert(event: MemoryEvent): Long

@Query("SELECT * FROM memory_events WHERE is_deleted = 0 ORDER BY timestamp DESC")
fun getAllActive(): Flow<List<MemoryEvent>>

@Query("SELECT * FROM memory_events WHERE is_deleted = 0 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
suspend fun getPage(limit: Int, offset: Int): List<MemoryEvent>

@Query("SELECT * FROM memory_events WHERE id = :id")
suspend fun getById(id: Long): MemoryEvent?

@Query("SELECT * FROM memory_events WHERE content_hash = :hash AND timestamp > :since AND is_deleted = 0 LIMIT 1")
suspend fun findDuplicate(hash: String, since: Long): MemoryEvent?

@Query("SELECT * FROM memory_events WHERE raw_text LIKE '%' || :query || '%' AND is_deleted = 0 ORDER BY timestamp DESC")
fun searchByText(query: String): Flow<List<MemoryEvent>>

@Update
suspend fun update(event: MemoryEvent)

@Query("UPDATE memory_events SET is_deleted = 1 WHERE id = :id")
suspend fun softDelete(id: Long)
```

### 4.6 File: `data/local/EmbeddingDao.kt`

**Purpose**: Room DAO for embeddings. Minimal in Phase 2 — populated by Phase 4.

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertOrReplace(embedding: Embedding)

@Query("SELECT * FROM embeddings WHERE memory_event_id = :eventId")
suspend fun getByEventId(eventId: Long): Embedding?

@Query("SELECT memory_event_id FROM embeddings")
suspend fun getAllEmbeddedEventIds(): List<Long>
```

### 4.7 File: `data/local/CapsuleDatabase.kt`

**Purpose**: Room database definition.

```kotlin
@Database(
    entities = [MemoryEvent::class, Embedding::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CapsuleDatabase : RoomDatabase() {
    abstract fun memoryEventDao(): MemoryEventDao
    abstract fun embeddingDao(): EmbeddingDao

    companion object {
        fun create(context: Context): CapsuleDatabase =
            Room.databaseBuilder(context, CapsuleDatabase::class.java, "capsule.db")
                .fallbackToDestructiveMigration()  // OK for pre-release
                .build()
    }
}
```

**Note**: `sqlite-vss` extension loading deferred to Phase 4 when embeddings are populated.

### 4.8 File: `data/local/VectorSearchHelper.kt`

**Purpose**: Stub in Phase 2. In Phase 4, loads `sqlite-vss` native library and provides `findSimilar(queryVector, k)` using raw SQL `vss_search`.

Phase 2 implementation: Empty class with TODO comment.

### 4.9 File: `data/ingestion/TextNormalizer.kt`

**Purpose**: Clean and normalize captured text before storage.

**Operations**:
- Trim whitespace.
- Collapse multiple newlines to double-newline.
- Strip zero-width characters.
- Truncate to 50,000 characters (prevent mega-pastes).
- Return normalized text or null if empty after normalization.

### 4.10 File: `data/ingestion/DeduplicationEngine.kt`

**Purpose**: Prevent storing identical content within a time window.

**Logic**:
- Compute SHA-256 hash of normalized text.
- Query `MemoryEventDao.findDuplicate(hash, System.currentTimeMillis() - 30_000)`.
- If found, return `DuplicateResult.DUPLICATE(existingId)`.
- If not found, return `DuplicateResult.UNIQUE`.

### 4.11 File: `data/ingestion/IngestionPipeline.kt`

**Purpose**: Orchestrator that takes raw captured content and writes it to the database.

**Flow**:
```
Input: CapturedContent(text, sourcePackage, timestamp)
  │
  ├── TextNormalizer.normalize(text)
  │     └── null? → abort (empty content)
  │
  ├── DeduplicationEngine.checkDuplicate(normalizedText)
  │     └── DUPLICATE? → emit DuplicateEvent, abort
  │
  ├── Build MemoryEvent(
  │       timestamp, source=CLIPBOARD, sourceAppPackage,
  │       rawText=normalizedText, contentHash=sha256)
  │
  ├── MemoryEventDao.insert(event)
  │
  └── Emit IngestionResult.SUCCESS(eventId)
```

**Returns**: `IngestionResult` sealed class: `SUCCESS(id)`, `DUPLICATE(existingId)`, `EMPTY`, `ERROR(cause)`.

### 4.12 File: `data/repository/MemoryRepository.kt`

**Purpose**: Single source of truth for memory operations. Wraps DAO + ingestion pipeline.

**API**:
```kotlin
class MemoryRepository(
    private val db: CapsuleDatabase,
    private val ingestionPipeline: IngestionPipeline
) {
    val allMemories: Flow<List<MemoryEvent>> = db.memoryEventDao().getAllActive()

    suspend fun capture(content: CapturedContent): IngestionResult =
        ingestionPipeline.ingest(content)

    suspend fun getMemory(id: Long): MemoryEvent? =
        db.memoryEventDao().getById(id)

    fun searchText(query: String): Flow<List<MemoryEvent>> =
        db.memoryEventDao().searchByText(query)

    suspend fun softDelete(id: Long) =
        db.memoryEventDao().softDelete(id)
}
```

### 4.13 Phase 1 → Phase 2 Integration Point

**File modified**: `overlay/OverlayViewModel.kt`

Change `onSaveClicked()` from:
```kotlin
// Phase 1: Logcat
Log.d("Capsule", "SAVED: ${capturedContent.value?.text}")
```
To:
```kotlin
// Phase 2: Real persistence
viewModelScope.launch {
    val result = memoryRepository.capture(capturedContent.value!!)
    when (result) {
        is IngestionResult.SUCCESS -> _feedbackText.value = "Saved ✓"
        is IngestionResult.DUPLICATE -> _feedbackText.value = "Already saved"
        is IngestionResult.EMPTY -> _feedbackText.value = "Nothing to save"
        is IngestionResult.ERROR -> _feedbackText.value = "Error saving"
    }
}
```

### 4.14 New Dependency: `build.gradle.kts`

```kotlin
// Phase 2 additions
implementation("androidx.room:room-runtime:2.7.0")
implementation("androidx.room:room-ktx:2.7.0")
ksp("androidx.room:room-compiler:2.7.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

### 4.15 Phase 2 Verification Checklist

| # | Test | Pass Criteria |
|---|------|---------------|
| 1 | Tap bubble → Save → check DB | `adb shell "run-as com.capsule.app sqlite3 databases/capsule.db 'SELECT * FROM memory_events'"` returns row |
| 2 | Save same text twice within 30s | Second save shows "Already saved" feedback |
| 3 | Save different text | Both rows exist with different `content_hash` |
| 4 | Empty clipboard → Save | "Nothing to save" feedback, no DB row |
| 5 | Large text (10K chars) | Truncated and saved without crash |
| 6 | Service restart → DB persists | Data survives process death |
| 7 | `memory_events` schema matches spec | All columns present, indices created |
| 8 | `embeddings` table exists (empty) | Table created, ready for Phase 4 |

> **GATE**: All 8 checks must pass before ANY Phase 3 code is written.

---

## 5. Phase 3: The Inbox & UI Workspace

**Codename**: "The Mind Palace"  
**Goal**: Full main-app UI with timeline feed, text search, memory detail view, settings with service health dashboard, and manual capture. This is the first phase where the user interacts with Capsule as an "app" rather than just an overlay.  
**Blocking Dependencies**: Phase 2 fully verified.  
**Completion Criteria**: Launching the app shows a scrollable timeline of captured memories. Search filters results by text. Tapping a card shows detail view. Settings shows service health and battery guide.

### 5.1 File: `ui/theme/CapsuleTheme.kt`, `Color.kt`, `Type.kt`

**Purpose**: Material 3 dynamic color theme.

**Specification**:
- Dynamic color on Android 12+ (`dynamicDarkColorScheme` / `dynamicLightColorScheme`).
- Fallback to custom palette: Primary = deep teal, Secondary = amber accent.
- Typography: `bodyLarge` for card content, `titleMedium` for card headers, `labelSmall` for metadata.

### 5.2 File: `ui/navigation/CapsuleNavGraph.kt`

**Purpose**: Compose Navigation graph for the main app.

**Routes**:
```kotlin
sealed class CapsuleRoute(val route: String) {
    object Inbox : CapsuleRoute("inbox")
    object Detail : CapsuleRoute("detail/{memoryId}") {
        fun create(id: Long) = "detail/$id"
    }
    object Settings : CapsuleRoute("settings")
    object GlobalChat : CapsuleRoute("chat")   // Phase 4
}
```

### 5.3 File: `ui/inbox/InboxScreen.kt`

**Purpose**: The main timeline feed (UI IA §3).

**Layout**:
- **Top App Bar**: 
  - Search bar (`SearchBar` composable with `DockedSearchBar` or custom).
  - Filter icon button → opens filter bottom sheet (by source, date range, tags).
- **Main Feed**: `LazyColumn` of `MemoryCardItem` composables.
  - Pull-to-refresh gesture.
  - Empty state: Illustration + "Tap the bubble to capture your first memory".
- **Bottom FAB**: "+" button → opens `ManualCaptureSheet` (bottom sheet for typed notes).

### 5.4 File: `ui/inbox/InboxViewModel.kt`

**Purpose**: ViewModel for InboxScreen.

**State**:
```kotlin
data class InboxUiState(
    val memories: List<MemoryEvent> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoading: Boolean = true,
    val filterSource: MemorySource? = null
)
```

**Logic**:
- Observe `memoryRepository.allMemories` via `Flow`.
- On search query change, switch to `memoryRepository.searchText(query)`.
- Pagination via `getPage()` if list grows large.

### 5.5 File: `ui/inbox/MemoryCardItem.kt`

**Purpose**: Individual memory card in the timeline (UI IA §3 Memory Card).

**Layout**:
- `ElevatedCard` with:
  - **Header Row**: Source icon (clipboard/share/note icon), auto-generated title (first line or first 50 chars), relative timestamp ("2h ago").
  - **Body**: 2-3 line text snippet (`maxLines = 3`, `overflow = Ellipsis`).
  - **Footer Row**: Auto-tags as `AssistChip` composables (populated in Phase 4, placeholder in Phase 3). Source app label chip.
- Tap → navigate to `Detail/{id}`.
- Long press → context menu (Delete, Share).

### 5.6 File: `ui/inbox/SearchBar.kt`

**Purpose**: Global search bar component.

**Behavior**:
- Phase 3: Plain text search (`LIKE %query%`).
- Phase 4: Upgrades to vector similarity search.
- Debounce input by 300ms before triggering query.
- Show result count badge.

### 5.7 File: `ui/detail/MemoryDetailScreen.kt`

**Purpose**: Full detail view for a single memory (UI IA §4).

**Layout**:
- **Header**: Source icon, full timestamp, editable tags row (chips with add/remove).
- **Content Split**:
  - **Top section** (collapsible): Raw captured text in a `SelectionContainer` (user can copy).
  - **Bottom section**: AI-generated summary (Phase 4 — shows "Summary available with Intelligence Engine" placeholder in Phase 3).
- **Bottom Bar**: Text input field for per-memory chat (Phase 4 — disabled in Phase 3 with "Coming soon" hint).
- **Overflow menu**: Delete, Share as text, Copy to clipboard.

### 5.8 File: `ui/detail/MemoryDetailViewModel.kt`

**Purpose**: ViewModel for detail screen.

**State**:
```kotlin
data class DetailUiState(
    val memory: MemoryEvent? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false
)
```

**Actions**: Load by ID, update tags, soft delete (navigate back on delete).

### 5.9 File: `ui/settings/SettingsScreen.kt`

**Purpose**: Settings and system health dashboard (UI IA §5).

**Sections**:
1. **Service Dashboard**:
   - Active/Inactive indicator with colored dot.
   - Enable/Disable toggle for overlay service.
   - Restart count + last start time.
   - "Optimize Battery Settings" button → deep link via `BatteryOptimizationGuide`.
   
2. **Privacy & Source Allowlist**:
   - Toggle: "Capture clipboard on bubble tap" (default on).
   - App blocklist: List of installed apps with toggles to disable overlay (future — stub list in Phase 3).
   
3. **Intelligence Tier** (partially functional in Phase 3):
   - Radio group:
     - Option A: "Fast/Local" (text search only) — functional in Phase 3.
     - Option B: "Maximum Privacy" (local SLM) — disabled, "Requires Phase 4".
     - Option C: "Hybrid Cloud" — disabled, "Requires Phase 4".
   
4. **About**: App version, privacy policy link, open-source licenses.

### 5.10 File: `ui/settings/ServiceDashboard.kt`

**Purpose**: Extracted composable for the service health card.

**Visual**: Card with animated health dot (green pulse when active, red static when killed), service uptime, restart count, manufacturer-specific advice.

### 5.11 File: `ui/MainActivity.kt` (Phase 3 Full)

**Purpose**: Replace Phase 1's minimal toggle with full navigation host.

**Implementation**:
- `setContent { CapsuleTheme { CapsuleNavGraph(startDestination = "inbox") } }`
- Bottom navigation bar with: Inbox, Chat (Phase 4), Settings.
- "Chat" tab shows "Coming in Phase 4" placeholder.

### 5.12 Share Intent Receiver

**Addition to `AndroidManifest.xml`**:
```xml
<activity android:name=".ui.ShareReceiverActivity"
    android:exported="true"
    android:theme="@style/Theme.Capsule.Transparent">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

**File: `ui/ShareReceiverActivity.kt`**: Receives `ACTION_SEND` text, routes through `IngestionPipeline` with `source = SHARE_INTENT`, shows brief confirmation toast, finishes immediately.

### 5.13 Phase 3 Verification Checklist

| # | Test | Pass Criteria |
|---|------|---------------|
| 1 | App launch shows Inbox | Timeline feed visible, empty state if no memories |
| 2 | Capture via bubble → appears in Inbox | New memory card appears at top of feed |
| 3 | Search filters results | Typing query reduces visible cards to matches |
| 4 | Tap card → Detail view | Full text displayed, collapsible raw section works |
| 5 | Delete from detail → returns to Inbox | Card removed from feed |
| 6 | Settings shows service health | Green/red dot matches actual service state |
| 7 | Battery optimization button | Opens correct system settings page |
| 8 | Share text from Chrome → Capsule | "Share to Capsule" appears in share sheet, text saved |
| 9 | Manual capture via FAB | Bottom sheet allows typing and saving a note |
| 10 | 50+ memories → smooth scroll | No jank, lazy loading works |

> **GATE**: All 10 checks must pass before ANY Phase 4 code is written.

---

## 6. Phase 4: The Intelligence Engine

**Codename**: "The Cortex"  
**Goal**: Integrate ONNX Runtime for local embeddings, populate vector DB, enable semantic search, and build the Global Chat interface with RAG, citation pills, and agentic thought transparency.  
**Blocking Dependencies**: Phase 3 fully verified.  
**Completion Criteria**: Saved memories get embeddings. Search returns semantically similar results. Global Chat answers questions using local RAG with cited sources.

### 6.1 File: `ai/DeviceTierClassifier.kt`

**Purpose**: Classify the device into Tier A/B/C based on hardware (Arch Spec §8.1).

**Logic**:
```kotlin
enum class DeviceTier { BASELINE, MID_RANGE, FLAGSHIP }

fun classifyDevice(context: Context): DeviceTier {
    val activityManager = context.getSystemService<ActivityManager>()
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val totalRamGb = memInfo.totalMem / (1024.0 * 1024 * 1024)

    return when {
        totalRamGb >= 8.0 -> DeviceTier.FLAGSHIP
        totalRamGb >= 6.0 -> DeviceTier.MID_RANGE
        else -> DeviceTier.BASELINE
    }
}
```

**Extended checks** (optional refinements):
- CPU core count via `Runtime.getRuntime().availableProcessors()`.
- NNAPI device list via `NnApiDelegate` probe (if available).
- SOC identification via `Build.SOC_MODEL` (API 31+).

### 6.2 File: `ai/OnnxSessionManager.kt`

**Purpose**: Manage ONNX Runtime environment and sessions with proper lifecycle.

**Specification**:
```kotlin
class OnnxSessionManager(private val context: Context) {
    private val ortEnvironment = OrtEnvironment.getEnvironment()
    private var embeddingSession: OrtSession? = null

    fun initEmbeddingSession(modelPath: String, tier: DeviceTier): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(if (tier == FLAGSHIP) 4 else 2)
            setOptimizationLevel(OptLevel.ALL_OPT)
            // Try NNAPI first, fall back to XNNPACK/CPU
            try { addNnapi() } catch (_: Exception) { /* NNAPI unavailable */ }
        }
        embeddingSession = ortEnvironment.createSession(modelPath, options)
        return embeddingSession!!
    }

    fun close() {
        embeddingSession?.close()
        ortEnvironment.close()
    }
}
```

**Model**: `minilm-quantized.onnx` (~22 MB) in `assets/models/`.
- Input: Tokenized text → `input_ids` (INT64), `attention_mask` (INT64).
- Output: 384-dim FLOAT32 embedding vector.

### 6.3 File: `ai/EmbeddingEngine.kt`

**Purpose**: Generate embeddings for text using the ONNX model.

**API**:
```kotlin
class EmbeddingEngine(private val sessionManager: OnnxSessionManager) {

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val tokens = tokenize(text)   // WordPiece tokenizer
        val inputIds = OnnxTensor.createTensor(env, tokens.ids)
        val attentionMask = OnnxTensor.createTensor(env, tokens.mask)
        val result = session.run(mapOf("input_ids" to inputIds, "attention_mask" to attentionMask))
        val output = (result[0].value as Array<FloatArray>)[0]
        normalize(output)  // L2 normalize
    }

    fun floatArrayToBlob(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun blobToFloatArray(blob: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(blob.size / 4) { buffer.getFloat() }
    }
}
```

**Tokenizer**: Bundle a lightweight WordPiece vocabulary file (`vocab.txt`) in assets. Implement a minimal tokenizer or use a lightweight library.

### 6.4 File: `data/local/VectorSearchHelper.kt` (Phase 4 Implementation)

**Purpose**: Perform approximate nearest neighbor search using sqlite-vss.

**Implementation**:
```kotlin
class VectorSearchHelper(private val db: CapsuleDatabase) {

    fun initVss() {
        // Load sqlite-vss native library
        db.openHelper.writableDatabase.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS vss_embeddings USING vss0(vector(384))"
        )
    }

    suspend fun findSimilar(queryVector: FloatArray, k: Int = 10): List<Long> {
        val vectorBlob = serializeVector(queryVector)
        val cursor = db.openHelper.readableDatabase.rawQuery(
            """SELECT rowid, distance FROM vss_embeddings
               WHERE vss_search(vector, ?)
               LIMIT ?""",
            arrayOf(vectorBlob.toString(), k.toString())
        )
        // Map rowids back to memory_event_ids
        return parseResults(cursor)
    }

    suspend fun indexEmbedding(eventId: Long, vector: FloatArray) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO vss_embeddings(rowid, vector) VALUES (?, ?)",
            arrayOf(eventId, serializeVector(vector))
        )
    }
}
```

### 6.5 Background Embedding Worker

**File: `ai/EmbeddingWorker.kt`**

**Purpose**: WorkManager worker that processes un-embedded memory events in the background.

**Logic**:
```
1. Query all MemoryEvent IDs not in embeddings table.
2. For each un-embedded event (batched, max 50 per run):
   a. Load rawText.
   b. Generate embedding via EmbeddingEngine.
   c. Store in Embedding table + VectorSearchHelper.indexEmbedding().
3. Schedule next run if more remain.
```

**Constraints**: `NetworkType.NOT_REQUIRED`, `RequiresBatteryNotLow`, `RequiresDeviceIdle` (for Tier A only).

### 6.6 File: `ai/RAGOrchestrator.kt`

**Purpose**: Retrieval-Augmented Generation pipeline.

**Flow**:
```
Input: userQuery (String)
  │
  ├── EmbeddingEngine.embed(userQuery) → queryVector
  │
  ├── VectorSearchHelper.findSimilar(queryVector, k=10) → eventIds
  │
  ├── MemoryEventDao.getByIds(eventIds) → List<MemoryEvent>
  │
  ├── RankAndFilter(memories, queryVector) → top 5 relevant
  │
  ├── BuildPrompt(userQuery, relevantMemories) → contextualPrompt
  │
  └── SLMInferenceEngine.generate(contextualPrompt) → answer + citations
```

**Output**:
```kotlin
data class RAGResponse(
    val answer: String,
    val citations: List<Citation>,
    val thoughtSteps: List<ThoughtStep>   // For transparency accordion
)

data class Citation(
    val memoryEventId: Long,
    val sourceLabel: String,       // e.g., "Chrome: Project Spec"
    val snippet: String,
    val relevanceScore: Float
)

data class ThoughtStep(
    val step: String,              // e.g., "Querying Vector DB..."
    val durationMs: Long
)
```

### 6.7 File: `ai/SLMInferenceEngine.kt`

**Purpose**: Optional local SLM for synthesis. Only active on Tier C devices.

**Specification**:
- Loads a separate quantized SLM model (1-3B params, W4A8).
- Input: Contextual prompt (query + retrieved snippets).
- Output: Generated text (max 512 tokens).
- **Tier A**: Disabled. Returns raw retrieved snippets concatenated.
- **Tier B**: Optional. User must explicitly enable. Warns about battery.
- **Tier C**: Default on.

### 6.8 File: `chat/GlobalChatScreen.kt`

**Purpose**: Full-screen RAG chat interface (UI IA §6).

**Layout**:
- **Top Bar**: Mode selector toggle:
  - "Local Vault" (queries only local vector DB).
  - "Internet + Vault" (future: web search + local RAG).
- **Chat Feed**: `LazyColumn` of message bubbles.
  - User messages: Right-aligned, primary color.
  - Agent messages: Left-aligned, surface color.
  - **Citation Pills**: Below agent messages, horizontal scrollable row of `AssistChip` composables. Each chip shows source label (e.g., "[Chrome: Project Spec]"). Tap navigates to `MemoryDetailScreen`.
  - **Agentic Thought Transparency**: `AnimatedVisibility` expandable accordion above the agent's answer showing `ThoughtStep` list.
- **Bottom Input**: `TextField` with send button. Disabled during generation (shows typing indicator).

### 6.9 File: `chat/GlobalChatViewModel.kt`

**Purpose**: ViewModel for the chat screen.

**State**:
```kotlin
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val mode: ChatMode = ChatMode.LOCAL_VAULT,
    val deviceTier: DeviceTier = DeviceTier.BASELINE
)

sealed class ChatMessage {
    data class UserMessage(val text: String, val timestamp: Long) : ChatMessage()
    data class AgentMessage(
        val text: String,
        val citations: List<Citation>,
        val thoughtSteps: List<ThoughtStep>,
        val timestamp: Long
    ) : ChatMessage()
}
```

**Flow**: On send → set `isGenerating = true` → call `ragOrchestrator.query(text)` → append `AgentMessage` → set `isGenerating = false`.

### 6.10 File: `chat/CitationPill.kt`

**Purpose**: Clickable citation chip composable.

```kotlin
@Composable
fun CitationPill(
    citation: Citation,
    onClick: () -> Unit   // Navigate to detail
)
```
**Visual**: `AssistChip` with source icon + truncated label. Tap navigates to `MemoryDetailScreen(citation.memoryEventId)`.

### 6.11 File: `chat/AgenticThoughtAccordion.kt`

**Purpose**: Expandable accordion showing RAG pipeline steps.

```kotlin
@Composable
fun AgenticThoughtAccordion(
    steps: List<ThoughtStep>,
    isExpanded: Boolean,
    onToggle: () -> Unit
)
```
**Visual**: Collapsed: "Show reasoning (3 steps)". Expanded: Vertical list of step descriptions with durations.

### 6.12 Search Upgrade (Phase 3 → Phase 4)

**Modified file**: `ui/inbox/SearchBar.kt`

Phase 3 search (`LIKE %query%`) is augmented with vector similarity:
```kotlin
// Phase 4: Hybrid search
suspend fun hybridSearch(query: String): List<MemoryEvent> {
    val textResults = memoryRepository.searchText(query).first()
    val vectorResults = if (embeddingEngine.isReady()) {
        val queryVec = embeddingEngine.embed(query)
        val ids = vectorSearchHelper.findSimilar(queryVec, k = 20)
        memoryRepository.getByIds(ids)
    } else emptyList()

    return mergeAndRank(textResults, vectorResults).distinctBy { it.id }
}
```

### 6.13 New Dependencies: `build.gradle.kts`

```kotlin
// Phase 4 additions
implementation("com.microsoft.onnxruntime:onnxruntime-mobile:1.20.0")
implementation("androidx.work:work-runtime-ktx:2.10.0")
// sqlite-vss native library (include via JNI or pre-built AAR)
```

### 6.14 Phase 4 Verification Checklist

| # | Test | Pass Criteria |
|---|------|---------------|
| 1 | EmbeddingWorker processes saved memories | `embeddings` table populated for existing events |
| 2 | New capture → embedding generated | Embedding row created within 30s of save |
| 3 | Vector search returns relevant results | Query "project deadline" surfaces memories about deadlines |
| 4 | Text search still works | Exact text matches still returned |
| 5 | Global Chat sends query → receives answer | Agent message appears with answer text |
| 6 | Citations link to correct memories | Tapping citation pill opens correct detail view |
| 7 | Thought steps display correctly | Accordion shows "Querying Vector DB... Ranking... Synthesizing..." |
| 8 | Tier A device → no SLM, search-only | Graceful degradation, no crash on low-RAM device |
| 9 | Tier C device → full local RAG | Answer synthesized locally without network |
| 10 | 100+ memories → search latency < 500ms | Vector search performs within budget |
| 11 | ONNX model loads without crash | Session initialized on cold start |
| 12 | WorkManager respects battery constraints | Embedding work deferred when battery low |

> **GATE**: Full integration test pass before release candidate.

---

## 7. Dependency Graph

```
Phase 1                    Phase 2                  Phase 3                Phase 4
─────────────────────────────────────────────────────────────────────────────────────
                                                                                    
AndroidManifest.xml ──────► (add Room schemas) ────► (add ShareReceiver) ──► (unchanged)
                                                                                    
CapsuleApplication ────────► (add DB singleton) ───► (add DI wiring) ─────► (add ONNX init)
                                                                                    
CapsuleOverlayService ────► (unchanged) ───────────► (unchanged) ──────────► (unchanged)
                                                                                    
OverlayLifecycleOwner ────► (unchanged) ───────────► (unchanged) ──────────► (unchanged)
                                                                                    
ClipboardFocusStateMachine► (unchanged) ───────────► (unchanged) ──────────► (unchanged)
                                                                                    
BubbleUI ─────────────────► (unchanged) ───────────► (unchanged) ──────────► (add "Deja Vu" pulse)
                                                                                    
CaptureSheetUI ───────────► (Save → DB) ──────────► (Tag functional) ─────► (Summarize functional)
                                                                                    
OverlayViewModel ─────────► (inject Repository) ──► (unchanged) ──────────► (inject EmbeddingEngine)
                                                                                    
MainActivity (toggle) ────► (unchanged) ───────────► (full NavHost) ───────► (add Chat tab)
                                                                                    
                            MemoryEvent ───────────► (query by InboxVM) ──► (embed + vector search)
                            Embedding ─────────────► (schema only) ────────► (populated by Worker)
                            CapsuleDatabase ───────► (used by Repository) ► (add vss extension)
                            IngestionPipeline ─────► (used by CaptureSheet)► (trigger embedding)
                            MemoryRepository ──────► (used by all ViewModels)► (add vector search)
                                                                                    
                                                     InboxScreen ──────────► (hybrid search)
                                                     MemoryDetailScreen ──► (show summary)
                                                     SettingsScreen ───────► (AI tier functional)
                                                                                    
                                                                            DeviceTierClassifier
                                                                            EmbeddingEngine
                                                                            OnnxSessionManager
                                                                            VectorSearchHelper
                                                                            RAGOrchestrator
                                                                            SLMInferenceEngine
                                                                            GlobalChatScreen
                                                                            EmbeddingWorker
```

---

## 8. Verification Gates

```
┌──────────────────────────────────────────────────────────────────────┐
│                        EXECUTION SEQUENCE                            │
│                                                                      │
│  Phase 1 ──[13 checks]──► GATE 1 ──► Phase 2 ──[8 checks]──►      │
│                                                                      │
│  GATE 2 ──► Phase 3 ──[10 checks]──► GATE 3 ──► Phase 4           │
│                                                                      │
│  ──[12 checks]──► GATE 4 ──► Release Candidate                     │
│                                                                      │
│  CRITICAL RULE:                                                      │
│  Code for Phase N+1 is FORBIDDEN until ALL checks for Phase N       │
│  are verified on a PHYSICAL DEVICE via manual testing.              │
│                                                                      │
│  "Verified" means:                                                   │
│    1. APK compiled without errors                                    │
│    2. Installed on physical Android 13+ device                       │
│    3. Each checklist item manually executed and confirmed             │
│    4. Verification signed off in this document                       │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Gate Sign-Off Template

```
## Gate [N] Sign-Off

- **Phase**: [N]
- **Device**: [Model, Android Version, RAM]
- **Date**: [YYYY-MM-DD]
- **Tester**: [Name]
- **All checks passed**: [ ] Yes / [ ] No
- **Notes**: [Any deviations or issues observed]
- **Decision**: [ ] PROCEED to Phase [N+1] / [ ] BLOCK — fix required
```

---

**END OF SPEC KIT**

**Constraints Acknowledged**:
- This document outputs the complete architectural plan and file structure for all four phases.
- When transitioning to writing actual code, Phase 2 code is strictly forbidden until Phase 1 is fully compiled, deployed to a physical device, and the clipboard focus hack is manually verified.
- Each subsequent phase follows the same gate protocol.
