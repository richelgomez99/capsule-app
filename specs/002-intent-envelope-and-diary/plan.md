# Implementation Plan: Intent Envelope and Diary

**Branch**: `002-intent-envelope-and-diary` | **Date**: 2026-04-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/002-intent-envelope-and-diary/spec.md`
**Governing document**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)

## Summary

Orbit v1 layers an **IntentEnvelope** abstraction and a **Daily Diary**
surface on top of the 001 overlay primitives. Captures come from two paths —
clipboard (bubble tap, existing) and screenshots (new MediaStore observer) —
and converge on a 2-second chip-row disambiguation with silent-wrap for
high-confidence cases. Each envelope is sealed with three pieces of
on-device context (time, foreground app category, Activity Recognition
state) and persisted in an encrypted local Room corpus. A WorkManager-driven
continuation engine enriches URL envelopes in the background on charger +
wifi, fetching through a narrow `:net` process gateway that strips referers
and cookies, HTTPS-only. The Diary, the new primary app surface, shows one
day per page with a Nano-generated header paragraph and thread-grouped
envelope cards. All AI is on-device via Gemini Nano through AICore; no user
content or derived inference ever leaves the device.

## Technical Context

**Language/Version**: Kotlin 2.x (latest stable, matching 001)
**Primary Dependencies**:
- Jetpack Compose BOM 2025+, Material 3, activity-compose (UI)
- androidx.lifecycle (lifecycle-service, lifecycle-viewmodel-compose, lifecycle-runtime)
- androidx.room 2.6+ with SupportFactory wiring
- net.zetetic:android-database-sqlcipher 4.5+ (SQLCipher via Room SupportFactory)
- androidx.work 2.9+ (WorkManager for continuations)
- androidx.security:security-crypto (Android Keystore helpers for DB key wrapping)
- ai.google.aicore (Gemini Nano client; feature-detect + graceful degradation)
- com.google.mlkit:text-recognition (on-device OCR for screenshot URL extraction)
- com.google.android.gms:play-services-location (Activity Recognition API)
- androidx.core:core-ktx for UsageStatsManager helpers
- jsoup (readability extraction; pure parsing, no JS)

**Storage**:
- Room database `orbit.db`, SQLCipher-encrypted, stored in `:ml` process only
- SharedPreferences (inherited from 001) for bubble position and service state — unencrypted, non-sensitive
- WorkManager internal database (Google-managed) for scheduled continuations
- No external storage, no Content Provider exports, no backup participation
  (`android:allowBackup="false"`, `android:dataExtractionRules` opts out)

**Testing**:
- JUnit 5 for unit tests
- Room instrumented tests for DAO behavior under SQLCipher
- Compose UI tests for chip row, diary scrolling, and onboarding
- Contract tests for `:net` gateway and the `:ml` binder surface
- Manual verification on physical Android 13+ device via quickstart.md checklist

**Target Platform**: Android 13+ (minSdk 33, targetSdk 36 per Play Aug 31, 2026
deadline)

**Project Type**: mobile-app (Android, 4-process split)

**Performance Goals**:
- Capture → chip-row visible: p95 < 2s (per FR-001, SC-001)
- Chip tap → envelope sealed: p95 < 300ms
- Silent-wrap path: p95 < 500ms (no UI)
- Diary "today" load: p95 < 1s (per SC-004)
- Diary day swipe: p95 < 500ms
- URL hydration (charger + wifi): p95 < 2 minutes end-to-end (per FR-013, SC-003)
- Day-header paragraph generation: p95 < 10s per day
- 60fps scroll in Diary with up to 1,000 envelopes

**Constraints**:
- All AI on-device (Gemini Nano via AICore). No cloud AI anywhere.
- All user content encrypted at rest (SQLCipher).
- Process with network access MUST NOT have corpus access (Principle VI).
- Process with corpus access MUST NOT have `INTERNET` permission.
- Outbound network: HTTPS-only, stripped referer/cookies, public URLs only.
- Offline-capable: captures and Diary work fully offline; only URL
  hydration requires network.
- 4GB RAM baseline device support (inherited from 001).

**Scale/Scope**:
- Typical user corpus: ~500–1,000 envelopes after 30 days of regular use
- Primary-user dogfood corpus target: ~5,000 envelopes by day 90
- URL hydration queue: bursty (0–50 envelopes), executed lazily
- Diary: 90-day rolling window loaded eagerly; older paginated on demand
- New/modified source files: ~60 Kotlin files, 0 Java

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | v1 Compliance | Status |
|---|---|---|---|
| I | Local-First Supremacy | All envelope content in SQLCipher. AI on-device only. Only outbound call is `:net` → public HTTPS URLs user captured. Audit log exposes every network call to the user. | ✅ PASS |
| II | Effortless Capture, Any Path | Clipboard (001 primitive) + MediaStore screenshot observer (ambient, silent). Chip row dismisses after 2s. High-confidence captures silent-wrap. 10-second undo toast covers mistakes. | ✅ PASS |
| III | Intent Before Artifact | `IntentEnvelope` is the unit; every capture seals with intent (explicit, silent-wrapped, or `AMBIGUOUS`). Intent append-only via `intent_history` chain; supersessions logged. | ✅ PASS |
| IV | Continuations Grow Captures | `Continuation` entity attaches to envelopes. `URL_HYDRATE` runs on charger + wifi via WorkManager. Diary surfaces enrichments already processed — no foreground work required. | ✅ PASS |
| V | Under-Deliver on Noise | Ripe Nudges deferred to Phase 2 entirely. v1 notifications are only the foreground service notification (1) and a non-interrupting capture confirmation toast (2). Zero opt-in marketing notifications. | ✅ PASS |
| VI | Privilege Separation By Design | 4-process split at manifest level: `:capture`, `:ml`, `:net`, `:ui`. `INTERNET` on `:net` alone. Corpus opens only in `:ml`. Custom lint rule `NoHttpClientOutsideNet` blocks rogue network code. | ✅ PASS |
| VII | Context Beyond Content | `state_snapshot` on each envelope: capture time, foreground app category (bucketed), Activity Recognition state. All computed on-device, never transmitted. Context labels in Diary show these. | ✅ PASS |
| VIII | Collect Only What You Use | v1 collects exactly the 3 capture-time signals + 2 scheduling signals listed in the constitution's Phase 1 scope. App-switching rate, DND, location, calendar, Health Connect, keystroke dynamics all explicitly deferred. | ✅ PASS |

**Inherited from 001**:
- Foreground service type is `specialUse` (per 001 research §1 — no 6-hour
  timeout, semantically correct for a persistent interactive overlay).
- Bubble drag, edge-snap, OEM kill survival (START_STICKY + AlarmManager +
  SharedPreferences recovery) are preserved without regression (FR-025).

**Post-Research Amendment** (2026-04-16, end of Phase 0):
No principle amendments required. Two nuances surfaced during research and
are captured in `research.md` but do not change the constitution:

1. Gemini Nano is treated as "nice-to-have infrastructure", not a
   prerequisite — every feature degrades gracefully on devices without
   AICore. This strengthens Principle I rather than contradicting it.
2. The "foreground app category" signal is read only at seal time and
   is stored as a coarse `AppCategory` enum; raw package names never
   touch disk. This stays inside Principle VIII ("collect only what
   you use") without needing constitutional carve-outs.

**Post-Design Constitution Re-check** (2026-04-16, end of Phase 1):
All 8 principles remain ✅ PASS after drafting `data-model.md`,
`quickstart.md`, and the four contracts. Two implementation-time
invariants emerged and are now codified:

- **IPC surface is closed**. The four contract files exhaustively
  enumerate every cross-process call. Adding a fifth requires a
  constitution-compliant review (Principle VI).
- **Audit is atomic with data**. `ENVELOPE_CREATED`, `INTENT_SUPERSEDED`,
  `ENVELOPE_ARCHIVED`, `ENVELOPE_DELETED` share a Room transaction with
  the envelope mutation. This is captured in
  `contracts/audit-log-contract.md` §6 and `data-model.md` §8
  (Principle III).

## Project Structure

### Documentation (this feature)

```text
specs/002-intent-envelope-and-diary/
├── plan.md                                  # This file
├── research.md                              # Phase 0 output
├── data-model.md                            # Phase 1 output
├── quickstart.md                            # Phase 1 output
├── contracts/                               # Phase 1 output
│   ├── envelope-repository-contract.md      # :ml process API exposed to :ui
│   ├── network-gateway-contract.md          # :net process exposed to :ml
│   ├── continuation-engine-contract.md      # WorkManager contracts
│   └── audit-log-contract.md                # Audit log creation + read surface
└── tasks.md                                 # Phase 2 output (speckit.tasks)
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts                         # Dependency and process-split config
├── proguard-rules.pro
└── src/
    ├── main/
    │   ├── AndroidManifest.xml              # Process splits + permission scoping
    │   └── java/com/capsule/app/            # NOTE: package rename to com.orbit.app
    │       │                                # tracked at repo root; v1 keeps current
    │       │                                # package to avoid churn in this milestone
    │       │
    │       ├── CapsuleApplication.kt        # 001 — KEPT; minor WorkManager init
    │       │
    │       ├── service/                     # 001 — KEPT AS-IS
    │       │   ├── CapsuleOverlayService.kt # (runs in :capture process now)
    │       │   ├── OverlayLifecycleOwner.kt
    │       │   ├── ClipboardFocusStateMachine.kt
    │       │   ├── ForegroundNotificationManager.kt
    │       │   └── ServiceHealthMonitor.kt
    │       │
    │       ├── overlay/                     # 001 — EXTENDED
    │       │   ├── BubbleUI.kt              # unchanged
    │       │   ├── BubbleState.kt           # unchanged
    │       │   ├── CaptureChipRowUI.kt      # NEW — replaces old CaptureSheetUI
    │       │   ├── CaptureConfirmToast.kt   # NEW — undo window
    │       │   └── OverlayViewModel.kt      # EXTENDED — envelope creation
    │       │
    │       ├── permission/                  # 001 — EXTENDED
    │       │   ├── OverlayPermissionHelper.kt
    │       │   ├── BatteryOptimizationGuide.kt
    │       │   └── OnboardingPermissionFlow.kt  # NEW — 4-permission walkthrough
    │       │
    │       ├── capture/                     # NEW — runs in :capture process
    │       │   ├── ScreenshotObserver.kt    # MediaStore ContentObserver
    │       │   ├── ClipboardCaptureSource.kt
    │       │   ├── StateSnapshotProvider.kt # UsageStats + ActivityRecognition
    │       │   ├── CaptureBinderService.kt  # IPC to :ml process
    │       │   └── AppCategoryDictionary.kt # package → category mapping
    │       │
    │       ├── data/                        # NEW — runs in :ml process
    │       │   ├── OrbitDatabase.kt         # Room + SQLCipher SupportFactory
    │       │   ├── DatabaseKeyProvider.kt   # Keystore-wrapped key
    │       │   ├── entities/
    │       │   │   ├── IntentEnvelopeEntity.kt
    │       │   │   ├── StateSnapshot.kt     # @Embedded
    │       │   │   ├── ContinuationEntity.kt
    │       │   │   ├── ContinuationResultEntity.kt
    │       │   │   └── AuditLogEntryEntity.kt
    │       │   ├── dao/
    │       │   │   ├── IntentEnvelopeDao.kt
    │       │   │   ├── ContinuationDao.kt
    │       │   │   └── AuditLogDao.kt
    │       │   ├── EnvelopeRepository.kt    # Main read/write surface
    │       │   └── AuditLogRepository.kt
    │       │
    │       ├── ai/                          # NEW — runs in :ml process
    │       │   ├── NanoClient.kt            # AICore/Gemini Nano wrapper
    │       │   ├── NanoAvailability.kt      # Feature-detect + fallback flags
    │       │   ├── IntentPredictor.kt       # Silent-wrap confidence scorer
    │       │   ├── SummaryGenerator.kt      # URL + day-header summaries
    │       │   ├── ThreadGrouper.kt         # Embedding-based similarity
    │       │   └── TextRecognizer.kt        # ML Kit OCR (screenshots → URLs)
    │       │
    │       ├── continuation/                # NEW — runs in :ml process
    │       │   ├── ContinuationEngine.kt    # Enqueue, dispatch, result write-back
    │       │   ├── ContinuationScheduler.kt # WorkManager constraints + cadence
    │       │   └── workers/
    │       │       └── UrlHydrateWorker.kt  # Calls :net via bound service
    │       │
    │       ├── net/                         # NEW — runs in :net process
    │       │   ├── NetworkGatewayService.kt # Bound service, single entry point
    │       │   ├── SafeHttpClient.kt        # HTTPS-only, referer/cookie stripped
    │       │   ├── ReadabilityExtractor.kt  # jsoup-based article extraction
    │       │   └── UrlValidator.kt          # HTTPS enforcement + redirect policy
    │       │
    │       ├── diary/                       # NEW — runs in :ui process
    │       │   ├── DiaryActivity.kt         # Primary app surface
    │       │   ├── DiaryViewModel.kt
    │       │   ├── DayPageUI.kt
    │       │   ├── EnvelopeCardUI.kt
    │       │   ├── ThreadCardUI.kt
    │       │   ├── DayHeaderUI.kt
    │       │   └── EmptyDayUI.kt
    │       │
    │       ├── settings/                    # NEW — runs in :ui process
    │       │   ├── SettingsActivity.kt
    │       │   ├── AuditLogUI.kt            # "What Orbit did today"
    │       │   ├── PermissionStatusUI.kt
    │       │   └── ExportActivity.kt        # FR-024 export
    │       │
    │       └── ui/                          # 001 — KEPT
    │           ├── MainActivity.kt          # DEMOTED to launcher → Diary
    │           └── theme/
    │               ├── CapsuleTheme.kt
    │               ├── Color.kt
    │               └── Type.kt
    │
    └── test/
        └── java/com/capsule/app/
            ├── data/
            │   ├── IntentEnvelopeDaoTest.kt     # Instrumented (Room + SQLCipher)
            │   ├── EnvelopeRepositoryTest.kt
            │   └── AuditLogDaoTest.kt
            ├── ai/
            │   ├── IntentPredictorTest.kt
            │   └── ThreadGrouperTest.kt
            ├── continuation/
            │   └── UrlHydrateWorkerTest.kt       # Fakes :net binder
            ├── net/
            │   ├── SafeHttpClientTest.kt
            │   └── UrlValidatorTest.kt
            └── diary/
                └── DiaryViewModelTest.kt

