# Architectural Specification and Feasibility Study for "Capsule" – A Proactive PKM Memory Agent on Android 13+

## 1. Executive Overview

Capsule is a persistent, privacy-first Personal Knowledge Management (PKM) agent for Android 13+ centered around a draggable floating overlay bubble that acts as a proactive capture surface and agentic entry point.
The system must operate robustly under OEM background-killing heuristics, respect modern Android privacy constraints (clipboard, browser, accessibility, VPN), and support edge AI through local SLMs and vector search with graceful degradation on lower-end hardware.[^1][^2][^3][^4]

This document specifies a production-ready architecture for Capsule, covering overlay and lifecycle management with Jetpack Compose, clipboard access via a focus-stealing mechanic, browser-context acquisition strategies and policy risks, foreground-service survival tactics on aggressive OEM skins (with emphasis on Samsung), and the design of a local AI and storage stack that scales from 4 GB baseline devices to modern flagships.
It also identifies key Google Play compliance risks around AccessibilityService, VpnService, and sensitive data handling.

## 2. System Context and Requirements

### 2.1 Functional Scope

Capsule provides:

- A persistent, draggable floating bubble overlay rendered via Jetpack Compose inside a WindowManager-managed view hosted by a long-lived foreground service.[^5][^1]
- Tap-to-activate capture behavior: when the user taps the bubble, Capsule briefly takes window focus, reads clipboard contents, and opens a lightweight Compose surface for classification or annotation.
- Proactive capture and recall of text snippets, links, and contextual metadata (app package, timestamp, optionally coarse browser context) into a local vectorized PKM store.
- On-device semantic search, summarization, and suggestion using local embeddings and, on capable devices, a local SLM running via ONNX Runtime Mobile.[^3][^6][^7]

### 2.2 Non-Functional Constraints

- Minimum SDK: 33 (Android 13), with forward-compatibility considerations for 14–15 (foreground service type requirements, clipboard UX, etc.).[^8][^9][^10][^11]
- OEM survivability: foreground overlay service must remain available across Samsung, Xiaomi, and other aggressive vendors, acknowledging that full guarantee is impossible but heuristics can significantly improve survival.[^2][^12][^13][^14]
- Privacy and policy compliance: Clipboard, AccessibilityService, and VpnService usage must strictly follow Google Play's User Data and Sensitive Permissions policies.[^15][^16][^17][^18][^1]
- Hardware bifurcation: architecture must scale from baseline 4 GB RAM mid-range devices to 8–12 GB flagships, with tiered AI features and storage strategies.[^4][^7][^19][^3]

### 2.3 High-Level Architecture

At a high level, Capsule comprises:

- A Foreground Overlay Service that owns the WindowManager overlay, Compose view hierarchy, and a bubble interaction controller.
- A Background Capture and Ingestion pipeline that normalizes, deduplicates, and enriches captured events before writing them to a local PKM store and vector index.
- An AI Runtime layer that coordinates embeddings, retrieval, and optional SLM inference using ONNX Runtime and hardware acceleration (NNAPI/XNNPACK).
- A Policy and Permissions module that centralizes all sensitive-surface behaviors (AccessibilityService, VpnService, clipboard) behind explicit disclosures and feature flags.


## 3. Browser Context Acquisition – Forensic Options and Policy Risk

### 3.1 Deprecated Browser Content Providers

The historical Browser.BOOKMARKS_URI and related browser history content providers are deprecated and no longer supported for non-system apps on recent Android versions, eliminating the straightforward passive browser history access path.
Apps are expected to rely on in-app APIs (e.g., custom tabs callbacks) or explicit user sharing flows instead.[^8]

Given this, Capsule must consider alternative techniques if browser context is required, each with distinct technical and policy risk profiles.

### 3.2 AccessibilityService Scraping of URL Bar and DOM

#### 3.2.1 Technical Feasibility

Accessibility services can retrieve the active window content via AccessibilityNodeInfo trees when configured with `android:canRetrieveWindowContent="true"` and appropriate event types, including `TYPE_WINDOW_STATE_CHANGED` or content change events.[^20][^21]
Multiple community examples show URL extraction from Chrome using AccessibilityService by searching the view hierarchy for the URL bar node (`className=android.widget.EditText`, `viewIdResName=com.android.chrome:id/url_bar`) or scanning nodes for text containing `http`.[^22][^23][^24]

These examples confirm that, at least for certain Chrome versions, URL bar text is visible to accessibility services when Chrome is the active window and the service is configured to listen to Chrome's package.[^23][^22]
However, developers report fragility across Chrome releases (structure changes, view IDs, focus behavior) and that further navigation sometimes fails to emit consistent events, requiring heuristic handling of window content changes.[^24][^23]

#### 3.2.2 Legal and Policy Considerations

Google's AccessibilityService policy explicitly permits use "across a wide range of applications" but distinguishes between apps that are genuine accessibility tools (isAccessibilityTool=true) and others using accessibility for non-accessibility purposes.[^17][^25][^18]
Non-accessibility tools must:

- Complete an Accessibility API declaration in Play Console.
- Provide clear in-app disclosures describing which data is accessed (e.g., URLs, page text), how it is used, and whether it is shared.
- Obtain affirmative user consent before reading such data via accessibility.

Google and independent analyses emphasize that using the Accessibility API for automation, tracking, or data harvesting unrelated to assisting users with disabilities is prohibited and may result in removal from Google Play.[^26][^27]
Scraping URLs and arbitrary DOM text from third-party apps as a PKM back-end is therefore high risk: even with explicit consent, the primary purpose is not to assist disabilities.

#### 3.2.3 Reliability Assessment

From an engineering standpoint, relying on AccessibilityService for browser context introduces:

- High brittleness to Chrome/Brave DOM and UI updates.
- Non-deterministic event patterns across OEM Chromium variants.
- Potential performance overhead and increased battery use due to full-tree traversal.[^22][^23][^24]

