# Feature Specification: Core Capture Overlay

**Feature Branch**: `001-core-capture-overlay`
**Created**: 2026-04-15
**Status**: Draft
**Input**: Phase 1 of Capsule Phased Spec Kit — "The Catcher's Mitt"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Toggle Overlay Service On/Off (Priority: P1)

As a user, I open the Capsule app and flip a switch to activate the floating
bubble overlay. A persistent notification confirms the service is running. The
bubble appears over all other apps. Flipping the switch off dismisses the bubble
and stops the foreground service.

**Why this priority**: Without a running foreground service and visible bubble,
no other feature can function. This is the absolute foundation.

**Independent Test**: Install APK on a physical Android 13+ device, grant
overlay permission, toggle the switch, and confirm the bubble renders over
Chrome and the home screen.

**Acceptance Scenarios**:

1. **Given** the app is freshly installed and overlay permission has not been
   granted, **When** the user taps the toggle switch, **Then** the system
   settings screen for overlay permission opens automatically.
2. **Given** overlay permission is granted and the service is off, **When** the
   user taps the toggle switch, **Then** a foreground service starts, a
   persistent low-priority notification appears, and the floating bubble
   renders over the current app within 500 ms.
3. **Given** the service is running, **When** the user taps the toggle switch
   off, **Then** the bubble is removed, the notification is dismissed, and the
   foreground service stops.

---

### User Story 2 - Capture Clipboard via Bubble Tap (Priority: P1)

As a user, I copy text in any app (browser, messaging, notes). I tap the
floating Capsule bubble. The bubble briefly steals window focus, reads the
clipboard, and displays the captured text in an expanded Capture Sheet card
overlaying the current app.

**Why this priority**: Clipboard capture is the primary data ingestion path
for Capsule. Without it the app has no content to manage.

**Independent Test**: Copy text in Chrome, tap the bubble, verify the Capture
Sheet shows the exact copied text and the Android 13+ clipboard access toast
appears.

**Acceptance Scenarios**:

1. **Given** the bubble is visible and the user has copied text to the
   clipboard, **When** the user taps the bubble, **Then** the system clipboard
   is read, the Capture Sheet expands with an animation, and the captured text
   is displayed within the sheet body.
2. **Given** the clipboard is empty, **When** the user taps the bubble,
   **Then** the Capture Sheet shows a message indicating no content was found
   and offers to dismiss.
3. **Given** the clipboard contains non-text content (image URI), **When** the
   user taps the bubble, **Then** the system gracefully displays "Text content
   not found" rather than crashing.

---

### User Story 3 - Drag Bubble and Edge Snap (Priority: P2)

As a user, I long-press and drag the floating bubble to reposition it anywhere
on screen. When I release, the bubble snaps to the nearest screen edge. The
position persists across service restarts.

**Why this priority**: The bubble overlays other apps permanently. If the user
cannot move it, it will obstruct content and become unusable.

**Independent Test**: Drag the bubble from left edge to the right half of the
screen, release, and confirm it snaps to the right edge. Kill the service and
restart — bubble appears at the right edge.

**Acceptance Scenarios**:

1. **Given** the bubble is idle at the left screen edge, **When** the user
   long-presses and drags to the right half, **Then** the bubble follows the
   finger and on release animates to the right screen edge.
2. **Given** the bubble was repositioned to y=400 on the right edge, **When**
   the service is stopped and restarted, **Then** the bubble appears at the
   same y=400 position on the right edge.

---

### User Story 4 - Save and Discard Captured Content (Priority: P2)

As a user viewing captured text in the Capture Sheet, I tap "Save & Close" to
log the content (to Logcat in Phase 1) and collapse back to the bubble, or I
tap the discard icon to throw it away.

**Why this priority**: Completes the capture flow end-to-end. Without save/
discard, the sheet has no exit path and the user is stuck.

**Independent Test**: Tap bubble, capture text, tap "Save & Close", check
`adb logcat -s Capsule` for saved text. Repeat and tap discard — no log entry.

