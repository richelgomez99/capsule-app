# Product Roadmap Audit - 2026-05-12

This audit is a turning-point map for Orbit after the Demo Day visual/duplicate/settings work and the cloud pivot. It does not change code. It consolidates the current product truth, identifies stale promises, validates specs 015-017, and proposes an implementation order for the rest of the app.

Related stack research: [Agent Stack Research Pass - 2026-05-12](agent-stack-research-2026-05-12.md), [Agent Stack POC Research - 2026-05-12](agent-stack-poc-research-2026-05-12.md), [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md), [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md), [Orbit Agent Architecture Round 1 - 2026-05-12](orbit-agent-architecture-round-1-2026-05-12.md), [Orbit Agent Architecture Round 2 - 2026-05-12](orbit-agent-architecture-round-2-2026-05-12.md), [Orbit Agent Architecture Round 3 - 2026-05-12](orbit-agent-architecture-round-3-2026-05-12.md), [Orbit Agent Architecture Round 4 - 2026-05-12](orbit-agent-architecture-round-4-2026-05-12.md), [Orbit Agent Architecture Round 5 - 2026-05-12](orbit-agent-architecture-round-5-2026-05-12.md), [Orbit Agent Architecture Round 6 - 2026-05-12](orbit-agent-architecture-round-6-2026-05-12.md), and [Android Architecture Verification - 2026-05-13](android-architecture-verification-2026-05-13.md).

## Executive conclusion

Orbit is no longer just a local screenshot/clipboard catcher. The current product direction is:

> Orbit is a mobile attention memory system: capture from anywhere, preserve why it mattered, return it through a quiet Diary, prevent duplicate uncertainty, and gradually become an agent that helps close loops with user-confirmed actions.

The strongest coherent product spine is:

1. Capture: bubble, screenshots, future manual compose/share.
2. IntentEnvelope: every capture carries intent, context, provenance, and later resolution state.
3. Diary: the first return surface, not a generic list.
4. Feedback: Already Saved, undo, notes, reclassify, open existing capture.
5. Continuations: hydration, summaries, source identity, clusters.
6. Ask/Search: retrieve and answer across captures with citations.
7. Resolution/Actions: turn saved items into completed loops without guilt or notification pressure.
8. Knowledge graph and Agent: long-term memory plus user-invoked planner/executor.
9. Cloud: a structurally bounded capability layer, not a new product identity.

The old copy that says or implies "local-only, BYOC-first, no Orbit servers" is now stale. The current constitutional truth is local-first source of truth, cloud-mode AI inference allowed and possibly default, Orbit Cloud as the default opt-in managed tier, BYOC as a later power-user tier, and a permanent local/Nano kill switch.

## Deeper product thesis

The earlier version of this audit was too structural. The product is not merely capture, Diary, search, then agent. The intended product is an **agentic memory system for closing loops**.

The agent is not valuable because it chats. It is valuable because it gradually understands the user's saved attention, asks clarifying questions when the user's context is missing, retrieves the right prior captures at the right moment, helps transform captures into concrete next actions, and remembers what the user accepted, rejected, corrected, or ignored.

The product should be planned around these user promises:

1. When I save something, Orbit helps me understand it later.
2. When I save many related things, Orbit notices the pattern without overclaiming.
3. When I return to a capture, Orbit can show why it mattered, what else it relates to, and what I might do next.
4. When I talk to Orbit, it can use my captures and prior conversations as memory, but it cites what it knows and admits what it does not.
5. When Orbit sees a gap in its understanding, it asks a lightweight question instead of inventing a profile.
6. When something is actionable, Orbit proposes a plan and waits for confirmation before acting.
7. When I reject a suggestion, Orbit learns from that rejection.
8. When a capture is not relevant now, Orbit can keep it in orbit without nagging me.

That means the roadmap has to prioritize the agent's **memory quality, uncertainty handling, relevance ranking, and feedback loops**, not just individual surfaces.

## Core user stories to build toward

### Story A: Capture becomes an understood object

As a user, when I capture a URL, screenshot, note, or shared text, Orbit should attach useful AI-generated understanding to it: title, source/provider, concise summary, extracted entities, possible actions, and user-authored notes.

Important constraints:

- The AI summary is attached to the capture as a continuation result, not a replacement for the original artifact.
- The detail screen should show original content, source context, AI summary, notes, related captures, and possible actions.
- The summary must be honest about missing context: "This looks like..." or "From the saved page...", not "You spent your day...".
- Generated summaries need citations or at least a clear provenance link to the captured artifact.

