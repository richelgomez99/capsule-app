# Orbit Agent Architecture Round 2 - 2026-05-12

This is Round 2 of the deeper replanning sequence. Round 1 decided that Orbit should not build the full planner/executor first. It should first build the trustworthy substrate: source identity, evidence-backed summaries, capture detail, relatedness, feedback, Ask Orbit citations, local action drafts, and confirmed execution later.

Round 2 turns that substrate into implementable contracts. It focuses on the parts that must exist before Speckit can safely replan branches and tasks:

- source identity contract;
- evidence bundle contract;
- capture understanding contract;
- relatedness and retrieval contract;
- candidate action contract;
- audit/deletion/eval rules;
- MVP storage shape;
- app layout implications for these objects.

Related docs:

- [Orbit Agent Architecture Round 1 - 2026-05-12](orbit-agent-architecture-round-1-2026-05-12.md)
- [Orbit Agent Architecture Round 3 - 2026-05-12](orbit-agent-architecture-round-3-2026-05-12.md)
- [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md)
- [Product Roadmap Audit - 2026-05-12](product-roadmap-audit-2026-05-12.md)
- [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md)
- [Capture Source Identity Plan](capture-source-identity-plan.md)
- [specs/002 Intent Envelope data model](../specs/002-intent-envelope-and-diary/data-model.md)
- [specs/002 Continuation Engine contract](../specs/002-intent-envelope-and-diary/contracts/continuation-engine-contract.md)
- [specs/014 Gateway request/response contract](../specs/014-edge-function-llm-gateway/contracts/gateway-request-response.md)

## Round 2 conclusion

Orbit needs to split the old `ContinuationResultEntity` idea into several explicit concepts:

1. **Source identity**: where this capture appears to come from and how confident Orbit is.
2. **Evidence bundle**: what Orbit actually saw or fetched.
3. **Understanding**: what Orbit inferred from evidence.
4. **Retrieval chunk**: what can be searched/embedded.
5. **Relatedness explanation**: why two captures are connected.
6. **Candidate action**: what the user might do next.
7. **Audit event**: what Orbit did and why.

The current flat continuation result is good for v1 URL hydration, but it cannot safely support deep summaries, screenshot/VLM evidence, transcripts, documents, relatedness, deletion, or Ask Orbit citations. The fix is not to immediately rip out the existing table. The fix is to introduce a v2 contract that can live beside it, then migrate UI surfaces gradually.

## Existing baseline and gaps

### What already exists

The current app already has useful foundations:

- `IntentEnvelopeEntity`: captures content type, text/image URI, intent, state snapshot, created date, archive/delete flags.
- `StateSnapshot`: app category, activity, timezone, local time/day.
- `ContinuationEntity`: async background work with status, retries, type, input URL.
- `ContinuationResultEntity`: title/domain/canonical URL/hash/excerpt/summary/model for URL hydration.
- `NetworkGatewayContract`: strict HTTPS-only public fetch through `:net`; no cookies/referer; host blocking; size/time caps.
- `UrlHydrateWorker`: fetches public URL, summarizes readable HTML, writes through repository binder.
- `SourceIdentityResolver`: quick YouTube-aware glyph resolver.
- LLM gateway: Zod discriminated union request schemas for `embed`, `summarize`, `extract_actions`, `classify_intent`, `generate_day_header`, and `scan_sensitivity`.

### What is missing

The missing pieces are structural:

- no first-class source identity record;
- no evidence bundle record;
- no evidence-level limitations;
- no citation IDs;
- no retrieval chunks or embedding linkage;
- no relatedness explanation shape;
- no candidate action shape grounded in evidence;
- no deletion graph from capture to derived data;
- no understanding-depth policy on each job/result;
- no eval dataset format for capture understanding quality.

## Design principle: contracts first, storage second

Round 2 should not prematurely choose final tables for every layer. But it should define contracts that can be represented in Room, Supabase Postgres, JSON, and API payloads.

Contract rules:

- Use explicit IDs for anything user-visible or citable.
- Every derived object references its parent capture/envelope and evidence IDs.
- Every model-derived object records model/prompt/extractor versions.
- Every object carries enough policy metadata for deletion/export/audit.
- Use discriminated unions for variant objects: evidence kinds, action kinds, failure kinds.
- Prefer append-only derived records over mutating old summaries in place.
- Treat low-confidence and failed understanding as first-class results, not missing rows.