**Acceptance Scenarios**:

1. **Given** the Capture Sheet is showing captured text, **When** the user taps
   "Save & Close", **Then** the text is logged to Logcat with tag "Capsule",
   the sheet collapses with animation, and the bubble returns to its previous
   position.
2. **Given** the Capture Sheet is showing captured text, **When** the user taps
   the discard icon, **Then** the captured content is cleared, the sheet
   collapses, and no log entry is created.

---

### User Story 5 - Service Survives OEM Kills (Priority: P3)

As a user on a Samsung/Xiaomi/OnePlus device, I clear Capsule from recent apps
or the system aggressively kills background processes. The Capsule service
restarts automatically within a reasonable timeframe and the bubble reappears.

**Why this priority**: Reliability on real-world devices with aggressive battery
management. Without this, the overlay disappears unpredictably.

**Independent Test**: On a Samsung Galaxy device, swipe Capsule from recents.
Verify the service restarts and the bubble reappears within 10 seconds.

**Acceptance Scenarios**:

1. **Given** the service is running on a Samsung device, **When** the user
   clears Capsule from recents, **Then** the service restarts within 10
   seconds and the bubble reappears at its persisted position.
2. **Given** the device screen has been off for 5 minutes, **When** the user
   turns the screen back on, **Then** the bubble is still visible.
3. **Given** the service has been killed and restarted, **When** the user
   opens the Capsule app, **Then** the service health monitor shows the
   restart count and manufacturer-specific battery optimization guidance.

---

### User Story 6 - Battery Optimization Guidance (Priority: P3)

As a user on a device from an aggressive OEM (Samsung, Xiaomi, Huawei, OnePlus,
Oppo, Vivo, Realme), I see a card in the main app that tells me exactly which
battery settings to change so the overlay stays alive.

**Why this priority**: Users cannot be expected to know device-specific battery
optimization menus. This is the only defense against silent service death.

**Independent Test**: Install on a Xiaomi device. Open the app. Confirm the
battery guide card shows Xiaomi-specific steps and a link to MIUI battery
settings.

**Acceptance Scenarios**:

1. **Given** the app is running on a device from a known aggressive OEM,
   **When** the user opens the Capsule app, **Then** a card is displayed with
   step-by-step battery optimization instructions specific to that
   manufacturer.
2. **Given** the device manufacturer is not in the known aggressive list,
   **When** the user opens the app, **Then** no battery optimization card is
   shown.

---

### Edge Cases

- What happens when overlay permission is revoked while the service is running?
  The service MUST detect the revocation (via `BadTokenException` on
  `updateViewLayout`) and stop itself gracefully.
- What happens when the clipboard focus hack gets stuck in `REQUESTING_FOCUS`
  for over 500 ms? A timeout guard MUST force-restore `FLAG_NOT_FOCUSABLE` and
  return to the idle state to prevent the overlay from permanently stealing
  input focus.
- What happens when the user taps the bubble rapidly multiple times? The state
  machine MUST reject taps while not in the `IDLE` state, preventing concurrent
  clipboard reads.
- What happens when another app's overlay occludes the Capsule bubble? The user
  MUST still be able to drag the bubble to a visible location. No Z-ordering
  guarantees are made.
- What happens when the device has very limited RAM (4 GB) and system pressure
  kills the service? The `onTaskRemoved` callback MUST schedule a restart via
  `AlarmManager.setExactAndAllowWhileIdle`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST render a draggable floating bubble overlay via a
  foreground service using `TYPE_APPLICATION_OVERLAY`.
- **FR-002**: System MUST toggle the overlay service on/off from the main
  activity via a user-facing switch control.
- **FR-003**: System MUST check and request `SYSTEM_ALERT_WINDOW` permission
  before starting the overlay service.
- **FR-004**: System MUST read the system clipboard when the user taps the
  bubble, using a focus-stealing state machine that temporarily removes
  `FLAG_NOT_FOCUSABLE`, reads, and restores the flag.