### Story B: Capture detail becomes an insight surface

As a user, when I tap a capture, I want to understand the context and relevance of that specific capture.

This should eventually include:

- what Orbit captured and where it came from;
- what Orbit inferred from the captured content;
- nearby captures only when actually useful;
- related clusters and patterns;
- user notes over time;
- actions suggested from this capture;
- a way to ask "why is this relevant?" or "what else did I save like this?".

Do not make this a dumb before/after timeline by default. Before/after captures may help sometimes, but the deeper product is an agentic context layer: "this capture appears connected to these three other saves because they share topic, entity, time window, and your explicit interest in X." It must explain its reasoning.

### Story C: The agent helps close loops

As a user, I save things because they imply future motion: read this, buy this, remember this, send this to someone, add this date, make this list, draft this message, follow up.

Orbit should help close those loops through:

- calendar event extraction from flights, events, receipts, RSVPs;
- grocery lists from recipes or food captures;
- to-dos from screenshots, notes, and conversations;
- structured reading lists from research clusters;
- message drafts based on captures and user-provided context;
- reminders or snoozes when the user explicitly asks;
- manual mark-done or dismiss-with-reason when Orbit cannot observe the real-world completion.

The agent must never silently write to external apps. It can draft, preview, and propose. Execution requires confirmation.

### Story D: The agent is conversational memory, not a chatbot island

As a user, I want to talk to my agent about a capture, a cluster, or an idea, and have it use my saved corpus as shared context.

Examples:

- "What was I collecting about coding agents?"
- "Turn these startup articles into questions I should answer for my company."
- "I'm thinking about this capture. What else have I saved that challenges it?"
- "Draft a message to Maya using the notes I saved about this dinner plan."
- "What follow-ups did I leave open this week?"

Conversation should itself become memory when the user consents or when it produces explicit artifacts: notes, profile facts, plans, rejections, corrections, derived envelopes, or action outcomes.

### Story E: The agent asks curious questions to improve relevance

As a user, I do not want Orbit to assume who I am from sparse saves. I want it to ask small, useful questions when that would make future surfacing better.

Examples:

- "You have saved several captures about coding agents. Is this for work, research, a product you are building, or general interest?"
- "A lot of startup material is showing up. Are you pre-idea, building, fundraising, hiring, or researching?"
- "Should I treat grocery-related captures as near-term shopping lists or long-term recipe ideas?"
- "You saved this for later. Is later this week, someday, or only if it becomes relevant again?"

These questions should be sparse, optional, and high-leverage. They should not feel like onboarding homework. They should appear when Orbit has enough evidence that one answer would improve ranking or action suggestions.

The answers become explicit profile facts with provenance. Rejections and corrections matter as much as positive answers.

### Story F: Patterns and clusters become trustworthy

As a user, I want Orbit to notice clusters and recurring patterns, but only when there is enough signal.

The product should distinguish:

- **Cluster**: a bounded set of captures related by topic/time/source, such as a research session.
- **Pattern**: a recurring behavior or preference across time, such as saving founder advice, collecting recipes, or repeatedly drafting standup messages.
- **Profile fact**: something about the user that is explicitly declared, inferred with evidence, or corrected.
- **Open loop**: a capture or cluster whose implied intent has not been resolved, dismissed, or snoozed.

The agent should avoid cheap keyword matching. "AI" appearing in three captures is not enough. Trustworthy surfacing needs multiple signals: embeddings, entities, temporal proximity, source diversity, intent, notes, conversation context, prior accepted/rejected suggestions, and resolution state.

### Story G: Saves can be not-now without being forgotten

As a user, some captures are not useful now. They may become useful when my stage, project, location, or question changes.

Orbit needs a notion of relevance horizon:

- now;
- this week;
- when I am working on a project;
- when I am planning a meal/trip/event;
- someday/reference;
- not relevant anymore.

This is where resolution semantics, snooze, decay, and profile questions connect. A save can stay in orbit without nagging the user or being treated as a task.

## Relevance and humility contract

This is the product's anti-bullshit contract. If Orbit violates this, the agent will feel like generic AI pasted onto a capture app.

