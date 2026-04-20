# Cloud Boost — Orbit-Managed LLM + BYOK (v1.1)

**Status**: DRAFT — targets v1.1
**Depends on**: spec 002 complete (LlmProvider abstraction from tasks 002/T025a–T025b); spec 006 (Orbit Cloud identity + quota backend for Orbit-managed proxy)
**Governing document**: `.specify/memory/constitution.md` — implements Principles IX, X, XI
**Created**: 2026-04-17
**Last amended**: 2026-04-20 (added Orbit-managed LLM proxy as default cloud path and consent-aware prompt assembly per constitution v3.0.0)

---

## Summary

Cloud Boost has two modes, both opt-in per user and per capability:

1. **Orbit-managed LLM proxy (default cloud path).** Users with Orbit
   Cloud (spec 006) enabled can route capabilities through
   Orbit-operated LLM endpoints without supplying an API key. Orbit
   pays for the inference and meters usage against the user's plan;
   the user never sees a vendor key. Prompts are assembled on-device
   through the Principle XI consent filter before crossing into
   `:net`, so the managed proxy never receives `local_only` facts or
   unredacted sensitive content.

2. **BYOK (power-user path).** A user who prefers to run on their own
   vendor account pastes an API key (Gemini, OpenAI, Anthropic,
   OpenRouter, or any OpenAI-compatible endpoint). From that moment
   the chosen capability routes through the user's own cloud account
   at the user's cost, with every call auditable and a one-tap return
   to on-device.

Defaults never change. Users who never touch the Cloud Boost settings
page see zero behaviour change from v1.

---

## User Stories

### US-005-001 — Power user brings their own key (Priority: P1)

As a user who wants higher-quality summaries and already has a Gemini API key, I open Settings → Cloud Boost, pick "Google Gemini" from a provider dropdown, paste my key, and toggle "URL summaries → Cloud." From that moment forward, URL summary continuations route through my own Gemini account; my intent classification, day-header, and sensitivity scans still run locally on Nano because I didn't enable those toggles.

**Acceptance**:
1. Pasting an invalid key surfaces a clear error from "Test connection" and does not persist.
2. Pasting a valid key persists it encrypted (Keystore + EncryptedSharedPreferences).
3. Toggling URL summaries to Cloud routes the next URL hydration continuation through the `:net` cloud path; audit log shows `CONTINUATION_COMPLETED` with `llmProvider=google_gemini`, `llmModel=gemini-2.5-flash`, and a prompt digest.
4. Toggling the same setting back to Nano falls back cleanly; subsequent continuations log `llmProvider=local_nano`.
5. Removing the key from Settings disables every cloud toggle and clears the key from storage.

### US-005-002 — Provider-agnostic BYOK (Priority: P1)

As a user who prefers OpenAI, I switch the provider dropdown to "OpenAI," paste my key, and everything keeps working. Same again with Anthropic, OpenRouter, and any OpenAI-compatible custom endpoint.

**Acceptance**:
1. All of Google Gemini, OpenAI, Anthropic, OpenRouter, and a "Custom OpenAI-compatible" option are supported out of the box.
2. Custom endpoint accepts base URL + model name fields and uses standard OpenAI-compatible chat/completions schema.
3. Each provider's per-capability behavior is equivalent; switching providers does not lose the per-capability toggle state.

### US-005-003 — First-time cloud consent (Priority: P1)

As a user about to enable cloud for the first time, I see a clear one-time dialog explaining *exactly* which data will leave my device for which capability when I tap Enable.

**Acceptance**:
1. The first time any capability toggle is flipped to Cloud, a modal appears explaining: "Enabling cloud for {capability} will send {what} to {provider}. They may log this according to their privacy policy. Orbit cannot guarantee privacy once data leaves your device."
2. The modal has Cancel / Enable buttons; Cancel returns the toggle to Nano with no side effects.
3. The modal does not repeat for subsequent toggles in the same session unless the user resets or 24 hours pass.

### US-005-004 — Transparency in the audit log (Priority: P1)

As a user who enabled Cloud Boost, I want every cloud call visible in *What Orbit did today* so I can verify what's going where.

**Acceptance**:
1. Every cloud LLM call produces exactly one audit log row with `llmProvider`, `llmModel`, capability name, prompt digest (SHA-256 of prompt), token count, success/failure, latency.
2. The audit log row shows a redacted 80-char prompt preview, not the full prompt.
3. Clicking a cloud audit row shows full details in a drawer including "Retry locally on Nano" option.

### US-005-005 — Graceful failure mode (Priority: P2)

As a user whose cloud provider is down or whose API key has expired, I want the system to fall back cleanly to Nano rather than failing silently.

**Acceptance**:
1. On cloud call failure (network, auth, rate limit), the system retries once with exponential backoff, then falls back to Nano for that call.
2. The audit row records `cloud_attempted=true, cloud_succeeded=false, fallback=local_nano` and the failure reason.
3. After 3 consecutive failures for the same provider, a non-blocking banner in Settings warns the user.

---

## Non-Goals (v1.1)

