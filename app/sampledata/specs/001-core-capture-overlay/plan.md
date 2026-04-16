# Implementation Plan: Core Capture Overlay

**Branch**: `001-core-capture-overlay` | **Date**: 2026-04-15 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-core-capture-overlay/spec.md`

## Summary

Implement a persistent floating bubble overlay backed by a foreground service
(`specialUse` type) that captures clipboard text on explicit user tap. The
overlay renders Jetpack Compose UI inside a WindowManager-managed ComposeView
with a custom OverlayLifecycleOwner. A 4-state clipboard focus state machine
handles the FLAG_NOT_FOCUSABLE toggle required to read the system clipboard from
an overlay context. The service survives OEM kills via START_STICKY +
AlarmManager restart + SharedPreferences state recovery. Phase 1 "save" writes
to Logcat only — no database persistence.

## Technical Context

**Language/Version**: Kotlin 2.x (latest stable)
**Primary Dependencies**: Jetpack Compose BOM 2025+, Material 3, lifecycle-service 2.8+, lifecycle-viewmodel-compose, activity-compose
**Storage**: SharedPreferences (bubble position, service state) — Room deferred to Phase 2
**Testing**: JUnit 5, Compose UI testing, manual physical device verification
**Target Platform**: Android 13+ (minSdk 33, targetSdk 35)
**Project Type**: mobile-app (Android)
**Performance Goals**: Bubble render <500ms, clipboard read <300ms, edge-snap animation <200ms
**Constraints**: Offline-only (no network), <50MB APK, 4GB RAM baseline device support
**Scale/Scope**: 1 foreground service, 1 overlay window, 1 toggle activity, ~17 source files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Phase 1 Compliance | Status |
|---|---|---|---|
| I | Privacy-First, On-Device by Default | All data stays on-device. "Save" writes to Logcat only. No network calls. | ✅ PASS |
| II | Phased Gate Execution | Phase 1 only — no Room, no AI, no Inbox UI. Phase 2 code forbidden. | ✅ PASS |
| III | OEM-Hostile Survival Design | START_STICKY + AlarmManager restart + SharedPreferences recovery + OEM battery guides for 7 manufacturers. | ✅ PASS |
| IV | Policy as Architecture | `specialUse` FGS type with Play justification. SYSTEM_ALERT_WINDOW via Settings intent. No AccessibilityService or VpnService. Clipboard read only on explicit tap (Principle VII). | ✅ PASS |
| V | Graceful Degradation Across Hardware Tiers | Phase 1 has no AI features. Overlay + clipboard read works on 4GB baseline devices. Tier classification deferred to Phase 4. | ✅ PASS |
| VI | Compose-Over-Overlay Lifecycle Discipline | Custom OverlayLifecycleOwner implementing LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner. Lifecycle events synced with overlay add/remove. | ✅ PASS |
| VII | Explicit Capture Only | Clipboard read gated behind user tap on bubble. No background polling. Android 12+ clipboard toast expected and not suppressed. | ✅ PASS |

**Post-Research Amendment**: Research recommends changing FGS type from `dataSync` to `specialUse` (see [research.md](research.md) §1). `dataSync` has a 6-hour timeout on Android 15 that is incompatible with an always-on overlay. This change strengthens Principle IV compliance.

## Project Structure

### Documentation (this feature)

```text
specs/001-core-capture-overlay/
├── plan.md              # This file
├── research.md          # Phase 0 research output
├── data-model.md        # Phase 1 data model
├── quickstart.md        # Phase 1 build & run guide
├── contracts/           # Phase 1 interface contracts
│   └── overlay-service-contract.md
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   └── java/com/capsule/app/
    │       ├── CapsuleApplication.kt
    │       │
    │       ├── service/
    │       │   ├── CapsuleOverlayService.kt       # LifecycleService + WindowManager
    │       │   ├── OverlayLifecycleOwner.kt        # Custom lifecycle/VM/savedstate owner
    │       │   ├── ClipboardFocusStateMachine.kt   # 4-state focus hack
    │       │   ├── ForegroundNotificationManager.kt # Notification channel + builder
    │       │   └── ServiceHealthMonitor.kt          # Active/Degraded/Killed tracking
    │       │
    │       ├── overlay/
    │       │   ├── BubbleUI.kt                     # Draggable FAB composable
    │       │   ├── CaptureSheetUI.kt               # Expanded card composable
    │       │   ├── OverlayViewModel.kt             # StateFlow for bubble + sheet
    │       │   └── BubbleState.kt                  # Position, expansion, drag state
    │       │
    │       ├── permission/
    │       │   ├── OverlayPermissionHelper.kt      # SYSTEM_ALERT_WINDOW check/request
    │       │   └── BatteryOptimizationGuide.kt     # OEM-specific battery instructions
    │       │
    │       └── ui/
    │           ├── MainActivity.kt                 # Toggle switch + service health
    │           └── theme/
    │               ├── CapsuleTheme.kt
    │               ├── Color.kt
    │               └── Type.kt
    │
    └── test/
        └── java/com/capsule/app/
            └── service/
                └── ClipboardFocusStateMachineTest.kt

build.gradle.kts                                     # Root build file
gradle/
└── libs.versions.toml                               # Version catalog
settings.gradle.kts
```

**Structure Decision**: Android single-module app structure matching the Phased
Spec Kit file layout. Phase 1 creates files in `service/`, `overlay/`,
`permission/`, and `ui/` packages only. `data/`, `ai/`, `chat/` packages are
Phase 2+ and MUST NOT exist in Phase 1.

## Complexity Tracking

> No violations detected. All Phase 1 files serve a single, necessary purpose.