1. **Cite or do not claim.** Any answer, summary, or insight based on captures must link back to the source envelopes or explicitly say it is a hypothesis.
2. **Sparse data means sparse claims.** Orbit cannot say "you spent the day doing X" from a few captures. It can say "your saved captures today include X" or "from what you saved...".
3. **Ask before assuming identity.** If Orbit sees many coding-agent captures, it should ask whether the user is a programmer, product person, founder, researcher, or just curious. It should not silently lock in a persona.
4. **Prefer precision over recall for proactive surfacing.** Missing a suggestion is better than surfacing irrelevant nonsense. Proactive cards need a high bar.
5. **Use negative feedback.** Dismissal, "not related," "not now," "wrong topic," and manual corrections are training signal for the user's personal graph.
6. **Separate similar text from relevant meaning.** Word overlap is only a weak signal. Ranking must combine semantic similarity, entities, intent, recency, source, user notes, profile facts, and prior behavior.
7. **Expose why.** Related capture cards and agent suggestions should be able to answer "why this?" in plain language.
8. **Let the user edit memory.** Profile facts, patterns, merged entities, and wrong relationships must be inspectable and correctable.
9. **Do not over-observe.** Orbit knows captures, notes, conversations, accepted/rejected suggestions, and explicit settings. It does not know everything the user did on the phone.
10. **Make uncertainty a feature.** The agent should have language and UI for "I am not sure," "this might be related," "want me to remember that?", and "should I treat this as current?".

## Agent memory model

The current specs mention KG/profile/patterns, but the planning needs a clearer memory ladder.

1. **Artifacts**: captures, screenshots, manual notes, shared text, URLs, summaries, OCR, transcripts.
2. **Annotations**: user notes, intent changes, source identity, resolution state, dismiss reasons.
3. **Episodes**: capture created, summary generated, cluster surfaced, suggestion accepted/rejected, action confirmed, conversation produced a decision.
4. **Entities**: people, companies, projects, products, places, topics, events, ingredients, tasks.
5. **Relationships**: mentions, related-to, contradicts, part-of, works-on, for-someone, user-prefers, user-rejected.
6. **Profile facts**: explicit or inferred facts about the user, always provenance-backed and editable.
7. **Patterns**: recurring clusters or behavior over time, such as "often saves agent tooling essays" or "uses Orbit to draft follow-up messages."
8. **Plans**: proposed steps with status, confirmations, tool calls, outcomes, undo.
9. **Conversations**: agent chats that may produce artifacts, explicit memories, corrections, and plans.

This memory model implies that conversations are not just UI. They are an input stream to the same provenance and KG system as captures.

## Capability map

### Capture and enrichment

- Bubble capture.
- Screenshot capture.
- Manual compose and backfill.
- Share sheet.
- Notes attached during capture and later from detail.
- URL hydration.
- AI summaries attached to captures.
- Entity extraction.
- Action extraction.
- Source identity: provider first, origin preserved.

### Diary and detail

- Daily Diary.
- Capture cards.
- Capture detail surface.
- Notes stack.
- AI summary.
- Related captures and clusters.
- Actions and open loops.
- Resolution history.
- "Why related?" explanation.

### Search and retrieval

- Text search.
- Filters by intent, source, date, note, URL, resolution, actionability.
- Semantic search.
- Related capture retrieval.
- Cluster retrieval.
- Ask Orbit with citations.
- Conversation follow-up using previous turns.

### Pattern and profile learning

- Cluster detection.
- Pattern promotion after repeated behavior.
- Curious questions when a profile gap affects relevance.
- Profile facts with provenance.
- User correction and deletion.
- Sensitivity tags and local-only boundaries.

### Agent actions

- Calendar events.
- To-dos.
- Grocery lists.
- Reading lists.
- Draft messages.
- Share/send handoff.
- Reminders/snoozes.
- Weekly review.
- Multi-step plans with confirmation per step.

### Trust controls

- Local/cloud kill switch.
- Per-capability cloud controls.
- Audit log.
- Prompt/content logging guarantees.
- Memory inspector.
- Pattern/profile editor.
- Relatedness feedback: useful, not related, not now, wrong topic.

## Roadmap implication from this deeper thesis

The next work should not jump straight from Diary search to full agent. The correct ladder is:

1. **Make each capture richer**: summary, source identity, notes, detail context.
2. **Make the corpus retrievable**: text search, filters, semantic retrieval, citations.
3. **Make relevance explainable**: related captures with reasons, not opaque suggestions.
4. **Add resolution state**: saved, acknowledged, in motion, resolved, abandoned, snoozed.
5. **Add narrow action extraction**: calendar/to-do/list/message drafts as confirmed proposals.
6. **Add conversation over a capture**: Ask Orbit anchored to one capture or cluster, with cited retrieval.
7. **Add profile questions**: sparse, high-leverage, provenance-backed questions that improve relevance.
8. **Add pattern promotion**: repeated accepted clusters/actions become editable patterns.
9. **Add full agent planning**: only after memory, retrieval, actions, feedback, and uncertainty are in place.

