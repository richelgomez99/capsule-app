# Tasks: Core Capture Overlay

**Input**: Design documents from `/specs/001-core-capture-overlay/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅
**Status**: Implementation complete as of 2026-04-17. All Phase 1–9 tasks below are checked off. See [Clarifications 2026-04-17](spec.md#clarifications) in spec.md for the three spec-vs-code reconciliations that closed the loop (FR-012 timeout raise, FR-017 `specialUse`, second-tap-reset invariant).

**Tests**: JVM unit tests for `ClipboardFocusState` transition contract + the `resetToIdle()` existence invariant live in `app/src/test/java/com/capsule/app/service/ClipboardFocusStateMachineTest.kt`. Full behavioral verification (flag manipulation, clipboard read races, service kill/restart) is covered by the manual Phase 1 Verification Checklist in quickstart.md on a physical Android 13+ device.

**Organization**: Tasks grouped by user story. 6 user stories (US1-US6) mapped from spec.md priorities (P1, P1, P2, P2, P3, P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US6)
- All paths relative to repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Android project initialization, Gradle configuration, manifest, theme, and Application class

- [x] T001 Create root `build.gradle.kts` with Android Gradle Plugin and Kotlin plugin configuration
- [x] T002 Create `settings.gradle.kts` with project name "Capsule" and app module include
- [x] T003 Create `gradle/libs.versions.toml` with version catalog: Kotlin 2.x, Compose BOM 2025+, Material 3, lifecycle-service 2.8+, lifecycle-viewmodel-compose, activity-compose, JUnit 5
- [x] T004 Create `app/build.gradle.kts` with minSdk 33, targetSdk 35, compose enabled, all dependencies from version catalog
- [x] T005 Create `app/src/main/AndroidManifest.xml` with permissions (SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS), Application class, MainActivity, CapsuleOverlayService with `foregroundServiceType="specialUse"` and PROPERTY_SPECIAL_USE_FGS_SUBTYPE, RestartReceiver
- [x] T006 [P] Create `app/src/main/java/com/capsule/app/CapsuleApplication.kt` — empty Application subclass for future initialization
- [x] T007 [P] Create `app/src/main/java/com/capsule/app/ui/theme/Color.kt` with Material 3 color definitions
- [x] T008 [P] Create `app/src/main/java/com/capsule/app/ui/theme/Type.kt` with Material 3 typography
- [x] T009 [P] Create `app/src/main/java/com/capsule/app/ui/theme/CapsuleTheme.kt` with dynamic color support (Android 12+) and dark/light theme
- [x] T010 Create placeholder notification icon `app/src/main/res/drawable/ic_capsule_notification.xml` (simple vector drawable)
- [x] T011 Verify project compiles: `./gradlew assembleDebug` must succeed with zero errors

**Checkpoint**: Empty app compiles and installs on physical device. No UI yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data classes, lifecycle owner, and notification manager that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T012 [P] Create `app/src/main/java/com/capsule/app/overlay/BubbleState.kt` — BubbleState data class (x, y, expansion, isDragging, edgeSide), ExpansionState enum, EdgeSide enum, CapturedContent data class (text, sourcePackage, timestamp, isSensitive) per data-model.md §1-§2
- [x] T013 [P] Create `app/src/main/java/com/capsule/app/service/OverlayLifecycleOwner.kt` — implements LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner with LifecycleRegistry, ViewModelStore, SavedStateRegistryController; expose onCreate(), onResume(), onPause(), onDestroy() methods that drive lifecycle events per research.md §4
- [x] T014 [P] Create `app/src/main/java/com/capsule/app/service/ForegroundNotificationManager.kt` — create notification channel `capsule_overlay` (IMPORTANCE_LOW, no sound/vibrate/badge), build foreground notification (id=1, "Capsule Active", Stop action sending STOP_OVERLAY intent) per contracts §2
- [x] T015 [P] Create `app/src/main/java/com/capsule/app/permission/OverlayPermissionHelper.kt` — check `Settings.canDrawOverlays()`, launch `ACTION_MANAGE_OVERLAY_PERMISSION` intent with package URI, check `POST_NOTIFICATIONS` permission and request via ActivityResultLauncher per contracts §5
- [x] T016 Verify foundational classes compile: `./gradlew assembleDebug` must succeed

**Checkpoint**: Foundation ready — data classes, lifecycle owner, notification manager, and permission helper all compile. User story implementation can begin.

---

## Phase 3: User Story 1 — Toggle Overlay Service On/Off (Priority: P1) 🎯 MVP

**Goal**: User toggles switch in MainActivity → foreground service starts → bubble appears over all apps. Toggle off → bubble removed → service stops.

**Independent Test**: Install APK, grant overlay permission, toggle switch, confirm bubble renders over Chrome and home screen.

### Implementation for User Story 1

- [x] T017 Create `app/src/main/java/com/capsule/app/overlay/OverlayViewModel.kt` — ViewModel with `MutableStateFlow<BubbleState>` (initially COLLAPSED at default position); expose `bubbleState: StateFlow<BubbleState>` per contracts §4
- [x] T018 Create `app/src/main/java/com/capsule/app/overlay/BubbleUI.kt` — Composable function rendering a Material 3 FAB at BubbleState coordinates; accept `onTap: () -> Unit` callback; no drag logic yet (US3)
- [x] T019 Create `app/src/main/java/com/capsule/app/service/CapsuleOverlayService.kt` — extend LifecycleService; in `onCreate()`: create OverlayLifecycleOwner, create ComposeView, attach lifecycle/viewmodel/savedstate tree owners, set BubbleUI content; add overlay to WindowManager with TYPE_APPLICATION_OVERLAY + FLAG_NOT_FOCUSABLE + FLAG_LAYOUT_NO_LIMITS per contracts §3; call `startForeground()` with ForegroundNotificationManager notification; handle START_OVERLAY/STOP_OVERLAY/RESTART_OVERLAY intent actions per contracts §1; `onStartCommand()` returns START_STICKY; remove overlay from WindowManager in `onDestroy()`; drive OverlayLifecycleOwner events (ON_CREATE→ON_RESUME on add, ON_DESTROY on remove)
- [x] T020 Create `app/src/main/java/com/capsule/app/ui/MainActivity.kt` — Compose Activity with Material 3 Scaffold; single Switch toggle that checks overlay permission via OverlayPermissionHelper, requests POST_NOTIFICATIONS if needed, then starts/stops CapsuleOverlayService via explicit intents with START_OVERLAY/STOP_OVERLAY actions; persist toggle state to SharedPreferences `capsule_overlay_prefs` key `service_enabled`; register ActivityResult for overlay permission settings return
- [x] T021 Verify on physical device: toggle ON → bubble visible over Chrome; toggle OFF → bubble gone; notification appears/disappears with service

**Checkpoint**: US1 complete — overlay toggles on/off, foreground service runs with notification, bubble renders over all apps.

---

## Phase 4: User Story 2 — Capture Clipboard via Bubble Tap (Priority: P1)

**Goal**: User copies text in any app → taps bubble → focus hack reads clipboard → Capture Sheet shows captured text.

**Independent Test**: Copy text in Chrome, tap bubble, verify Capture Sheet shows exact copied text and ClipboardFocus state transitions log correctly.

### Implementation for User Story 2

- [x] T022 Create `app/src/main/java/com/capsule/app/service/ClipboardFocusStateMachine.kt` — ClipboardFocusState enum (IDLE, REQUESTING_FOCUS, READING_CLIPBOARD, RESTORING_FLAGS) per data-model.md §3; class takes WindowManager and LayoutParams; `requestClipboardRead()` transitions IDLE→REQUESTING_FOCUS (removes FLAG_NOT_FOCUSABLE, calls updateViewLayout), then READING_CLIPBOARD (calls ClipboardManager.getPrimaryClipDescription() to check MIME, then getPrimaryClip()), then RESTORING_FLAGS→IDLE (restores FLAG_NOT_FOCUSABLE); 500ms timeout via Handler.postDelayed from REQUESTING_FOCUS that force-restores flags; reject calls when not IDLE; check EXTRA_IS_SENSITIVE flag; expose StateFlow<ClipboardFocusState> for observation
- [x] T023 Create `app/src/main/java/com/capsule/app/overlay/CaptureSheetUI.kt` — Composable card that shows CapturedContent.text, sourcePackage, timestamp, and isSensitive indicator; "Save & Close" and "Discard" buttons (Save disabled label "Tag" and "Summarize" buttons grayed out per spec assumptions); expand/collapse animation synced with ExpansionState
- [x] T024 Update `app/src/main/java/com/capsule/app/overlay/OverlayViewModel.kt` — add `MutableStateFlow<CapturedContent?>` and `MutableStateFlow<ClipboardFocusState>`; implement `onBubbleTap()` that triggers clipboard read when COLLAPSED (via callback to service); implement `onClipboardReadResult()` that sets capturedContent and expansion to EXPANDED; implement `onFocusStateChanged()` to update clipboardFocusState; handle empty clipboard (null content → show "Nothing to capture") and non-text MIME ("Text only in Phase 1")
- [x] T025 Update `app/src/main/java/com/capsule/app/service/CapsuleOverlayService.kt` — instantiate ClipboardFocusStateMachine with WindowManager and overlay LayoutParams; wire bubble tap callback to state machine's `requestClipboardRead()`; pass clipboard read results to OverlayViewModel; update ComposeView content to include CaptureSheetUI when expanded
- [x] T026 Create `app/src/test/java/com/capsule/app/service/ClipboardFocusStateMachineTest.kt` — JUnit 5 tests: verify IDLE→REQUESTING_FOCUS→READING_CLIPBOARD→RESTORING_FLAGS→IDLE happy path; verify tap rejected when not IDLE; verify 500ms timeout forces RESTORING_FLAGS→IDLE; verify state never gets stuck
- [x] T027 Verify on physical device: copy text in Chrome → tap bubble → Capture Sheet shows text → ClipboardFocus Logcat shows all 4 state transitions → underlying app regains focus after capture

**Checkpoint**: US1+US2 complete — full clipboard capture flow works. This is the core value proposition.

---

## Phase 5: User Story 3 — Drag Bubble and Edge Snap (Priority: P2)

**Goal**: User long-presses and drags bubble anywhere → on release, bubble animates to nearest screen edge → position persists across restarts.

**Independent Test**: Drag bubble to right half, release, confirm right-edge snap. Kill service, restart — bubble at same position.

### Implementation for User Story 3

- [x] T028 Update `app/src/main/java/com/capsule/app/overlay/BubbleUI.kt` — add `pointerInput` modifier for drag gestures: detect long-press start → emit `onDragStart()`, track drag deltas → emit `onDrag(dx, dy)`, detect release → emit `onDragEnd()`; prevent bubble tap during active drag
- [x] T029 Update `app/src/main/java/com/capsule/app/overlay/OverlayViewModel.kt` — implement `onBubbleDragStart()` (set isDragging=true), `onBubbleDrag(dx, dy)` (update x,y with screen bounds clamping accounting for display cutouts), `onBubbleDragEnd()` (calculate nearest edge, animate to edge x-position via `animateIntAsState` or coroutine-based animation <200ms, set edgeSide, set isDragging=false, persist x/y/edgeSide to SharedPreferences `capsule_overlay_prefs`)
- [x] T030 Update `app/src/main/java/com/capsule/app/service/CapsuleOverlayService.kt` — observe OverlayViewModel.bubbleState changes and call `WindowManager.updateViewLayout()` to move the overlay window to match new x,y coordinates; on `onCreate()` restore bubble position from SharedPreferences before adding overlay to WindowManager
- [x] T031 Verify on physical device: drag bubble left→right, snaps to right edge <200ms; kill service via recents, restart, bubble at saved right-edge position

**Checkpoint**: US1+US2+US3 complete — bubble is repositionable and remembers position.

---

## Phase 6: User Story 4 — Save and Discard Captured Content (Priority: P2)

**Goal**: User taps "Save & Close" → content logged to Logcat → sheet collapses. User taps "Discard" → content cleared → sheet collapses. No log entry.

**Independent Test**: Capture text, tap Save, check `adb logcat -s CapsuleCapture`. Capture again, tap Discard — no new log entry.

### Implementation for User Story 4

- [x] T032 Update `app/src/main/java/com/capsule/app/overlay/OverlayViewModel.kt` — implement `onSaveCapture()`: log CapturedContent (text, sourcePackage, timestamp, isSensitive) to Logcat with tag `CapsuleCapture` at DEBUG level, then clear capturedContent and set expansion to COLLAPSED; implement `onDiscardCapture()`: clear capturedContent and set expansion to COLLAPSED without logging
- [x] T033 Update `app/src/main/java/com/capsule/app/overlay/CaptureSheetUI.kt` — wire "Save & Close" button to OverlayViewModel.onSaveCapture(); wire "Discard" button/icon to OverlayViewModel.onDiscardCapture(); add collapse animation on state change to COLLAPSED; return bubble to previous position after collapse
- [x] T034 Verify on physical device: capture text → Save → `adb logcat -s CapsuleCapture:D` shows entry; capture text → Discard → no new log entry; sheet collapses smoothly in both cases

**Checkpoint**: US1+US2+US3+US4 complete — full capture-save-discard loop works end-to-end.

---

## Phase 7: User Story 5 — Service Survives OEM Kills (Priority: P3)

**Goal**: Service restarts automatically after being killed by OEM/recents-clear. Bubble reappears at persisted position. Health monitor tracks restarts.

**Independent Test**: On Samsung Galaxy, swipe Capsule from recents. Service restarts within 10 seconds, bubble reappears.

### Implementation for User Story 5

- [x] T035 Create `app/src/main/java/com/capsule/app/service/ServiceHealthMonitor.kt` — ServiceHealthStatus enum (ACTIVE, DEGRADED, KILLED) and ServiceHealth data class per data-model.md §4; read/write restart_count, last_start_ts, last_kill_ts to SharedPreferences `capsule_overlay_prefs`; expose `StateFlow<ServiceHealth>`; implement DEGRADED logic (start within 5min of kill + restartCount > 0 → DEGRADED, transitions to ACTIVE after 5min stable via coroutine delay)
- [x] T036 Update `app/src/main/java/com/capsule/app/service/CapsuleOverlayService.kt` — integrate ServiceHealthMonitor: call `onServiceStarted()` in `onCreate()` (increment restartCount if last_kill_ts recent, update last_start_ts, set ACTIVE or DEGRADED); implement `onTaskRemoved()`: update last_kill_ts, schedule AlarmManager.setExactAndAllowWhileIdle with RESTART_OVERLAY PendingIntent targeting RestartReceiver, record kill timestamp
- [x] T037 Create `app/src/main/java/com/capsule/app/service/RestartReceiver.kt` (BroadcastReceiver) — on receive RESTART_OVERLAY action: check SharedPreferences `service_enabled` is true and `Settings.canDrawOverlays()`, then start CapsuleOverlayService via `startForegroundService()` with RESTART_OVERLAY action; register in AndroidManifest
- [x] T038 Update `app/src/main/java/com/capsule/app/ui/MainActivity.kt` — observe ServiceHealthMonitor state (via SharedPreferences or broadcast); display service health indicator (ACTIVE=green, DEGRADED=amber, KILLED=red) and restart count below the toggle switch
- [x] T039 Verify on physical device: enable overlay → swipe from recents → bubble reappears within 10s → open app → health shows DEGRADED with restartCount=1 → wait 5min → status transitions to ACTIVE

**Checkpoint**: US1-US5 complete — service is resilient to OEM kills.

---

## Phase 8: User Story 6 — Battery Optimization Guidance (Priority: P3)

**Goal**: App detects OEM and shows manufacturer-specific battery optimization instructions. No card shown for non-aggressive OEMs.

**Independent Test**: Install on Xiaomi device → battery guide card shows Xiaomi-specific MIUI steps. Install on Pixel → no card.

### Implementation for User Story 6

- [x] T040 Create `app/src/main/java/com/capsule/app/permission/BatteryOptimizationGuide.kt` — detect manufacturer via `Build.MANUFACTURER` (case-insensitive match); define guidance data for 7 OEMs (Samsung, Xiaomi, Huawei, OnePlus, Oppo, Vivo, Realme) with: manufacturer name, step-by-step instructions string, optional settings intent action; expose `fun getGuide(): BatteryGuideInfo?` returning null for unknown/non-aggressive OEMs; include `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent for system-level whitelist prompt
- [x] T041 Update `app/src/main/java/com/capsule/app/ui/MainActivity.kt` — below the toggle switch and health indicator, conditionally show a Material 3 Card with BatteryOptimizationGuide content when `getGuide()` returns non-null; card shows manufacturer name, step-by-step instructions, and a button to open battery settings intent if available
- [x] T042 Verify on physical device: confirm OEM-specific card appears on Samsung/Xiaomi/etc.; confirm no card on Pixel/stock Android

