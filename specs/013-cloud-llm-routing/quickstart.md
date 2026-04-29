# Quickstart — 013-cloud-llm-routing

**Audience**: the next implementer picking this up to do the actual code.

This is a 5-minute walkthrough. Do steps in order; do not skip step 0.

---

## 0. Verify branch + baseline

```sh
cd /Users/richelgomez/dev/capsule-app
git rev-parse --abbrev-ref HEAD     # MUST print: cloud-pivot
git log -1 --oneline                # SHOULD be at or descendant of 922b606
```

If you're not on `cloud-pivot`, stop. Either `git checkout cloud-pivot` or escalate.

## 1. Run the baseline build + tests (must be green BEFORE any changes)

```sh
./gradlew compileDebugKotlin compileDebugUnitTestKotlin testDebugUnitTest \
  --tests '*Llm*' --tests '*Cluster*'
```

This is also the smoke test for SC-002. If it's red BEFORE you start, fix that first — do not begin the spec on a red baseline.

## 2. Read in this order (do not skim — these contradict in places, the most-recent-modified wins)

1. [spec.md](spec.md) — authoritative requirements.
2. [plan.md](plan.md) — architecture decisions, constitution check.
3. [data-model.md](data-model.md) — every type and table you will touch.
4. [contracts/llm-gateway-envelope-contract.md](contracts/llm-gateway-envelope-contract.md) — wire format Android → Edge Function.
5. [contracts/aidl-callllmgateway-contract.md](contracts/aidl-callllmgateway-contract.md) — wire format `:capture` → `:net`.
6. [contracts/supabase-rls-contract.md](contracts/supabase-rls-contract.md) — RLS shape.
7. [contracts/supabase-cluster-membership-check-contract.md](contracts/supabase-cluster-membership-check-contract.md) — FR-032 server-side enforcement.
8. [research.md](research.md) — skim for validated facts; full read only if you need to challenge a decision.

## 3. Implementation order

Run `/speckit.tasks` to generate `tasks.md` first. Implement in the order it produces. Each task lands as **its own atomic commit** on `cloud-pivot` (NFR-013-007). Do not squash before review.

The expected commit sequence (the `tasks.md` will refine):

1. `feat(ai): add LlmGatewayRequest/Response sealed classes` — `app/src/main/java/com/capsule/app/ai/gateway/`.
2. `feat(net): add LlmGatewayRequestParcel/ResponseParcel` — `app/src/main/java/com/capsule/app/net/ipc/` + AIDL parcelable declarations.
3. `feat(aidl): extend INetworkGateway with callLlmGateway` — `app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl`.
4. `feat(net): add LlmGatewayClient with retry-once fallback` — `app/src/main/java/com/capsule/app/net/LlmGatewayClient.kt`.
5. `feat(net): wire callLlmGateway into NetworkGatewayImpl` — `NetworkGatewayImpl.kt`.
6. `feat(ai): add CloudLlmProvider implementing LlmProvider` — `app/src/main/java/com/capsule/app/ai/CloudLlmProvider.kt`.
7. `feat(ai): add LlmProviderRouter` — `LlmProviderRouter.kt`.
8. `feat: extend RuntimeFlags with useLocalAi + clusterEmitEnabled` — `RuntimeFlags.kt`.
9. `refactor: migrate NanoLlmProvider call sites to LlmProviderRouter` — six files: `ReducedModeActivity.kt`, `UrlHydrateWorker.kt`, `EnvelopeRepositoryService.kt` (× 2), `DiaryActivity.kt`, `CapsuleSealOrchestrator.kt`. **Do NOT touch `ClusterDetectionWorker.kt`.**
10. `chore: add CLUSTER-LOCAL-PIN comment to ClusterDetectionWorker` — `app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt` line 63 area.
11. `chore(supabase): add initial schema + RLS + cluster CHECK migrations` — `supabase/migrations/`.
12. `chore(supabase): add multi-user smoke test` — `supabase/tests/multi_user_smoke.sql`.

Test commits (`test(ai): ...`, `test(net): ...`) interleave between the feature commits — the new file lands in the same commit as its test where small, separately when the test is large.

## 4. Final verification (must run before opening review)

```sh
# Smoke test (SC-002)
./gradlew compileDebugKotlin compileDebugUnitTestKotlin testDebugUnitTest \
  --tests '*Llm*' --tests '*Cluster*'

# Migration sweep (SC-001)
grep -rn "NanoLlmProvider()" app/src/main/ \
  | grep -v "cluster/ClusterDetectionWorker.kt" \
  | grep -v "ai/LlmProviderRouter.kt"
# Expected output: zero lines.

# Cluster-pin comment (FR-013-028)
grep -n "CLUSTER-LOCAL-PIN" app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt
# Expected output: exactly one line.

# AIDL stub generation (SC-003)
find app/build/generated/aidl_source_output_dir -name '*INetworkGateway*' \
  | xargs grep -l 'callLlmGateway'
# Expected output: at least one match.
```

For the Supabase backbone (SC-006, SC-007, SC-008): once a Supabase project is provisioned (out-of-band — write the credentials to 1Password, NOT to the repo), apply the migrations in order via `psql`, then run the smoke test:

```sh
psql "$SUPABASE_PROD_DB_URL" -f supabase/migrations/00000000_initial_schema.sql
psql "$SUPABASE_PROD_DB_URL" -f supabase/migrations/00000001_rls_policies.sql
psql "$SUPABASE_PROD_DB_URL" -f supabase/migrations/00000002_cluster_membership_check.sql
psql "$SUPABASE_PROD_DB_URL" -f supabase/tests/multi_user_smoke.sql
# Expected output: a single 'PASS' line, exit code 0.
```

## 5. Do NOT push

```sh
# DO NOT RUN until user explicitly approves (NFR-013-008):
# git push -u origin cloud-pivot
```

The work stays local on `cloud-pivot` until the user gives the green light.

## 6. Common pitfalls

- **Forgetting the AIDL parcelable declaration files.** When you add `LlmGatewayRequestParcel.kt`, you also need `LlmGatewayRequestParcel.aidl` (one-line file: `parcelable LlmGatewayRequestParcel;`). Same for the response. Build will fail confusingly without these.
- **Touching `NanoLlmProvider.kt` body.** FR-013-017 forbids it. The TODOs stay. If you find yourself "fixing" anything inside it, stop.
- **Using `data class` equality on `LlmGatewayResponse.EmbedResponse`.** Default `FloatArray` equality is reference-equality. Override `equals`/`hashCode` with `contentEquals` / `contentHashCode`. Tests will fail subtly without this.
- **Forgetting `requestId`.** Every request constructs a UUIDv4. Without it, SC-009 latency attribution is impossible and the Edge Function trace is broken.
- **Migrating `ClusterDetectionWorker.kt`.** Do NOT. Add the `CLUSTER-LOCAL-PIN` comment and move on. The migration is owned by the Phase 11 Block 4 spec.
- **Putting Anthropic / OpenAI / Vercel API keys in the Android binary.** NFR-013-003 forbids it. The keys live server-side at the Edge Function (separate spec).
- **Skipping the constitution amendment.** Check [plan.md](plan.md) Constitution Check section. The amendment must land via `/speckit.constitution` BEFORE implementation begins.
