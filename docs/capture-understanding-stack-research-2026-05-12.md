# Capture Understanding Stack Research - 2026-05-12

This research pass answers the next stack question for Orbit: if the product is an agentic memory system for closing loops, what does it need on top of the cloud LLM, knowledge graph, gateway, and observability stack so it can accurately understand and summarize captures?

Short answer: Orbit needs an **evidence acquisition layer**. The agent should not summarize from vibes, app names, thumbnails, or screenshots alone when better evidence is available. It should gather the strongest permitted evidence for each capture, preserve the provenance of that evidence, summarize only what the evidence supports, and clearly say when the available evidence is weak.

Related docs:

- [Product Roadmap Audit - 2026-05-12](product-roadmap-audit-2026-05-12.md)
- [Agent Stack Research Pass - 2026-05-12](agent-stack-research-2026-05-12.md)
- [Agent Stack POC Research - 2026-05-12](agent-stack-poc-research-2026-05-12.md)
- [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md)
- [Capture Source Identity Plan](capture-source-identity-plan.md)
- [Orbit Agent Architecture Round 1 - 2026-05-12](orbit-agent-architecture-round-1-2026-05-12.md)
- [Orbit Agent Architecture Round 2 - 2026-05-12](orbit-agent-architecture-round-2-2026-05-12.md)

## Context7 note

This pass originally used primary docs fetched directly because Context7 was not yet exposed in the Copilot tool session. After fixing the local MCP config, Context7 was verified against Firecrawl docs and is available for subsequent rounds. Round 1 agent architecture research also used Context7 for Mastra, LangGraph JS, and Pydantic AI framework details.

## Research sources checked

Primary docs and source pages checked in this pass:

- Firecrawl scrape/extract/crawl docs.
- Browserbase platform/session/stealth docs.
- Playwright page/browser automation docs.
- Cloudflare Browser Rendering and Browser Run docs.
- Crawlee docs.
- Mozilla Readability docs.
- Unstructured open source overview and partitioning docs.
- Microsoft MarkItDown README/docs.
- Tavily Search, Extract, and Crawl docs.
- Exa Search, Contents, and Answer docs.
- YouTube Data API videos/search/captions docs and oEmbed behavior.
- oEmbed spec.
- Existing Orbit URL hydration, continuation, source identity, and LLM gateway specs/code.

## Executive answer: does the agent need to visit the site?

Sometimes, yes. More precisely: the agent needs to acquire evidence.

Orbit should not browse every capture by default. Browsing is only one evidence acquisition method, and it is often the most expensive, slow, fragile, and legally sensitive one. But for a URL capture, an accurate summary usually requires at least fetching and extracting the public page content. If Orbit only has a URL, title, thumbnail, or screenshot, the summary must say that.

The right rule is:

1. If a capture has usable local text, summarize that local text first.
2. If a capture has a public URL, fetch public metadata and static readable content first.
3. If static extraction is missing or obviously incomplete, use a managed extractor or browser-rendered fallback when policy allows it.
4. If the capture is screenshot-only, use OCR and optionally a VLM, but label the summary as based on visible screenshot content.
5. If the capture is media, use provider metadata and transcript/caption sources when available. Do not claim to understand a full video if only the title and thumbnail were available.
6. If the content is authenticated, private, paywalled, blocked, or app-only, be honest. Future connected-account browsing can be a separate explicit-consent feature, not a default enrichment path.

This turns summarization into a provenance problem, not only a model-quality problem.

## User-facing understanding controls

The acquisition ladder should not be exposed to users as a list of crawlers, browsers, OCR models, document parsers, and search APIs. From the user's point of view, the real question is simpler:

> How hard should Orbit try to understand what I saved?

The best product shape is a single primary setting, per-capture overrides, and a readable audit trail.

### Primary setting: understanding depth

Use three user-facing tiers:

| Tier | User-facing meaning | What Orbit may do automatically | What still requires a trigger or separate consent |
| --- | --- | --- | --- |
| **Basic** | Save it, identify it, and do safe hydration. | Source/app/provider identity, URL canonicalization, public metadata, OpenGraph/oEmbed, static public fetch, Readability when available, local OCR when available, metadata-only summaries with limitations. | Managed extraction, browser rendering, cloud VLM screenshot analysis, external search enrichment, connected-account access, repeated deep retries. |
| **Smart** | Go deeper when it likely matters to me. | Everything in Basic, plus managed extraction for public URLs when static extraction fails, transcript/document parsing for captures the user opens or marks as important, limited cloud summarization/entity/action extraction, budgeted enrichment for active interests or clusters. | Browser rendering for sensitive domains, cloud VLM on screenshots, broad external search enrichment, connected-account access, high-cost repeated enrichment. |
| **Deep** | Get as much public context as Orbit reasonably can. | Everything in Smart, plus broader managed extraction, browser-rendered fallback for public pages, richer transcript/document parsing, proactive relatedness enrichment, richer entity/action extraction, and a larger per-user budget. | Logged-in/private browsing, CAPTCHA bypass, external writes, and sensitive screenshot analysis still require separate explicit consent. Deep does not mean unsafe. |