**Checkpoint**: All 6 user stories complete.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Edge case handling, error conditions, and final validation

- [x] T043 Update `app/src/main/java/com/capsule/app/service/CapsuleOverlayService.kt` — handle overlay permission revocation: catch `BadTokenException` on `updateViewLayout()`, gracefully stop service and clean up OverlayLifecycleOwner
- [x] T044 Update `app/src/main/java/com/capsule/app/service/CapsuleOverlayService.kt` — handle `ForegroundServiceStartNotAllowedException` (Android 15): catch in service start path, log error, schedule AlarmManager retry, report KILLED status via ServiceHealthMonitor
- [x] T045 [P] Add `app/proguard-rules.pro` with keep rules for Compose, lifecycle, and service classes
- [x] T046 Run full quickstart.md validation on physical device: execute all 6 verification sections (A through F) from `specs/001-core-capture-overlay/quickstart.md` — all must pass
- [x] T047 Verify `./gradlew assembleDebug` produces APK <50MB per plan.md constraints

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **US1 Toggle (Phase 3)**: Depends on Foundational — first MVP milestone
- **US2 Clipboard (Phase 4)**: Depends on US1 (needs running service + bubble)
- **US3 Drag (Phase 5)**: Depends on US1 (needs visible bubble) — can parallel with US2
- **US4 Save/Discard (Phase 6)**: Depends on US2 (needs capture sheet with content)
- **US5 OEM Survival (Phase 7)**: Depends on US1 (needs running service) — can parallel with US2-US4
- **US6 Battery Guide (Phase 8)**: Depends on US1 (needs MainActivity) — can parallel with US2-US5
- **Polish (Phase 9)**: Depends on all user stories being complete