From a policy standpoint, generic URL logging is likely considered misuse of AccessibilityService given recent policy tightening focused on preventing hidden tracking.[^18][^27][^17][^26]

**Recommendation:** Restrict AccessibilityService usage, if used at all, to narrow, user-triggered features with explicit flows (e.g., enabling a "reading assistance" mode per domain) and avoid general-purpose passive browser history scraping.

### 3.3 Local On-Device VPN for DNS/URL Logging

#### 3.3.1 Technical Feasibility

Android's VpnService base class permits an app to create a device-level virtual network interface and route traffic through a local or remote endpoint. Content-filtering and ad-blocking apps implement local VPNs to inspect and potentially block HTTP(S) traffic or DNS queries without a remote server.[^28][^29]
Tools like PCAP-based traffic analyzers show that local VPNs can capture a large portion of app traffic but may miss system processes or traffic that OEMs deliberately exempt from VPN routing or send via hardcoded IPs, limiting completeness of browsing history reconstructions.[^30]

DNS-based logging can approximate which domains are accessed but cannot reliably reconstruct full URLs over HTTPS, especially with HTTP/2, HTTP/3 (QUIC), and encrypted DNS, and system-level bypass means some browser/system requests never appear in VPN capture.[^29][^30]

#### 3.3.2 Google Play VpnService Policy

Google Play requires that any app using VpnService complete a declaration form and reserves usage primarily for apps whose core functionality is VPN-related (network security, parental control, usage tracking, remote access, or web browsing itself).[^31][^15]
The policy explicitly forbids using VpnService to:

- Collect personal and sensitive user data without prominent disclosure and consent.
- Redirect or manipulate traffic for monetization or ad fraud.

It also requires documenting VPN usage in the Play listing, encrypting data to the VPN endpoint, and complying with all user data policies.[^16][^15]

A PKM app whose primary purpose is note-taking and memory augmentation would have difficulty justifying VpnService as "core functionality" and would be scrutinized if using VPN solely to log or infer browsing behavior.

#### 3.3.3 Risk Assessment

Using a local VPN for passive browsing logging exposes Capsule to:

- High enforcement risk if declared but functionally unrelated to core VPN use.
- Immediate removal if discovered to collect personal browsing data without extremely clear disclosures and granular consent controls.[^15][^16]
- Incomplete and noisy signals due to encrypted traffic, VPN bypass by system apps, and DNS caching.[^29][^30]

**Recommendation:** Do not rely on VpnService for browsing history inference unless Capsule repositions itself explicitly as a network instrumenting tool with PKM as a secondary feature and fully meets VPN policy requirements.

### 3.4 Alternative Browsers and Extension-Based Backdoors

Chromium-based alternative browsers such as Kiwi Browser have historically offered Chrome extension support on Android, allowing extensions to access history and communicate with external apps via native messaging.[^32]
However, Kiwi's source repository has been archived and marked read-only, and community discussions indicate uncertainty about its long-term viability and Play Store presence.[^33][^34]

Even where extensions are supported, there is no standard API to push full history to third-party Android apps; any such mechanism would require a bespoke extension that the user installs and authorizes explicitly, and its long-term maintenance would depend on the browser vendor.
Furthermore, relying on niche browsers fractures the user base and fails to address mainstream Chrome/Brave users.

**Recommendation:** Treat browser integration via alternative browsers and extensions as an optional, opt-in integration path (e.g., a "Capsule Companion" extension for desktop browser and Kiwi/compatible mobile browsers) rather than a core architectural pillar.
Ensure separate policy review for the browser extension distribution channels.

### 3.5 Summary of Browser Context Options and Policy Risks

| Technique | Technical Feasibility | Completeness | Play Policy Risk | Notes |
|----------|----------------------|-------------|------------------|-------|
| Deprecated browser content providers | Not available on modern Android | N/A | N/A | Removed for third-party apps.[^8] |
| AccessibilityService URL/DOM scraping | Demonstrated in samples for Chrome URL bar and web content | Potentially high (active tab only) but brittle | High – likely misuse unless strictly accessibility-focused with strong disclosures | Only consider for narrow, user-triggered accessibility features.[^22][^23][^17][^18] |
| Local VPN DNS/traffic logging | Technically feasible for many flows | Partial – misses bypasses, encrypted DNS, some system traffic | High – VpnService reserved for VPN/core network tools; strict user data rules | Difficult to justify for a PKM app.[^15][^31][^29][^30] |
| Alternative browsers + extension | Feasible with Chrome-compatible APIs | Depends on browser adoption | Medium – extension must comply with browser store policies | Niche; treat as optional integration.[^32][^33][^34] |

**Primary architectural posture:** Capsule should not rely on passive browser scraping.
Instead, it should focus on:

- Explicit share flows (user invokes "Share to Capsule").
- In-app custom tabs with explicit integration.
- Optional, clearly disclosed AccessibilityService features focused on assistive workflows.


## 4. Jetpack Compose Overlay Architecture

### 4.1 Compose in a WindowManager Overlay from a Service

Jetpack Compose is designed primarily for Activity/Fragment environments where ViewTreeLifecycleOwner, SavedStateRegistryOwner, and ViewModelStoreOwner are automatically provided.
When using ComposeView from a Service with WindowManager (TYPE_APPLICATION_OVERLAY), these owners are absent, causing errors such as `ViewTreeLifecycleOwner not found` unless manually supplied.[^35][^5]

Practitioners have resolved this by:

- Creating a dedicated LifecycleOwner/SavedStateRegistryOwner implementation (e.g., `MyLifecycleOwner`) that wraps a LifecycleRegistry and SavedStateRegistryController.[^36][^35]
- Attaching this owner to the ComposeView via `ViewTreeLifecycleOwner.set(view, owner)` and `ViewTreeSavedStateRegistryOwner.set(view, owner)` before calling `setContent {}`.
- Manually driving lifecycle events (`ON_CREATE`, `ON_START`, `ON_RESUME`, etc.) when the overlay is added/removed from the WindowManager.[^37][^35][^36]

