# T014-019b — Wire Supabase Kotlin SDK in `:net`

**Status**: PLAN — ready to implement; no user credentials required.
**Spec**: 014 (Edge Function LLM Gateway), follow-up to T014-018/019.
**Created**: 2026-04-29
**Blocks**: T014-021 (live E2E). Without this, every cloud LLM call returns `Error(UNAUTHORIZED)` because the production `SupabaseAuthStateBinder` is wired to a `() -> null` shim.

---

## Why this is a separate task

T014-018 deliberately landed `SupabaseAuthStateBinder(sessionAccessTokenProvider: () -> String?)` as a lambda shim because:

1. The Supabase Kotlin SDK was not in `gradle/libs.versions.toml` (verified absent).
2. Pulling a network-touching SDK in alongside the auth seam in the same task would have made T014-018 too large per atomic-commit discipline.
3. Constitution Principle II requires auth tokens live in `:net` only — the SDK initialization point matters and warrants its own design pass.

This document closes that decision and produces a single atomic commit `T014-019b`.

---

## Constitution implications

| Principle | Implication |
|-----------|-------------|
| **II — Process Isolation** | `SupabaseClient` MUST be initialised inside the `:net` process (in `NetworkGatewayService.onCreate()` or lazy in `NetworkGatewayImpl`). MUST NOT be reachable from `:ml`, `:ui`, `:capture`, or default. The only way `:ml` learns whether a session exists is by observing `Error(UNAUTHORIZED)` responses through the existing AIDL boundary. |
| **VI — Network Egress** | The Supabase SDK performs HTTPS for token refresh against `omohxxhsjrqpkwfxbkau.supabase.co`. Since it runs in `:net` (the only INTERNET-permitted process), this is compliant. Lint rule `OrbitNoHttpClientOutsideNet` (existing) protects against accidental imports in other processes. |
| **VII — Encrypted at Rest** | Supabase Kotlin SDK persists the refresh token by default in plain `SharedPreferences`. We MUST configure it to use `EncryptedSharedPreferences` (Android Keystore-wrapped, AES-256-GCM) — the SDK exposes a `SessionManager` interface for this. |
| **XIV — Bounded Observation** | The SDK MUST NOT log JWT contents. We pass our own logger that filters anything looking like a JWT (`eyJ...`). |

---

## Decision: SDK choice and version

**Pick**: `io.github.jan-tennert.supabase:gotrue-kt` (now part of `supabase-kt` v3.x line).

Rationale: only Kotlin-first Supabase SDK; covers Auth (Gotrue) without forcing the full Postgrest / Realtime / Storage modules we don't yet need; ktor-based so it composes with our existing OkHttp world via the Android engine.

**Version**: pin to latest stable 3.x at implement-time. Verify with `https://github.com/supabase-community/supabase-kt/releases` before pinning. Anticipated range: **3.1.x – 3.2.x** as of April 2026.

**Required Gradle additions** (in `gradle/libs.versions.toml`):

```toml
[versions]
supabaseKt = "3.X.Y"   # Pin at implement time
ktor = "2.3.X"         # Match supabase-kt's transitive ktor expectation

[libraries]
supabase-bom        = { group = "io.github.jan-tennert.supabase", name = "bom",      version.ref = "supabaseKt" }
supabase-gotrue-kt  = { group = "io.github.jan-tennert.supabase", name = "gotrue-kt" }
ktor-client-android = { group = "io.ktor",                        name = "ktor-client-android", version.ref = "ktor" }
```

In `app/build.gradle.kts`:

```kotlin
implementation(platform(libs.supabase.bom))
implementation(libs.supabase.gotrue.kt)
implementation(libs.ktor.client.android)
```

---

## Architecture

### One process owns the client

```
:net process
├── NetworkGatewayService (existing, Service)
│   └── NetworkGatewayImpl
│       ├── client: OkHttpClient (existing)
│       ├── llmGatewayClient: LlmGatewayClient (existing)
│       └── supabaseClient: SupabaseClient ← NEW (lazy)
├── SupabaseAuthStateBinder ← already exists; wire its lambda to supabaseClient.auth
└── EncryptedSessionManager ← NEW
```

`SupabaseClient` is created exactly once per `:net` process lifecycle. Any cross-process consumer (i.e. `:ml`) reaches it only through the existing `INetworkGateway.callLlmGateway()` AIDL — no new IPC surface.

### Encrypted session storage

The SDK's default `SessionManager` writes plaintext JWT + refresh token to `SharedPreferences`. We replace it with one backed by `EncryptedSharedPreferences`:

```kotlin
// app/src/main/java/com/capsule/app/net/EncryptedSessionManager.kt
internal class EncryptedSessionManager(context: Context) : SessionManager {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "supabase_auth_session",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    override suspend fun saveSession(session: UserSession) { /* JSON-encode + put */ }
    override suspend fun loadSession(): UserSession? { /* get + JSON-decode or null */ }
    override suspend fun deleteSession() { prefs.edit().clear().apply() }
}
```

Concrete `SessionManager` API surface depends on the pinned SDK version — verify against the SDK source at implement time. The SDK's docs reference is at `supabase-community/supabase-kt/tree/master/GoTrue` (see the "Custom session manager" section).

Required dep already in repo: `androidx.security:security-crypto` (libs.versions.toml line `securityCrypto = "1.1.0-alpha06"`).

### Wiring in `NetworkGatewayImpl`

```kotlin
// inside NetworkGatewayImpl
private val supabase: SupabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    ) {
        install(Auth) {
            sessionManager = EncryptedSessionManager(appContext)
            alwaysAutoRefresh = true
        }
    }
}

private val authBinder: AuthStateBinder = SupabaseAuthStateBinder {
    supabase.auth.currentSessionOrNull()?.accessToken
}
```

`BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_PUBLISHABLE_KEY` already exist in `app/build.gradle.kts` (added in spec 013 — verify; if not, add via the same `local.properties` pattern used for `cloud.gateway.url` in T014-016).

`LlmGatewayClient` is constructed with `authBinder` instead of the default `NoSessionAuthStateBinder`.

### Login surface

**Out of scope for T014-019b.** This task assumes a session exists (placed by some future Settings → Sign in flow, or by a dev-only debug-build helper). The valid states are:

- **Session present + non-expired** → `currentJwt()` returns the access token; gateway calls succeed.
- **Session present + access token expired but refresh token valid** → SDK auto-refreshes (`alwaysAutoRefresh = true`); `currentJwt()` returns the refreshed token.
- **No session OR refresh failed** → `currentJwt()` returns null; `LlmGatewayClient` short-circuits to `Error(UNAUTHORIZED)` per T014-019.

A real Sign in / Sign up UI is a separate spec (likely folded into spec 006 Orbit Cloud or a dedicated `015-supabase-auth-ui`). Until that ships, dev builds get a debug-only helper:

```kotlin
// app/src/debug/java/com/capsule/app/net/DebugSupabaseSeed.kt
// Reads dev credentials from local.properties → BuildConfig.DEBUG_SUPABASE_EMAIL/PASSWORD
// and calls supabase.auth.signInWith(Email) on first :net startup if no session exists.
// Compiled out of release builds.
```

This unblocks T014-021 (live E2E from emulator) without needing UI work.

---

## File-by-file change set

| File | Action | Purpose |
|------|--------|---------|
| `gradle/libs.versions.toml` | modify | Add `supabaseKt`, `ktor`, three library refs |
| `app/build.gradle.kts` | modify | Add three deps; add `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_PUBLISHABLE_KEY` if missing; add `BuildConfig.DEBUG_SUPABASE_EMAIL` / `DEBUG_SUPABASE_PASSWORD` (debug-only, blank in release) |
| `app/src/main/java/com/capsule/app/net/EncryptedSessionManager.kt` | create | EncryptedSharedPreferences-backed `SessionManager` |
| `app/src/main/java/com/capsule/app/net/NetworkGatewayImpl.kt` | modify | Lazy `SupabaseClient`; replace `NoSessionAuthStateBinder` with `SupabaseAuthStateBinder { supabase.auth.currentSessionOrNull()?.accessToken }` |
| `app/src/debug/java/com/capsule/app/net/DebugSupabaseSeed.kt` | create | Debug-only auto-sign-in for emulator E2E (T014-021 unblocker) |
| `app/src/test/java/com/capsule/app/net/EncryptedSessionManagerTest.kt` | create | Round-trip save/load/delete; survives across instances |
| `local.properties` (gitignored) | document only | New keys: `supabase.debug.email`, `supabase.debug.password` (dev-only) |

---

## Acceptance criteria

1. `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon` PASS.
2. `./gradlew :app:testDebugUnitTest --tests "*EncryptedSessionManager*"` PASS (≥3 tests).
3. `./gradlew :app:lint` PASS — specifically no `OrbitNoHttpClientOutsideNet` regressions; SDK does not pull OkHttp/ktor into the default process.
4. Cold launch of debug build, with `supabase.debug.email` set in `local.properties`, results in a logged "session restored" or "signed in" log line in `:net` and a non-null `currentJwt()` within 5 s.
5. Without `supabase.debug.email`, cold launch logs a single warn line `"no debug seed credentials; gateway calls will UNAUTHORIZED until UI sign-in lands"` and `currentJwt()` returns null.
6. Constitution check: ✓ Principle II (SDK in `:net` only, verified by `grep -r "io.github.jan-tennert.supabase" app/src` returning matches only under `app/src/main/java/com/capsule/app/net/` and `app/src/debug/`).
7. Constitution check: ✓ Principle VII (no JWT or refresh token in plain `SharedPreferences` — verified by inspecting `/data/data/com.capsule.app/shared_prefs/` after sign-in: only `supabase_auth_session.xml` exists and its values are base64 ciphertext).

---

## Open question (one — for the user)

**Q1**: Does the user want `local.properties` debug seed credentials wired in this task, or should we ship the SDK + `EncryptedSessionManager` only and defer the auto-sign-in to a separate debug-only commit?

- **Option A (recommended)**: Ship together. Keeps T014-019b a single atomic commit and produces a working E2E loop the moment T014-020 lands. Requires user to drop one extra dev test-account email/password into `local.properties` (the user creates a throwaway Supabase user manually via the dashboard).
- **Option B**: Ship SDK + EncryptedSessionManager only. T014-021 then needs the user to manually `adb shell am start` a sign-in helper or postpone live E2E until a real Sign-in UI lands in spec 006.

If unanswered at implement time, default to **Option A** with a placeholder dev user — the user can swap credentials.

---

## Estimated effort

~90 min: SDK pin verification (10), Gradle wiring (15), `EncryptedSessionManager` + tests (30), `NetworkGatewayImpl` refactor (15), debug seed helper (15), verification matrix (5).

Single commit message: `T014-019b: wire Supabase SDK in :net with encrypted session manager (deviation #1 closeout)`.

---

## What this does NOT do

- Does not ship a Sign-in / Sign-up UI (spec 006 or 015).
- Does not register the user in any onboarding flow.
- Does not handle multi-account session switching.
- Does not address password recovery or social auth.

These are explicitly later-spec work.