Default recommendation:

- Internal alpha can default to **Smart** if cloud-mode AI is already the product default; otherwise the product will feel underpowered.
- Public builds should introduce the choice plainly: `Orbit can understand saved links and screenshots at three depths. You can change this anytime.`
- **Basic** should remain one tap away for users who want capture/storage without deep enrichment.
- **Deep** should be opt-in and framed as public-context enrichment, not unlimited browsing.

Possible UI copy:

- `Basic: save and lightly identify captures`
- `Smart: understand important captures automatically`
- `Deep: try harder to gather public context`

### Per-capture controls

The global setting is not enough because the same user may want Deep behavior for research links and Basic behavior for messages, finance, health, workplace pages, or screenshots with personal data.

Capture detail should support local override actions when relevant:

- `Get more context` for metadata-only, failed, or shallow summaries.
- `Summarize only what I saved` when the user wants no web/search expansion.
- `Refresh source` for stale fetched pages.
- `Use screenshot only` when the user does not want URL fetching.
- `Never fetch this domain` for domain-level suppression.
- `Wrong source` for provider/app identity corrections.
- `Wrong summary`, `Not relevant`, and `Too much context` for feedback and evals.

These actions should be visible only when useful. The normal capture screen should stay calm: summary, evidence/limitations, source, related items, actions.

### Interest-aware enrichment

The middle tier should not mean Orbit randomly browses more. It should mean Orbit spends effort where the user has shown interest.

Signals that can justify Smart-tier deeper enrichment:

- user opened the capture detail;
- user explicitly starred, pinned, annotated, or asked about the capture;
- capture belongs to an active cluster the user has engaged with;
- the domain/source has high extraction success and low sensitivity;
- the user recently asked Ask Orbit about the same topic;
- the capture contains action-like evidence: dates, tickets, recipes, receipts, travel, tasks;
- the capture is needed to explain a relatedness suggestion.

Signals that should suppress or downgrade enrichment:

- foreground app or provider is likely sensitive: messaging, password manager, banking, health, workplace auth, private docs;
- URL is private, authenticated, paywalled, robots-disallowed, or blocked;
- screenshot OCR contains likely secrets, tokens, addresses, financial/account numbers, or private messages;
- user has rejected enrichment for that domain/source;
- per-user cost/budget is exhausted.

### What the setting should not imply

Even the deepest tier should not imply:

- logged-in browsing without connected-account consent;
- CAPTCHA bypass as a product behavior;
- scraping private content;
- external writes/actions;
- hiding network/model activity from the audit log;
- retaining raw screenshots/page text forever;
- summarizing beyond available evidence.

The product promise is: **Orbit can try harder, but it still tells you what evidence it used and where it stopped.**

### Suggested settings layout

In Settings, this belongs near `What Orbit did` and cloud controls rather than in a developer-style extractor menu.

Suggested section:

```text
UNDERSTANDING

Depth
Basic / Smart / Deep

Cloud visual understanding
Off by default unless Smart/Deep and user enables it

Use browser fallback for public pages
Off in Basic, limited in Smart, available in Deep

Search the web to add context
Ask first / Smart / Deep

Never fetch these domains
Manage list

What Orbit did today
Audit log of fetches, model calls, summaries, and failures
```

Do not expose every vendor or parser in normal settings. The user controls behavior and risk. The backend can route to Firecrawl, Tavily, Exa, Playwright, MarkItDown, or Unstructured based on cost/quality policy.

## Can this coexist with the current app?

Yes, if it lands as an extension of continuations rather than a second product bolted beside Orbit.

The app already has the right shape:

- capture produces envelopes;
- continuations enrich captures asynchronously;
- URL hydration already proves the `capture -> network gateway -> summary -> continuation result` pattern;
- Settings already has a `Pause Orbit`/continuations control;
- the roadmap already calls for local/cloud kill switch, per-capability controls, audit log, memory inspector, and relatedness feedback;
- source identity work already separates provider, foreground app, and category.

The coexistence rule should be:

1. Keep the first screen as Diary/captures, not an agent console.
2. Treat summaries, evidence, relatedness, and actions as capture detail layers.
3. Keep the agent subordinate to the user's saved corpus and explicit questions.
4. Put trust controls in Settings and evidence labels in capture detail.
5. Use the audit log to make every fetch, browser fallback, model call, and failed enrichment inspectable.
6. Keep Basic mode and Pause Orbit as escape hatches so the app never feels like it is running away from the user.