This order makes the agent feel earned. If full chat arrives before summaries, citations, relatedness, corrections, and actions are solid, it will feel like generic AI with a memory-shaped prompt.

## Branch validation: 015, 016, 017

| Spec | Branch/worktree checked | Status | Merge readiness | Caveat |
| --- | --- | --- | --- | --- |
| 015 Visual Refit | `/Users/richelgomez/dev/capsule-app-015-phase1-split`, branch `015-phase1-cluster-surface`, head `a031b32` | Product work for Demo Day visual refit is staged and validated. Settings refit, bubble/logo, launcher cleanup, capture/detail polish, and QA follow-ups are in the right slice. | Functionally merge-ready after review. Standard Android gate previously passed. | `tasks.md` still has older open verification rows (`T015-104`, `T015-206`, `T015-305`, `T015-409`). Treat as task hygiene/test-infra history, not proof the current Phase 5 product work is incomplete. Source default for `useNewVisualLanguage` remains `false`. |
| 016 Intent Set Migration | `/Users/richelgomez/dev/capsule-app-spec-016`, branch `016-intent-set-migration`, head `96ac77d` | Complete. All tasks checked. Preserves `WANT_IT` and `INTERESTING`, adds `READ_LATER`, aligns cloud classifier labels, defers ContactRef. | Merge-ready. | None observed. |
| 017 Capture Feedback Actions | `/Users/richelgomez/dev/capsule-app-spec-017`, branch `017-capture-feedback-actions`, head `ceca6e6` with staged duplicate fallback work on top | Complete for current product need. Typed `AlreadySaved`, exact text/url duplicate logic, legacy fallback/backfill behavior, and tests are staged. | Functionally merge-ready after review. Standard Android gate previously passed. | Original spec said historical rows without keys were not backfilled; implementation has now intentionally improved legacy behavior. Keep the spec/tasks aligned with that decision before final merge if desired. |
| Stacked QA | `/Users/richelgomez/dev/capsule-app`, branch `qa/015-017-stacked` | Clean. | Use only as integration reference. | Do not accidentally continue new feature work here if the branch split is the intended landing shape. |

Bottom line: 015/016/017 are done enough to proceed with roadmap planning. They are not all committed/merged in the primary repo. The next operational step is branch review/merge hygiene, not more feature coding inside the stacked branch.

## Source-of-truth hierarchy

When docs conflict, use this order:

1. `.specify/memory/constitution.md` v3.2.0 and later specs 013/014 for cloud/LLM/network boundaries.
2. Specs 015/016/017 for the current Demo Day UI, intent, duplicate feedback, source identity, and settings decisions.
3. Spec 002 for the v1 envelope/diary/cluster foundation, including the 2026-04-26 cluster amendment.
4. Specs 011/012 for manual compose, notes, and resolution lifecycle.
5. Specs 003/004/006/007/008/009 for post-v1 capability direction.
6. README and older PRD copy only as historical narrative. They need refresh where they say BYOC-first, no Orbit servers, or cloud as v2-only.

## Spec recency and weight