## Contract 1: CaptureSourceIdentity

Purpose: answer `what is this from?` without conflating provider, foreground app, and category.

```kotlin
data class CaptureSourceIdentity(
    val id: String,
    val envelopeId: String,
    val primaryProvider: SourceProvider?,
    val originApp: OriginApp?,
    val appCategory: AppCategory,
    val sourceUrl: String?,
    val canonicalUrl: String?,
    val canonicalUrlHash: String?,
    val displayLabel: String,
    val secondaryLabel: String?,
    val glyphKind: SourceGlyphKind,
    val confidence: Confidence,
    val evidence: List<SourceIdentityEvidence>,
    val limitations: List<SourceIdentityLimitation>,
    val resolvedAt: Long,
    val resolverVersion: String,
)
```

Suggested TypeScript/Zod shape:

```ts
const CaptureSourceIdentitySchema = z.object({
  schemaVersion: z.literal(1),
  id: z.string().uuid(),
  envelopeId: z.string().uuid(),
  primaryProvider: z.enum([
    'youtube', 'reddit', 'instagram', 'tiktok', 'amazon', 'google_maps',
    'nytimes', 'substack', 'github', 'generic_web', 'unknown'
  ]).nullable(),
  originApp: z.object({
    packageNameHash: z.string().nullable(),
    label: z.string().max(128).nullable(),
    category: z.string().max(64),
  }).nullable(),
  sourceUrl: z.string().url().nullable(),
  canonicalUrl: z.string().url().nullable(),
  canonicalUrlHash: z.string().nullable(),
  displayLabel: z.string().max(128),
  secondaryLabel: z.string().max(160).nullable(),
  glyphKind: z.string().max(64),
  confidence: z.enum(['high', 'medium', 'low']),
  evidence: z.array(z.object({
    kind: z.enum(['provider_url', 'foreground_app', 'app_category', 'oembed', 'metadata', 'ocr_url']),
    valueHash: z.string().nullable(),
    displayValue: z.string().max(256).nullable(),
  })).max(8),
  limitations: z.array(z.string().max(128)).max(8),
  resolvedAt: z.string().datetime(),
  resolverVersion: z.string().max(64),
})
```

Rules:

- Provider URL wins over foreground app.
- Foreground app clarifies origin: `YouTube via Brave`.
- Category never pretends to be provider unless provider/app evidence supports it.
- Store raw package name only if product/privacy policy allows it; otherwise store hash + user-facing label.
- Corrections produce a new source identity version or explicit correction event, not silent mutation.

MVP providers:

- YouTube family: `youtube.com`, `m.youtube.com`, `youtu.be`, `youtube-nocookie.com`, Shorts, live, embed, app share redirects.
- Browser apps: Chrome, Brave, Samsung Internet.
- Messaging/email categories: SMS, Gmail, generic mail.
- Generic web fallback.

## Contract 2: EvidenceBundle

Purpose: record what Orbit actually saw, fetched, parsed, or generated before summarization.

```ts
const EvidenceBundleSchema = z.discriminatedUnion('kind', [
  LocalTextEvidence,
  UrlMetadataEvidence,
  UrlReadableEvidence,
  RenderedPageEvidence,
  ScreenshotOcrEvidence,
  ScreenshotVlmEvidence,
  DocumentParseEvidence,
  MediaMetadataEvidence,
  TranscriptEvidence,
  SearchEvidence,
])
```

Shared fields:

```ts
const EvidenceBase = z.object({
  schemaVersion: z.literal(1),
  evidenceId: z.string().uuid(),
  envelopeId: z.string().uuid(),
  userId: z.string().uuid().nullable(),
  acquisitionJobId: z.string().uuid().nullable(),
  sourceIdentityId: z.string().uuid().nullable(),
  observedAt: z.string().datetime(),
  acquiredAt: z.string().datetime(),
  acquisitionMethod: z.string().max(128),
  acquisitionTier: z.enum(['basic', 'smart', 'deep', 'manual']),
  policy: z.object({
    understandingDepth: z.enum(['basic', 'smart', 'deep']),
    cloudAllowed: z.boolean(),
    visualCloudAllowed: z.boolean(),
    browserFallbackAllowed: z.boolean(),
    searchAllowed: z.boolean(),
    retention: z.enum(['metadata_only', 'clean_text', 'raw_until_ttl', 'raw_retained']),
  }),
  contentHash: z.string().nullable(),
  rawRef: z.string().nullable(),
  cleanTextRef: z.string().nullable(),
  snippets: z.array(z.object({
    snippetId: z.string().uuid(),
    text: z.string().max(1000),
    location: z.string().max(128).nullable(),
    confidence: z.number().min(0).max(1).nullable(),
  })).max(20),
  limitations: z.array(z.string().max(128)).max(16),
  confidence: z.enum(['high', 'medium', 'low']),
  extractorVersion: z.string().max(128),
})
```

Evidence variants:

| Kind | Required additional fields | Use |
| --- | --- | --- |
| `local_text` | `textLength`, `language` | User saved text or note. |
| `url_metadata` | `url`, `canonicalUrl`, `title`, `description`, `imageUrl`, `provider` | OpenGraph/oEmbed/title metadata. |
| `url_readable` | `url`, `canonicalUrl`, `title`, `siteName`, `byline`, `publishedAt`, `textLength` | Static public fetch + Readability. |
| `rendered_page` | `url`, `canonicalUrl`, `renderedTextLength`, `screenshotRef`, `browserProvider` | Browser fallback result. |
| `screenshot_ocr` | `imageRef`, `textLength`, `detectedUrls`, `ocrProvider` | Visible screenshot text. |
| `screenshot_vlm` | `imageRef`, `descriptionRef`, `modelLabel`, `sensitiveFlags` | Visual understanding. |
| `document_parse` | `documentRef`, `mimeType`, `pageCount`, `parser`, `elementCounts` | PDFs/docs/slides/files. |
| `media_metadata` | `provider`, `mediaId`, `title`, `creator`, `thumbnailUrl`, `durationSeconds` | YouTube/social metadata. |
| `transcript` | `provider`, `mediaId`, `language`, `durationSeconds`, `segmentCount` | Captions/transcripts/audio text. |
| `search_result` | `query`, `provider`, `sourceUrls`, `answerRef` | Explicit Ask/web enrichment only. |

Rules:

- A summary cannot cite a source unless an EvidenceBundle exists.
- If raw content is not retained, `rawRef = null` and snippets/clean text become the citation surface.
- Search evidence must never masquerade as original captured-source evidence.
- Browser-rendered and VLM evidence must record policy flags and limitations.

## Contract 3: CaptureUnderstanding

Purpose: what Orbit believes about a capture after reading evidence.

```ts
const CaptureUnderstandingSchema = z.object({
  schemaVersion: z.literal(1),
  understandingId: z.string().uuid(),
  envelopeId: z.string().uuid(),
  sourceIdentityId: z.string().uuid().nullable(),
  producedAt: z.string().datetime(),
  status: z.enum(['ready', 'partial', 'limited', 'failed']),
  basedOn: z.array(z.enum([
    'captured_text', 'fetched_page', 'rendered_page', 'screenshot_ocr',
    'screenshot_vlm', 'document_parse', 'metadata_only', 'transcript', 'search'
  ])).min(1),
  evidenceIds: z.array(z.string().uuid()).min(1),
  title: z.string().max(256).nullable(),
  shortSummary: z.string().max(600).nullable(),
  longSummaryRef: z.string().nullable(),
  limitations: z.array(z.string().max(160)).max(16),
  entities: z.array(z.object({
    entityId: z.string().uuid().nullable(),
    type: z.enum(['person', 'org', 'product', 'place', 'topic', 'event', 'date', 'money', 'ingredient', 'unknown']),
    label: z.string().max(160),
    evidenceIds: z.array(z.string().uuid()).min(1),
    confidence: z.number().min(0).max(1),
  })).max(50),
  candidateActionIds: z.array(z.string().uuid()).max(16),
  model: z.object({
    providerLabel: z.string().max(128),
    modelLabel: z.string().max(128),
    promptVersion: z.string().max(64),
  }).nullable(),
  quality: z.object({
    coverage: z.enum(['full', 'partial', 'metadata_only', 'visual_only']),
    confidence: z.enum(['high', 'medium', 'low']),
    requiresReview: z.boolean(),
  }),
})
```