So yes, this can coexist. The risk is not technical coexistence; the risk is product overreach. If Orbit makes deep enrichment feel like a hidden surveillance feature, it loses. If Orbit frames it as user-controlled understanding depth with per-capture evidence and undoable feedback, it becomes the thing that makes the agent believable.

## Current repo baseline

The existing Orbit code/specs already have a narrow version of this idea:

- `:net` is the sole egress boundary for URL hydration and cloud LLM calls.
- `UrlHydrateWorker` binds to the network gateway, calls `fetchPublicUrl`, hashes/canonicalizes URLs, asks the local/cloud summarizer over readable HTML, and persists a URL hydration continuation.
- The original IntentEnvelope continuation design expects URL title/domain/excerpt/summary and screenshot URL detection.
- Existing fetch policy is intentionally conservative: HTTPS-only, GET-only, public fetch, no cookies/referer, fixed user agent, size/time caps, and terminal non-retriable errors for unsupported URLs.
- The source identity plan already separates foreground origin, content provider, category, and copy like `YouTube via Brave`.

That was the right v1 shape for a local-first/static-public-web design. The cloud pivot changes the next step: keep the existing `:net` boundary on Android, but add a server-side enrichment/acquisition service behind the cloud API for richer extraction, browser fallback, document parsing, OCR/VLM, transcripts, queueing, cost controls, and observability.

## The core architecture addition

Add a new cloud-side service boundary:

```text
Android capture
  -> IntentEnvelope / artifact metadata
  -> narrow cloud continuation enqueue API
  -> Evidence acquisition workers
  -> EvidenceBundle records
  -> summary/entity/action extraction via LLM gateway
  -> embeddings + KG/memory ingestion
  -> cited summary and continuations back to Orbit
```

The important new object is an `EvidenceBundle`: a normalized, provenance-rich package of what Orbit actually saw. The agent uses evidence bundles as inputs, not raw assumptions.

Suggested conceptual fields:

| Field | Purpose |
| --- | --- |
| `evidence_id` | Stable ID for citations and deletion. |
| `user_id` / `capture_id` | Tenant and capture ownership. |
| `source_kind` | `local_text`, `url_static_html`, `url_readability`, `oembed`, `rendered_dom`, `screenshot_ocr`, `screenshot_vlm`, `pdf`, `document`, `youtube_metadata`, `transcript`, `search_result`. |
| `acquisition_method` | Adapter/tool that produced it. |
| `source_url` / `canonical_url` | The web source, if any. |
| `source_app_package` / `source_app_label` | App origin from device state. |
| `provider` | YouTube, Reddit, Instagram, Chrome, Brave, etc., when known. |
| `observed_at` | Capture time. |
| `fetched_at` | Web/document acquisition time. |
| `content_hash` | Deduplication, cache key, invalidation. |
| `extractor_version` | Reproducibility and regression tracking. |
| `raw_ref` | Pointer to raw blob if retained, not inline trace content. |
| `clean_text_ref` | Pointer to cleaned text/markdown chunks. |
| `snippets` | Short citable excerpts or OCR spans. |
| `confidence` | Extractor confidence/coverage, not model confidence alone. |
| `limitations` | `auth_required`, `robots_disallowed`, `paywall`, `js_failed`, `no_transcript`, `ocr_low_confidence`, etc. |
| `retention_policy` | Whether raw evidence can be retained, expired, or only hashed. |

Evidence bundles then feed three downstream paths:

1. **User-facing understanding**: title, provider, summary, key entities, possible actions, limitations.
2. **Retrieval**: chunks, embeddings, lexical index, capture-level metadata.
3. **Memory/KG**: conservative facts and relationships with source evidence IDs.

## Evidence hierarchy by capture type