| Spec/doc | Last relevant recency | Current weight | Notes |
| --- | ---: | --- | --- |
| 001 Core Capture Overlay | 2026-04-17 | Foundation | Implemented base overlay. Superseded by 002 for save/diary model, but service/tap/drag primitives remain alive. |
| 002 Intent Envelope and Diary | 2026-04-29 | High | v1 core. Includes cluster-suggestion amendment: research-session clusters, Summarize, citations, prompt injection guard, lifecycle, audit. |
| 003 Orbit Actions | 2026-04-29 | Roadmap high | v1.1 user-confirmed external actions and AppFunctions for the agent. Needs full implementation spec later. |
| 004 Ask Orbit | 2026-04-20 | Roadmap medium, needs refresh | RAG/search concept is right, but defaults still read Nano/BYOK-local-era. Update against 013/014 cloud routing and Orbit Cloud retrieval. |
| 005 Cloud Boost/BYOK LLM | 2026-04-20 | Roadmap high, stale in places | Important for BYOK and per-capability toggles. Conflicts with 013/014 where cloud-mode default and Orbit-managed proxy are already underway. Must be rewritten before alpha promises. |
| 006 Orbit Cloud Storage | 2026-04-20 | Roadmap high | Establishes Orbit Cloud as default opt-in Tier 1 and BYOC migration path. Still broader than current Supabase implementation. |
| 007 Knowledge Graph | 2026-04-20 | Roadmap high | Turns Diary into relational memory. Orbit Cloud Tier 1 default, local projection, KG/profile subgraph. |
| 008 Orbit Agent | 2026-04-29 | Roadmap high | v1.2 full agent. Never autonomous. User-invoked planner/executor with AppFunctions, consent filter, KG memory. Contains v1 cluster precursor requirements. |
| 009 BYOC Sovereign Storage | 2026-04-20 | Roadmap medium | Power-user Tier 2, now after Orbit Cloud. Not the default product path. |
| 010 Visual Polish Pass | 2026-05-11 | High for presentation history | Quiet Almanac foundation. Some visual details superseded by 015 product decisions and current physical QA. |
| 011 Manual Compose | 2026-05-12 | High next-product candidate | Jot a Note, backfill, share sheet, and append-only note stack. Important because a memory app without deliberate compose feels incomplete. |
| 012 Resolution Semantics | 2026-04-29 | High next-product candidate | Defines capture lifecycle, derived envelopes, search/filter implications, and anti-guilt posture. Some v1 local-only sync lines conflict with later cloud posture and need amendment. |
| 013 Cloud LLM Routing | 2026-04-29 | Very high | Cloud-mode AI default, local kill switch, LLM router, Supabase backbone, RLS gate, cluster local carve-out. Supersedes older cloud narratives. |
| 014 Edge Function LLM Gateway | 2026-04-29 | Very high | Real Vercel/Supabase authenticated gateway, no prompt logging, audit metadata only, no hard quota cutoff in Day 2. |
| 015 Visual Refit | 2026-05-12 | Very high current | Current visual/brand/settings source for Demo Day branch work. |
| 016 Intent Set Migration | 2026-05-11 | Very high current | Current intent set truth. |
| 017 Capture Feedback Actions | 2026-05-12 | Very high current | Current duplicate/Already Saved/feedback contract. |
| README | 2026-04-29 | Stale public narrative | Needs refresh before serious sharing. Says BYOC/no Orbit servers/local-only style promises that no longer match 013/014/constitution. |
| TODOS.md | 2026-04-29 | Active backlog | Still useful, especially DateTimeParser T9 and cluster stretch tasks. Needs reprioritization after 015-017. |
| docs/orbital-framing-deferred.md | 2026-04-29 | Brand strategy | Keep as a decision record. Current app uses pragmatic UI language with Orbit metaphor in brand moments. |
| docs/capture-source-identity-plan.md | 2026-05-11 | Current follow-up | Provider-first source identity is correct and should become shared resolver work. |

## Current product truth

### What Orbit is

Orbit is not a folder, not a screenshot manager, and not a generic notes app. It is a system for preserving attention with enough context and intent that the saved thing can become useful later.

The unit is the `IntentEnvelope`: captured artifact plus why it was saved, when/where-in-device-context it happened, what source/provider it came from, what continuations enriched it, and eventually what happened to it.

The Diary is the product's home screen because it turns captures back into a story of the user's day. The detail screen and notes matter because they let interpretation evolve without mutating the original captured artifact.

### What Orbit should not promise now

Do not promise:

- "Everything is local-only."
- "No Orbit servers."
- "BYOC is the default cloud path."
- "Cloud is v2 someday."
- "All AI is on-device."
- "The agent will act autonomously."

Do promise, once implementation matches:

- Local device storage remains authoritative for capture.
- Audit log and consent ledger stay on-device.
- Cloud AI/storage are structurally bounded and user-controllable.
- Local/Nano mode remains a permanent kill switch/fallback.
- Prompt content is not persisted in gateway logs or audit rows.
- User-confirmed actions only; no silent external writes.

### Orbit name and logo philosophy

The Orbit name should drive product physics, not decorative space theming. Captures are objects in the user's attention orbit. Some stay close, some drift, some form clusters, some land as resolved actions, and some are allowed to escape. The canonical mark should remain the tilted elliptical orbit with self-dot and captured-object dot. Avoid radar/concentric marks and avoid making every UI label poetic.

Use orbital language where it clarifies the product model: brand, onboarding, Sunday review, pitch. Keep daily controls pragmatic: Save, Already saved, Add note, Open, Done, Snooze, Dismiss.

## Key contradictions to clean up

### 1. README is stale against the cloud pivot

README currently says things like "Nothing leaves your device unless you opt in," frames BYOC/cloud as v2.0+, and says Orbit never uses Orbit servers for LLMs. Constitution v3.2, spec 013, and spec 014 now permit cloud-mode AI inference as default through `:net`, Vercel Edge Function, and Supabase Auth, with local kill switch and audit constraints.