A published example demonstrates an OverlayService that sets up a ComposeView, attaches a manually managed lifecycle owner, and successfully displays a simple Composable via WindowManager using SYSTEM_ALERT_WINDOW/TYPE_APPLICATION_OVERLAY.[^1][^36]

### 4.2 Lifecycle and State Management Outside Activities

To support recomposition, state hoisting, and side-effect management, the overlay Compose UI should be driven by a ViewModel or controller whose lifetime is tied to the foreground service, not the individual overlay view.
Community examples show using a custom LifecycleOwner and ViewModelStoreOwner to ensure Compose can resolve ViewModels and respond to state changes even though no Activity is present.[^37][^35][^36]

A robust pattern for Capsule:

- Make the overlay service a `LifecycleService` so it has its own lifecycle.
- Create a `DrawOverLifecycleOwner` (or similar) which implements LifecycleOwner, ViewModelStoreOwner, and SavedStateRegistryOwner and delegates/coordinates with the service's lifecycle.[^35][^36]
- When adding the ComposeView to WindowManager, attach this owner as the tree owner and invoke `ON_CREATE/ON_START/ON_RESUME` events; when removing, call `ON_PAUSE/ON_STOP/ON_DESTROY` to avoid leaks.[^36][^35]
- Hold overlay state (bubble position, expansion state, animation flags) in a single shared ViewModel scoped to this owner.

### 4.3 WindowManager Layout and Bubble Mechanics

For a draggable bubble overlay, Capsule should use:

- `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` for Android O+ overlays with SYSTEM_ALERT_WINDOW permission.[^38][^39]
- Flags: `FLAG_NOT_TOUCH_MODAL` (allow touches outside bubble to fall through), `FLAG_NOT_FOCUSABLE` (most of the time, while idle), optionally `FLAG_LAYOUT_IN_SCREEN` for full-screen coordinates.[^38][^5][^35]
- Gravity: `TOP|START` or `TOP|END`, with `x`/`y` updated on drag gestures.

State updates:

- Expose bubble position as mutable state in the ViewModel so Compose can recompose the bubble position as drag events update `x`/`y`.
- Use pointer input modifiers (`pointerInput`) to detect drag gestures and, on drag, mutate both the Compose state and the LayoutParams (via a synchronized `updateViewLayout` call) to keep the OS window and Composable aligned.

### 4.4 Lifecycle Edge Cases

Key edge cases to handle:

- Configuration changes: As an overlay not tied to an Activity, Capsule may choose to treat configuration changes as no-ops and ignore orientation changes, or recalculate positions relative to new screen bounds, triggered via `onConfigurationChanged` in the service.
- Overlay permission revocation: If the user revokes SYSTEM_ALERT_WINDOW, WindowManager.addView will fail with `BadTokenException`; Capsule must detect this and tear down cleanly, then prompt the user to re-enable the permission.[^40][^39][^38]
- Process death: OEM background kills will remove the overlay; on process restart, the service should restore bubble state (position, pinned status) from persistent storage (e.g., SharedPreferences or a small local table) to avoid disorienting the user.


## 5. Focus-Stealing Clipboard Mechanic – Feasibility and Risks

### 5.1 Clipboard Access Constraints in Android 10+

Android 10 introduced a privacy change that limits clipboard access: unless an app is the default input method editor (IME) or is the app that currently has focus, it cannot read clipboard contents.[^8]
Security documentation emphasizes enforcing minSdk ≥ 29 to prevent background processes from accessing clipboard data from the foreground app.[^1]
Android 13 adds a system toast whenever an app reads clipboard data, increasing user visibility but not changing the core requirement that the reading app be in the foreground.[^41][^1]

Community reports confirm that background clipboard managers broke when targeting Android 10+, with some developers resorting to ADB-granted READ_LOGS hacks or invisible overlays to force focus and detect changes; Google has signaled that such logcat-based automation and background clipboard access is "working as intended" blocked and not supported.[^42][^43][^44]

### 5.2 Focus-Stealing via FLAG_NOT_FOCUSABLE Toggling

The proposed mechanic for Capsule is:

1. Keep the overlay window flagged `FLAG_NOT_FOCUSABLE` while idle so it does not intercept navigation or keyboard focus.
2. On explicit user tap on the bubble, temporarily remove `FLAG_NOT_FOCUSABLE`, causing the overlay window to become focusable and (briefly) the focused foreground component.
3. Read `ClipboardManager.getPrimaryClip()` inside this focus window.
4. Immediately restore `FLAG_NOT_FOCUSABLE` and, if needed, programmatically collapse or minimize the overlay.

Technically, this aligns with the requirement that the app be "the app that currently has focus" when reading the clipboard.[^8]
StackOverflow and Android Q discussions suggest that checking the clipboard in window-focus callbacks (`onWindowFocusChanged(true)`) is the intended pattern for apps that need to update paste buttons when they come to the foreground.[^45][^8]

By explicitly requiring a user tap on the bubble, Capsule satisfies the user-interaction expectation and avoids continuous background polling.

### 5.3 Race Conditions and UX Considerations

Potential issues with this approach:

- **Focus competition:** If another app starts an activity or dialog during the bubble tap (e.g., system dialogs), there may be a brief race where Capsule assumes focus but does not actually receive it, leading to a failed clipboard read.
This should be handled gracefully by treating a `null`/empty clip as "no content" and avoiding crashes.

- **ANR risk:** Changes to LayoutParams and clipboard reads must occur on the main thread but must be lightweight; the sequence should not involve blocking operations.
Ensure that longer ingestion and AI tasks run on background threads or coroutines.

- **Visual jank:** Rapidly toggling focus flags can cause subtle changes in system navigation behaviors or visual glitches if not done carefully.
Mitigation strategies:

  - Use a short state machine: `Idle -> RequestFocus -> ReadClipboard -> RestoreFlags` with timeouts.
  - Avoid starting full-screen activities; keep the overlay minimal and semi-transparent to reduce perceived disruption.
  - Accept that Android 13's clipboard access toast will appear; do not attempt to suppress it.[^41][^1]

