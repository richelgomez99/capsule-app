# Specification Quality Checklist: Cloud LLM Routing + Supabase Backbone (Day 1)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-04-28
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - **Note**: This is a refactor spec for existing Kotlin/Android/Supabase infrastructure. Naming
    existing types (`LlmProvider`, `INetworkGateway`, `RuntimeFlags`, etc.) and required wire
    formats (kotlinx.serialization JSON, Postgres CHECK, RLS) is necessary to specify the work
    unambiguously. Provider/model identifiers are externalised at the Edge Function (not in scope
    here); the mobile client is provider-agnostic per FR-013-007.
- [x] Focused on user value and business needs
  - User here = Orbit engineers (Phase 11 Block 4+ unblocking) and Orbit alpha users (RLS
    isolation guarantee). Both framed in user stories.
- [x] Written for non-technical stakeholders
  - **Partial**: this is an internal infrastructure spec; the audience is engineering. Stakeholder
    accessibility is achieved by the Summary, ADR cross-references, and Risk section rather than
    by avoiding technical terms entirely.
- [x] All mandatory sections completed (User Scenarios, Requirements, Success Criteria,
  Assumptions; Key Entities included; Risks added)

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable (shell-command-verifiable for SC-001, SC-002, SC-003,
  SC-006, SC-007; runtime-verifiable for SC-004; behavioural for SC-005, SC-008, SC-009)
- [x] Success criteria are technology-agnostic where possible
  - **Note**: SC-002, SC-003, SC-006 reference the build tool and SQL surface because the spec is
    a refactor on a fixed stack. The user-visible outcomes (no behaviour regression, RLS proven)
    are the actual success bar; the commands are the proof mechanism.
- [x] All acceptance scenarios are defined (US1, US2, US3 each have 4–5 Given/When/Then scenarios)
- [x] Edge cases are identified (8 edge cases including network unavailable, embed null parity,
  missing auth, hardware mismatch, AIDL drift, FloatArray equality, 5xx storm, RLS bypass risk)
- [x] Scope is clearly bounded (explicit in-scope morning/evening blocks; explicit out-of-scope
  list of 7+ deferred items)
- [x] Dependencies and assumptions identified (Assumptions section; Risks section with 6 entries;
  ADR-003, ADR-006, ADR-007 cross-references)

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria (FR-013-001 through FR-013-027,
  each phrased as MUST/MUST NOT with verifiable conditions)
- [x] User scenarios cover primary flows (engineer-flips-flag; cloud-mode-via-AIDL; multi-user
  smoke test)
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification beyond what is necessary to specify a
  refactor on existing infrastructure

## Notes

- This spec is a Day-1 execution slice of a larger pivot plan. By design it ships an abstraction
  with stub/canned downstream behaviour; the Edge Function and real provider calls land in
  separate specs. SC-007 (multi-user smoke test PASS) is the hard alpha gate per ADR-007.
- All three user stories are P1 because the abstraction is incomplete without all three landing
  together: routing without AIDL extension is a half-refactor; AIDL without RLS-proven Supabase
  cannot ship to alpha. The "independent test" property is preserved within each story (each can
  be verified independently of the others) even though the release gate requires all three.
- Constitution Principle II requires an amendment to permit cloud-mode `LlmProvider`
  implementations to touch the network. This is tracked in R-013-006 and is out-of-band of this
  spec.