Action: rewrite README before sharing broadly or using it as investor/user-facing copy.

### 2. Spec 005 conflicts with spec 013/014

Spec 005 still says users who never touch Cloud Boost see zero behavior change and has non-goals like "Orbit never operates its own LLM keys." Spec 013/014 already moved to an Orbit-managed gateway and cloud default. The right shape is not to delete spec 005, but to recast it as:

- BYOK and per-capability opt-out/override.
- User-facing cloud controls.
- Quota/cost UX.
- Local-mode kill switch UX.
- Audit details users can inspect.

Action: rewrite 005 as a control plane for the cloud gateway, not as the first introduction of cloud.

### 3. Spec 006 vs spec 013 Supabase shape

Spec 006 describes schema-per-tenant Orbit Cloud with per-user DEK and dedicated roles. Spec 013 Day 1 used Supabase shared tables with RLS smoke tests as the backbone. That is a pragmatic alpha step, but it is not the full constitutional Model A storage guarantee.

Action: document whether Supabase shared-table RLS is alpha bootstrap only or the intended implementation of Orbit Cloud. Constitution currently says RLS alone is not sufficient for storage isolation.

### 4. Spec 012 cloud/local lines are stale

Spec 012 says resolution metadata is local-only at v1 and no aggregate metric exfiltration ever. The anti-guilt and no-telemetry principles are still right. But future cloud sync of resolution state may be necessary for multi-device, KG, and agent memory if the user opts in.

Action: amend 012 to distinguish local-only audit/consent from syncable resolution state under consent.

### 5. Spec 002 duplicate rule changed in practice

Spec 002 originally said duplicate URL captures across time become separate envelopes while sharing hydration results. Spec 017 now correctly changes the product behavior for user-trust: a repeat save gets `Already saved` and actions instead of an indistinguishable duplicate.

Action: amend 002 with a short note that spec 017 supersedes duplicate capture behavior for active/non-deleted envelopes.

### 6. Visual specs and task state are split across 010 and 015

Spec 010 is the broad Quiet Almanac polish pass. Spec 015 is the current implementation refit and branch status. Some 010 assumptions like no bubble path or exact primitive requirements were later superseded by 015 and physical QA.

Action: let 015 be current for Demo Day visual behavior. Keep 010 as design history unless a specific primitive is still needed.

## The roadmap from here

### Phase 0: Land the current cleanup stack

Goal: stop the branch confusion and make the app's current good state durable.

1. Review and merge 016 first.
2. Review and merge 015 after verifying no old launcher assets or wrong Orbit mark remain.
3. Review and merge 017 after aligning the spec with legacy duplicate fallback/backfill behavior.
4. Decide whether to clean old open 015 task rows now or leave a note in `tasks.md` explaining the physical QA/product-decision closure.
5. Keep `RuntimeFlags.useNewVisualLanguage` default `false` until a deliberate release/alpha decision.
6. Rewrite README/local-only/cloud wording before outside readers rely on it.

### Phase 1: Make the v1 product feel complete

This is the next product slice after 015-017.

1. AI summaries and capture detail context.
   - Treat summaries as continuation results attached to captures.
   - Show source/provider, original artifact, AI summary, notes, and possible actions in detail.
   - Require provenance links and cautious language.
   - This is the first step toward "talk to my agent about this capture."
2. Manual compose and notes from spec 011.
   - Add a deliberate in-app capture path.
   - Add backfill to a prior day.
   - Model notes as append-only child records.
   - This answers the user expectation: "I should be able to add something to Orbit directly."
3. Shared source identity resolver.
   - Centralize provider-first source detection from `docs/capture-source-identity-plan.md`.
   - Support YouTube formats and then expand to major providers.
   - Render provider plus origin: "YouTube via Brave".
4. Related-capture foundation.
   - Add a retrieval service that can return "possibly related" captures with an explanation.
   - Start conservative: same provider/topic/entity/time window plus semantic similarity.
   - Do not surface related captures without a reason string and a feedback affordance.
5. Diary search/filter baseline.
   - Before full Ask Orbit/KG, ship practical filters: intent, source/provider, date/day, has note, has URL, duplicate/acknowledged, resolution state later.
   - Add text search over captured body/title/summary/notes.
   - This is the answer to "can captures later be searched/filtered?" Yes, and it should happen before the full agent.
6. Fix high-confidence TODOs that threaten trust.
   - `TODOS.md` T9 DateTimeParser UTC conversion.
   - Any remaining duplicate/device QA issues after merge.