### User Story Dependencies

```
Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1: Toggle) ─┬─→ Phase 4 (US2: Clipboard) → Phase 6 (US4: Save/Discard)
                                                                    ├─→ Phase 5 (US3: Drag)
                                                                    ├─→ Phase 7 (US5: OEM Survival)
                                                                    └─→ Phase 8 (US6: Battery Guide)
                                                                                                      ─→ Phase 9 (Polish)
```

### Within Each User Story

- Models/data classes before services
- Services before UI composables
- Core implementation before integration
- Verify on physical device before moving to next story

### Parallel Opportunities

- **Phase 1**: T006, T007, T008, T009 can run in parallel (independent files)
- **Phase 2**: T012, T013, T014, T015 can run in parallel (independent files)
- **After US1**: US3 (Drag), US5 (OEM Survival), and US6 (Battery Guide) can all start in parallel
- **After US2**: US4 (Save/Discard) can start
- **Phase 9**: T045 can run in parallel with T043/T044

---

## Parallel Example: After US1 Complete

```text
# These can proceed simultaneously after Phase 3 (US1) is done:

Stream A (Critical Path):
  Phase 4: US2 Clipboard (T022-T027) → Phase 6: US4 Save/Discard (T032-T034)

Stream B:
  Phase 5: US3 Drag (T028-T031)

Stream C:
  Phase 7: US5 OEM Survival (T035-T039)

Stream D:
  Phase 8: US6 Battery Guide (T040-T042)
```

