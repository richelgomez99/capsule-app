# Orbit

**A private, local-first personal agent for Android.**

> Think OpenClaw, but on the device that's with you all day — and the OS is the sandbox.

Orbit catches what you screenshot, copy, and save across every app on your phone, understands it on-device, and helps you act on it — a daily diary of what your attention was actually on, calendar events extracted from flight confirmations, to-dos surfaced from screenshots, and eventually natural-language answers across everything you've ever saved.

**Nothing leaves your device unless you opt in.**

---

## Status

Building in public as 1 of 500 builders in **[Canopy by Founders, Inc.](https://f.inc/canopy)** — a 5-week incubator for "obsessed builders." Demo Day: **May 22, 2026**.

| Milestone | State | Shipped |
|-----------|-------|---------|
| **Capture overlay** (the always-on bubble) | ✅ Shipped | Apr 18 |
| **Daily diary** (v1 target) | 🔨 Building | Target: May 22 |
| **Orbit Actions** (v1.1) — calendar + to-do extraction | 📋 Spec'd | Post-v1 |
| **Ask Orbit** (v1.2) — natural-language Q&A | 📋 Spec'd | Post-v1 |
| **Knowledge graph** (v1.3) | 📋 Spec'd | Post-v1 |

---

## Why Android, why now

On **April 2, 2026**, Google previewed [**Gemini Nano 4**](https://android-developers.googleblog.com/2026/04/AI-Core-Developer-Preview.html) in the AICore Developer Preview — the next generation of on-device AI for Android.

- **4× faster** than the previous Nano
- **60% less battery** per inference
- **Multimodal**: text, image, audio
- **140+ languages**
- Two variants: Nano 4 **Fast** (speed) and Nano 4 **Full** (reasoning)
- Forward-compatible: code written today ships the moment flagship devices get the model later in 2026

Orbit is being built against that preview. This isn't a bet on future AI — it's a bet on AI developers can touch right now, that consumers will have on their phones in months.

---

## The problem

Your phone is full of screenshots, copied text, recipes, articles, flight confirmations, voice notes, and group-chat snippets you saved because they mattered in the moment. They go nowhere. Your phone forgets them. You forget you ever cared.

We've normalized building products that turn your attention into someone else's data and then give you nothing back in return. Orbit is the inverse: your attention compounds for *you*, on a device only you have access to, and the agent acts on it on your terms.

---

## The five-week roadmap

| Version | What ships | Timing |
|---------|-----------|--------|
| **v1** | Floating capture bubble · daily diary · on-device intent classification · soft-delete & trash · URL-hash deduplication | **May 22, 2026** (Demo Day) |
| **v1.1** | Orbit Actions — extract calendar events from confirmations, build to-dos from screenshots | Post-v1 |
| **v1.2** | Ask Orbit — natural-language Q&A across your saves, with RAG on-device | Post-v1 |
| **v1.3** | Private knowledge graph — compounds the longer you use it | Post-v1 |
| **v2.0+** | Cloud, but on infrastructure *you* own — BYO LLM key, BYO Postgres. Local stays the source of truth, always. | Long-term |

---

## Architecture at a glance

**Core principles** (full list in [`.specify/memory/constitution.md`](./.specify/memory/constitution.md)):

1. Local-first supremacy — source of truth lives on-device, in SQLCipher
2. Privilege separation by design — split processes for capture, ML, network, UI
3. Intent before artifact — every save starts with *why*, not *what*
4. Under-deliver on noise — Orbit stays quiet by default
5. User-sovereign cloud escape hatch (LLM) — BYO API key, never Orbit's servers
6. User-sovereign cloud storage — BYO database, local remains source of truth
7. …and four more (audit-log transparency, accumulated corpus lock-in, continuations grow captures, cross-app intent graph)

**Stack:**

- **Language:** Kotlin, Jetpack Compose
- **On-device AI:** Gemini Nano 4 (via AICore, from April preview) · ML Kit for OCR
- **Storage:** Room + SQLCipher, Android Keystore for key wrapping
- **Service model:** Foreground service with `specialUse` type for the overlay, WorkManager for retention + hydration workers
- **IPC:** Four-process split (`:capture`, `:ml`, `:net`, `:ui`) wired via AIDL

---

## Documentation

The depth of the work lives here:

| Doc | What it is |
|-----|------------|
| [`.specify/memory/constitution.md`](./.specify/memory/constitution.md) | The ten founding principles — what Orbit will and won't do |
| [`.specify/memory/PRD.md`](./.specify/memory/PRD.md) | Product requirements: v1 scope + roadmap to v2.0 |
| [`.specify/memory/design.md`](./.specify/memory/design.md) | Visual architecture: typography, color, motion, every UI surface for v1–v1.3. Includes the "Quiet Almanac" aesthetic and first-class Graphite dark mode. |
| [`specs/001-core-capture-overlay/`](./specs/001-core-capture-overlay/) | Spec-kit breakdown of the capture overlay (the foundation that shipped) |
| [`specs/002-intent-envelope-and-diary/`](./specs/002-intent-envelope-and-diary/) | Spec-kit breakdown of v1 (envelope model + daily diary) |
| [`specs/002-intent-envelope-and-diary/quickstart.md`](./specs/002-intent-envelope-and-diary/quickstart.md) | Step-by-step acceptance walk-through for v1 on a physical device |
| [`specs/003-orbit-actions/quickstart.md`](./specs/003-orbit-actions/quickstart.md) | **v1.1 Orbit Actions** — calendar / to-do / share AppFunctions + weekly Sunday digest, on-device only |
| [`specs/004-ask-orbit/`](./specs/004-ask-orbit/) through [`007-knowledge-graph/`](./specs/007-knowledge-graph/) | Post-v1 spec stubs |

---

## Who I'm looking to talk to

- **Android power users** who are tired of their phone being a one-way data pipeline for everyone but them
- Builders working on **on-device AI**, **mobile-first agents**, or **privacy-respecting consumer products**
- Anyone who's shipped a **fully privacy-respecting consumer product** (Signal, ProtonMail, Apple Health, Pixel on-device features, Arc) and figured out how to learn from users *without* surveilling them — I want to compare notes on the feedback-loop problem
- Potential **co-founders** who want to build this with me past Demo Day

Reach out: [LinkedIn](https://www.linkedin.com/in/richelgomez) · or open an Issue on this repo

---

## Built by

[**Richel Gomez**](https://www.linkedin.com/in/richelgomez) — Implementation & Solutions Engineer, 1st place NYC AI Agents Hackathon, Overclock Accelerator Cohort 5, Canopy by Founders, Inc. cohort member.

I had never built an Android app before Wednesday, April 15, 2026. Three days later, the capture overlay was shipped. This is what coding agents enable now — and what five weeks of heads-down focus at Founders, Inc. is about to produce.

---

## License

All rights reserved, for now. License decision will be made at/after Demo Day (May 22, 2026) once the v1 scope is shipped and the post-v1 architecture (BYOK LLM, BYOC storage) is stable.