### Phase 2: Search, Ask Orbit, and relevance

This phase makes the Diary useful after the first few days of data.

1. Make search and retrieval good enough to trust:
   - lexical search;
   - semantic retrieval;
   - filters;
   - citations;
   - relatedness explanations;
   - explicit "not related" feedback.
2. Build Ask Orbit as an evolution of search, not as a separate chatbot island.
   - Start with questions over captures and notes.
   - Add capture-anchored conversation: "talk about this capture".
   - Add cluster-anchored conversation: "talk about this group".
   - Every answer must cite source envelopes or say it is a hypothesis.
3. Implement resolution state from spec 012 in a narrow form:
   - Saved, Acknowledged, In motion, Resolved, Abandoned, Snoozed.
   - Long-press/detail actions for mark done, snooze, dismiss with reason.
   - Filters: all, open, in motion, resolved, abandoned, snoozed.
4. Connect duplicate re-encounter to resolution.
   - Re-saving an existing URL/text should mark Acknowledged and surface fast actions.
5. Prepare derived envelopes.
   - Summaries and structured lists are envelopes too, with provenance.
6. Add profile-question primitives.
   - The agent can ask one high-leverage question when a gap affects relevance.
   - Answers create editable profile facts with provenance.
   - Non-answers and dismissals are also signal.

### Phase 3: Orbit Actions

This is the first "Orbit helps me do the thing" phase.

1. Implement action extraction for calendar/to-do/share with user confirmation.
2. Register actions as AppFunctions so the future agent can use them.
3. Use action confirmations as canonical resolution triggers.
4. Keep all external writes user-confirmed. No silent writes.
5. Reuse audit/provenance for every suggestion, confirmation, dismissal, and undo.
6. Add grocery lists, reading lists, and message drafts as first-class action families.
7. Keep generated drafts/lists as derived envelopes when they become persistent artifacts.

### Phase 4: Cloud controls and cloud truth cleanup

This phase prevents cloud from becoming a trust hole.

1. Recast spec 005 around actual 013/014 architecture.
2. Add user-facing cloud controls:
   - local AI kill switch;
   - per-capability cloud/local toggles;
   - BYOK provider setup;
   - audit visibility for cloud calls;
   - gateway/account status.
3. Decide and document storage architecture:
   - alpha Supabase shared tables with RLS;
   - or schema-per-tenant constitutional target;
   - and the migration path between them.
4. Ensure prompt logging rules are hard-tested in the Edge Function.

### Phase 5: Knowledge graph and Agent

This is the long-term compounding product.

1. KG from spec 007:
   - entities, edges, provenance, sensitivity, temporal validity;
   - profile as a subgraph;
   - local last-N-days projection;
   - Orbit Cloud full graph if enabled.
2. Agent precursor from spec 002/008:
   - cluster-suggestion cards;
   - accepted/rejected suggestion episodes;
   - hardcoded actions first.
3. Pattern learning:
   - promote recurring clusters/actions/profile facts only after repeated evidence;
   - keep pattern memory inspectable and deletable;
   - use rejected/dismissed suggestions as negative examples.
4. Full Orbit Agent from spec 008:
   - user-invoked only;
   - visible plan before execution;
   - AppFunctions as tools;
   - consent filter before cloud prompts;
   - every tool call is an episode;
   - no autonomous scheduled agents.

## Missing specs or amendments implied by this audit

The current spec set has pieces of the vision, but some important product behavior is not yet explicitly specified enough.

1. **Capture Understanding / Detail Intelligence spec**
   - AI summaries attached to each capture.
   - Detail screen context and provenance.
   - Related captures with explanations.
   - "Ask about this capture" entry point.
2. **Relevance Ranking and Feedback spec**
   - Ranking inputs: embeddings, entities, source, time, intent, notes, profile facts, resolution, feedback.
   - Precision thresholds for proactive surfacing.
   - Feedback labels: useful, not related, wrong topic, not now, already handled.
   - Evaluation fixtures to prevent keyword-only matching.
3. **Curious Agent / Profile Questions spec**
   - When the agent is allowed to ask a question.
   - How often it can ask.
   - How answers become profile facts.
   - How to avoid assuming identity from sparse captures.
4. **Conversation Memory spec**
   - What parts of a chat become memory.
   - User consent for remembered facts.
   - Conversation-to-capture links.
   - Conversation-to-plan links.
5. **Action Families spec expansion**
   - Calendar events, to-dos, grocery lists, reading lists, message drafts.
   - What gets written externally vs stored as derived envelopes.
   - Per-step confirmation UX.