Rules:

- `status = limited` is valid and user-visible. It is not a failure.
- `metadata_only` cannot produce a definitive content summary.
- `screenshot_vlm` summaries must say they are visual observations unless backed by text/transcript/document evidence.
- `requiresReview = true` for dates, money, health/finance/private messages, and action drafts.
- User edits create correction events and optionally a new Understanding version.

## Contract 4: RetrievalChunk

Purpose: the unit of lexical/vector search.

```ts
const RetrievalChunkSchema = z.object({
  schemaVersion: z.literal(1),
  chunkId: z.string().uuid(),
  envelopeId: z.string().uuid(),
  evidenceId: z.string().uuid().nullable(),
  understandingId: z.string().uuid().nullable(),
  sourceKind: z.enum(['artifact', 'evidence', 'summary', 'note', 'conversation', 'action', 'profile_fact']),
  textRef: z.string().nullable(),
  inlineTextPreview: z.string().max(500),
  tokenEstimate: z.number().int().nonnegative(),
  embeddingRef: z.string().nullable(),
  embeddingModel: z.string().max(128).nullable(),
  metadata: z.object({
    userId: z.string().uuid().nullable(),
    dayLocal: z.string().nullable(),
    provider: z.string().nullable(),
    appCategory: z.string().nullable(),
    intent: z.string().nullable(),
    resolutionState: z.string().nullable(),
    sensitivity: z.string().nullable(),
  }),
  createdAt: z.string().datetime(),
  invalidatedAt: z.string().datetime().nullable(),
})
```

Supabase/pgvector guidance from Context7:

- Store embeddings in Postgres with the `vector` extension and enable RLS.
- Use HNSW indexes for production vector search; pgvector supports L2, inner product, cosine, L1, halfvec, sparsevec, and bit opclasses.
- Combine exact metadata filters with vector nearest-neighbor search, e.g. user/date/source filters plus `ORDER BY embedding <=> query`.
- For filtered ANN queries with low matching row counts, tune `hnsw.ef_search` or use iterative scan where supported.
- Keep pgvector as default until evals prove Qdrant or another vector DB is needed.

Suggested MVP table shape in Supabase:

```sql
create table retrieval_chunks (
  id uuid primary key,
  user_id uuid not null,
  envelope_id uuid not null,
  evidence_id uuid,
  understanding_id uuid,
  source_kind text not null,
  text_preview text not null,
  text_ref text,
  embedding vector(1536),
  embedding_model text,
  provider text,
  app_category text,
  intent text,
  day_local date,
  resolution_state text,
  sensitivity text,
  created_at timestamptz not null default now(),
  invalidated_at timestamptz
);

alter table retrieval_chunks enable row level security;
create index retrieval_chunks_user_idx on retrieval_chunks(user_id);
create index retrieval_chunks_day_idx on retrieval_chunks(user_id, day_local);
create index retrieval_chunks_provider_idx on retrieval_chunks(user_id, provider);
create index retrieval_chunks_embedding_hnsw_idx
  on retrieval_chunks using hnsw (embedding vector_cosine_ops)
  where invalidated_at is null;
```

## Contract 5: RelatedCaptureExplanation

Purpose: make relatedness trustworthy and debuggable.

```ts
const RelatedCaptureExplanationSchema = z.object({
  schemaVersion: z.literal(1),
  relationId: z.string().uuid(),
  sourceEnvelopeId: z.string().uuid(),
  targetEnvelopeId: z.string().uuid(),
  producedAt: z.string().datetime(),
  relationKind: z.enum(['same_source', 'same_topic', 'same_entity', 'same_session', 'same_project', 'contrasts_with', 'follow_up', 'duplicate_or_near_duplicate']),
  score: z.number().min(0).max(1),
  reasons: z.array(z.object({
    kind: z.enum(['entity_overlap', 'embedding_similarity', 'time_window', 'same_domain', 'same_provider', 'user_note', 'ask_context', 'feedback_pattern', 'resolution_state']),
    label: z.string().max(180),
    evidenceIds: z.array(z.string().uuid()).max(8),
    weight: z.number().min(0).max(1),
  })).min(1).max(6),
  displayText: z.string().max(240),
  confidence: z.enum(['high', 'medium', 'low']),
  feedback: z.enum(['useful', 'not_related', 'wrong_topic', 'not_now', 'too_obvious', 'none']).default('none'),
})
```