| Capture type | Strongest default evidence | Fallback evidence | What Orbit may safely say |
| --- | --- | --- | --- |
| Plain text / note | Captured text and user-authored note. | None needed. | Summary of the user's saved text. |
| Clipboard URL | Public fetch, OpenGraph/oEmbed, Readability/static extraction. | Managed extractor, browser render, then metadata-only. | `From the saved page...` only if page text was extracted. Otherwise `The link metadata says...`. |
| Screenshot with visible URL | OCR URL -> same URL path above, plus screenshot OCR. | Screenshot OCR/VLM only. | Prefer fetched page summary; if fetch fails, say `From the screenshot text...`. |
| Screenshot without URL | OCR and visual understanding. | Foreground app label/category and nearby captures. | Visible UI/text summary only, not full app/page claims. |
| YouTube link | Canonical video ID, oEmbed/title/channel/thumbnail, YouTube Data API metadata, public transcript/captions if available. | MarkItDown YouTube transcription path or page extraction, then metadata-only. | Full video summary only with transcript/caption/audio evidence. Metadata-only summary must say so. |
| YouTube app screenshot | OCR visible title/channel and any detected video ID/URL. | App label + thumbnail/VLM. | `This appears to be a YouTube screen...` until URL/transcript evidence exists. |
| Social/media URL | oEmbed/provider metadata, public page extraction, provider API if allowed. | Browser render for public content, screenshot OCR/VLM. | Public post/page summary only if text extracted. Private/app-only content should be marked unavailable. |
| Article/news/blog | Static HTML + Readability. | Firecrawl/Tavily/Exa/Jina, browser render. | Good fit for automatic summaries when extracted text is sufficient. |
| JS-heavy page/product page | Managed extractor or rendered browser DOM/screenshot. | Metadata-only. | Product/page summary if rendered text is acquired; otherwise title/metadata limitations. |
| PDF/document | MarkItDown or Unstructured partitioning, with page/element metadata. | OCR/VLM for scanned docs. | Document summary with page/element citations where possible. |
| Receipt/ticket/event screenshot | OCR + event/receipt extraction schema + VLM if layout matters. | Manual confirmation prompt. | Candidate dates, places, totals, actions with confirmation. |
| App-only capture | Foreground app package/label, screenshot OCR/VLM, user note. | Nearby context, share intent text if user shared content into Orbit. | `Captured from AppName` plus visible content only. Do not infer hidden app state. |

## Acquisition ladder

Orbit should implement a ladder rather than one extractor. Each rung is more expensive and more sensitive than the last.

### Tier 0: device-side capture metadata

Inputs:

- timestamp;
- foreground app package/activity/label;
- capture modality;
- original artifact hash;
- OCR-detected URLs when available;
- user note/intent, if present.

Use this for immediate UI and for routing. Do not let it become the summary source when web/document evidence exists.

### Tier 1: static public URL metadata and readable text

Tools:

- Existing `fetchPublicUrl` gateway.
- OpenGraph/Twitter card/meta tags.
- oEmbed discovery/provider endpoints for supported media URLs.
- Mozilla Readability for article extraction.
- jsoup or equivalent HTML parsing/sanitization.

Why first:

- Cheap.
- Fast.
- Easy to cache by canonical URL and content hash.
- Fits the existing no-cookie/no-auth/no-referer posture.

Important constraints:

- Readability returns title, content, textContent, excerpt, byline, siteName, language, and published time, but it is article-oriented and can fail on dashboards, ecommerce, apps, or heavily dynamic sites.
- Readability output must be sanitized before display or storage as HTML.
- Static extraction must use SSRF protections: HTTPS only, public IP ranges only, redirect limits, size/time limits, MIME checks, no metadata-service or private network access.

### Tier 2: managed webpage extraction

Tools to POC:

- Firecrawl for scrape-to-markdown, dynamic-page support, extraction, crawl, screenshots, cache/ZDR claims.
- Tavily Extract for URL-to-markdown/text with basic/advanced extraction, images/favicons, failed results, credit usage, and crawl/search options.
- Exa Contents for URL content, text/highlights/summary, cached content with livecrawl freshness controls, subpages, statuses, and explicit cost fields.
- Jina Reader as a simple URL-to-reader-text utility candidate.

Use cases:

- Static extraction failed or produced too little text.
- Need better cleaned markdown for web pages.
- Need search/answer enrichment for a user-asked question, not default capture hydration.

Recommendation:

- POC Firecrawl, Tavily Extract, and Exa Contents against the same capture eval set.
- Do not make AI search answer endpoints the default source for capture summaries. They are useful for Ask Orbit and enrichment, but capture summaries should be grounded in the captured URL/artifact first.

### Tier 3: browser-rendered fallback

Tools to POC:

- Cloudflare Browser Rendering / Browser Run if Orbit's backend leans into Workers/Durable Objects.
- Browserbase if managed browser sessions, stealth, proxies, CAPTCHA handling, compliance posture, and recording/debugging become valuable.
- Self-hosted Playwright when we want maximum control and lower vendor dependency.
- Crawlee when we need robust crawling queues, retries, sessions, proxy management, and a mix of HTTP and real-browser crawlers.

Use cases:

- JS-rendered content where static extraction returns shell HTML.
- Product pages and app-like pages that need rendered DOM text.
- Public pages where a screenshot/render gives better source identity and visible evidence.

Hard boundaries:

- Browser rendering is fallback, not default.
- No default login/cookie use.
- No CAPTCHA bypass as a product feature.
- Respect robots/legal/terms constraints and stop when blocked.
- Per-domain rate limits and per-user budgets must gate use.

### Tier 4: OCR, VLM, and document/media parsers

Tools to POC:

- Android ML Kit or platform OCR for on-device visible text extraction.
- Server-side OCR/VLM through the LLM gateway for screenshot and image understanding when user opts into cloud mode.
- MarkItDown for lightweight conversion of PDF, Office, images, audio, HTML, CSV/JSON/XML, ZIPs, YouTube URLs, and EPubs to Markdown for LLM pipelines.
- Unstructured for richer document partitioning into elements such as `Title`, `NarrativeText`, `ListItem`, and tables across PDFs, images, Word, PowerPoint, email, HTML, markdown, XML, etc.

Recommendation:

- Use MarkItDown first for cheap document-to-markdown prototyping and YouTube transcript experiments.
- Use Unstructured for document-heavy cases where page/element structure, OCR strategy, and table handling matter.
- Treat Unstructured OSS as prototype-grade based on its own docs; production may require its API/UI or a self-hosted production service.
- Keep VLM extraction behind explicit cloud-mode controls and budgets because screenshots can contain highly sensitive personal data.

### Tier 5: search and external enrichment

Tools to POC:

- Exa Search/Answer for cited web answers and freshness controls.
- Tavily Search/Crawl for LLM-oriented search, ranked sources, raw content, images, and credit-tracked crawl.

Use cases:

- Ask Orbit questions that require current external information.
- Recover context around a capture when the original page is inaccessible and the user asks for help.
- Compare or enrich a capture cluster with external sources.

Not default:

- Do not use search results to silently replace the captured artifact. Search can supplement, not pretend to be the original source.

## Tool comparison

| Option | Best role | Strengths | Risks / limits | POC question |
| --- | --- | --- | --- | --- |
| Existing fetch + Readability | Tier 1 baseline | Cheapest, simple, fits current `:net` policy, good for articles. | Fails on JS-heavy, social, private, paywalled, non-article pages. Sanitization required. | What percent of real captures get useful text under strict public fetch? |
| oEmbed | Provider metadata | Standardized title/author/provider/thumbnail/embed fields, useful for YouTube/media. | Metadata only; embeds can carry XSS risk if displayed; provider coverage varies. | Can it improve source identity and title/channel accuracy for YouTube/social links? |
| Firecrawl | Managed web extraction | Page-to-markdown, extraction, crawl, dynamic content, screenshots. | Vendor cost, terms/robots posture, output variance. | Is it the best default managed extractor after static failure? |
| Tavily Extract/Crawl/Search | Managed extraction + AI search | Markdown/text extraction, advanced extraction for tables/embedded content, crawl, ranked search, credit usage. | API-key vendor, credit costs, search answers are not original evidence. | Does Extract beat Firecrawl/Exa on capture URL coverage and cost? |
| Exa Contents/Search/Answer | Search + content retrieval | Contents endpoint returns text/highlights/summary/status/cost; livecrawl/max-age controls; Answer includes citations. | Search/answer can drift away from captured source; API costs. | Best fit for Ask Orbit and freshness-sensitive research clusters? |
| Jina Reader | Simple reader endpoint | Easy URL-to-reader-text utility. | Less control/observability than owned extractor; needs validation. | Is it a cheap fallback for public pages? |
| Cloudflare Browser Rendering / Browser Run | Serverless browser fallback | Browser sessions through Workers/Durable Objects, Playwright/Puppeteer/CDP/Stagehand-style access. | Cloudflare platform coupling; browser work can get expensive. | Best browser fallback if Orbit deploys workers on Cloudflare? |
| Browserbase | Managed browser platform | Managed sessions, stealth/proxy/CAPTCHA/identity features, debugging/recordings, compliance posture. | More power than Orbit should use by default; cost; avoid CAPTCHA/login creep. | Is managed browser reliability worth vendor cost for high-value captures? |
| Self-hosted Playwright | Controlled browser fallback | Mature automation, rendered DOM, screenshots, network inspection. | Infra, scaling, anti-bot fragility, patching. | Can a small worker fleet handle Orbit's fallback volume cheaply? |
| Crawlee | Crawler framework | Queues, retries, storage, HTTP + browser crawling, proxies/sessions. | More crawler complexity than single-page hydration. | Useful if Orbit needs domain crawling or cluster expansion later? |
| MarkItDown | Lightweight document/media-to-Markdown | Broad format coverage, YouTube URL support, optional OCR/audio transcript plugins, LLM-friendly Markdown. | Security warning: I/O with process privileges; must restrict URI/file access. | Fastest way to ingest PDFs/docs/YouTube transcripts for POC? |
| Unstructured | Structured document partitioning | Rich file support, OCR strategies, semantic elements, tables, document metadata. | OSS not production-grade per docs; dependencies like Tesseract/Poppler; no OSS GPU support. | Does structure improve summary/action extraction enough to justify heavier stack? |
| YouTube Data API + oEmbed | YouTube identity/metadata | Stable video IDs, title/channel/thumbnails, search/list metadata, captions list visibility. | Captions/download/auth/quota limits; transcript availability varies. | Can Orbit cover common YouTube URL formats and avoid fake video summaries? |
| OCR/VLM via LLM gateway | Screenshot understanding | Handles app-only and screenshot-only captures, receipts, visual layouts. | Privacy/cost risk; hallucination risk; hard to cite exact visual evidence. | What screenshot categories are worth cloud VLM vs local OCR only? |