6. **Memory Inspector / Correction spec**
   - User can see what Orbit thinks it knows.
   - User can edit/delete profile facts, patterns, and relationships.
   - Corrections become negative-weight graph signal, not silent overwrites.

## Diary search/filter answer

Yes, captures can and should later be searched and filtered. There are three levels:

1. Practical v1 search/filter:
   - local text search over captured text, titles, summaries, and notes;
   - filters by intent, provider/source, date, has URL, has note, deleted/archived state;
   - later filters by resolution state.
2. Ask Orbit:
   - natural-language questions over the corpus;
   - citations back to envelopes;
   - initially keyword/FTS, then vector retrieval and cloud synthesis when gated.
3. Knowledge graph search:
   - entity pages;
   - multi-hop temporal queries;
   - profile-aware answers;
   - relationships like person, project, topic, place, item, event, state.

Search should land before full Ask Orbit. Users need a reliable way to find captures before they trust a conversational answer layer.

## Agent story

The agent should not be introduced as an autonomous assistant. The coherent progression is:

1. v1 precursor: cluster-suggestion engine notices research sessions and offers hardcoded actions.
2. v1.1: Orbit Actions turn single envelopes into user-confirmed external actions.
3. v1.2: Ask Orbit can hand off action-shaped requests to the agent.
4. v1.2: Full Orbit Agent is user-invoked planner/executor:
   - long-press bubble or Ask Orbit handoff;
   - reads KG/profile/patterns;
   - produces a human-readable plan;
   - executes AppFunctions only after confirmation;
   - writes episodes for every tool call and outcome.

This is powerful enough for the product vision while avoiding the trust cliff of silent autonomous behavior.

## Recommended doc cleanup list

1. Rewrite `README.md` to match current truth:
   - local-first, not local-only;
   - Orbit Cloud/managed gateway exists;
   - BYOC is later power-user path;
   - no prompt logging;
   - local kill switch.
2. Add amendments to:
   - `specs/002-intent-envelope-and-diary/spec.md` for spec 017 duplicate behavior;
   - `specs/005-cloud-boost-byok-llm/spec.md` for cloud control plane;
   - `specs/012-resolution-semantics/spec.md` for cloud/sync wording;
   - maybe `specs/006-orbit-cloud-storage/spec.md` to reconcile Supabase RLS alpha vs schema-per-tenant target.
3. Keep `docs/orbital-framing-deferred.md` as a brand decision record. Do not force poetic labels into daily controls yet.
4. Promote `docs/capture-source-identity-plan.md` into implementation tasks after 015/017 merge.
5. Add a short `docs/branch-split-status.md` or update `docs/capture-overlay-followups.md` after merge so future work does not drift back to the old stacked branch.

## Open product decisions

1. When does `useNewVisualLanguage` default flip to true: before Demo Day, alpha-only, or after merge QA?
2. Is Supabase shared-table RLS an alpha bootstrap or an acceptable long-term storage model? Constitution currently demands stronger schema-per-tenant guarantees for Orbit Cloud storage.
3. How much cloud should be visible in Settings for Demo Day? Avoid overbuilding, but do not hide a trust-critical kill switch once cloud AI is default.
4. Does Manual Compose ship before Search? Recommendation: Manual Compose first if the app still lacks an obvious add-note path; Search next because data accumulates quickly.
5. Should duplicate recapture mark the existing envelope Acknowledged immediately? Recommendation: yes, after resolution state exists.
6. Should orbital language appear in-app before alpha evidence? Recommendation: brand/onboarding only; controls stay pragmatic.

## Suggested next implementation order

1. Merge/land 016.
2. Merge/land 015.
3. Merge/land 017.
4. Refresh README and stale cloud claims.
5. Implement AI summaries attached to captures and improve capture detail context.
6. Implement Manual Compose/notes slice from 011.
7. Implement shared source identity resolver.
8. Implement related-capture retrieval with explanations and feedback.
9. Implement Diary search/filter baseline.
10. Implement Ask Orbit over search with citations, starting with capture-anchored chat.
11. Implement narrow resolution state.
12. Implement Orbit Actions, starting calendar/to-do and expanding to grocery lists, reading lists, and message drafts.
13. Add curious profile questions and editable profile facts.
14. Rework Cloud Boost controls against 013/014.
15. Implement KG, pattern promotion, conversation memory, and full Agent.

This order keeps the app feeling whole to a real user before deepening the agent architecture. The agent becomes more credible when it stands on a Diary users can compose into, search, filter, and trust.
