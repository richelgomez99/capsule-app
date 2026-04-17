# Network Gateway Contract: Intent Envelope and Diary

**Feature Branch**: `002-intent-envelope-and-diary`
**Date**: 2026-04-16
**Owning process**: `:net`
**Callers**: `:ml` only (specifically the Continuation Engine)

---

## 1. Purpose

The Network Gateway is the **sole network egress** point in Orbit.
`android.permission.INTERNET` is declared only on `:net`. No other
process can make HTTP calls: this is enforced by

1. Manifest: `INTERNET` declared on `:net` process only (see
   `plan.md` §2 Technical Context).
2. Custom Android Lint rule `OrbitNoHttpClientOutsideNet`: any
   reference to `OkHttpClient`, `HttpURLConnection`, `java.net.Socket`
   or `Ktor` client outside the `com.capsule.app.net.*` packages
   fails the build.
3. Contract: the Gateway exposes **one method only**, `fetchPublicUrl`.

Principle enforced: **VI (Privilege Separation By Design)**.

---

## 2. Service

```xml
<service
    android:name=".net.NetworkGatewayService"
    android:process=":net"
    android:exported="false" />
```

**Action**: `com.capsule.app.action.BIND_NETWORK_GATEWAY`
**Returns**: `INetworkGateway` AIDL binder.
**Auth**: `exported="false"`. Additionally, the gateway checks
`Binder.getCallingUid() == android.os.Process.myUid()` and rejects any
other UID.

---

## 3. AIDL Interface

```aidl
// INetworkGateway.aidl
package com.capsule.app.net.ipc;

import com.capsule.app.net.ipc.FetchResultParcel;

interface INetworkGateway {

    // The only network primitive available to the rest of the app.
    //
    // @param url     canonical URL to fetch (must be https://…)
    // @param timeoutMs hard timeout (default 10_000ms; max 15_000ms)
    //
    // @return FetchResultParcel
    FetchResultParcel fetchPublicUrl(String url, long timeoutMs);
}
```

```kotlin
// FetchResultParcel (conceptual)
data class FetchResultParcel(
    val ok: Boolean,
    val finalUrl: String?,          // post-redirect, null on failure
    val title: String?,             // extracted <title>
    val canonicalHost: String?,     // host of finalUrl (for audit/display)
    val readableHtml: String?,      // post-Readability HTML, ≤ 200 KB
    val errorKind: String?,         // null on success; else enum below
    val errorMessage: String?,      // never leaked to UI verbatim
    val fetchedAtMillis: Long
) : Parcelable
```

`errorKind` ∈ `{ "invalid_url", "not_https", "blocked_scheme",
"blocked_host", "timeout", "too_large", "redirect_loop",
"dns_failure", "tls_failure", "http_error", "unknown" }`.

---

## 4. Pre/Postconditions

### `fetchPublicUrl(url, timeoutMs)`

**Preconditions (enforced inside `:net`)**:

| Check | Reject reason |
|---|---|
| `url` parses as URI | `invalid_url` |
| scheme == `https` | `not_https` |
| host is public (not localhost, not RFC1918, not link-local, not `.onion`) | `blocked_host` |
| port is 443 or unset | `blocked_scheme` |
| `timeoutMs` ≤ 15_000 | clamped |
| user has not globally disabled continuations (Settings) | return early `ok=false`, `errorKind="disabled"` |

**Request policy** (non-negotiable):

- Method: `GET` only. No POST/PUT/DELETE ever.
- Redirects: followed up to **5 hops**, each must also be `https`. A
  redirect to non-https ⇒ fail with `not_https`.
- `Referer` header: **never sent**.
- Cookies: disabled (`CookieJar.NO_COOKIES`).
- `User-Agent`: fixed string `Orbit/1.0 (Android; local-first reader)`.
- Cache: OkHttp cache disabled; each fetch is uncached.
- TLS: default Android trust anchors; no custom certs; no cleartext.
- Response size cap: 2 MB. Larger ⇒ `too_large`.

**Postconditions**:

- No bytes written to any shared storage.
- `readableHtml`, if present, is the output of Readability on the
  fetched document, with `<script>`, `<style>`, and event handlers
  stripped by jsoup's safelist.
- One audit entry written via a callback to `:ml` (see §5) with
  `action = NETWORK_FETCH` and the canonical host.
- No telemetry or request metadata leaves the device.

**Postcondition invariants enforced in a unit test**:

- The OkHttp `Call.request()` contains **no** `Referer` header.
- The OkHttp `CookieJar` is `CookieJar.NO_COOKIES`.
- A redirect handler replaces 3xx responses whose `Location` is non-
  https with a failure.

---

## 5. Audit Integration

Every call to `fetchPublicUrl` results in exactly one audit entry:

```kotlin
AuditLogEntryEntity(
    id = UUID.random(),
    at = now,
    action = AuditAction.NETWORK_FETCH,
    description = "Fetched $canonicalHost for envelope $envelopeId",
    envelopeId = <passed via the call's contextual parcel>,
    extraJson = """{"ok": $ok, "errorKind": "$errorKind"}"""
)
```

To keep the contract free of DB access, the gateway binds to
`IAuditWriter` (exposed by `:ml`) and fires a one-way audit call on
completion. The envelope id is carried in a companion parcel in a
follow-up revision — for v1 the Continuation Engine supplies the
envelope id to the audit writer itself after the fetch resolves.

---

## 6. Concurrency & Rate Limits

- Internal OkHttp dispatcher: max 4 concurrent calls, max 2 per host.
- Fetches are only initiated when WorkManager has granted the
  `NetworkType.UNMETERED` + `RequiresCharging(true)` constraints, so
  the gateway itself does not need to queue; it assumes the caller is
  batch-friendly.
- A single domain-cooldown: if a host returns `http_error` in the 5xx
  range, the gateway refuses further requests to that host for 60s.
  This is a local in-memory cache only.

---

## 7. Error Surface

Failures never throw across the binder. They return `ok=false` with an
`errorKind`. The continuation engine decides retry policy:

| errorKind | Retry? |
|---|---|
| `timeout`, `dns_failure`, `tls_failure`, `http_error` (5xx) | yes, up to 3 attempts with exponential backoff |
| `invalid_url`, `not_https`, `blocked_host`, `too_large`, `redirect_loop`, `http_error` (4xx) | no |
| `unknown` | yes, once |

---

## 8. Tests (Contract)

- **No secrets leak**: instrumented MITM proxy asserts the outbound
  request carries no Referer and no cookies for a freshly-built app.
- **https-only**: mock server on `http://` returns the `not_https`
  error and no socket is opened on port 80.
- **Size cap**: server streams 10 MB; client terminates at 2 MB with
  `too_large`.
- **Redirect loop**: 6 `Location` hops ⇒ `redirect_loop`.
- **Private host**: `https://10.0.0.1` ⇒ `blocked_host`.
- **UID check**: a second fake app with a different UID attempts to
  bind and is rejected.
- **Lint rule**: compiling `com.capsule.app.ui` with an `OkHttpClient`
  import fails.

---

## 9. Out of scope (v1)

- WebSocket, gRPC, or long-lived connections.
- POST requests (will be reconsidered only for user-authorized
  AppFunctions/AP2 in Phase 2, through a different, explicitly
  user-consented API — not through this gateway).
- Third-party analytics, crash reporting, or telemetry.
