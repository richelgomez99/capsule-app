# Tasks: Intent Envelope and Diary (Orbit v1)

**Input**: Design documents from `/specs/002-intent-envelope-and-diary/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md
**Tests**: INCLUDED. The constitution (Principle VI — Privilege Separation By Design, Principle III — Transparency Of Intelligence) and the four contract documents require contract tests. Instrumented tests are called out explicitly because device-level behavior (SQLCipher, WorkManager, binder IPC, foreground service) cannot be validated on the JVM alone.

**Organization**: Tasks grouped by user story. P1 stories form the MVP. Checkpoints at the end of each story phase.

**Visual source of truth**: `.specify/memory/design.md` — every Compose task below (bubble, capture sheet, chip row, diary, envelope card, settings, audit log, trash, onboarding, reduced mode) cites a section of that document by number. Implementations MUST NOT introduce typography, color, motion, or spacing decisions that are not already in `design.md`; if a decision is missing, update `design.md` first, then the Compose task.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no blocking dependencies)
- **[Story]**: Maps to spec.md user stories (US1..US7); Setup/Foundational/Polish have no story label
- All file paths are repo-relative

## Path Conventions (Android single-module, 4-process app)

- `app/src/main/java/com/capsule/app/…` — production code
- `app/src/androidTest/java/com/capsule/app/…` — instrumented tests (Room, WorkManager, IPC)
- `app/src/test/java/com/capsule/app/…` — JVM unit tests
- `app/src/main/AndroidManifest.xml` — process splits + permission scoping
- `build-logic/lint/…` — custom lint rule `OrbitNoHttpClientOutsideNet`

> Note: package remains `com.capsule.app.*` for v1 (product renamed to Orbit; package rename deferred — see plan.md §Project Structure).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bring the repo to a state where every later phase can build cleanly. Nothing here touches product behavior.

- [ ] T001 Update `gradle/libs.versions.toml` with versions for Gemini Nano / AICore, ML Kit text recognition, SQLCipher for Android (`net.zetetic:android-database-sqlcipher`), Room 2.6+, WorkManager 2.9+, jsoup, Readability4J, Kotlinx Serialization, AndroidX Security-Crypto, and pin Kotlin 2.x
- [ ] T002 Update `app/build.gradle.kts` to consume the new version catalog entries, enable `kotlinx.serialization`, enable Room KSP (over KAPT), and add `androidTestImplementation` deps for Room/WorkManager/Binder test utilities
- [ ] T003 [P] Create `build-logic/lint/OrbitNoHttpClientOutsideNet` custom lint module that fails the build when `OkHttpClient`, `HttpURLConnection`, `java.net.Socket`, or any Ktor HTTP client is referenced outside `com.capsule.app.net.*` (see contracts/network-gateway-contract.md §1)
- [ ] T004 [P] Wire the lint rule into `app/build.gradle.kts` via `lintChecks(project(":build-logic:lint"))` and set `lintOptions { abortOnError = true }` for Release builds
- [ ] T005 [P] Create source package skeletons: `com.capsule.app.data`, `.data.ipc`, `.ai`, `.continuation`, `.net`, `.net.ipc`, `.diary`, `.diary.ui`, `.settings`, `.onboarding`, `.audit` with empty `package-info.kt` files so later [P] tasks can land without module conflicts
- [ ] T006 [P] Add ProGuard / R8 keep rules in `app/proguard-rules.pro` for Room entities, Kotlinx-Serialization classes, and AIDL parcelables (`com.capsule.app.data.ipc.**`, `com.capsule.app.net.ipc.**`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Process splits, encrypted DB, AIDL surfaces, and DI skeleton. Nothing user-facing in this phase — but every user story depends on it.

**⚠️ CRITICAL**: No user-story phase may begin until Phase 2 is green.

### Process split & manifest

- [ ] T007 Edit `app/src/main/AndroidManifest.xml`: declare `<application>` with no process override; add `android:process=":capture"` to `CapsuleOverlayService`, add new `EnvelopeRepositoryService` with `android:process=":ml"` and `NetworkGatewayService` with `android:process=":net"` and `android:exported="false"` on both; keep `MainActivity`/`DiaryActivity` on the default (`:ui`) process (see plan.md §Technical Context, contracts/network-gateway-contract.md §2)
- [ ] T008 Scope permissions per process in `AndroidManifest.xml`: keep `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW` at app level but put `android.permission.INTERNET` behind `android:process=":net"` only (use `<uses-permission android:name="android.permission.INTERNET"/>` with a comment pointing at `OrbitNoHttpClientOutsideNet`); add `PACKAGE_USAGE_STATS` and `ACTIVITY_RECOGNITION`

### Encrypted data layer

- [ ] T009 Create `app/src/main/java/com/capsule/app/data/security/KeystoreKeyProvider.kt` — Android Keystore wrapper that generates, caches, and unwraps the 256-bit SQLCipher passphrase per data-model.md §Encryption Key Lifecycle
- [ ] T010 Create `app/src/main/java/com/capsule/app/data/OrbitDatabase.kt` — Room `@Database` class version 1, `SupportFactory(passphrase)` wiring via `KeystoreKeyProvider`, export schema to `app/schemas/` (per data-model.md §Schema Migration)
- [ ] T011 [P] Create Room entity `com.capsule.app.data.entity.IntentEnvelopeEntity` with `@Embedded StateSnapshot`, indices on `dayLocal` and `intent`, per data-model.md §IntentEnvelopeEntity. Additional v1 columns (beyond data-model.md): `deletedAt: Instant?` for soft-delete (FR-022 / Clarification Q5), `sharedContinuationResultId: UUID?` for URL-hash dedupe (FR-013 / Clarification Q2)
- [ ] T012 [P] Create Room entity `com.capsule.app.data.entity.ContinuationEntity` + `ContinuationResultEntity` per data-model.md §Continuation entities. `ContinuationResultEntity` MUST include a nullable `canonicalUrlHash: String?` column with a unique index (populated only for URL_HYDRATE results, used for dedupe per FR-013 / Clarification Q2)
- [ ] T013 [P] Create Room entity `com.capsule.app.data.entity.AuditLogEntryEntity` with index on `at` per contracts/audit-log-contract.md §2 and data-model.md §AuditLogEntryEntity
- [ ] T014 [P] Create enums `com.capsule.app.data.model.{ContentType, Intent, IntentSource, AppCategory, ActivityState, ContinuationType, ContinuationStatus, AuditAction}` per data-model.md §Enums. `IntentSource` MUST enumerate `{USER_CHIP, PREDICTED_SILENT, AUTO_AMBIGUOUS, FALLBACK, DIARY_REASSIGN}` (clarification A2). `AuditAction` MUST include `CAPTURE_SCRUBBED` (T037c), `ENVELOPE_SOFT_DELETED`, `ENVELOPE_RESTORED`, `ENVELOPE_HARD_PURGED` (C2), `URL_DEDUPE_HIT` (C1), and `PERMISSION_REDUCED_MODE_ENTERED` (U2). Also create `LlmProvider` result data classes used by T025a in `com.capsule.app.ai.model.*`: `IntentClassification(intent: Intent, confidence: Float, provenance: LlmProvenance)`, `SummaryResult(text: String, generationLocale: String, provenance: LlmProvenance)`, `SensitivityResult(flagsJson: String, provenance: LlmProvenance)`, `DayHeaderResult(text: String, generationLocale: String, provenance: LlmProvenance)`.
- [ ] T015 [P] Create `com.capsule.app.data.entity.StateSnapshot` embedded type per data-model.md §StateSnapshot
- [ ] T016 [P] Create DAOs: `IntentEnvelopeDao`, `ContinuationDao`, `ContinuationResultDao`, `AuditLogDao` with the queries required by the repository + diary — all in `com.capsule.app.data.dao.*`. Minimum query surface: load day (excluding `deletedAt IS NOT NULL` and archived), load envelope, insert with transaction, update intent, archive, delete (soft — writes `deletedAt`), audit read, **plus**: `existsNonArchivedNonDeletedInLast30Days(appCategory, intent): Boolean` (for T036a silent-wrap predicate), `distinctDayLocalsWithContent(limit: Int, offset: Int): List<LocalDate>` (for T050 unlimited backscroll), `findByCanonicalUrlHash(hash: String): ContinuationResultEntity?` (for T066a URL dedupe), `restoreFromTrash(id: UUID)`, `listSoftDeletedWithinDays(days: Int): List<EnvelopeView>`, `countSoftDeletedWithinDays(days: Int): Int`, `hardPurgeWhereDeletedBefore(cutoff: Instant)` (for T089a retention worker).
- [ ] T017 Wire `IntentEnvelopeDao`, `ContinuationDao`, `ContinuationResultDao`, `AuditLogDao` into `OrbitDatabase` and add migration-0-to-1 scaffold (no-op but present for future)

### AIDL boundaries

- [ ] T018 [P] Create AIDL file `app/src/main/aidl/com/capsule/app/data/ipc/IEnvelopeRepository.aidl` matching contracts/envelope-repository-contract.md §3
- [ ] T019 [P] Create AIDL file `app/src/main/aidl/com/capsule/app/data/ipc/IEnvelopeObserver.aidl` (oneway) matching contracts/envelope-repository-contract.md §5
- [ ] T020 [P] Create AIDL file `app/src/main/aidl/com/capsule/app/data/ipc/IAuditLog.aidl` matching contracts/audit-log-contract.md §5
- [ ] T021 [P] Create AIDL file `app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl` matching contracts/network-gateway-contract.md §3
- [ ] T022 [P] Create Parcelable DTOs under `com.capsule.app.data.ipc.*`: `IntentEnvelopeDraftParcel`, `StateSnapshotParcel`, `EnvelopeViewParcel`, `DayPageParcel`, `AuditEntryParcel` + `@Parcelize` on each
- [ ] T023 [P] Create Parcelable DTO `com.capsule.app.net.ipc.FetchResultParcel` per contracts/network-gateway-contract.md §3

### Foundational services (skeletons bound in Phase 3+)

- [ ] T024 Create `com.capsule.app.data.ipc.EnvelopeRepositoryService` (extends `Service`, `android:process=":ml"`) returning an `IEnvelopeRepository.Stub` that delegates to `EnvelopeRepositoryImpl` (to be fleshed out in US1)
- [ ] T025 Create `com.capsule.app.net.NetworkGatewayService` (extends `Service`, `android:process=":net"`) returning an `INetworkGateway.Stub` that delegates to `SafeOkHttpClient` (to be fleshed out in US3) — now only verifying UID check per contracts/network-gateway-contract.md §2

### Forward-compatible abstractions (constitution Principles IX & X)

> **Rationale**: These two interfaces are introduced in v1 with a single on-device implementation each so that v1.1 cloud work (spec 005 LLM routing and spec 006 Orbit Cloud storage) and v1.3 BYOC sovereign storage (spec 009) are additive rather than requiring a refactor of v1 call sites. Together they cost roughly 1 engineer-day and have zero user-visible impact on v1. See `.specify/memory/PRD.md` §Post-v1 Roadmap → v1 abstractions required by the roadmap.

- [ ] T025a [P] Create `com.capsule.app.ai.LlmProvider` interface with methods `classifyIntent(text)`, `summarize(text, maxSentences)`, `scanSensitivity(text)`, `generateDayHeader(DayPage)`, each returning a result type that includes an `LlmProvenance` field (`sealed class LlmProvenance { object LocalNano : LlmProvenance; data class OrbitManaged(val model: String) : LlmProvenance; data class Byok(val provider: String, val model: String) : LlmProvenance }`). Every Nano call site in US1–US8 MUST route through this interface.
- [ ] T025b [P] Create `com.capsule.app.ai.NanoLlmProvider` implementing `LlmProvider` by delegating to the existing AICore/Gemini Nano wrappers. This is the only implementation registered in v1. `OrbitManagedLlmProvider` and `ByokLlmProvider` are introduced in spec 005 v1.1 without touching any call site.
- [ ] T025c [P] Create `com.capsule.app.data.EnvelopeStorageBackend` interface capturing the subset of operations `EnvelopeRepositoryImpl` performs against storage: `sealTransaction(envelope, continuations, audit)`, `observeDay(localDate)`, `getEnvelope(id)`, `updateIntent(id, newIntent)`, `archive(id)`, `delete(id)`, `count*`. Every call in `EnvelopeRepositoryImpl` (T033) goes through this interface rather than touching Room DAOs directly. The interface is backend-agnostic: v1 routes to `LocalRoomBackend` (T025d); spec 006 v1.1 routes opted-in writes to `OrbitCloudBackend`; spec 009 v1.3 routes opted-in writes to `ByocPostgresBackend`. Selection is via a dependency-injected router, not via subclass hierarchy, so future backends do not require changes here.
- [ ] T025d [P] Create `com.capsule.app.data.LocalRoomBackend` implementing `EnvelopeStorageBackend` by delegating to the Room DAOs from T016. Sole implementation in v1. Spec 006 v1.1 adds `OrbitCloudBackend` and spec 009 v1.3 adds `ByocPostgresBackend` without changing `EnvelopeRepositoryImpl` or any caller.
- [ ] T025e [P] Update `audit_log_entry` schema (T013) to include optional columns `llmProvider TEXT`, `llmModel TEXT`, `promptDigestSha256 TEXT`, `tokenCount INTEGER`. Nullable in v1 (always null because provenance is always LocalNano). Populated starting v1.1 per constitution Principle IX, condition 3.

### Contract tests for the foundation

- [ ] T026 [P] `app/src/androidTest/java/com/capsule/app/data/OrbitDatabaseTest.kt` — open encrypted Room with SQLCipher on an in-memory DB, insert + read an `IntentEnvelopeEntity`, verify schema exported under `app/schemas/1.json`
- [ ] T027 [P] `app/src/androidTest/java/com/capsule/app/data/security/KeystoreKeyProviderTest.kt` — verify key is generated once, unwraps to the same passphrase across process restarts (simulated)
- [ ] T028 [P] `app/src/test/java/com/capsule/app/lint/OrbitNoHttpClientOutsideNetTest.kt` — lint unit test: sample Kotlin file importing `OkHttpClient` from `com.capsule.app.ui.*` fails; same import from `com.capsule.app.net.*` passes

**Checkpoint**: Manifest process splits wire up, encrypted DB opens, AIDL surfaces compile, lint rule enforces, contract tests green, `LlmProvider` + `EnvelopeStorageBackend` interfaces exist with their local-only implementations. User-story phases can start.

---

## Phase 3: User Story 1 — Capture With Intent (Priority: P1) 🎯 MVP CORE

**Goal** (spec.md §US1): Copy a URL, tap the bubble, see a 4-chip row `[Want it] [Reference] [For someone] [Interesting]` within 2s. Assign intent or let it auto-dismiss to `AMBIGUOUS`. Envelope sealed locally with a state snapshot. 10-second undo.

**Independent Test**: Copy a URL in Chrome → tap bubble → chip row appears in < 2s → tap *Want it* → toast "Saved as Want it. Undo" appears → open Diary → envelope exists with `intent=WANT_IT`, state snapshot populated.

### Contract & integration tests (US1)

- [ ] T029 [P] [US1] `app/src/androidTest/java/com/capsule/app/data/ipc/EnvelopeRepositorySealContractTest.kt` — bind to `EnvelopeRepositoryService`, call `seal()` with a URL draft, assert one envelope row + one `CONTINUATION` row (URL_HYDRATE) + one `ENVELOPE_CREATED` audit row written in a single transaction (contracts/envelope-repository-contract.md §4)
- [ ] T030 [P] [US1] `…/EnvelopeRepositoryUndoContractTest.kt` — seal → `undo()` within 10s succeeds and rolls back; outside 10s returns false (contracts/envelope-repository-contract.md §4)
- [ ] T031 [P] [US1] `…/EnvelopeRepositoryReassignContractTest.kt` — seal → `reassignIntent(WANT_IT → REFERENCE)` appends to `intentHistoryJson`, writes `INTENT_SUPERSEDED` audit row
- [ ] T032 [P] [US1] `app/src/test/java/com/capsule/app/ai/IntentPredictorTest.kt` — on-device Nano call is faked; assert high-confidence text yields `(intent, confidence)` suitable for silent-wrap; low-confidence yields `AMBIGUOUS`

### Implementation (US1)

- [ ] T033 [P] [US1] Create `com.capsule.app.data.EnvelopeRepositoryImpl` that implements `IEnvelopeRepository.Stub` — transactional `seal`, `reassignIntent`, `archive`, `delete`, `undo`, `getEnvelope`, `observeDay` (Flow → binder adapter debounced 150ms), `countAll/Archived/Deleted` (contracts/envelope-repository-contract.md §3, §4, §6). Implementation MUST delegate all storage operations to an injected `EnvelopeStorageBackend` (T025c) — never touch Room DAOs directly, so spec 006 can add a cloud-mirroring backend without modifying this class or any caller.
- [ ] T033b [US1] Extend `IEnvelopeRepository` AIDL (T018) to expose `restoreFromTrash(envelopeId)` and `listSoftDeletedWithinDays(days): List<EnvelopeViewParcel>` and `countSoftDeletedWithinDays(days): Int`. Update `EnvelopeRepositoryImpl.delete` from T033 to be **soft-delete**: write `deletedAt = now()`, keep the row in the DB, exclude it from `observeDay` results, write one `ENVELOPE_SOFT_DELETED` audit row. Add `restoreFromTrash(id)` that clears `deletedAt` and writes `ENVELOPE_RESTORED`. Per FR-022 + spec.md Clarification Q5.
- [ ] T033c [P] [US1] `app/src/androidTest/java/com/capsule/app/data/ipc/EnvelopeRepositorySoftDeleteContractTest.kt` — seal → delete: row persists with `deletedAt` set, `observeDay` no longer emits it, `listSoftDeletedWithinDays(30)` returns it, one `ENVELOPE_SOFT_DELETED` audit row exists. Then `restoreFromTrash`: `deletedAt` is null, envelope re-appears in `observeDay`, one `ENVELOPE_RESTORED` audit row exists.
- [ ] T034 [P] [US1] Create `com.capsule.app.audit.AuditLogWriter` in `:ml` process — writes to `AuditLogDao` inside the caller's Room transaction (contracts/audit-log-contract.md §4, §6)
- [ ] T035 [US1] Wire `EnvelopeRepositoryImpl` and `AuditLogWriter` into `EnvelopeRepositoryService` created in T024
- [ ] T036 [P] [US1] Create `com.capsule.app.ai.IntentPredictor` — thin wrapper over AICore/Gemini Nano with graceful-degrade path (returns `AMBIGUOUS` + `INTENT_SOURCE = FALLBACK` when Nano unavailable) per research.md §Gemini Nano. Routes through `LlmProvider` (T025a) and returns `IntentClassification` (T014).
- [ ] T036a [P] [US1] Create `com.capsule.app.ai.SilentWrapPredicate` — pure decision function `evaluate(prediction: IntentClassification, draft: IntentEnvelopeDraft): SilentWrapDecision` returning `SILENT_WRAP(intent)` **only when both conditions hold**: (a) `prediction.confidence >= 0.70` AND (b) `EnvelopeStorageBackend.existsNonArchivedNonDeletedInLast30Days(draft.appCategory, prediction.intent)` returns true. Otherwise returns `SHOW_CHIP_ROW`. Implements FR-004's composite silent-wrap predicate per spec.md Clarification Q4.
- [ ] T036b [P] [US1] `app/src/test/java/com/capsule/app/ai/SilentWrapPredicateTest.kt` — covers the 2×2 truth table: (confidence ≥ 0.70 ∧ prior match) → SILENT_WRAP; (confidence ≥ 0.70 ∧ no prior match) → SHOW_CHIP_ROW; (confidence < 0.70 ∧ prior match) → SHOW_CHIP_ROW; (confidence < 0.70 ∧ no prior match) → SHOW_CHIP_ROW. Additionally: archived priors do NOT count; soft-deleted priors do NOT count; priors older than 30 days do NOT count.
- [ ] T037 [P] [US1] Create `com.capsule.app.capture.StateSnapshotCollector` — queries `UsageStatsManager` for foreground package at capture moment and maps to `AppCategory` via an internal map (no raw package names stored); reads latest Activity Recognition sample; returns `StateSnapshot` (research.md §State Signal Collection)
- [ ] T037a [P] [US1] Create `com.capsule.app.capture.SensitivityScrubber` in the `:capture` process — synchronous regex-based redaction pass over captured text **before** it is placed into an `IntentEnvelopeDraftParcel`. Redacts: AWS access/secret key shapes, GitHub PAT shapes (`ghp_*`, `github_pat_*`), OpenAI/Anthropic key shapes (`sk-*`, `sk-ant-*`), JWT (three-base64-segment pattern), credit card numbers (Luhn-validated), US SSN (`\d{3}-\d{2}-\d{4}`), email addresses, US phone numbers (`\+?1?-?\(?\d{3}\)?[-. ]?\d{3}[-. ]?\d{4}`), IPv4 addresses. Each match replaced with `[REDACTED_<TYPE>]`. Returns `ScrubResult(scrubbedText, redactionCountByType)`.
- [ ] T037b [P] [US1] `app/src/test/java/com/capsule/app/capture/SensitivityScrubberTest.kt` — covers every redaction pattern with true-positive + true-negative fixtures (e.g., AWS-like string that is actually a build hash must NOT be redacted; real AWS key format MUST be redacted). Include benign-text regression fixture (news article) that yields zero redactions.
- [ ] T037c [US1] Extend `IntentEnvelopeDraftParcel` to carry `redactionCountByType: Map<String, Int>` so the audit log can record that a capture was scrubbed without leaking what was scrubbed. Extend `AuditAction` enum with `CAPTURE_SCRUBBED`; `EnvelopeRepositoryImpl.seal()` writes one `CAPTURE_SCRUBBED` row when `redactionCountByType` is non-empty.
- [ ] T038 [US1] Modify existing `app/src/main/java/com/capsule/app/overlay/OverlayViewModel.kt` — on clipboard focus: bind to `EnvelopeRepositoryService`, call `SensitivityScrubber` (T037a) on captured text **before** IPC, then call `IntentPredictor` on scrubbed text, decide between silent-wrap vs chip row vs auto-dismiss, build `IntentEnvelopeDraftParcel` with scrubbed text + redaction counts + `StateSnapshotParcel`, call `seal()`, emit UI state for the 10s undo toast
- [ ] T039 [US1] Rebuild `app/src/main/java/com/capsule/app/overlay/BubbleUI.kt` chip row — 4 chips (`Want it`, `Reference`, `For someone`, `Interesting`), 2-second countdown ring, auto-dismiss → silent seal with `AMBIGUOUS`, tactile + haptic feedback on chip tap (research.md §Chip Row UX)
- [ ] T040 [US1] Add silent-wrap path: call `SilentWrapPredicate.evaluate()` (T036a) with the `IntentClassification` from T036 and the draft; on `SILENT_WRAP(intent)` skip the chip row and show a 2s "Saved as {Intent}. Undo" snackbar-over-overlay with `IntentSource.PREDICTED_SILENT`; on `SHOW_CHIP_ROW` render the 4-chip row. NEVER silent-wrap on confidence alone — the predicate's composite check (confidence ≥ 0.70 AND prior-30d match) is the single source of truth.
- [ ] T041 [US1] Add 10-second undo affordance in the overlay — on tap, call `IEnvelopeRepository.undo(envelopeId)`; on success, toast "Removed"; on failure (outside window), toast "Already saved to Diary"
- [ ] T042 [US1] Bind lifecycle: `OverlayViewModel` unbinds `EnvelopeRepositoryService` when overlay goes offscreen > 30s to free `:ml` (documented in research.md §Process Architecture)
- [ ] T043 [US1] Update `com.capsule.app.service.ClipboardFocusStateMachine` to call the new seal path and log `CLIPBOARD_FOCUSED` + `ENVELOPE_SEALED` transitions

**Checkpoint**: Copy → chip row → seal → undo works end to end. `adb` dump of `audit_log` shows `ENVELOPE_CREATED` rows. No other user story required.

---

## Phase 4: User Story 2 — Daily Diary (Priority: P1)

**Goal** (spec.md §US2): Open Orbit → land on today's diary → see the day's envelopes grouped into threads with a Nano-generated day header paragraph. P50 render ≤ 1s.

**Independent Test**: After capturing 5+ envelopes across apps, open Orbit. Today's page loads < 1s, envelopes are grouped into ≤ 3 threads, each card shows `from {app} · {activity} · {time}`, day header paragraph is non-empty.

### Contract & integration tests (US2)

- [ ] T044 [P] [US2] `app/src/androidTest/java/com/capsule/app/data/ipc/EnvelopeRepositoryObserveDayContractTest.kt` — `observeDay(today)` emits initial `DayPageParcel` within 1s; emits again within 500ms after an external `seal()` (contracts/envelope-repository-contract.md §4)
- [ ] T045 [P] [US2] `app/src/test/java/com/capsule/app/diary/ThreadGrouperTest.kt` — given N envelopes across apps/times, groups into threads by `(appCategory, activityState, hour-bucket)` with cosine-similarity collapse per research.md §Thread Grouping
- [ ] T046 [P] [US2] `app/src/test/java/com/capsule/app/diary/DayHeaderGeneratorTest.kt` — Nano-faked: given a `DayPage`, produces a 1–2 sentence neutral paragraph; with Nano unavailable, returns the structured fallback string per research.md §Day-Header Generation

### Implementation (US2)

- [ ] T047 [P] [US2] Create `com.capsule.app.diary.ThreadGrouper` — pure function `(List<EnvelopeView>) → List<Thread>`, deterministic, covered by T045
- [ ] T048 [P] [US2] Create `com.capsule.app.diary.DayHeaderGenerator` — wraps `LlmProvider.generateDayHeader()` (T025a) with a prompt template at `com.capsule.app.ai.prompts.DayHeaderPrompt.kt`; fallback returns `"{N} captures, mostly in {top AppCategory}, {top ActivityState} for most of the day."`. **Locale gating**: the generator inspects the system locale before invoking Nano; if the locale is NOT in the Nano-supported set (v1 support list: `en-*`), the generator short-circuits to English generation (or the structured fallback) and sets `DayHeaderResult.generationLocale = "en"` while the card renders a small "Generated in English" indicator. The supported set lives in `com.capsule.app.ai.LocaleSupport.NANO_SUPPORTED_LOCALES` so it can expand without touching callers. Covered by T046 locale parameterisation.
- [ ] T049 [P] [US2] Create `com.capsule.app.diary.DiaryViewModel` — binds `IEnvelopeRepository`, calls `observeDay`, applies `ThreadGrouper` and `DayHeaderGenerator`, exposes `StateFlow<DayUiState>` (loading/ready/empty/error)
- [ ] T050 [P] [US2] Create Compose screen `com.capsule.app.diary.ui.DiaryScreen` — **unbounded** horizontal day pager over a sparse list of **non-empty days** returned by `IntentEnvelopeDao.distinctDayLocalsWithContent()` (per FR-010 + spec.md Clarification Q1: backscroll is unlimited, empty days are skipped so the user never pages through silence). Today's page is always rendered first even when the count is zero (empty-state copy comes from T054). Implementation uses `HorizontalPager` with `pageCount = Int.MAX_VALUE`; page-index-to-day mapping is backed by a `DiaryPagingSource` that paginates older non-empty days in batches of 30 as the user swipes left. Sticky day header paragraph + thread-grouped envelope cards.
- [ ] T051 [P] [US2] Create Compose `com.capsule.app.diary.ui.EnvelopeCard` — surfaces intent chip, `from {appCategory} · {activityState} · {localTime}`, title + summary slot (populated only after US3 continuation runs), content preview. **Tap-to-reassign**: tapping the intent chip opens the same 4-chip row UI used by the overlay (extracted from `BubbleUI` T039 into a reusable `com.capsule.app.ui.IntentChipRow` composable); picking a new intent calls `IEnvelopeRepository.reassignIntent(envelopeId, newIntent)` with `IntentSource.DIARY_REASSIGN`. Supports AMBIGUOUS → any intent **and** any intent → any other intent; the supersession chain is preserved in `intentHistoryJson` by T033. Per FR-007.
- [ ] T052 [US2] Create `com.capsule.app.diary.DiaryActivity` with `android:exported="true"` + `android.intent.action.MAIN` / `LAUNCHER` (takes over launcher role); hosts `DiaryScreen`; demote existing `MainActivity` to a deep-link-only internal activity
- [ ] T053 [US2] Edit `AndroidManifest.xml`: move `LAUNCHER` + `MAIN` intent filters from `MainActivity` to `DiaryActivity`; remove `MainActivity`'s launcher role (per plan.md §Source Code)
- [ ] T054 [US2] Add empty-day state to `DiaryScreen` with copy "Nothing captured yet today. Orbit is watching." (quickstart.md §3 acceptance)
- [ ] T055 [US2] Pre-warm the `:ml` process bind in `DiaryActivity.onCreate` so the first `observeDay` call meets the ≤ 1s p50 target (plan.md §Technical Context performance)

**Checkpoint**: Diary renders today with real envelopes from US1; updates within 500ms when a new envelope is sealed from the overlay.

---

## Phase 5: User Story 3 — URL Continuation (Priority: P1)

**Goal** (spec.md §US3): On charger + unmetered Wi-Fi, Orbit turns captured URLs into title + 2–3 sentence summary and surfaces them in Diary, within 30s p95 of constraints being met.

**Independent Test**: Capture a URL on cellular → put device on charger + Wi-Fi → wait ≤ 2 min → open Diary → envelope card shows title + summary + domain; audit log contains one `NETWORK_FETCH` and one `CONTINUATION_COMPLETED`.

### Contract & integration tests (US3)

- [ ] T056 [P] [US3] `app/src/androidTest/java/com/capsule/app/net/NetworkGatewayContractTest.kt` — every rejection path from contracts/network-gateway-contract.md §4 (invalid_url, not_https, blocked_host `10.0.0.1`, too_large via streaming mock, redirect_loop) returns the right `FetchResultParcel.errorKind`; success path returns non-null `readableHtml`
- [ ] T057 [P] [US3] `…/net/NoRefererNoCookiesTest.kt` — MITM proxy in test asserts outgoing request has no `Referer` header and an empty cookie jar
- [ ] T058 [P] [US3] `…/net/UidCheckTest.kt` — a fake UID attempting to bind `NetworkGatewayService` is rejected (contracts/network-gateway-contract.md §2)
- [ ] T059 [P] [US3] `app/src/androidTest/java/com/capsule/app/continuation/UrlHydrateWorkerTest.kt` — enqueue with `RequiresCharging + UNMETERED`; with faked gateway success, writes `ContinuationResultEntity` with title+summary; with 503, retries up to 3× with exponential backoff; with `blocked_host`, no retries

### Implementation (US3)

- [ ] T060 [P] [US3] Create `com.capsule.app.net.SafeOkHttpClient` — OkHttp client with `CookieJar.NO_COOKIES`, cache disabled, max 5 redirects with https-only hop enforcement, 2 MB body cap via `RequestBody.source().buffer()` + counting sink, fixed `User-Agent: Orbit/1.0 (Android; local-first reader)`, timeout 10s default / 15s max (contracts/network-gateway-contract.md §4)
- [ ] T061 [P] [US3] Create `com.capsule.app.net.UrlValidator` — rejects non-https, private/local/link-local hosts, non-standard ports, malformed URIs
- [ ] T062 [P] [US3] Create `com.capsule.app.net.ReadabilityExtractor` — jsoup + Readability4J wrapping the fetched HTML → `(title, readableHtml)` ≤ 200 KB with `<script>/<style>/onclick` stripped
- [ ] T063 [US3] Create `com.capsule.app.net.NetworkGatewayImpl` in `:net` that binds the pieces above and exposes the `INetworkGateway.Stub` wired to `NetworkGatewayService` from T025; enforces calling-UID check; fires an `IAuditLog` callback for `NETWORK_FETCH` on completion (contracts/network-gateway-contract.md §5)
- [ ] T064 [P] [US3] Create prompt `com.capsule.app.ai.prompts.UrlSummaryPrompt.kt` (2–3 neutral sentences, no speculation, no PII fabrication)
- [ ] T065 [P] [US3] Create `com.capsule.app.ai.NanoSummariser` wrapping AICore summarisation with graceful-degrade (returns null, caller stores `summaryModel="fallback"`)
- [ ] T066 [US3] Create `com.capsule.app.continuation.UrlHydrateWorker` (CoroutineWorker) — binds `INetworkGateway`, calls `fetchPublicUrl`, runs Readability (cross-process step happens in `:ml`), calls `NanoSummariser`, writes `ContinuationResultEntity` (including `canonicalUrlHash` computed from the original URL), updates `ContinuationEntity.status`, writes `CONTINUATION_COMPLETED`/`FAILED` audit rows (contracts/continuation-engine-contract.md §4.1)
- [ ] T066a [US3] Create `com.capsule.app.net.CanonicalUrlHasher` — pure function `hash(rawUrl: String): String` that produces `sha256(canonicalize(url))` where `canonicalize` lowercases host, strips fragment, strips `utm_*` + `fbclid` + `gclid` query params, strips trailing slash on path, and sorts remaining query params lexicographically. Plus implement URL-hash dedupe in `EnvelopeRepositoryImpl.seal` (T033): when a draft carries a URL, compute `canonicalUrlHash`, query `ContinuationResultDao.findByCanonicalUrlHash(hash)`. **On hit**: set `envelope.sharedContinuationResultId` to the existing result's id, do NOT enqueue `URL_HYDRATE`, write one `URL_DEDUPE_HIT` audit row. **On miss**: enqueue `URL_HYDRATE` as before; on its success (T066) the resulting `ContinuationResultEntity` stores the hash so future captures of the same URL hit the cache. Per FR-013 + spec.md Clarification Q2.
- [ ] T066b [P] [US3] `app/src/androidTest/java/com/capsule/app/data/UrlHashDedupeContractTest.kt` — Scenario A: seal envelope A with URL `https://example.com/a?utm_source=twitter` → one `URL_HYDRATE` enqueued. After the worker completes, seal envelope B with URL `https://example.com/a?utm_source=email` → zero new `URL_HYDRATE` enqueued, `B.sharedContinuationResultId == A.sharedContinuationResultId`, one `URL_DEDUPE_HIT` audit row exists. Scenario B: seal envelope C with URL `https://example.com/different` → normal hydration (no dedupe hit).
- [ ] T067 [P] [US3] Create `com.capsule.app.continuation.ContinuationEngine` — `enqueueForNewEnvelope`, `retry`, `cancelAll`; enqueues with `RequiresCharging`, `UNMETERED`, `BatteryNotLow`, exponential backoff starting 60s, max 3 attempts (contracts/continuation-engine-contract.md §2, §3, §4)
- [ ] T068 [US3] Wire `ContinuationEngine.enqueueForNewEnvelope` into `EnvelopeRepositoryImpl.seal()` from T033 — same transaction as the envelope write
- [ ] T069 [US3] Update `EnvelopeCard` (T051) to display title + summary + domain + "Couldn't enrich this link. Try again" (with menu action that calls `ContinuationEngine.retry`) for `FAILED_MAX_RETRIES`
- [ ] T070 [US3] Add "Pause continuations" switch in `com.capsule.app.settings.SettingsScreen` that calls `ContinuationEngine.cancelAll("user_paused")` and blocks new enqueues; writes `PRIVACY_PAUSED` / `PRIVACY_RESUMED` audit rows (contracts/continuation-engine-contract.md §6)