Rules:

- Never show more than 3 related captures in detail for MVP.
- Do not show relatedness without a display reason.
- High-confidence proactive cards need at least two reason families, not just embedding similarity.
- User feedback becomes retrieval training data and memory signal.

## Contract 6: CandidateAction

Purpose: represent a possible next step before execution.

```ts
const CandidateActionSchema = z.discriminatedUnion('kind', [
  z.object({
    kind: z.literal('reading_list'),
    actionId: z.string().uuid(),
    title: z.string().max(160),
    itemEnvelopeIds: z.array(z.string().uuid()).min(1),
  }),
  z.object({
    kind: z.literal('grocery_list'),
    actionId: z.string().uuid(),
    title: z.string().max(160),
    items: z.array(z.object({ name: z.string().max(120), quantity: z.string().max(80).nullable() })).min(1),
  }),
  z.object({
    kind: z.literal('calendar_event_draft'),
    actionId: z.string().uuid(),
    title: z.string().max(160),
    startsAt: z.string().datetime().nullable(),
    endsAt: z.string().datetime().nullable(),
    location: z.string().max(240).nullable(),
  }),
  z.object({
    kind: z.literal('todo_draft'),
    actionId: z.string().uuid(),
    title: z.string().max(200),
    dueAt: z.string().datetime().nullable(),
  }),
  z.object({
    kind: z.literal('message_draft'),
    actionId: z.string().uuid(),
    recipientLabel: z.string().max(160).nullable(),
    body: z.string().max(4000),
  }),
])
```

Shared action metadata should wrap each variant:

```ts
const CandidateActionEnvelopeSchema = z.object({
  schemaVersion: z.literal(1),
  action: CandidateActionSchema,
  envelopeIds: z.array(z.string().uuid()).min(1),
  evidenceIds: z.array(z.string().uuid()).min(1),
  understandingIds: z.array(z.string().uuid()).max(8),
  status: z.enum(['suggested', 'draft_saved', 'dismissed', 'confirmed', 'executed', 'failed']),
  confidence: z.enum(['high', 'medium', 'low']),
  requiresConfirmation: z.literal(true),
  limitations: z.array(z.string().max(160)).max(12),
  producedAt: z.string().datetime(),
  modelLabel: z.string().max(128).nullable(),
  promptVersion: z.string().max(64).nullable(),
})
```

Rules:

- MVP action drafts do not execute external writes.
- Dates, money, recipients, locations, and message bodies always require review.
- Dismissals and edits become feedback episodes.
- External execution waits for future planner/executor/AppFunctions branch.

## Contract 7: AuditEvent v2

Purpose: make user trust and debugging possible.

Existing audit already records network fetch, inference, continuation, export, and permission changes. Round 2 needs a richer but still readable audit taxonomy.

Suggested additional actions:

| Action | Description example |
| --- | --- |
| `SOURCE_IDENTITY_RESOLVED` | `Identified source as YouTube via Brave.` |
| `EVIDENCE_ACQUIRED` | `Fetched public page text from example.com.` |
| `EVIDENCE_LIMITED` | `Could not access page content because sign-in was required.` |
| `UNDERSTANDING_CREATED` | `Summarized saved page from example.com.` |
| `RETRIEVAL_INDEXED` | `Indexed capture summary for search.` |
| `RELATEDNESS_SUGGESTED` | `Suggested 2 related captures.` |
| `USER_FEEDBACK_RECORDED` | `Marked related capture as not related.` |
| `ACTION_DRAFTED` | `Drafted a reading list from 4 captures.` |
| `MEMORY_PROMOTION_CANDIDATE` | `Asked whether to remember a recurring interest.` |
| `DERIVED_DATA_DELETED` | `Deleted summaries, evidence, embeddings, and memory linked to a capture.` |

Audit principles:

- User-visible description first.
- `extraJson` can carry IDs and failure codes but not raw prompt/content.
- Every network/model/browser/VLM event gets an audit entry.
- Failed/limited enrichments are audit events, not hidden absences.

## Deletion and invalidation graph

Every derived object needs a parent link so deletion is mechanical.

When deleting an envelope:

```text
IntentEnvelope
  -> ContinuationEntity
  -> ContinuationResultEntity
  -> CaptureSourceIdentity
  -> EvidenceBundle
  -> CaptureUnderstanding
  -> RetrievalChunk + embedding
  -> RelatedCaptureExplanation edges involving envelope
  -> CandidateAction drafts based only on envelope
  -> KG episodes/entities/edges derived only from envelope
  -> Profile facts/patterns derived only from envelope
  -> Raw/clean text blobs if user-specific
  -> Eval rows containing real content
```

Deletion rules:

- If a derived object has multiple source envelopes, remove the deleted envelope reference and re-evaluate confidence.
- If confidence drops below display threshold, invalidate the object.
- Public shared cache may keep public content only if user linkage is removed and URL had no private/query-sensitive parameters.
- Audit can keep deletion event without raw content.
- Vector rows should be soft-invalidated first (`invalidated_at`) and hard-deleted by retention sweep.

## MVP storage shape

### Local Room

Near-term app code can avoid a giant migration by adding sidecar tables rather than overloading `ContinuationResultEntity`:

1. `capture_source_identity`
2. `capture_understanding`
3. `capture_relatedness`
4. `candidate_action`
5. `retrieval_chunk_local` if local search needs it

Keep `ContinuationResultEntity` as the compatibility layer for current URL hydration UI. New UI should gradually prefer `CaptureUnderstanding` when present.

### Cloud Supabase

Recommended MVP cloud tables:

1. `cloud_captures` or sync mirror pointer for envelope metadata.
2. `evidence_bundles` for evidence metadata and refs.
3. `capture_understandings` for summaries/entities/limitations.
4. `retrieval_chunks` for searchable text + embeddings.
5. `candidate_actions` for draft actions.
6. `capture_relations` for relatedness explanations.
7. `agent_events` for audit/memory episodes.

Use Supabase Storage for raw/clean blobs if retained. Store object paths in Postgres rows. Keep RLS on metadata tables and storage policies aligned. Edge Functions should act as trusted server paths for enrichment and retrieval where service-role access is required.

## Retrieval pipeline for MVP

Retrieval should be layered, not pure vector search.

### Query inputs

- user query;
- current capture ID if scoped;
- date range;
- source/provider filters;
- intent/resolution filters;
- understanding depth and cloud settings;
- user feedback exclusions.

### Candidate generation

1. Exact filters: user, active/not-deleted, date, source, intent.
2. Lexical search: title, summary, notes, OCR, domain.
3. Vector search: chunks over evidence and summaries.
4. Graph/KG search later: entity/pattern/action relationships.
5. Deduplicate by envelope.

### Reranking

Score should combine:

- vector similarity;
- lexical match;
- source/entity overlap;
- recency;
- user note/intent boost;
- accepted/rejected feedback;
- resolution state;
- capture quality/evidence confidence.

### Output

- top capture IDs;
- cited chunks/evidence IDs;
- display reasons;
- limitations.

MVP can implement this in SQL + app logic before adopting a specialized reranker.

## Evaluation dataset

Round 2 defines the dataset format so dogfood can start collecting signal.

### Dataset row

```ts
const CaptureUnderstandingEvalCaseSchema = z.object({
  caseId: z.string().uuid(),
  captureFixtureRef: z.string(),
  captureType: z.enum(['url', 'screenshot_url', 'screenshot_only', 'youtube', 'social', 'document', 'receipt_event', 'app_only']),
  sourceAppCategory: z.string(),
  providerExpected: z.string().nullable(),
  urlsExpected: z.array(z.string()).max(10),
  evidenceExpected: z.array(z.enum(['metadata', 'readable_text', 'transcript', 'ocr', 'vlm', 'document_parse', 'none_available'])),
  expectedLimitations: z.array(z.string()).max(10),
  summaryRubric: z.object({
    mustMention: z.array(z.string()).max(10),
    mustNotClaim: z.array(z.string()).max(10),
    citationRequired: z.boolean(),
  }),
  relatedExpectedEnvelopeIds: z.array(z.string().uuid()).max(10),
  actionExpected: z.array(z.string()).max(10),
  sensitivityFlags: z.array(z.string()).max(10),
})
```