### 5.4 Exact LayoutParams Sequence

Recommended flow:

1. Initial overlay setup:

   - `type = TYPE_APPLICATION_OVERLAY`.
   - `flags = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN`.

2. On bubble tap (main thread):

   - Update `flags` to remove `FLAG_NOT_FOCUSABLE` and call `updateViewLayout(view, params)`; optionally add `FLAG_ALT_FOCUSABLE_IM` if the overlay might interact with IME.
   - Wait for `view.post { ... }` or `Choreographer` callback to ensure the window has been re-laid out and can accept focus.
   - Read `clipboardManager.primaryClip`.

3. Immediately after read:

   - Restore `FLAG_NOT_FOCUSABLE` and re-apply LayoutParams via `updateViewLayout`.

This preserves the overlay's non-intrusive behavior outside explicit user taps while meeting the foreground requirement for clipboard access.

### 5.5 Compliance and Documentation

Capsule must:

- Clearly explain in onboarding that tapping the bubble will read the clipboard once to assist with memory capture and that a system toast will indicate clipboard access on Android 13+.[^41][^1]
- Avoid any attempt to read clipboard without an explicit tap, or via ADB-only permissions (READ_LOGS) or invisible overlays, as such techniques have been explicitly discouraged by Google and may violate user expectations.[^43][^44][^42]


## 6. Surviving OEM Battery Killers and Foreground Service Strategy

### 6.1 Foreground Service Basics and Service Types

Foreground services run with elevated priority and display a persistent notification, making them less likely to be killed under memory pressure.
Android 14+ requires that foreground services declare one or more foregroundServiceType values such as `dataSync`, `mediaPlayback`, `location`, or `health`, each with associated manifest permissions and constants passed to `startForeground()`.[^9][^10][^46]
Guides emphasize using the type that matches the actual long-running work (e.g., `mediaPlayback` for audio players) and avoiding misuse.[^10][^47]

