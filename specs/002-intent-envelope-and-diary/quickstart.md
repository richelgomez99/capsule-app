# Quickstart: Intent Envelope and Diary

**Feature Branch**: `002-intent-envelope-and-diary`
**Audience**: a developer (you, an Orbit contributor, or an auditor)
who wants to bring up the v1 system on a clean Pixel and exercise the
core loop end to end.

This document is both a bring-up guide and a mental model of what
"working" means for Orbit v1.

---

## 1. Prerequisites

**Hardware**
- Pixel 8, 8 Pro, 9 series, or any Android 14+ device with AICore /
  Gemini Nano available. (Nano is optional — the app gracefully
  degrades — but several flows are only fully observable with Nano on.)
- ≥ 6 GB RAM.

**Host**
- macOS or Linux.
- Android Studio Ladybug+ (or Koala Feature Drop).
- JDK 17.
- `adb` in `PATH`.

**Repo state**
- Branch: `002-intent-envelope-and-diary` (or `main` with
  `.specify/feature.json` pointing to this feature).
- Gradle sync OK.
- Device connected in "File transfer" mode, developer options on,
  USB debugging on.

---

## 2. First run

```bash
# From repo root
./gradlew :app:installDebug
adb shell am start -n com.capsule.app/.onboarding.OnboardingActivity
```

The onboarding flow asks for, in order:

1. **Notifications** — foreground service notification.
2. **Display over other apps** — overlay bubble.
3. **Usage access** — foreground app category (launched via the
   Settings deep-link; manual grant, one-tap back).
4. **Physical activity** — Activity Recognition transitions.

Accept all four for the "full" experience. Decline any of (3) or (4)
to verify graceful degradation: envelopes will still be created, but
without `appCategory` / `activityState` signals.

After onboarding, the capture foreground service is running in the
`:capture` process and the bubble is visible. Verify:

```bash
adb shell ps -A | grep com.capsule.app
# Expect up to 4 processes: :capture, :ml, :net, :ui (spawned lazily)
```

Not all processes are alive at all times. `:net` spawns on first
continuation, `:ui` spawns on first Diary launch, `:ml` spawns on
first seal.

---

## 3. The golden path: clipboard URL capture

Goal: verify the full seal → continuation → Diary pipeline.

1. Plug into Wi-Fi (unmetered).
2. Put the phone on the charger (so `RequiresCharging` constraint
   satisfies).
3. In any app (Gmail, Chrome, Signal), copy a URL:
   `https://www.nytimes.com/2026/03/11/technology/android-agents.html`
4. The bubble should animate (clipboard focus state machine
   `CLIPBOARD_RECEIVED` → `AWAITING_INTENT`).
5. Tap the bubble. A 4-chip row appears:
   `[Want it]  [Reference]  [For someone]  [Interesting]`
6. Tap **Reference**.
7. The chip row collapses with a 10-second undo toast.
8. After ~30 seconds:
   - Pull notifications, you'll see nothing (Orbit does not notify for
     continuations in v1).
   - Open Orbit (tap app icon). Diary opens on **today**.
   - The just-captured envelope shows a card with:
     `from ChromeOS · WALKING · 14:03 · Reference`
     with the title + 2–3 sentence Nano summary.

Verify with audit:

- Settings → "What Orbit did today".
- Expect today's groups:
  - 1 capture.
  - 1 enrichment (succeeded).
  - 1 network fetch to `nytimes.com`.
  - 1 Nano summary generated.

---

## 4. Alternate paths to verify

### 4.1 Silent-wrap

- Copy plain text with no URL and no ambiguity (e.g. a recipe line
  "1 cup flour, 2 eggs, pinch of salt").
- Orbit should classify as **Reference** with high confidence,
  create the envelope silently (no chip row), show a 2-second
  "Saved as Reference" toast.
- Verify in Diary under today.

### 4.2 Ambiguous chip-row auto-dismiss

- Copy something genuinely ambiguous (e.g. a phone number or a short
  codename).
- The chip row appears.
- Do nothing for 2 seconds.
- Row auto-dismisses; the envelope is sealed with `intent = AMBIGUOUS`.
- Verify card in Diary shows "Needs intent" hint and tapping it
  re-opens the 4-chip picker via `reassignIntent`.

### 4.3 Screenshot capture

- Take a screenshot of a tweet or article page.
- No chip row fires (screenshots are silent-by-default in v1).
- Envelope is created with `contentType = IMAGE`, `intent = AMBIGUOUS`
  pending an OCR-based reassignment heuristic.
- After ~30s on charger/unmetered, OCR runs; any URLs found are
  hydrated like in §3.