## YouTube and app recognition

YouTube needs special handling because captures arrive in many forms:

- `youtube.com/watch?v=...`
- `youtu.be/...`
- `m.youtube.com/...`
- `music.youtube.com/...`
- `youtube-nocookie.com/embed/...`
- `/shorts/...`
- `/live/...`
- `/embed/...`
- app share URLs and redirect/wrapped URLs.

Recommended handling:

1. Keep and expand canonical URL parsing in one shared source identity/canonicalization layer.
2. Resolve video ID and provider before category. Do not infer YouTube from generic `VIDEO` category alone.
3. Fetch oEmbed for quick title/channel/provider/thumbnail where possible.
4. Use YouTube Data API for structured metadata when quota/auth policy is acceptable.
5. Attempt transcript/caption extraction only through allowed APIs/libraries and record availability. Caption track listing does not guarantee Orbit may download every transcript without auth/permission.
6. If there is no transcript/audio evidence, show a metadata-only understanding: title, channel, thumbnail, visible screenshot text, and possible topic, with a limitation.

App recognition should also be provider-first:

1. Provider URL wins: `YouTube`, `Reddit`, `Amazon`, etc.
2. Foreground app clarifies origin: `YouTube via Brave`, `Amazon via Chrome`, `Recipe in Pinterest`.
3. Category is only fallback: `Video`, `Shopping`, `Article`, `Screenshot`.
4. App label from `UsageStatsManager` is helpful, but not content identity. A Chrome foreground label does not mean the content is Chrome; it means the content was viewed in Chrome.

## Summary contract

Every generated summary should carry an evidence contract. Suggested fields:

| Field | Purpose |
| --- | --- |
| `summary_text` | User-facing concise summary. |
| `based_on` | `captured_text`, `fetched_page`, `rendered_page`, `screenshot_ocr`, `screenshot_vlm`, `transcript`, `metadata_only`, or mixed list. |
| `evidence_ids` | IDs used to produce it. |
| `limitations` | Human-readable and machine-readable caveats. |
| `generated_at` | Freshness. |
| `source_fetched_at` | Web evidence freshness. |
| `model` / `prompt_version` | Reproducibility. |
| `extractor_versions` | Extraction provenance. |
| `confidence` | Calibrated confidence/coverage bucket. |
| `requires_review` | True for low evidence, sensitive actions, or extracted dates/totals. |

Copy rules:

- If summary is based on fetched public page content: `From the saved page...`
- If based on screenshot OCR: `From the visible screenshot text...`
- If based only on metadata: `The link metadata says...`
- If based on YouTube title/channel only: `This appears to be...`, not `The video explains...`
- If content could not be fetched: `Orbit could not access the page content. It saved the link and metadata.`
- If a source is stale: `Last fetched on ...`

## Storage and deletion implications

Evidence acquisition creates derived data. Deletion must cover all of it.

When a user deletes a capture, Orbit must delete or invalidate:

- original artifact pointer;
- raw fetched page/document blobs;
- cleaned markdown/text chunks;
- OCR spans;
- VLM descriptions;
- summaries;
- extracted entities/actions;
- embeddings;
- KG episodes/entities/edges derived only from that capture;
- eval dataset entries if they include real user content;
- trace content if any was accidentally retained;
- cache references tied to private/user-specific evidence.

For public URL caches, Orbit can consider content-addressed shared cache entries only if they contain public content, no user identifiers, no private URL parameters, and deletion removes user linkage. Keep this conservative until the privacy/product policy is explicit.

## Safety, privacy, legal, and security constraints

Default web fetch policy should remain strict:

- HTTPS only.
- GET/HEAD only for acquisition.
- No cookies, no referer, no logged-in session by default.
- Fixed transparent user agent.
- Redirect limit and canonical URL recording.
- Size, MIME, and timeout caps.
- Block private, loopback, link-local, multicast, and cloud metadata IP ranges before and after redirects.
- Per-domain and per-user rate limits.
- Robots/terms review before crawling or browser fallback.
- No CAPTCHA bypass as a default feature.
- No credentialed browsing without explicit future connected-account consent.

Server-side parsers need special care:

- MarkItDown warns that it performs I/O with the privileges of the current process. Orbit must use narrow conversion APIs, restrict URI schemes, block private network destinations, and avoid permissive `convert()` behavior on untrusted inputs.
- Readability does not sanitize output; sanitize before storing/displaying HTML.
- Browser sessions must not share cookies/storage across users.
- Screenshot/VLM enrichment should be opt-in cloud mode and budgeted because screenshots can contain messages, tokens, health/finance data, and other sensitive content.

## Cost and latency policy

Recommended defaults:

| Path | Default policy |
| --- | --- |
| Static metadata/readability | Automatic for public URLs. |
| Managed extraction | Automatic only after static failure or for selected domains/content types. |
| Browser rendering | Fallback for high-value captures, user-visible retry, or eval-proven domains. |
| OCR local | Automatic when available. |
| VLM screenshot analysis | Cloud-mode opt-in, budgeted, maybe deferred. |
| Document parsing | Automatic for supported files if user captured/imported the document. |
| Search enrichment | User-initiated or agent-initiated with explanation, not default hydration. |

Controls:

- Cache by canonical URL and content hash.
- Store negative results with TTLs to avoid repeated failed fetches.
- Cap browser/VLM attempts per user/day.
- Attribute costs per capability in LiteLLM/Langfuse traces.
- Prefer cheap model summaries over large reasoning models for routine capture summaries.
- Batch embeddings/KG ingestion after summary extraction.
- Run expensive enrichment asynchronously and show partial understanding first.

## Integration with the agent stack

### Supabase/Postgres

Use Supabase as the product/control plane for:

- capture records;
- continuation job status;
- evidence bundle metadata;
- source identity records;
- summaries and limitations;
- embeddings or vector pointers;
- audit metadata and cost attribution.

### LiteLLM / model gateway

All summary/entity/action extraction model calls should route through the same gateway policy as other Orbit LLM work:

- capability labels: `capture_summary`, `entity_extract`, `action_extract`, `ocr_vlm`, `transcript_summary`;
- per-capability budgets;
- model routing by sensitivity/cost;
- call IDs mapped to continuation IDs;
- no raw capture content in logs by default;
- fallback models for routine summaries.

### Langfuse / Phoenix

Langfuse should track masked traces:

- capture/evidence IDs, not raw content;
- extractor method;
- model, token count, cost, latency;
- limitation/failure class;
- eval scores;
- user feedback outcome.

Phoenix can complement this with RAG/retrieval evals over evidence chunks and cited answers.

### Graphiti / Zep / Mem0

The KG/memory layer should ingest from evidence-grounded summaries, not directly from arbitrary model impressions.

Recommended ingestion rules:

- Captures are episodes.
- Evidence bundles are cited source objects.
- Extracted entities/actions are candidate facts with provenance.
- Low-confidence screenshot/VLM observations should stay as observations, not promoted profile facts.
- User corrections and rejections override generated understanding.

### pgvector / Qdrant

Embed cleaned evidence chunks, summary text, and selected metadata. Keep the vector record linked to evidence IDs and capture IDs so citations and deletion work. Do not embed raw private blobs unless retention policy allows it.

## New continuation types

Existing URL hydration is too narrow for the new product. Proposed continuation families:

| Continuation | Purpose |
| --- | --- |
| `source_identity` | Provider/origin/category normalization. |
| `url_static_hydration` | Existing public fetch + metadata + Readability. |
| `managed_web_extraction` | Firecrawl/Tavily/Exa/Jina result. |
| `rendered_page_extraction` | Browser-rendered DOM/text/screenshot result. |
| `screenshot_ocr` | OCR text spans and detected URLs. |
| `screenshot_vlm` | Cloud visual understanding with limitations. |
| `document_parse` | PDF/Office/document markdown/elements. |
| `media_metadata` | YouTube/oEmbed/provider metadata. |
| `transcript_extract` | Captions/transcripts/audio text where available. |
| `capture_summary` | Summary over one or more evidence bundles. |
| `entity_extract` | People/org/product/place/topic/date extraction. |
| `action_extract` | Candidate calendar/to-do/list/message actions. |
| `relatedness_index` | Embedding/KG links and why-related explanations. |

The UI can still surface this as one calm `Understanding` section, but the backend should keep the provenance separate.

## POC plan

Build a fixed capture-understanding eval set before choosing vendors.

