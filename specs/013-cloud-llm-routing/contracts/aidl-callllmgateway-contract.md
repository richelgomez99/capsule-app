# AIDL `callLlmGateway` Contract

**Boundary**: Android `:capture` process (`CloudLlmProvider`) → Android `:net` process (`NetworkGatewayImpl`)
**Status**: DRAFT — Day 1 of `013-cloud-llm-routing`
**Surface**: extends [`INetworkGateway.aidl`](../../../app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl) — package `com.capsule.app.net.ipc`.

This contract is the Android-internal IPC counterpart of [`llm-gateway-envelope-contract.md`](llm-gateway-envelope-contract.md). It is concerned only with the Binder boundary between processes; once the request reaches `:net`, the envelope contract takes over.

---

## 1. AIDL signature

```aidl
package com.capsule.app.net.ipc;

import com.capsule.app.net.ipc.FetchResultParcel;
import com.capsule.app.net.ipc.LlmGatewayRequestParcel;
import com.capsule.app.net.ipc.LlmGatewayResponseParcel;

interface INetworkGateway {
    FetchResultParcel fetchPublicUrl(String url, long timeoutMs);                       // existing, UNCHANGED
    LlmGatewayResponseParcel callLlmGateway(in LlmGatewayRequestParcel request);        // NEW
}
```

The two new parcelable types each require a sibling `.aidl` declaration file:

```aidl
// LlmGatewayRequestParcel.aidl
package com.capsule.app.net.ipc;
parcelable LlmGatewayRequestParcel;
```

```aidl
// LlmGatewayResponseParcel.aidl
package com.capsule.app.net.ipc;
parcelable LlmGatewayResponseParcel;
```

(Mirrors the existing `FetchResultParcel.aidl` declaration pattern.)

---

## 2. Parcel binary format

Both parcels are minimal: a single String field carrying a kotlinx.serialization JSON payload.

```kotlin
data class LlmGatewayRequestParcel(
    val payloadJson: String,    // kotlinx.serialization Json.encodeToString(LlmGatewayRequest, value)
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(payloadJson)
    }
    override fun describeContents(): Int = 0
    companion object CREATOR : Parcelable.Creator<LlmGatewayRequestParcel> {
        override fun createFromParcel(parcel: Parcel) =
            LlmGatewayRequestParcel(payloadJson = parcel.readString() ?: error("missing payloadJson"))
        override fun newArray(size: Int) = arrayOfNulls<LlmGatewayRequestParcel>(size)
    }
}
```

`LlmGatewayResponseParcel` is identical in shape (single String field, same `writeToParcel` / `CREATOR` pattern).

### 2.1 Why JSON-in-String, not multi-field Parcelable
The payload is a sealed Kotlin class with six request shapes and seven response shapes. Per-field Parcelable would require either polymorphic Parcelable (not supported) or an enum-tag + nullable-everywhere schema (brittle as fields evolve). JSON-in-String is the canonical Android pattern for this exact problem (per research D-007 / F-007).

### 2.2 Encoding details
- `Json` instance: `kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true; classDiscriminator = "type" }`.
- Sealed class polymorphism handled by the kotlinx.serialization sealed-class registry (every subtype carries its own `@Serializable` annotation; the generated serializer handles the discriminator).
- UTF-8 encoding (Java String default; Parcel writeString handles encoding internally).

### 2.3 Size envelope
- 1536-float embedding payload: ~6 KB binary, ~20 KB JSON-encoded as numbers. Well within the ~1 MB Binder transaction limit (F-011).
- Largest expected payload: `extract_actions` request with full envelope text + state snapshot + registered function list. Conservative estimate ~50 KB. Still well within limit.
- Implementer note: if a future change adds image bytes or other binary payload, switch from `writeString` to `writeByteArray` or use Binder-shared memory; do not let the JSON payload exceed ~500 KB.

---

## 3. UID gate (security)

