<!--
  Sync Impact Report
  ===================
  Version change: N/A → 1.0.0 (initial ratification)
  Added principles:
    - I. Privacy-First, On-Device by Default
    - II. Phased Gate Execution (NON-NEGOTIABLE)
    - III. OEM-Hostile Survival Design
    - IV. Policy as Architecture
    - V. Graceful Degradation Across Hardware Tiers
    - VI. Compose-Over-Overlay Lifecycle Discipline
    - VII. Explicit Capture Only
  Added sections:
    - Technology Stack & Platform Constraints
    - Phased Development Workflow
  Templates requiring updates:
    - .specify/templates/plan-template.md ✅ no conflicts (generic structure)
    - .specify/templates/spec-template.md ✅ no conflicts (generic structure)
    - .specify/templates/tasks-template.md ✅ no conflicts (generic structure)
  Follow-up TODOs: none
-->

# Capsule Constitution

## Core Principles

### I. Privacy-First, On-Device by Default

All user data — clipboard captures, text snippets, embeddings, and
metadata — MUST be stored exclusively on-device by default. No data
leaves the device unless the user explicitly opts into a cloud-backed
intelligence tier and provides affirmative consent per session.
Rationale: Capsule is a Personal Knowledge Management agent. User trust
is the product. Every architectural decision MUST default to the most
private option and require explicit user action to relax that posture.

### II. Phased Gate Execution (NON-NEGOTIABLE)

Implementation follows four strictly ordered phases (Overlay → Persistence
→ Inbox UI → Intelligence Engine). Code for Phase N+1 is FORBIDDEN until
Phase N is compiled, deployed to a physical Android 13+ device, and every
verification checklist item is manually confirmed. No exceptions.
Rationale: Each phase is a blocking dependency for the next. Skipping
ahead creates integration debt that compounds on a platform where overlay
lifecycle, foreground service survival, and clipboard access are fragile
OS-level contracts.

### III. OEM-Hostile Survival Design

The foreground service MUST be designed to survive aggressive OEM
battery-killing heuristics (Samsung, Xiaomi, Huawei, OnePlus, Oppo,
Vivo, Realme). This means: idempotent restart logic, persistent state
recovery from SharedPreferences, manufacturer-specific battery
optimization guides surfaced to the user, and re-scheduling via
AlarmManager on task removal. The system MUST NOT assume the OS will
honor foreground service priority.
Rationale: Android OEM skins routinely kill foreground services.
Capsule's core value proposition (persistent overlay) fails entirely if
the service dies silently.

### IV. Policy as Architecture

Google Play policy constraints (SYSTEM_ALERT_WINDOW, foreground service
type declarations, clipboard access rules, AccessibilityService
restrictions, VpnService restrictions, Data Safety reporting) are
first-class architectural requirements, not afterthoughts. Every feature
that touches a sensitive API surface MUST be evaluated against current
Play policy before design begins. AccessibilityService and VpnService
MUST NOT be used for passive data collection.
Rationale: A Play Store rejection or removal after launch is a
catastrophic failure. Policy compliance is cheaper to design in than to
retrofit.

### V. Graceful Degradation Across Hardware Tiers

The architecture MUST define three device tiers (Baseline 4 GB /
Mid-range 6-8 GB / Flagship 8-12 GB) and gate AI features accordingly.
Tier A devices MUST receive full capture and persistence without AI.
Tier B devices MUST support on-device embeddings and vector search.
Tier C devices MAY run local SLM inference. No tier may crash or ANR
due to features designed for a higher tier.
Rationale: Capsule targets Android 13+ which spans a wide hardware
range. A flagship-only app excludes the majority of the addressable
market.

### VI. Compose-Over-Overlay Lifecycle Discipline

All overlay UI MUST be rendered via Jetpack Compose inside a
WindowManager-managed ComposeView hosted by a LifecycleService. A
custom OverlayLifecycleOwner implementing LifecycleOwner,
ViewModelStoreOwner, and SavedStateRegistryOwner MUST be attached
before setContent is called. Lifecycle events MUST be driven manually
(ON_CREATE through ON_DESTROY) synchronized with overlay add/remove.
Rationale: Compose assumes Activity-hosted lifecycle owners. Without
manual wiring, recomposition, state restoration, and ViewModel scoping
all silently break, producing non-deterministic overlay behavior.

### VII. Explicit Capture Only

Capsule MUST NOT read clipboard, scrape browser content, or capture
any user data without an explicit user-initiated action (bubble tap,
share intent, or manual note entry). Background polling, invisible
overlays, ADB-granted permissions, and passive logging are prohibited.
The Android 13+ clipboard access toast is expected and MUST NOT be
suppressed.
Rationale: This is both a policy requirement (Play Store) and a trust
contract with the user. Silent data capture violates both.

## Technology Stack & Platform Constraints

- **Language**: Kotlin (latest stable, targeting Kotlin 2.x)
- **UI**: Jetpack Compose with Material 3 (dynamic color on Android 12+)
- **Min SDK**: 33 (Android 13), Target SDK: 35
- **Architecture**: Single-activity for main app; LifecycleService for
  overlay; MVVM with StateFlow
- **Persistence**: Room (SQLite) with sqlite-vss extension for vectors
- **AI Runtime**: ONNX Runtime Mobile (onnxruntime-android) with
  NNAPI/XNNPACK acceleration
- **Background Work**: WorkManager for deferrable tasks; foreground
  service for overlay
- **Build**: Gradle with Kotlin DSL, version catalog (libs.versions.toml)
- **DI**: Manual construction in Phase 1-2; Hilt evaluated for Phase 3+
- **Foreground Service Type**: `specialUse` (no timeout, supports
  BOOT_COMPLETED; requires Play Store review justification)
- **Target Devices**: Physical Android 13+ devices; emulator acceptable
  only for UI layout — all verification gates require physical hardware

## Phased Development Workflow

Each feature progresses through the Spec Kit workflow:
`/speckit.specify` → `/speckit.plan` → `/speckit.tasks` →
`/speckit.implement`. Feature branches follow the pattern
`NNN-feature-name` where NNN is a zero-padded sequence number.

Phase transitions require a signed-off verification gate (see Phased
Spec Kit document). The gate sign-off includes: device model, Android
version, RAM, date, tester, and pass/fail for every checklist item.

Commits follow Conventional Commits: `feat:`, `fix:`, `docs:`,
`test:`, `chore:`. Each phase completion is tagged
`phase-N-verified`.

## Governance

This constitution supersedes all other development practices for the
Capsule project. Amendments require:

1. A written proposal documenting the change and its rationale.
2. Impact analysis against all seven principles.
3. Version bump following semantic versioning:
   - MAJOR: Principle removal or backward-incompatible redefinition.
   - MINOR: New principle or materially expanded guidance.
   - PATCH: Clarifications, wording, non-semantic refinements.
4. Update to this document with amended date.

All code reviews and spec reviews MUST verify compliance with these
principles. Complexity beyond what is specified MUST be justified
against a named principle.

**Version**: 1.0.1 | **Ratified**: 2026-04-15 | **Last Amended**: 2026-04-15

### Amendment Log

- **1.0.1** (2026-04-15): Changed FGS type from `dataSync` to `specialUse`.
  Rationale: `dataSync` imposes a 6-hour timeout on Android 15 and blocks
  BOOT_COMPLETED restart — both incompatible with a persistent overlay service.
  `specialUse` has no timeout and supports all required launch contexts.
  Impact: Strengthens Principle IV (Policy as Architecture). No principle
  violations.