- **FR-005**: System MUST display captured clipboard text in an expanded
  Capture Sheet overlay card with source attribution and timestamp.
- **FR-006**: System MUST provide "Save & Close" (logs to Logcat in Phase 1)
  and "Discard" actions on the Capture Sheet.
- **FR-007**: System MUST persist the bubble's screen position across service
  restarts.
- **FR-008**: System MUST snap the bubble to the nearest screen edge when the
  user ends a drag gesture.
- **FR-009**: System MUST display a persistent low-priority foreground
  notification while the overlay service is running.
- **FR-010**: System MUST re-schedule the service for restart via
  `AlarmManager` when killed by the OS or cleared from recents.
- **FR-011**: System MUST host Jetpack Compose UI inside the overlay by
  attaching a custom lifecycle owner, ViewModel store owner, and saved state
  registry owner to the ComposeView.
- **FR-012**: System MUST enforce a 500 ms timeout on the clipboard focus
  state machine, force-restoring `FLAG_NOT_FOCUSABLE` if the read does not
  complete.
- **FR-013**: System MUST detect the device manufacturer and present
  manufacturer-specific battery optimization instructions for known aggressive
  OEMs (Samsung, Xiaomi, Huawei, OnePlus, Oppo, Vivo, Realme).
- **FR-014**: System MUST track service health (active, degraded, killed) and
  restart count, and expose this state in the main activity.
- **FR-015**: System MUST NOT read the clipboard without an explicit user tap
  on the bubble. Background polling and passive reading are prohibited.
- **FR-016**: System MUST request the `POST_NOTIFICATIONS` runtime permission
  on Android 13+ before starting the foreground service.
- **FR-017**: System MUST declare `foregroundServiceType="dataSync"` for
  Android 14+ forward compatibility.

### Key Entities

- **BubbleState**: The position (x, y), expansion state (collapsed bubble vs.
  expanded sheet), drag state, and service health indicator for the overlay.
- **CapturedContent**: The text extracted from the clipboard along with the
  source app package (if resolvable) and capture timestamp.
- **ClipboardFocusState**: The four-state enum driving the focus hack state
  machine: IDLE → REQUESTING_FOCUS → READING_CLIPBOARD → RESTORING_FLAGS.
- **ServiceHealth**: Enum tracking whether the foreground service is ACTIVE,
  DEGRADED (restarted recently), or KILLED.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The bubble overlay renders on screen within 500 ms of the user
  toggling the service on.
- **SC-002**: Clipboard text is captured and displayed in the Capture Sheet
  within 300 ms of a bubble tap (excluding focus acquisition delay).
- **SC-003**: The bubble edge-snap animation completes within 200 ms of drag
  release.
- **SC-004**: The foreground service survives a recents-clear on Samsung Galaxy
  devices and restarts within 10 seconds.
- **SC-005**: The service persists through a 5-minute screen-off period without
  being killed.
- **SC-006**: The bubble position is restored to the exact saved coordinates
  after a service restart.
- **SC-007**: The clipboard focus state machine returns to IDLE within 500 ms
  of any bubble tap, regardless of success or failure.
- **SC-008**: All 13 items in the Phase 1 Verification Checklist pass on a
  physical Android 13+ device.

## Assumptions

- The target device runs Android 13 (API 33) or higher. Devices below API 33
  are out of scope.
- The user grants `SYSTEM_ALERT_WINDOW` permission manually through system
  settings (no ADB-granted shortcuts).
- Phase 1 does not persist captured data to a database — "Save" writes to
  Logcat only. Persistence is deferred to Phase 2.
- No dependency injection framework is used in Phase 1. All wiring is manual
  construction.
- The Capture Sheet "Tag" and "Summarize" buttons are visible but disabled
  (grayed out). They become functional in Phase 2 and Phase 4 respectively.
- Emulator testing is acceptable for layout verification, but all 13
  verification checklist items MUST pass on a physical device.
- `POST_NOTIFICATIONS` permission is requested at service start time; if denied,
  the service still functions but the notification channel falls back to system
  defaults.