**Checkpoint**: Full golden-path flow from quickstart.md §3 works. MVP (P1) is shippable at this point.

---

## Phase 6: User Story 4 — Ambient Screenshot Capture (Priority: P2)

**Goal** (spec.md §US4): Screenshots are silently turned into envelopes with OCR-extracted text; any URLs found are hydrated like in US3. No interruption to the user.

**Independent Test**: Take 3 screenshots using the device shortcut, do not open Orbit; later, Diary today shows 3 new envelopes with `contentType=IMAGE`; screenshots containing URLs have hydrated link cards.

### Contract & integration tests (US4)

- [ ] T071 [P] [US4] `app/src/androidTest/java/com/capsule/app/capture/ScreenshotObserverTest.kt` — simulate a MediaStore insertion under `Pictures/Screenshots/`, assert `EnvelopeRepositoryImpl.seal` is called with `ContentType.IMAGE` and the correct content URI
- [ ] T072 [P] [US4] `…/continuation/ScreenshotUrlExtractWorkerTest.kt` — fixture image with a readable URL → `ContinuationResultEntity` contains the extracted URL + one follow-up `URL_HYDRATE` continuation enqueued; fixture image with no URL → status `SUCCEEDED_EMPTY`

### Implementation (US4)

- [ ] T073 [P] [US4] Create `com.capsule.app.capture.ScreenshotObserver` — a `ContentObserver` registered on `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with a path filter for `Pictures/Screenshots/` and `DCIM/Screenshots/`; lifecycle tied to `CapsuleOverlayService` (research.md §Screenshot Observation)
- [ ] T074 [US4] Register `ScreenshotObserver` in `CapsuleOverlayService.onCreate`/unregister in `onDestroy`; on new image event, call `StateSnapshotCollector` and `EnvelopeRepositoryImpl.seal()` with `ContentType.IMAGE` + URI
- [ ] T075 [P] [US4] Create `com.capsule.app.ai.OcrEngine` — ML Kit text recognition wrapper with on-device-only option, returns `List<TextBlock>`
- [ ] T076 [P] [US4] Create `com.capsule.app.continuation.ScreenshotUrlExtractWorker` — loads the image via `ContentResolver` read-only, runs `OcrEngine`, extracts URLs via the same `UrlValidator` regex, stores OCR text in `ContinuationResultEntity.extraJson`, enqueues a `URL_HYDRATE` continuation per unique URL, writes `OCR_RUN` audit row (contracts/continuation-engine-contract.md §4.2)
- [ ] T077 [US4] Extend `ContinuationEngine.enqueueForNewEnvelope` to fan `ContentType.IMAGE` into `ScreenshotUrlExtractWorker` + any downstream `URL_HYDRATE`
- [ ] T078 [US4] Extend `EnvelopeCard` (T051) to show a thumbnail for `IMAGE` envelopes and, when OCR'd URLs exist, a nested "linked article" block

**Checkpoint**: Take a screenshot with a visible URL while charging + unmetered. Diary today shows the image envelope with a nested link card populated within 60s.

---

## Phase 7: User Story 5 — Contextual Envelope Labels (Priority: P2)

**Goal** (spec.md §US5): Every envelope card displays its `StateSnapshot` as `from {appCategory} · {activityState} · {localTime}`, so the Diary reads as a narrative.

**Independent Test**: Capture while in Instagram+still and Chrome+walking. Diary cards show the two state labels correctly, and they persist across re-opens.

### Contract & integration tests (US5)

- [ ] T079 [P] [US5] `app/src/test/java/com/capsule/app/capture/StateSnapshotCollectorTest.kt` — fake `UsageStatsManager` + Activity Recognition samples; verify `AppCategory` mapping table covers the top 30 apps; missing data yields `AppCategory.UNKNOWN` / `ActivityState.STILL` (not `null`) per research.md §State Signal Collection

### Implementation (US5)

- [ ] T080 [P] [US5] Ship an internal `com.capsule.app.capture.AppCategoryMap.kt` mapping the top 30 Android apps (social, messaging, browsing, shopping, reading, video, music, finance, productivity, other); unknowns fall back to `AppCategory.OTHER`
- [ ] T081 [P] [US5] Create `com.capsule.app.capture.ActivityRecognitionClient` — thin wrapper over `ActivityRecognitionClient` + `ActivityTransitionRequest`; emits the latest observed `ActivityState`; graceful-degrade returns `ActivityState.STILL`
- [ ] T082 [US5] Wire `AppCategoryMap` into `StateSnapshotCollector` from T037 so raw package names are discarded after categorisation (principle VIII — Collect Only What You Use)
- [ ] T083 [US5] Update `EnvelopeCard` (T051) to render the full `from {appCategory} · {activityState} · {localTime}` label with proper pluralisation and RTL support
- [ ] T084 [US5] Add graceful fallback: if `PACKAGE_USAGE_STATS` not granted, card reads `from an app · {activityState} · {localTime}` without leaking the package name; if `ACTIVITY_RECOGNITION` not granted, drops the activity segment entirely

**Checkpoint**: Two cards side-by-side in Diary visibly show different context labels; toggling off either permission via Settings degrades gracefully without crashes.

---

## Phase 8: User Story 6 — Audit Log (Priority: P2)

**Goal** (spec.md §US6): "What Orbit did today" is accurate, user-readable, and covers every Nano call, network fetch, envelope mutation.

**Independent Test**: Use the app for an hour, open *What Orbit did today*. Counts match actions taken; tapping a row shows human-readable description; audit data never leaves the device.

### Contract & integration tests (US6)

- [ ] T085 [P] [US6] `app/src/androidTest/java/com/capsule/app/data/ipc/AuditLogContractTest.kt` — covers contracts/audit-log-contract.md §9: seal + reassign + archive + delete each produce exactly one audit row; 10 fetches produce 10 NETWORK_FETCH + 10 CONTINUATION_* rows; retention worker removes > 90d entries; export writes the bracket pair
- [ ] T086 [P] [US6] `…/AuditLogRetentionWorkerTest.kt` — seed 100 rows dated 91d ago + 10 fresh rows → worker leaves 10 rows; writes no audit-of-audit row (retention is silent)

### Implementation (US6)

- [ ] T087 [P] [US6] Create `com.capsule.app.audit.AuditLogImpl` implementing `IAuditLog.Stub` — `entriesForDay`, `entriesForEnvelope`, `countForDay`; bounded at 1000 rows per parcel (contracts/audit-log-contract.md §5)
- [ ] T088 [US6] Expose `IAuditLog` binder from `EnvelopeRepositoryService` as a companion binder (service returns both via `onBind` branching on action)
- [ ] T089 [P] [US6] Create `com.capsule.app.audit.AuditLogRetentionWorker` — daily periodic `CoroutineWorker`, deletes rows older than 90 days (contracts/audit-log-contract.md §2)
- [ ] T089a [P] [US6] Create `com.capsule.app.continuation.SoftDeleteRetentionWorker` — daily periodic `CoroutineWorker` that hard-purges envelopes where `deletedAt < now() - 30 days` along with (a) their `ContinuationEntity` rows, (b) their `AuditLogEntry` rows tied via `envelopeId`, and (c) any `ContinuationResultEntity` row no longer referenced by any envelope via `sharedContinuationResultId` (GC step preserves results still linked to live envelopes). Writes one `ENVELOPE_HARD_PURGED` audit row per purged envelope. Per FR-022 + spec.md Clarification Q5.
- [ ] T089b [P] [US6] `app/src/androidTest/java/com/capsule/app/continuation/SoftDeleteRetentionWorkerTest.kt` — seed envelope X soft-deleted 31 days ago + envelope Y soft-deleted 5 days ago → worker purges X only; assert X's continuations and cross-referenced audit rows are gone; assert one `ENVELOPE_HARD_PURGED` audit row was written; assert Y still exists. GC variant: seed envelopes Z1+Z2 sharing a `ContinuationResultEntity` R, soft-delete Z1 31d ago → worker purges Z1 but R survives because Z2 still references it; soft-delete Z2 31d ago → worker purges Z2 and R.
- [ ] T090 [US6] Register `AuditLogRetentionWorker` in `CapsuleApp.onCreate` as a unique periodic work
- [ ] T090a [US6] Register `SoftDeleteRetentionWorker` (T089a) in `CapsuleApp.onCreate` alongside `AuditLogRetentionWorker` as a separate unique periodic work named `orbit.soft_delete_retention`
- [ ] T091 [P] [US6] Create Compose `com.capsule.app.settings.AuditLogScreen` — day picker (today / yesterday / last 7 days), grouped counts (captures, enrichments, network fetches, Nano summaries, archives), tap-to-expand row list, details drawer with pretty-printed `extraJson` + "Open envelope" deep link (contracts/audit-log-contract.md §8)
- [ ] T091a [P] [US6] Create Compose `com.capsule.app.settings.TrashScreen` — lists soft-deleted envelopes from `IEnvelopeRepository.listSoftDeletedWithinDays(30)` sorted newest-deleted first; each row shows envelope content preview, `deletedAt` formatted as "deleted {N} days ago", and `willPurgeAt = deletedAt + 30d` formatted as "auto-purges in {M} days". Two actions per row: **Restore** (calls `IEnvelopeRepository.restoreFromTrash(id)`, envelope disappears from Trash and reappears in Diary) and **Purge now** (calls `IEnvelopeRepository.delete(id, hardPurge = true)` — a forthcoming overload that bypasses soft-delete and is audit-logged as `ENVELOPE_HARD_PURGED` with `reason = "user_purge"`). Per FR-022 + spec.md Clarification Q5.
- [ ] T091b [US6] Add a `SettingsScreen` entry point "Trash ({count}) →" that navigates to `TrashScreen` — count uses `IEnvelopeRepository.countSoftDeletedWithinDays(30)` (T016). Adjacent to the existing "What Orbit did today →" entry.
- [ ] T092 [US6] Add a `SettingsScreen` entry point "What Orbit did today →" that navigates to `AuditLogScreen`
- [ ] T093 [P] [US6] Add `com.capsule.app.settings.ExportService` — user-triggered, writes `Downloads/Orbit-Export-<ts>/` with `envelopes.json`, `continuations.json`, `results.json`, `audit.json`, `README.md`; writes `EXPORT_STARTED`/`EXPORT_COMPLETED` audit rows (contracts/audit-log-contract.md §7)
- [ ] T094 [US6] Add "Export my data" action to `SettingsScreen`, with a confirmation that warns "Export is not encrypted."

**Checkpoint**: The audit log view accurately reflects the day's activity, export bundle validates, retention worker runs nightly.

---

## Phase 9: User Story 7 — Onboarding With Informed Consent (Priority: P3)

**Goal** (spec.md §US7): First-run flow asks for overlay, notifications, usage access, activity recognition — in plain language, with clear explanations of what declining loses.

**Independent Test**: Fresh install → time ≤ 60s → decline usage-access → app still captures, just without app-category labels.

### Contract & integration tests (US7)

- [ ] T095 [P] [US7] `app/src/androidTest/java/com/capsule/app/onboarding/OnboardingFlowTest.kt` — fresh state launches `OnboardingActivity`; declining permission 3 surfaces a graceful explanation and proceeds; accepting all transitions into DiaryActivity

### Implementation (US7)

- [ ] T096 [P] [US7] Create `com.capsule.app.onboarding.OnboardingActivity` with Compose host and 4 steps
- [ ] T097 [P] [US7] Step 1: notifications rationale + runtime request (`POST_NOTIFICATIONS`)
- [ ] T098 [P] [US7] Step 2: overlay rationale + `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` deep link
- [ ] T099 [P] [US7] Step 3: usage access rationale + `Settings.ACTION_USAGE_ACCESS_SETTINGS` deep link with polling-on-resume to detect grant (research.md §State Signal Collection)
- [ ] T100 [P] [US7] Step 4: physical activity rationale + runtime request (`ACTIVITY_RECOGNITION`)
- [ ] T101 [US7] Route `MAIN`/`LAUNCHER` to `OnboardingActivity` on first run (SharedPreferences flag `onboarding.completed=false`), else to `DiaryActivity`
- [ ] T102 [US7] Write `PERMISSION_GRANTED`/`PERMISSION_REVOKED` audit rows for each permission outcome (contracts/audit-log-contract.md §3)
- [ ] T103 [US7] Add "Grant later" escape on each step that marks `onboarding.completed=true` but keeps the missing-permission banner persistent in `DiaryScreen` until granted
- [ ] T103a [US7] On permission-denied for `SYSTEM_ALERT_WINDOW` **or** `POST_NOTIFICATIONS` (both are structurally required to run the foreground capture service), surface a Compose rationale modal (NOT the system dialog — explains the consequence: "Without this, Orbit cannot capture. You can still browse the Diary in read-only mode."). On second decline, set `SharedPreferences.orbit.reducedMode = true`, skip `startForegroundService(CapsuleOverlayService)`, and route `MAIN`/`LAUNCHER` to `ReducedModeActivity` instead of `DiaryActivity`. Write one `PERMISSION_REDUCED_MODE_ENTERED` audit row. Per FR-021.
- [ ] T103b [P] [US7] Create Compose `com.capsule.app.onboarding.ReducedModeActivity` — hosts `DiaryScreen` in read-only mode (no overlay, no new-capture FAB, no bubble service start); a persistent banner at the top reads "Capture is off. Tap to enable." → links to system permission settings. On resume, if both permissions are now granted, clear `reducedMode`, start `CapsuleOverlayService`, and hand off to `DiaryActivity`.

**Checkpoint**: Fresh install yields a 45-60s onboarding; declining optional permissions does not break core capture.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Lock down privacy invariants, close the loop on self-dogfood, and run the quickstart as a release gate.

- [ ] T104 [P] Add `SERVICE_STARTED` / `SERVICE_STOPPED` audit rows in `CapsuleOverlayService.onCreate`/`onDestroy` (contracts/audit-log-contract.md §3)
- [ ] T105 [P] Add local-only counters in `com.capsule.app.audit.DebugDumpReceiver` — captures, continuation success rate, diary open count; broadcast-driven dump handler for `com.capsule.app.DEBUG_DUMP` (dev builds only) per plan.md §Source Code
- [ ] T106 Gate `DebugDumpReceiver` behind `BuildConfig.DEBUG` so no production build exports counters
- [ ] T107 [P] Rename user-visible strings "Capsule" → "Orbit" in `app/src/main/res/values/strings.xml` and any XML (`android:label`, `android:description`); leave package names untouched per plan.md (addresses pending `rename_references` todo for strings-only)
- [ ] T108 [P] Update launcher label + monochrome icon to "Orbit" in `res/drawable/`, `res/mipmap-anydpi-v26/ic_launcher.xml`
- [ ] T109 [P] Add `README.md` pointer at repo root linking to `.specify/memory/PRD.md`, `.specify/memory/constitution.md`, and `specs/002-intent-envelope-and-diary/quickstart.md`
- [ ] T110 Run quickstart.md §3..§4.7 end-to-end on a physical Pixel; record results in `specs/002-intent-envelope-and-diary/acceptance-results.md`
- [ ] T110a [P] Create `app/src/androidTest/java/com/capsule/app/regression/Spec001SmokeTest.kt` — regression harness covering the 001 primitives per FR-025 so 002's overlay/service changes cannot silently break capture: (a) bubble drag end position is clamped to the screen edge within the 200ms animation window; (b) `ClipboardFocusStateMachine.resetToIdle()` restores `FLAG_NOT_FOCUSABLE` after the capture sheet collapses (001 Clarification 2026-04-17); (c) service survives an OEM-simulated process kill via `Process.killProcess(Process.myPid())` and re-appears within the AlarmManager restart window; (d) `CapsuleOverlayService.onCreate` catches `ForegroundServiceStartNotAllowedException` on a simulated A15 restricted launch and no crash is logged. Fails the release gate if any 001 primitive regresses.
- [ ] T111 [P] MITM-proxy acceptance run (quickstart.md §6): assert zero outgoing HTTP from any process besides `:net`, no `Referer`, no cookies; attach `mitm.log` to acceptance doc
- [ ] T112 [P] Memory leak check on overlay + `:ml` unbind path using Android Studio Profiler over a 10-min capture burst (50 envelopes) — document results
- [ ] T113 [P] Performance validation: assert p50 seal < 200ms, p95 URL hydration < 30s, Diary p50 render < 1s per plan.md §Technical Context; adjust debouncers if violated
- [ ] T114 Archive the deleted `app/sampledata/specs/001-core-capture-overlay/*` artifacts (already deleted from 001 — confirm they are not referenced by any build target)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no deps
- **Phase 2 (Foundational)**: depends on Phase 1 — **BLOCKS** all user stories
- **Phase 3 (US1 — Capture)**: depends on Phase 2
- **Phase 4 (US2 — Diary)**: depends on Phase 2; renders most usefully after Phase 3 but `DiaryScreen` can be developed against fake data from the start
- **Phase 5 (US3 — URL Continuation)**: depends on Phase 2; integrates with US1's seal and US2's EnvelopeCard
- **Phase 6 (US4 — Screenshots)**: depends on Phase 2; reuses US3's URL hydrate pipeline
- **Phase 7 (US5 — Context Labels)**: depends on Phase 2; enriches US2 cards — does not block Diary's initial render
- **Phase 8 (US6 — Audit)**: depends on Phase 2; completeness checked against US1/US3 writers
- **Phase 9 (US7 — Onboarding)**: depends on Phase 2; independent of other story phases
- **Phase 10 (Polish)**: depends on all P1 + P2 stories being complete

### Critical Path to MVP

Phase 1 → Phase 2 → Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3) → Phase 10 release gate. ~5–7 engineer-weeks solo.

### Parallel Opportunities

- Setup: T003, T004, T005, T006 all [P]
- Foundational: T011–T016, T018–T023, T026–T028 are [P] once T007–T010 are done
- US1: T029–T032 tests in parallel; T033–T037 impl in parallel; T038–T043 serialize on `OverlayViewModel` + `BubbleUI`
- US2: T044–T046 tests in parallel; T047–T051 impl in parallel; T052–T055 serialize on `DiaryActivity` / manifest
- US3: T056–T059 tests in parallel; T060–T062 + T064–T065 impl in parallel; T063 + T066 + T068 + T070 serialize on service wiring
- US4–US7 are largely independent of each other once Phase 2 is done and can be assigned to different contributors

---

## Parallel Example: User Story 3 (URL Continuation)

```bash
# Tests first — all in parallel
Task: "Contract test: NetworkGatewayContractTest"
Task: "Contract test: NoRefererNoCookiesTest"
Task: "Contract test: UidCheckTest"
Task: "Contract test: UrlHydrateWorkerTest"

# Core primitives — all in parallel
Task: "Implement SafeOkHttpClient in com.capsule.app.net"
Task: "Implement UrlValidator in com.capsule.app.net"
Task: "Implement ReadabilityExtractor in com.capsule.app.net"
Task: "Implement NanoSummariser in com.capsule.app.ai"
Task: "Author UrlSummaryPrompt in com.capsule.app.ai.prompts"

# Serialize the wiring after the above
Task: "Wire NetworkGatewayImpl into NetworkGatewayService"
Task: "Implement UrlHydrateWorker"
Task: "Wire ContinuationEngine.enqueueForNewEnvelope into seal()"
```

---

## Implementation Strategy

### MVP First (P1: US1 + US2 + US3)

1. Phase 1 → Phase 2 → Phase 3 (US1) → STOP + VALIDATE: undo + seal + audit visible in `adb`
2. → Phase 4 (US2) → STOP + VALIDATE: Diary renders today with real envelopes
3. → Phase 5 (US3) → STOP + VALIDATE: golden path from quickstart.md §3 works on charger + unmetered
4. Decide whether to ship MVP or include P2 stories before first dogfood

### Incremental Delivery (P2 stories, any order)

5. Phase 6 (US4 — Screenshots): biggest-bang-for-buck because screenshots dominate Android "graveyard"
6. Phase 7 (US5 — Context labels): narrative Diary feel
7. Phase 8 (US6 — Audit): trust surface, required before inviting external dogfood

### Polish Before Public

8. Phase 9 (US7 — Onboarding): raises time-to-trust for first-time installs
9. Phase 10: rename strings, MITM audit, perf regression run, quickstart acceptance

---

## Notes

- [P] tasks touch different files and have no blocking dependencies on incomplete tasks; verify before parallelising
- Every user-story task carries its story tag to keep traceability in PRs and commit messages
- All contract tests MUST fail before their implementation tasks; this is enforced by ordering (test task ID < impl task ID)
- Commit at each checkpoint; never across phase boundaries
- Do not modify `com.capsule.app.net.*` outside its package — the `OrbitNoHttpClientOutsideNet` lint rule (T003) will refuse the build
- Any new `AuditAction` requires constitution-compliant review (contracts/audit-log-contract.md §3)
- Keep AIDL parcels ≤ 8 KB per envelope / ≤ 256 KB per day page (contracts/envelope-repository-contract.md §7) — enforced by a test in T044