For Android 13 (Capsule's minSdk), service-type requirements are less strict, but future-proofing for 14/15 is critical.
Given Capsule's continuous synchronization of user PKM events, the best fit is `dataSync`, with a possible combination with `specialUse` or `systemExempted` only if explicitly granted and justified.

### 6.2 OEM Heuristics – Insights from DontKillMyApp

DontKillMyApp benchmarks show that OEMs such as Huawei, Xiaomi, and Samsung employ aggressive background-killing policies, sometimes impacting even foreground services and alarm-based tasks.[^48][^12]
Guides recommend:

- Using a true foreground service with a visible notification.
- Asking users to exempt the app from battery optimization and, for some OEMs, setting "no restrictions" and enabling autostart/app lock.[^13][^2]
- Scheduling periodic work via AlarmManager.setExactAndAllowWhileIdle, and combining wakelocks with foreground services for time-critical tasks.[^12][^2]

Brand-specific instructions for Samsung typically involve:

- Setting the app's battery mode to "Unrestricted".
- Adding the app to "Never sleeping apps" in Battery and Device Care.
- Avoiding being placed in "Deep sleeping apps".[^2][^13]

### 6.3 Capsule's Foreground Service Design

Key design decisions:

- Use a single long-lived foreground service (`CapsuleOverlayService`) responsible for both the overlay and minimal ingestion orchestration.
- Declare `android.permission.FOREGROUND_SERVICE` and, for Android 14+, `FOREGROUND_SERVICE_DATA_SYNC` with `android:foregroundServiceType="dataSync"`.
- Start the service only via explicit user action (e.g., "Enable Capsule" switch) and maintain a persistent, low-distraction notification indicating the overlay is active.

Notification design:

- Non-dismissible while the overlay is enabled, to signal ongoing behavior.
- Tapping opens a control panel where the user can pause Capsule, configure privacy, or disable the overlay.

Additional survival tactics:

- Detect manufacturer via `Build.MANUFACTURER` and show vendor-specific guides (with deep links to relevant battery settings) modeled on DontKillMyApp recommendations.[^12][^13][^2]
- Avoid multiple redundant background services; keep background work concentrated in the foreground service and use WorkManager for larger, deferrable tasks (e.g., batch vector reindexing).
- Use wake locks sparingly: Acquire short-lived partial wake locks for brief ingestion bursts only when strictly necessary, and always release promptly to avoid policy and battery issues.[^12]

### 6.4 Limits of Survivability

Even with a foreground service, OEM firmware can kill processes under extreme memory pressure (especially on 4 GB devices when many heavy apps are active), or when the user explicitly clears recents and the OEM decides to treat the app as killable despite foreground status.
Capsule must therefore:

- Design for idempotent restart: when the service is recreated, it should re-establish the overlay and restore minimal state from storage.
- Implement health metrics to detect frequent restarts and prompt the user to adjust device settings as recommended.


## 7. Edge AI and Local Storage Sandboxing

### 7.1 Storage Requirements for Text and Vectors

Capsule must store:

- High-frequency unstructured text snippets (clipboard captures, quick notes, transcriptions) with metadata.
- Dense vector embeddings for semantic retrieval.
- Derived summaries and tags from local or remote language models.

Options:

- **SQLite + vector extension:** Libraries such as sqlite-vss or SQLite Vector introduce vector columns and approximate nearest neighbor search into standard SQLite, enabling vector search with minimal footprint (~1–2 MB extension) and good performance for tens of thousands of vectors.[^49][^3]
- **ObjectBox with vector support:** ObjectBox 4.0 adds an on-device vector database for Android/Java, positioned as a fast, lightweight edge database tailored for restricted devices, with claims of being the first on-device vector DB and optimized for mobile and IoT.[^50][^19][^4]
- **Realm or plain SQLite:** Good for structured data but require custom handling for vectors (storing blobs and managing custom indexes externally).

Given Android's built-in SQLite and Room ecosystem, integrating sqlite-vss or SQLite Vector as a plugin is attractive for operational simplicity, while ObjectBox offers higher-level abstractions and potentially better performance at the cost of adding a new dependency stack.[^3][^49][^4][^50]

### 7.2 Recommended Data Model

A model suitable for both SQLite(+vector) and ObjectBox:

- `MemoryEvent` table/entity:
  - `id` (PK)
  - `timestamp`
  - `source` (clipboard, share, overlay note, accessibility event)
  - `sourceAppPackage`
  - `rawText`
  - `metadataJson` (optional structured context)

- `Embedding` table/entity:
  - `id` (PK, FK to MemoryEvent)
  - `vector` (BLOB or ObjectBox vector field)
  - `modelId` (e.g., embedding model version)

- Additional indices:
  - Index on `timestamp`
  - Index on `sourceAppPackage`

For SQLite Vector, vectors are stored in a BLOB field with a configured dimension and type (e.g., FLOAT32, dimension 384), and `vector_init` plus `vector_quantize` can be used to set up and accelerate similarity search.[^49][^3]
ObjectBox's vector DB natively supports vector fields with similarity operations and is optimized for small devices.[^19][^4]

### 7.3 ONNX Runtime Integration

ONNX Runtime provides an `onnxruntime-android` artifact and a `onnxruntime-mobile` variant with reduced operator sets and smaller footprint, suitable for mobile deployment.[^6][^7]
Integration typically involves:

- Adding `com.microsoft.onnxruntime:onnxruntime-android` or `onnxruntime-mobile` to Gradle dependencies.[^7][^6]
- Creating an `OrtEnvironment` and `OrtSession` with session options enabling NNAPI (for NPUs/DSPs) or XNNPACK (CPU SIMD) and tuning thread counts.[^6][^7]
- Loading a quantized embedding or SLM model from assets or external storage.

MVP Factory reports a fully local RAG pipeline on mobile using sqlite-vss, ONNX Runtime Mobile, and a quantized MiniLM-like embedding model (~22 MB, 384-dim embeddings) achieving ~140 ms p95 query latency and a total footprint under 40 MB on a mid-range Pixel device.[^3]
This suggests Capsules can feasibly perform on-device embedding and retrieval on modern mid-range phones.

### 7.4 Local SLM Options and Quantization

Research on mobile-friendly quantization indicates that integer-only quantization with 4-bit weights and 8-bit activations (e.g., W4A8) can enable on-device large language models to exploit NPUs while maintaining acceptable accuracy.[^51][^52][^53]
Independent analyses emphasize that smaller models (on the order of 1–3 billion parameters) with aggressive quantization can handle many mobile tasks such as summarization, rewriting, and short-form generation when carefully scoped.[^54][^55]

For Capsule, local SLMs should be limited to:

- Short summarization of snippets.
- Intent classification and tagging.
- Retrieval-augmented answer synthesis over local memories.

This allows selecting compact models that fit within a constrained on-device footprint when quantized and combined with ONNX Runtime Mobile.[^53][^7][^3]


## 8. Hardware Bifurcation and Tiered AI Architecture

### 8.1 Hardware Profiles

Define three rough device tiers:

- **Tier A – Baseline (4 GB RAM, mid-range CPU/GPU, limited NPU):** May experience frequent low-memory conditions under OEM policies; foreground services more likely to be killed under pressure.
- **Tier B – Modern mid-range (6–8 GB RAM, recent Snapdragon/Exynos/Dimensity with basic NPU):** Capable of consistent ONNX embedding generation and vector search; can support small SLMs with careful scheduling.[^7][^3]
- **Tier C – Flagship (8–12 GB RAM, high-performance NPU/TPU, UFS 3.x/4.x):** Capable of running quantized 1–3B parameter SLMs with acceptable latency for short contexts.[^55][^54][^7][^3]

Capsule should probe device capabilities at startup (RAM, supported NNAPI ops, CPU cores, presence of hardware acceleration) and assign the device to a tier to drive feature toggles.

### 8.2 Tiered AI Capability Matrix

| Tier | Embeddings | Vector DB | SLM Inference | Network Dependency |
|------|------------|-----------|---------------|--------------------|
| A – Baseline | Optional or batched; prefer lightweight embedding models or cloud embedding | SQLite-only or ObjectBox without vector extensions; fallback to lexical search or coarse clustering | None or very small local classifiers only; main LLM in cloud | Hybrid: local capture + optional cloud processing for summaries/answers |
| B – Mid-range | On-device embeddings via ONNX Runtime Mobile with small quantized models (~384-dim) | SQLite + sqlite-vss or ObjectBox vector DB | Optional small SLM for short summaries and classification; longer answers via cloud | Hybrid RAG: local retrieval, cloud LLM for synthesis; user can opt into full local mode if performance acceptable.[^3][^4][^19] |
| C – Flagship | On-device embeddings as in Tier B | Full vector DB with approximate search (sqlite-vss or ObjectBox) | Local SLM with quantized 1–3B model, on-device RAG end-to-end | Fully local by default; cloud optional for heavy tasks.[^3][^7][^54][^53][^55] |

### 8.3 Hybrid RAG for Lower-End Devices

For Tiers A and B, Capsule should adopt a hybrid RAG design:

- Always keep capture, storage, and retrieval local to preserve privacy.
- On devices too weak to run a full SLM, send only the retrieved snippets and minimal query metadata to a cloud LLM endpoint when the user explicitly triggers heavy operations (e.g., "summarize my day").
- Maintain strict user controls: offline-only mode disabling network-based summarization, and network-mode with transparent disclosures about data transmission.[^16]

Edge cases:

- If the device enters severe memory pressure, temporarily suspend embedding generation and revert to raw-text search until resources are available.
- Use WorkManager to process embeddings in the background when the device is idle, charging, and on unmetered networks (for remote tasks), particularly on Tier A devices.

### 8.4 Memory and Performance Guardrails

To protect device stability:

- Limit active ONNX sessions and SLM contexts; keep ephemeral sessions per task rather than long-lived heavy sessions, especially on baseline devices.[^6][^7]
- Implement backpressure in the ingestion pipeline to drop or defer low-value events when embedding queue length exceeds thresholds.
- Expose user-facing toggles to disable SLM features or reduce capture intensity (e.g., track only explicit shares, not every clipboard tap).


## 9. Risk Assessment – Crashes and Play Store Rejection

### 9.1 Risk 1 – Misuse of AccessibilityService for URL/DOM Scraping

**Description:** Using AccessibilityService to scrape Chrome/Brave URLs or page text for PKM without a disability-focused purpose.

- **Technical risk:** Chrome UI changes may break scraping, leading to crashes (null node trees, unexpected class names) unless defensively coded.[^23][^24][^22]
- **Policy risk:** Google Play's restrictions on AccessibilityService require that non-accessibility tools provide explicit disclosures and avoid data collection unrelated to assisting users with disabilities; misuse can lead to app removal.[^27][^17][^26][^18]

**Mitigation:**

- Restrict AccessibilityService usage to clearly assistive features (e.g., reading support) and ensure it is optional and separately gated.
- Avoid generic URL logging; instead, rely on explicit share flows and in-app browsing contexts.
- Implement robust null-checking and try/catch blocks around accessibility operations.

### 9.2 Risk 2 – Misuse of VpnService for Passive Browsing Logging

**Description:** Using a local VpnService to capture DNS or HTTP(S) traffic solely to reconstruct browsing history for PKM.

- **Technical risk:** Incomplete capture due to encrypted DNS, QUIC, and OEM bypass can produce inconsistent behavior or heavy resource use, increasing crash and battery-drain risk.[^30][^29]
- **Policy risk:** Google Play requires VpnService to be core to VPN-like functionality and forbids using it to collect sensitive user data without clear justification and consent; misuse can result in enforcement actions.[^31][^15][^16]

**Mitigation:**

- Do not use VpnService unless Capsule is repositioned as a VPN-centric tool.
- If ever used, declare VpnService in Play Console, provide strong disclosures, and encrypt all VPN data.[^15][^16]

### 9.3 Risk 3 – Foreground Service and Overlay Misconfiguration

**Description:** Incorrect WindowManager flags or missing SYSTEM_ALERT_WINDOW permission can cause `BadTokenException` or crashes when adding/removing overlay views; misconfigured foreground-service types on Android 14+ can cause runtime exceptions or app rejection during review.[^39][^40][^9][^10][^38]

**Mitigation:**

- Perform runtime permission checks for overlay capability; handle failure by disabling overlay and showing UX to guide enabling the permission.
- Use TYPE_APPLICATION_OVERLAY and appropriate flags (`FLAG_NOT_FOCUSABLE`, etc.), and guard all add/remove operations with try/catch and null checks.[^5][^38][^35]
- Declare correct foreground service types and associated permissions for Android 14+, ensuring the declared type matches actual behavior (`dataSync` for PKM sync) to avoid policy violations.[^47][^9][^10]

### 9.4 Risk 4 – Sensitive Data Handling and Data Safety Misreporting

**Description:** Collecting clipboard contents, URLs, or app usage data without accurately disclosing collection, storage, and sharing practices in the Google Play Data safety section and privacy policy.

Cases exist where apps were flagged for discrepancies between on-device data transmission and declared practices.[^56][^57]
User Data policy requires that access to personal and sensitive data be limited to reasonably expected purposes, with clear disclosure and consent.[^16]

**Mitigation:**

- Build a data inventory listing every category of collected data and map it to required disclosures in Play Console.
- Provide a detailed, accurate privacy policy and link it from the Play listing and in-app.
- Offer opt-in controls for sensitive capture (clipboard, accessibility, any browser context) and an offline-only mode.


## 10. Conclusion and Implementation Blueprint

This specification outlines a feasible architecture for Capsule that respects Android's modern privacy model, survives OEM battery heuristics as far as possible, and delivers edge AI features tailored to device capabilities.

Core guidance:

- Implement the overlay as a Compose-based TYPE_APPLICATION_OVERLAY view owned by a foreground service, with a custom LifecycleOwner and ViewModel backing state.
- Use a focus-stealing clipboard mechanism only in response to explicit user taps on the bubble, with immediate restoration of non-focusable flags and clear user disclosures.
- Avoid using AccessibilityService and VpnService for generic passive browsing logging; prioritize explicit user-driven capture and optional, tightly scoped assistive features.
- Build a local PKM store around SQLite+vector extensions or ObjectBox's on-device vector database, feeding it via ONNX-based embedding models and, on capable hardware, a quantized SLM.
- Adopt a tiered AI architecture to ensure graceful degradation on 4 GB devices while leveraging full local RAG and SLM capabilities on flagships.
- Treat Google Play policies as first-class architectural constraints, especially for AccessibilityService, VpnService, clipboard, and data safety reporting.

With this blueprint, a senior Android developer can implement Capsule in a way that is technically robust, privacy-preserving, and aligned with evolving Android platform and policy constraints.

---

## References

1. [How to Use Jetpack Compose Inside Android Service](https://www.techyourchance.com/jetpack-compose-inside-android-service/) - This articles explains how to resolve the errors when using Jetpack Compose inside Android Service

2. [Keep Background Services Alive on Android (by Brand)](https://help.biolovision.net/Keep_Background_Services_Alive_on_Android_(by_Brand))

3. [Local RAG on mobile: vector search under 200ms - MVP Factory](https://mvpfactory.io/blog/building-a-local-rag-pipeline-on-mobile-vector-search-with-sqlite-on-device/) - Implementing a fully offline retrieval-augmented generation system using sqlite-vss for vector simil...

4. [On-device vector database for Android (Java, Kotlin) - ObjectBox](https://objectbox.io/the-on-device-vector-database-for-android-and-java/) - ObjectBox 4.0 is an on-device vector database for Android and Java developers to enhance their apps ...

5. [how to use Jetpack Compose in Service (Floating Window)](https://stackoverflow.com/questions/76503237/how-to-use-jetpack-compose-in-service-floating-window) - I want to use Jetpack Compose to implement the floting window UI. But I got this error:java.lang.Ill...

6. [On-device ML model ONNX Runtime integration for mobile app](https://truetech.dev/mobile-apps-development/services/ai-ml/on-device-ml-onnx-runtime-mobile-app.html) - Integrate ONNX Runtime for cross-platform ML model execution on iOS and Android without internet. Su...

7. [Bringing ONNX models to Android - Surface Duo Blog](https://devblogs.microsoft.com/surface-duo/onnx-machine-learning-3/) - Hello Android developers, One of the advantages of the ONNX runtime is the ability to run locally on...

8. [When to check the clipboard content in Android API 29 when app ...](https://stackoverflow.com/questions/61732218/when-to-check-the-clipboard-content-in-android-api-29-when-app-gets-focus) - One of the privacy-related changes in API 29 is that an app does not have access to the clipboard un...

9. [Foreground service types are required | Android Developers](https://developer.android.com/about/versions/14/changes/fgs-types-required) - This document details the new requirements for foreground service types in apps targeting Android 14...

10. [Foreground service types | Background work - Android Developers](https://developer.android.com/develop/background-work/services/fgs/service-types) - This document outlines the specific foreground service types required for apps targeting Android 14 ...

11. [Changes to foreground service types for Android 15](https://developer.android.com/about/versions/15/changes/foreground-service-types) - This document outlines the changes and new types introduced for foreground services in Android 15, i...

12. ['DontKillMyApp' measures how Android kills background ...](https://9to5google.com/2020/06/25/dontkillmyapp-android-kill-background-apps/) - A new app, 'DontKillMyApp', is designed to measure how different Android devices treat background ap...

13. [Samsung](https://dontkillmyapp.com/samsung) - Hey Android vendors, don’t kill my app!

14. [Google | Don't kill my app!](https://dontkillmyapp.com/google) - Hey Android vendors, don’t kill my app!

15. [Understanding Google Play's VpnService policy - Play Console Help](https://support.google.com/googleplay/android-developer/answer/12564964?hl=en_my) - The VpnService is a base class for applications to extend and build their own VPN solutions. If your...

16. [User Data - Play Console Help - Google Help](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) - If your app handles personal and sensitive user data, then you must: Limit the access, collection, u...

17. [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en) - Disclaimer: Policy summaries and Key Considerations are overviews only; always refer to the full pol...

18. [Use of the AccessibilityService API - Play Console Help - Google Help](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en) - Google Play permits the use of the AccessibilityService API for a wide range of applications. Howeve...

19. [First on-device Vector Database for Edge AI - ObjectBox](https://objectbox.io/the-first-on-device-vector-database-objectbox-4-0/) - The first on-device vector database empowers advanced AI apps that can combine user content with LLM...

20. [Create your own accessibility service - Android Developers](https://developer.android.com/guide/topics/ui/accessibility/service) - Learn how to create, configure, and implement an Android accessibility service to enhance the user i...

21. [AccessibilityService | API reference - Android Developers](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)

22. [Android : Read Google chrome URL using accessibility service](https://stackoverflow.com/questions/38783205/android-read-google-chrome-url-using-accessibility-service) - I want to read the url user has entered in his browser. Here is my accessibility service code. <acce...

23. [Get Chrome current Url via AccessibilityService](https://stackoverflow.com/questions/53186529/get-chrome-current-url-via-accessibilityservice) - Im trying to create an app which includes chrome visited site logger function here is my Accessibili...

24. [Amir-yazdanmanesh/Accessibility-Service-Browser-URL- ...](https://github.com/Amir-yazdanmanesh/Accessibility-Service-Browser-URL-Filter) - restrict the URLs that the user enters in their browser. - Amir-yazdanmanesh/Accessibility-Service-B...

25. [Google Play policy about use of the Accessibility API](https://orangeoma.zendesk.com/hc/en-us/articles/4407888308242-Google-Play-policy-about-use-of-the-Accessibility-API) - On July 28, 2021 Google Play announced a number of upcoming new policies. Among them the policy on P...

26. [Impact of Accessibility Permission in Android Apps - BrowserStack](https://www.browserstack.com/guide/accessibility-permission-in-android) - Learn what Android accessibility permission is, why it matters, and the risks involved. Also, explor...

27. [Google Play Accessibility Services Policy Update](https://myappmonitor.com/blog/google-play-accessibility-services-policy-update) - Google Play is introducing an update to its Accessibility Services policy. While the Accessibility A...

28. [How to Implement a Local VPN in Android for Content Filtering ...](https://stackoverflow.com/questions/78898268/how-to-implement-a-local-vpn-in-android-for-content-filtering-without-server-dep) - Create and manage a VPN service locally on the Android device using Kotlin. Configure VPN settings s...

29. [Many Android VPN-enabled apps do not protect user traffic - iTnews](https://www.itnews.com.au/news/many-android-vpn-enabled-apps-do-not-protect-user-traffic-449132) - Android users should not trust virtual private networking (VPN) enabled apps to protect them, accord...

30. [How do I capture traffic that is bypassing local VPN on android?](https://www.reddit.com/r/AskNetsec/comments/1ph7i05/how_do_i_capture_traffic_that_is_bypassing_local/) - I am using PCAPDroid to capture traffic for all apps, it does capture most of the traffic but there ...

31. [Google Play To Ban Android VPN Apps From Interfering With Ads](https://tech.slashdot.org/story/22/08/30/2147205/google-play-to-ban-android-vpn-apps-from-interfering-with-ads) - "Google claims to be cracking down on apps that are using the VPN service to track user data or rero...

32. [Kiwi Browser's latest update brings Google Chrome Extensions to Android](https://www.xda-developers.com/kiwi-browser-google-chrome-extensions-android/) - The popular third-party Chromium-based browser called Kiwi Browser has just added a major new featur...

33. [RIP, Kiwi Browser with extension support](https://forum.vivaldi.net/topic/105060/rip-kiwi-browser-with-extension-support) - I've been on Vivaldi for my Linux desktop for many years now, and believe it's the best browser, all...

34. [Source-code used in Kiwi Browser for Android](https://github.com/kiwibrowser/src) - Source-code used in Kiwi Browser for Android. Contribute to kiwibrowser/src development by creating ...

35. [Jetpack Compose, recomposition won't trigger when using custom ...](https://stackoverflow.com/questions/72379865/jetpack-compose-recomposition-wont-trigger-when-using-custom-lifecycle-viewmod) - I'm trying to create an overlay and attach the view to the WindowManager, I've managed to do this by...

36. [MyLifecycleOwner.kt - GitHub Gist](https://gist.github.com/handstandsam/6ecff2f39da72c0b38c07aa80bbb5a2f) - Jetpack Compose OverlayService. You have to have all the correct permissions granted and in your man...

37. [Has Anyone Used Jetpack Compose in an Overlay Service? - Reddit](https://www.reddit.com/r/androiddev/comments/1g4g0d3/has_anyone_used_jetpack_compose_in_an_overlay/) - You just need to mock life cycle owner and saved state registry. I would really like to chat about w...

38. [Android Tutorial - Creating Overlay (always-on-top) Windows](https://so.parthpatel.net/android/doc/6214/creating-overlay-always-on-top-windows/) - Android Tutorial - Creating Overlay (always-on-top) Windows

39. [Android System overlay window](https://stackoverflow.com/questions/32652533/android-system-overlay-window) - I'm trying to create a system overlay. But I keep getting "permission denied". I'm using SDK version...

40. [Android - Creating Overlay (always-on-top) Windows - DevTut](https://devtut.github.io/android/creating-overlay-always-on-top-windows.html) - Popup overlay, Granting SYSTEM_ALERT_WINDOW Permission on android 6.0 and above

41. [Android apps are reading your clipboard – here's how you can stop ...](https://blog.tinaciousdesign.com/android-apps-are-reading-your-clipboard-heres-how-you-can-stop-them) - Disabling clipboard read permissions. Currently, there doesn't appear to be a way to disable clipboa...

42. [Automation Apps Are Broken By Android 13's Privacy Push That's ...](https://hothardware.com/news/automation-apps-broken-android-13-privacy-push-working-as-intended) - Android 13 breaks this roundabout way to automatically read updated clipboard contents by changing t...

43. [Clipboard not accessible from background app with Android 10 SDK ...](https://stackoverflow.com/questions/58727690/clipboard-not-accessible-from-background-app-with-android-10-sdk-upgrade) - The copy/paste feature used to work in my app. But once I have upgraded my apps SDK to target Androi...

44. [Private Clipboard helps mimic Android 10's clipboard privacy on older Android devices](https://www.xda-developers.com/private-clipboard-mimic-android-10-privacy/) - Private Clipboard app by XDA Senior Member easyjoin lets you mimic the clipboard privacy feature fro...

45. [Would you be happy if Google restricted background clipboard access ONLY to System apps with Android Q?](https://www.reddit.com/r/Android/comments/alpcei/would_you_be_happy_if_google_restricted/) - Would you be happy if Google restricted background clipboard access ONLY to System apps with Android...

46. [android - how do i know the foregroundServiceType for a particular ...](https://stackoverflow.com/questions/78106162/how-do-i-know-the-foregroundservicetype-for-a-particular-foreground-service) - Foreground Service Types: connectedDevice : Used for services interacting with connected devices (e....

47. [Android Foreground Services: Types, Permissions and Limitations](https://softices.com/blogs/android-foreground-services-types-permissions-use-cases-limitations) - Foreground services allow your app to perform critical tasks that are visible to the user and less l...

48. [DontKillMyApp: Make apps work - Apps on Google Play](https://play.google.com/store/apps/details?id=com.urbandroid.dontkillmyapp&hl=en_US) - Benchmark & fix reliability of background tasks on your phone to make apps work

49. [SQLite Vector - A blazing fast and memory efficient vector search ...](https://www.sqlite.ai/sqlite-vector) - SQLite AI transforms SQLite into a distributed AI-native database for the Edge—combining the simplic...

50. [The Database for Android Apps - ObjectBox Java, Kotlin, and Flutter](https://objectbox.io/android-database/) - First On-Device vector database for Android. Develop data-driven AI apps for any Android device. Obj...

51. [Mobile-friendly Quantization for On-device Language Models - arXiv](https://arxiv.org/html/2408.13933v1) - Yet, 8-bit activations are very attractive for on-device deployment as they would enable LLMs to ful...

52. [Mobile-friendly Quantization for On-device Language Models](https://arxiv.org/html/2408.13933v2)

53. [MobileQuant: Mobile-friendly Quantization for On-device ... - GitHub](https://github.com/saic-fi/MobileQuant) - Yet, 8-bit activations are very attractive for on-device deployment as they would enable LLMs to ful...

54. [On-Device LLMs: State of the Union, 2026 - Vikas Chandra](https://v-chandra.github.io/on-device-llms/) - This enables 8-bit quantization of both weights and activations with minimal loss. SpinQuant (Meta) ...

55. [How to Run Tiny LLMs on Device: Model Choice, Quantization, and ...](https://vocal.media/futurism/how-to-run-tiny-ll-ms-on-device-model-choice-quantization-and-app-size-tricks) - Does quantization always hurt quality? Quality changes rather than disappears. Studies show 8-bit qu...

56. [Action Required: Your app is not compliant with Google Play ...](https://stackoverflow.com/questions/71199143/action-required-your-app-is-not-compliant-with-google-play-policies-what-is-t) - All apps are required to complete an accurate Data safety section that discloses their data collecti...

57. [Invalid Privacy Policy URL Rejection for Google Play Store](https://www.termsfeed.com/blog/invalid-privacy-policy-url-google/) - The Google Play Store "Privacy Policy link invalid or missing" rejection message informs app develop...