- Orbit never operates its own LLM keys. There is no "Orbit Plus" tier that bills for cloud inference.
- No streaming responses in v1.1 (add later if Ask Orbit demands it).
- No self-hosted LLM endpoints beyond the "Custom OpenAI-compatible" option.
- No per-capability cost estimation or budgeting. (Users monitor usage in their provider's dashboard.)

---

## Functional Requirements

### Key and provider handling (BYOK)

- **FR-005-001**: System MUST store BYOK provider keys encrypted via Android Keystore-wrapped `EncryptedSharedPreferences` scoped to the `:net` process.
- **FR-005-002**: BYOK keys MUST NEVER leave the `:net` process over any IPC or binder boundary; `:ml` never sees the key, `:ui` never sees the key, `:agent` never sees the key.
- **FR-005-003**: System MUST expose a `ByokLlmProvider` implementation of the `LlmProvider` interface (002 T025a) that routes each method through a new `:net` binder entrypoint `INetworkGateway.callUserLlm(provider, prompt, capability)`.
- **FR-005-005**: System MUST validate any new/changed BYOK key via a low-cost round trip before persisting, presenting the full request body to the user on that round trip so they see what leaves the device.

### Orbit-managed LLM proxy

- **FR-005-010**: System MUST expose an `OrbitManagedLlmProvider` implementation of `LlmProvider` (002 T025a) that routes each method through a `:net` binder entrypoint `INetworkGateway.callOrbitLlm(model, prompt, capability)`. Available only when Orbit Cloud (spec 006) is enabled and the user is signed in.
- **FR-005-011**: Orbit-managed calls MUST authenticate with the user's Orbit identity token (not an API key). Tokens live in `:net` only and are never exposed to `:ml`, `:ui`, or `:agent`.
- **FR-005-012**: Orbit MUST meter managed-proxy usage against the user's plan (recorded in `usage_meter`, spec 006) and return structured quota-exceeded errors that trigger graceful fallback (FR-005-009).
- **FR-005-013**: Orbit MUST NOT retain prompt content or model outputs beyond what is required for real-time inference. No training on user prompts. Transparency report (spec 006 FR-006-017) MUST include aggregate managed-proxy call counts.

### Consent-aware prompt assembly (Principle XI)

- **FR-005-014**: Every prompt destined for any cloud LLM (Orbit-managed or BYOK) MUST be assembled in the `:agent` process (spec 008) and pass the four-point consent filter before crossing the `:net` boundary: strip all `local_only` facts; verify consent freshness for the sensitivity tier; check sensitivity against the chosen provider's policy; verify necessity (prompt is minimally scoped to the capability's need).
- **FR-005-015**: The consent filter MUST be a hard gate. A failed check returns the prompt to the planner with a reason code; no cloud call is made with an unfiltered prompt.
- **FR-005-016**: Audit rows for cloud calls MUST include a `consent_filter_hash` covering the filter policy version and the set of redactions applied, so auditors can reconstruct why a given prompt was or was not allowed.

### Toggles, defaults, audit, fallback

- **FR-005-004**: System MUST gate each capability with an independent user toggle persisted in SharedPreferences; the default for every toggle is `LOCAL_NANO`. Available routes per capability: `LOCAL_NANO | ORBIT_MANAGED | BYOK(provider)`.
- **FR-005-006**: System MUST populate the audit log `llmProvider`, `llmModel`, `promptDigestSha256`, `tokenCount` columns (002 T025e) on every cloud call. `llmProvider` values include `local_nano`, `orbit_managed`, and BYOK vendor names.
- **FR-005-007**: System MUST display a first-use warning modal the first time any capability is flipped to `ORBIT_MANAGED` or BYOK. The modal distinguishes Orbit-managed (Orbit's inference, your identity, metered) vs BYOK (your vendor, your key, your bill).
- **FR-005-008**: System MUST provide a single "Disable all cloud" kill switch in Settings that flips every capability toggle back to `LOCAL_NANO` and clears all BYOK keys with a confirmation.
- **FR-005-009**: Cloud call failures (managed or BYOK) MUST fall back to Nano with the fallback recorded in the audit log; 3 consecutive failures for the same provider MUST surface a Settings banner.

---

## Dependencies

- 002 T025a–T025e (`LlmProvider`, provenance, audit columns)
- 002 T025 (`NetworkGatewayService` in `:net`)
- New: `INetworkGateway.callUserLlm(provider, prompt, capability) → LlmCallResult` binder method
- New: `com.capsule.app.settings.CloudBoostScreen` Compose UI
- New: `com.capsule.app.net.UserLlmClient` with per-provider adapters (Gemini, OpenAI, Anthropic, OpenRouter)

---

## Open Questions

- Do we allow more than one provider configured at once (e.g., Gemini for URL summaries, OpenAI for Ask Orbit)? Leaning yes — each capability picks a provider.
- Do we expose system prompts as user-editable power-user setting? Leaning no for v1.1, yes once Ask Orbit ships.
- Do we need an "export redacted version of the prompt" feature for users debugging? Leaning yes.

---

*Targeted for v1.1 after v1 stabilizes. Requires 002/T025a–T025e + 002/T025 foundations.*