build.gradle.kts                              # Root build file
gradle/
└── libs.versions.toml                        # Version catalog (new AICore, mlkit, sqlcipher, etc.)
settings.gradle.kts
```

**Structure Decision**: Android single-module app with four
`android:process` splits declared in the manifest. The package layout
follows the process boundaries: `service/`, `overlay/`, `capture/` →
`:capture`; `data/`, `ai/`, `continuation/` → `:ml`; `net/` → `:net`;
`diary/`, `settings/`, `ui/` → `:ui`. The `permission/`, `service/`
(bubble), and `overlay/` packages from 001 are kept as-is. New packages are
added without modifying existing ones except where `OverlayViewModel.kt`
and `MainActivity.kt` need extension. Custom Android lint rule
`NoHttpClientOutsideNet` enforces Principle VI at build time.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| 4-process split (vs. single-process app) | Principle VI — structural enforcement that no single process can both hold user content and reach the network. Makes Principle I (Local-First) a compile-time guarantee, not a runtime hope. | Single-process with lint-only enforcement was rejected because lint can be disabled per file. Once `INTERNET` is in the manifest, any code path in the process can use it. Process-level permission scoping is the only enforcement a future contributor or a bug cannot silently undo. |
| SQLCipher for Room (vs. plain Room + Android Keystore file encryption) | Principle I — encrypt the database content, not just the file on disk. SQLCipher decrypts per-page, so a memory dump of the app reveals only hot rows, and a compromised backup file is unreadable without the Keystore-wrapped key. | Plain Room + relying on Android full-disk encryption was rejected because root-level app access or debug builds can bypass FDE. Jetpack Security's EncryptedSharedPreferences doesn't cover Room. File-level encryption of the SQLite file is brittle (Room needs open-file access). SQLCipher is the only approach that encrypts the data at rest at the database layer. |
| 4 contract files (vs. one monolithic contract) | Each contract covers a distinct process boundary (`:ml`→`:net`, `:ui`→`:ml`, WorkManager, audit log). Merging them hides the boundary and invites leakage across processes. | Single contract rejected because Principle VI demands each boundary's invariants be reviewable in isolation. 001 had one service — one contract was right. 002 has multiple processes and IPC surfaces. |

## Progress Tracking

- [x] **Phase 0 — Research** (`research.md`): database encryption, process architecture, Gemini Nano integration strategy, URL hydration, screenshot observation, state-signal collection, chip-row UX, thread grouping, day-header generation, AppFunctions posture.
- [x] **Phase 1 — Design**:
  - [x] `data-model.md` — enums, entities, embedded types, derived UI views, SharedPreferences, encryption key lifecycle, migration strategy.
  - [x] `contracts/envelope-repository-contract.md` — AIDL surface, pre/postconditions, concurrency, size budgets, tests.
  - [x] `contracts/network-gateway-contract.md` — single `fetchPublicUrl` entrypoint, request policy, error taxonomy, tests.
  - [x] `contracts/continuation-engine-contract.md` — WorkManager model, URL hydrate, screenshot URL extract, scheduling, cancel, tests.
  - [x] `contracts/audit-log-contract.md` — action enum, write API, read API (`IAuditLog`), retention, UI bindings, export.
  - [x] `quickstart.md` — first-run, golden path, alternate paths, acceptance checks aligned to Constitution + SC-001..SC-011.
- [x] **Post-Design Constitution Re-check** (see Constitution Check → Post-Design note above).
- [ ] **Phase 2 — Tasks** (`tasks.md`) — to be generated by `/speckit.tasks`.
- [ ] **Phase 3 — Implement** — per `tasks.md`, enforced by the `NoHttpClientOutsideNet` lint rule and contract tests.