`NetworkGatewayImpl.callLlmGateway` MUST enforce the same UID gate `fetchPublicUrl` uses today:

```kotlin
val caller = Binder.getCallingUid()
if (caller != Process.myUid()) {
    return LlmGatewayResponseParcel(
        Json.encodeToString(LlmGatewayResponse.Error(
            requestId = parsed.requestId,
            code = "UNAUTHORIZED",
            message = "caller uid $caller is not allowed"
        ))
    )
}
```

Rationale: the AIDL surface is exposed only to the Orbit app's own processes via the existing service binding pattern. The UID gate is structural defence in depth — even if a same-uid sandboxed component were tricked into binding, an external uid call returns an error envelope (not a thrown exception, which would crash the binder thread).

---

## 4. Binder thread safety

- Blocking IO on the binder thread is **OK** for this method (mirrors the existing `fetchPublicUrl` pattern). Android Binder threads are designed to absorb blocking IO; the request comes in on a binder thread, the OkHttp call happens synchronously on that thread, and the response goes back on the same thread.
- The handler MUST NOT take any locks across the network call (NFR / FR-013-011). `LlmGatewayClient` is internally stateless aside from the OkHttp client (which is thread-safe), so this is naturally satisfied; the implementation MUST NOT add any synchronization that wraps the `client.newCall(...).execute()` call.
- Multiple concurrent `callLlmGateway` invocations are supported (each binder thread handles one). OkHttp's connection pool handles concurrency.

---

## 5. Error semantics across the boundary

The AIDL method **MUST NOT throw** across the binder boundary. Every error path returns an `LlmGatewayResponseParcel` whose JSON payload is `LlmGatewayResponse.Error(...)`. Reasons:

1. AIDL `RemoteException` propagation across processes is brittle — the calling process sees a generic remote exception with no structured detail.
2. Returning a typed Error envelope keeps `CloudLlmProvider`'s error mapping (data-model §1.3) uniform: it inspects the response variant and maps to either `null` (embed) or `IOException` (others).
3. The method signature in AIDL is non-throwing (no `throws` clause); Android-generated stubs would convert a thrown `RuntimeException` to `RemoteException` and lose the diagnostic detail.

The only exception: a parcel-decoding failure in `:net` (malformed request JSON) is **also** returned as `Error(code = "MALFORMED_RESPONSE", message = parseError.message)`. Never thrown.

---

## 6. Caller pattern (`CloudLlmProvider` side)

`CloudLlmProvider` follows the binding pattern from `UrlHydrateWorker.kt`:

```kotlin
class CloudLlmProvider(private val gateway: INetworkGateway) : LlmProvider {
    private val json = Json { ... }   // same instance as gateway parcels use

    override suspend fun embed(text: String): EmbeddingResult? {
        if (text.isBlank()) return null
        val request = LlmGatewayRequest.Embed(requestId = UUID.randomUUID().toString(), text = text)
        val parcel = LlmGatewayRequestParcel(json.encodeToString<LlmGatewayRequest>(request))
        val responseParcel = try {
            gateway.callLlmGateway(parcel)
        } catch (e: RemoteException) {
            return null   // embed contract: null on any error
        }
        val response = json.decodeFromString<LlmGatewayResponse>(responseParcel.payloadJson)
        return when (response) {
            is LlmGatewayResponse.EmbedResponse -> EmbeddingResult(response.vector, response.modelLabel)
            else -> null
        }
    }
    // ... similar pattern for the other five methods, with throw on error per the asymmetric contract
}
```

`gateway: INetworkGateway` is supplied by the same wiring code that powers `UrlHydrateWorker.kt` today; no new service binding is needed.

---

## 7. AIDL stub generation verification (SC-003)

After build:
```sh
find app/build/generated/aidl_source_output_dir -name 'INetworkGateway*' \
  | xargs grep -l 'callLlmGateway'
```
MUST return at least one file path. Existence in the generated source dir is the build-level proof that the AIDL signature is wired into both processes.