---

## Implementation Strategy

### MVP First (US1 + US2 Only)

1. Complete Phase 1: Setup (T001-T011)
2. Complete Phase 2: Foundational (T012-T016)
3. Complete Phase 3: US1 Toggle (T017-T021)
4. **STOP and VALIDATE**: Bubble appears/disappears on toggle
5. Complete Phase 4: US2 Clipboard (T022-T027)
6. **STOP and VALIDATE**: Clipboard capture works — this is the core value proposition
7. Deploy MVP if ready

### Incremental Delivery

1. Setup + Foundational → App compiles and installs
2. US1 → Overlay toggles on/off (minimal viable overlay)
3. US2 → Clipboard capture works (core feature complete)
4. US3 → Bubble is moveable (UX improvement)
5. US4 → Save/Discard loop complete (end-to-end flow)
6. US5 → Service survives kills (reliability)
7. US6 → Battery guide shown (user education)
8. Polish → Edge cases handled, quickstart validation passes

### Phase 1 Verification Gate

After ALL tasks complete, execute the full Phase 1 Verification Checklist from quickstart.md on a **physical Android 13+ device**. All items must pass before Phase 2 (Persistence) can begin per Constitution Principle II.

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [USn] label maps task to specific user story for traceability
- All file paths use Android convention: `app/src/main/java/com/capsule/app/`
- Phase 1 "Save" = Logcat only. NO Room, NO database, NO AI — Constitution Principle II
- `specialUse` FGS type per research.md decision (NOT `dataSync`)
- Spec FR-017 says `dataSync` but constitution v1.0.1 and research.md override to `specialUse`
- Commit after each task or logical group following Conventional Commits
