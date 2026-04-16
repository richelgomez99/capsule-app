# Data Model: Core Capture Overlay (Phase 1)

**Feature Branch**: `001-core-capture-overlay`
**Date**: 2026-04-15

---

## Overview

Phase 1 has no database. All state is either in-memory (ViewModel StateFlow)
or persisted to SharedPreferences (bubble position, service state). "Save"
writes to Logcat only. This document defines the data structures used across
the overlay service, UI, and state machines.

---

## 1. BubbleState

**Location**: `app/src/main/java/com/capsule/app/overlay/BubbleState.kt`
**Scope**: In-memory via StateFlow in OverlayViewModel

```kotlin
data class BubbleState(
    val x: Int = 0,
    val y: Int = 0,
    val expansion: ExpansionState = ExpansionState.COLLAPSED,
    val isDragging: Boolean = false,
    val edgeSide: EdgeSide = EdgeSide.LEFT
)

enum class ExpansionState {
    COLLAPSED,  // Bubble FAB only
    EXPANDED    // Capture Sheet visible
}

enum class EdgeSide {
    LEFT, RIGHT
}
```

**Persistence**: `x`, `y`, and `edgeSide` are written to SharedPreferences
on drag-end and restored on service restart.

| Field | Type | Persisted | Description |
|---|---|---|---|
| `x` | `Int` | ✅ SharedPreferences | Pixel X coordinate of bubble center |
| `y` | `Int` | ✅ SharedPreferences | Pixel Y coordinate of bubble center |
| `expansion` | `ExpansionState` | ❌ | Always starts COLLAPSED on restart |
| `isDragging` | `Boolean` | ❌ | Transient drag gesture state |
| `edgeSide` | `EdgeSide` | ✅ SharedPreferences | Which edge the bubble is snapped to |

---

## 2. CapturedContent

**Location**: `app/src/main/java/com/capsule/app/overlay/BubbleState.kt` (same file, co-located)
**Scope**: In-memory only — Phase 1 "save" writes to Logcat

```kotlin
data class CapturedContent(
    val text: String,
    val sourcePackage: String?,    // Package name of foreground app, if resolvable
    val timestamp: Long,           // System.currentTimeMillis()
    val isSensitive: Boolean       // ClipDescription.EXTRA_IS_SENSITIVE flag
)
```

| Field | Type | Persisted | Description |
|---|---|---|---|
| `text` | `String` | ❌ (Logcat) | Clipboard text content |
| `sourcePackage` | `String?` | ❌ | Package that last wrote to clipboard |
| `timestamp` | `Long` | ❌ | Capture time in epoch millis |
| `isSensitive` | `Boolean` | ❌ | Whether source app flagged content as sensitive |

**Phase 2 Migration**: This becomes a Room `@Entity` with auto-generated
primary key, and `text` feeds into the ingestion pipeline.

---

## 3. ClipboardFocusState

**Location**: `app/src/main/java/com/capsule/app/service/ClipboardFocusStateMachine.kt`
**Scope**: In-memory state machine — never persisted

```kotlin
enum class ClipboardFocusState {
    IDLE,               // Normal overlay state, FLAG_NOT_FOCUSABLE set
    REQUESTING_FOCUS,   // FLAG_NOT_FOCUSABLE removed, waiting for focus
    READING_CLIPBOARD,  // Focus acquired, calling getPrimaryClip()
    RESTORING_FLAGS     // Clipboard read done, restoring FLAG_NOT_FOCUSABLE
}
```

**State Transitions**:

```
IDLE ──[user tap]──► REQUESTING_FOCUS
  │                       │
  │                       ├──[focus acquired]──► READING_CLIPBOARD
  │                       │                          │
  │                       │                          ├──[read success]──► RESTORING_FLAGS ──► IDLE
  │                       │                          │
  │                       │                          └──[read failure]──► RESTORING_FLAGS ──► IDLE
  │                       │
  │                       └──[500ms timeout]──► RESTORING_FLAGS ──► IDLE
  │
  └──[tap while !IDLE]──► REJECTED (no state change)
```

**Timeout**: 500ms hard deadline from entering REQUESTING_FOCUS. If still not
in IDLE by deadline, force-transition through RESTORING_FLAGS.

---

## 4. ServiceHealth

**Location**: `app/src/main/java/com/capsule/app/service/ServiceHealthMonitor.kt`
**Scope**: Persisted to SharedPreferences, exposed via StateFlow

```kotlin
enum class ServiceHealthStatus {
    ACTIVE,    // Service running normally
    DEGRADED,  // Service restarted recently (within last 5 minutes)
    KILLED     // Service not running (only visible in Activity)
}

data class ServiceHealth(
    val status: ServiceHealthStatus = ServiceHealthStatus.KILLED,
    val restartCount: Int = 0,
    val lastStartTimestamp: Long = 0L,
    val lastKillTimestamp: Long = 0L
)
```

| Field | Type | Persisted | Description |
|---|---|---|---|
| `status` | `ServiceHealthStatus` | ✅ SharedPreferences | Current service state |
| `restartCount` | `Int` | ✅ SharedPreferences | Cumulative unexpected restart count |
| `lastStartTimestamp` | `Long` | ✅ SharedPreferences | Last `onCreate()` time |
| `lastKillTimestamp` | `Long` | ✅ SharedPreferences | Last `onTaskRemoved()` time |

**DEGRADED Logic**: If `lastStartTimestamp - lastKillTimestamp < 5 minutes` AND
`restartCount > 0`, status is DEGRADED. Transitions to ACTIVE after 5 minutes
of stable operation.

---

## 5. SharedPreferences Keys

All Phase 1 persisted state lives in a single SharedPreferences file:

**File Name**: `capsule_overlay_prefs`
**Mode**: `MODE_PRIVATE`

| Key | Type | Default | Description |
|---|---|---|---|
| `bubble_x` | `Int` | `0` | Bubble X position |
| `bubble_y` | `Int` | `100` | Bubble Y position |
| `bubble_edge` | `String` | `"LEFT"` | Edge side enum name |
| `service_enabled` | `Boolean` | `false` | User's toggle state |
| `restart_count` | `Int` | `0` | Unexpected restart count |
| `last_start_ts` | `Long` | `0` | Last service start timestamp |
| `last_kill_ts` | `Long` | `0` | Last service kill timestamp |

---

## Phase 2 Migration Notes

When Phase 2 introduces Room:
- `CapturedContent` → `MemoryEvent` entity with auto-generated ID
- `text` → `rawText` column
- `sourcePackage` → `sourceAppPackage` column
- `timestamp` → `createdAt` column
- New columns: `summaryText`, `tags`, `metadataJson`
- SharedPreferences remain for overlay state (not migrated to Room)