### Dataset

At minimum:

- 10 static article/blog/news URLs.
- 10 JS-heavy/product/app-like pages.
- 10 YouTube URLs covering watch, shorts, youtu.be, mobile, nocookie/embed, app share, and redirect formats.
- 10 social/media URLs or screenshots.
- 10 screenshot-only captures with visible UI/text.
- 10 receipts/tickets/events/calendar-like screenshots.
- 10 PDFs/docs/slides.
- 10 blocked/private/paywalled/auth-required cases.
- 10 app-only captures from browsers, YouTube, Instagram/TikTok/Reddit, shopping apps, maps, and messaging.
- 10 adversarial cases: misleading title, stale page, duplicate URL with tracking params, AMP, redirect, low OCR quality.

### Competitors

Run the same cases through:

1. Existing static fetch + Readability baseline.
2. Firecrawl.
3. Tavily Extract.
4. Exa Contents.
5. Cloudflare Browser Rendering or Browser Run.
6. Browserbase or self-hosted Playwright.
7. MarkItDown for docs/YouTube.
8. Unstructured for docs/PDFs/images.
9. Local OCR + one cloud VLM model.

### Metrics

Measure:

- extraction success rate;
- source identity accuracy;
- summary factuality against evidence;
- limitation honesty;
- YouTube format coverage;
- transcript availability handling;
- cost per successful capture;
- p50/p95 latency;
- raw content retained;
- deletion/invalidation completeness;
- tenant isolation;
- trace quality without content logging;
- user feedback: accepted summary, edited summary, wrong provider, wrong action.

### Hard gates

Do not proceed unless the POC proves:

- Summaries cite evidence and do not overclaim on metadata-only captures.
- Browser fallback is budgeted and rare.
- Private/auth/blocked pages fail gracefully.
- Screenshot/VLM summaries are labeled as visual/visible evidence.
- Deleting a capture deletes or invalidates derived evidence, embeddings, summaries, and KG facts.
- Cross-user cache/trace/retrieval leakage tests pass.

## Recommended first implementation sequence

1. Centralize `CaptureSourceIdentity`: provider URL, foreground app, category, display label, icon/glyph, confidence.
2. Define `EvidenceBundle` and `CaptureUnderstanding` schemas in docs/contracts before app code.
3. Keep existing URL hydration as Tier 1 baseline and add explicit evidence/limitation fields.
4. Add server-side enrichment queue and adapter interface.
5. POC Firecrawl, Tavily Extract, Exa Contents, and one browser fallback on the eval set.
6. POC MarkItDown and Unstructured for documents/YouTube transcripts.
7. Add OCR/VLM screenshot path only after summary contract and cloud privacy controls are in place.
8. Feed summaries/entities/actions into pgvector and Graphiti/Zep/Mem0 POCs with evidence IDs.
9. Add user feedback controls: wrong summary, wrong source, not relevant, summarize again, never fetch this domain.
10. Only after evals pass, expand into Ask Orbit and loop-closing actions.

## Key decisions still open

1. Should managed extraction be Firecrawl-first, Tavily-first, Exa-first, or provider-routed by domain/content type?
2. Should browser fallback run on Cloudflare, Browserbase, or self-hosted Playwright?
3. What is the retention policy for raw public page text versus cleaned chunks versus summaries?
4. Will YouTube transcript extraction rely on official APIs only, MarkItDown/library behavior, user-provided transcript, or audio/VLM fallback?
5. How much screenshot/VLM understanding is allowed in default cloud mode?
6. Should search enrichment be available automatically for failed captures, or only after user intent?
7. Does Orbit want a connected-account browser someday, and if so what consent and data boundary does it require?

## Recommendation

For the next planning cycle, add **Capture Understanding / Evidence Acquisition** as a first-class stack layer between capture storage and the agent/KG.

Default stack recommendation for POC:

- Baseline: existing public fetch + Readability + OpenGraph/oEmbed.
- Managed web extraction: compare Firecrawl, Tavily Extract, and Exa Contents.
- Browser fallback: compare Cloudflare Browser Rendering/Browser Run with either Browserbase or self-hosted Playwright.
- Documents/media: MarkItDown first, Unstructured as richer document parser.
- Screenshots: local OCR first, cloud VLM second behind controls.
- Search enrichment: Exa/Tavily for Ask Orbit and explicit enrichment, not default summaries.
- Provenance: `EvidenceBundle` IDs attached to every summary, extraction, embedding, and KG episode.

This is the layer that makes Orbit's agent credible. Without it, the agent will sound confident while knowing only app labels, URLs, thumbnails, and partial screenshots. With it, Orbit can say exactly what it saw, what it did not see, what it inferred, and what the user can do next.