- Verify Diary card shows OCR'd title preview, and — if URLs found —
  a nested "linked article" block.

### 4.4 Nano unavailable

On a device without AICore:

- Same capture as §3.
- Diary card shows title + domain only. No summary.
- Settings → "What Orbit did today" shows 0 Nano summaries for the
  day. This is expected and documented.

### 4.5 Privacy kill-switch

- Settings → "Pause continuations".
- Perform a capture from §3.
- Verify: envelope created, but Diary card stays "bare" (title only
  if Nano silent-summary was able to run on the local text — no
  network fetch).
- Audit log: `PRIVACY_PAUSED` entry, no `NETWORK_FETCH` entries after
  the toggle.
- Toggle back → existing failed continuation is re-enqueued on next
  WorkManager tick.

### 4.6 Undo

- Copy a URL.
- Tap **Reference**.
- Within 10 seconds, tap "Undo" on the toast.
- Verify Diary today **does not** include the envelope, and the
  audit log contains `ENVELOPE_CREATED` immediately followed by an
  `envelope_undone` entry.

### 4.7 Intent reassignment

- Copy a URL, label **Interesting**.
- Open Diary, tap the envelope card, tap "Change intent" → "Want it".
- Verify `intentHistoryJson` has two entries and audit log shows
  `INTENT_SUPERSEDED`.

### 4.8 Envelope detail screen

- Open Diary and tap a capture card (anywhere outside the intent pill,
  the delete icon, or the "Tap to retry" row).
- The detail screen opens with:
  - TopAppBar back arrow + overflow menu
    (Archive / Delete / Open original URL / Copy text / Share).
  - Full-width intent picker (4 chips, current intent highlighted).
  - `from {app} · {activity} · {absolute time}` subtitle.
  - IMAGE thumbnail (if capture is an image).
  - Full hydrated title and summary (no maxLines clamp).
  - Domain chip → tapping opens the canonical URL in the browser.
  - Original captured text in a monospace-styled selectable block.
  - **Intent history** list, oldest-first, one row per entry in
    `intentHistoryJson` with `intent · source · timestamp`.
  - **Audit trail** list (hidden if empty) — per-envelope rows from
    `IAuditLog.entriesForEnvelope(envelopeId)`.
- Tap a chip → intent reassigns silently; the screen stays open and
  re-loads.
- Open the overflow menu → tap **Archive** → screen closes; the card
  no longer appears in Diary today.
- Re-open a different card → tap **Delete** → confirm in the dialog →
  screen closes; the capture moves to trash (Settings → Trash).

---

## 5. How to inspect local state

Orbit never exposes the DB. For dev builds you can:

```bash
# Dump counters via debug broadcast (dev build only)
adb shell am broadcast -a com.capsule.app.DEBUG_DUMP

# Pull an export bundle (user-initiated in Settings → Export my data)
adb pull /sdcard/Download/Orbit-Export-<timestamp>/ ./out/
```

To inspect raw Room/SQLCipher (never do this on a release build):

```bash
# Requires the device to have the debug key in keystore, won't work
# on a production device/build. Rotate if used.
adb shell "run-as com.capsule.app cat databases/orbit.db" > orbit.db
# Then open with the sqlcipher CLI using the wrapped key material
# obtained via a debug-only activity.
```

---

## 6. What "working" means (Constitution-aligned acceptance)

To call v1 done, all of these must be true on a fresh install + the
golden-path run above:

- [ ] P50 seal time from tap to confirmation < 200 ms.
- [ ] P95 URL hydration within 30 s on charger + unmetered.
- [ ] Diary "today" renders < 1 s after launch.
- [ ] Every action in §3 appears correctly in "What Orbit did today".
- [ ] `adb logcat` shows **zero** outgoing HTTP calls from any process
      except `:net`. (A Lint rule and a physical MITM check confirm.)
- [ ] No crash in 24 hours of background use with 50+ captures.
- [ ] Uninstall + reinstall loses all data (no cloud tail).

---

## 7. Known v1 limits (pre-documented)

- No export format compatibility with other apps.
- No multi-device sync.
- No voice capture.
- No AppFunctions / AP2 integration yet (Phase 2).
- No "Ripe Nudges" — Diary is the only recall surface.
- Foreground app category is read only at capture moment — no
  historical state ticks.
- The chip row intents are fixed (4 options); no custom user-defined
  intents in v1.

---

## 8. Where to go from here

- Start with `tasks.md` (next speckit artifact) for the granular
  build order.
- Architectural questions → `plan.md`.
- Decision rationale → `research.md`.
- DB shape → `data-model.md`.
- Process boundaries and IPC contracts → `contracts/*.md`.
- Governing principles → `.specify/memory/constitution.md`.