### First eval set

Build 100 cases:

- 15 static article/blog/news URLs.
- 10 JS-heavy/product pages.
- 15 YouTube cases across URL formats and app screenshots.
- 10 social/media cases.
- 10 screenshot-only UI/text cases.
- 10 receipts/tickets/events.
- 10 PDFs/docs/slides.
- 10 blocked/private/auth/paywall cases.
- 10 adversarial cases: misleading title, stale page, redirect, AMP, tracking params, low OCR quality.

### Metrics

- source identity accuracy;
- evidence acquisition success;
- summary groundedness;
- limitation honesty;
- no-overclaim rate;
- relatedness precision;
- action extraction correctness;
- deletion/invalidation completeness;
- cost/latency per case;
- user feedback acceptance.

Ship gates:

- No metadata-only case can produce a full-content summary.
- YouTube summaries require transcript/audio/documented evidence or must be metadata-only.
- Sensitive screenshots require local-only or explicit cloud visual consent.
- Relatedness explanations must cite at least one concrete reason.

## Top provider/app coverage for MVP

MVP source identity should cover the common capture distribution before long-tail providers.

### Providers/domains

1. YouTube.
2. Reddit.
3. Amazon/product pages.
4. Google Maps.
5. GitHub.
6. Substack/newsletters.
7. Major news/article sites through generic Readability.
8. TikTok/Instagram as limited/metadata-first.
9. Recipes/food sites through generic extraction.
10. Generic web fallback.

### Apps/categories

1. Chrome.
2. Brave.
3. Samsung Internet.
4. YouTube app.
5. Messages/SMS.
6. Gmail/email.
7. Reddit.
8. Instagram/TikTok.
9. Maps.
10. Unknown/private app.

The goal is not brand polish. The goal is source truth: `YouTube via Brave`, `Recipe from Chrome`, `Message screenshot`, `Private app screenshot`.

## UI implications

### Diary card

Minimum fields:

- source glyph/display label;
- title or best label;
- short summary or limitation;
- note/intent marker;
- stale/limited indicator only when important.

### Capture detail

Required sections:

1. Source header from `CaptureSourceIdentity`.
2. Original artifact.
3. Understanding from `CaptureUnderstanding`.
4. Evidence label: `From saved page`, `From screenshot text`, `Metadata only`, `Transcript available`.
5. Related captures from `RelatedCaptureExplanation`.
6. Possible next steps from `CandidateAction`.
7. Feedback/correction controls.
8. Audit drawer.

### Ask Orbit

Required response structure:

- answer;
- citations to captures/evidence;
- limitations;
- follow-up chips;
- `Search web for more` only when policy allows or user triggers.

## Round 2 priority recommendation

Before new UI branches multiply, define these contracts in the next Speckit pass:

1. `CaptureSourceIdentity` contract.
2. `EvidenceBundle` contract.
3. `CaptureUnderstanding` contract.
4. `RetrievalChunk` contract.
5. `RelatedCaptureExplanation` contract.
6. `CandidateAction` contract.
7. `AuditEvent v2` contract.
8. Eval dataset contract.

Implementation order after contracts:

1. Centralize source identity resolver and tests.
2. Extend URL hydration to produce `CaptureUnderstanding` from current fetch results.
3. Build capture detail around the new understanding shape.
4. Add local feedback/correction events.
5. Add retrieval chunks and pgvector/Supabase retrieval POC.
6. Add related captures with reasons.
7. Add action drafts.

## What Round 3 should do

Round 3 should focus on memory and retrieval beyond single captures:

- how Graphiti/Zep/Mem0/Supabase-only compare on the same contracts;
- what becomes a KG episode versus a profile fact versus a pattern;
- how user feedback changes ranking and memory;
- what the memory inspector must show;
- how deletion/export works across graph memory.

Round 2's contracts are the bridge. Without them, memory research will be abstract. With them, every memory candidate can be judged by whether it preserves evidence, citations, deletion, and user correction.
