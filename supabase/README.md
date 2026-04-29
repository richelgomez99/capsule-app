# Orbit Supabase backbone

Server-side schema, RLS policies, triggers, and tests for the Orbit cloud
backbone. Provisioned per spec [`013-cloud-llm-routing`](../specs/013-cloud-llm-routing/spec.md)
(Day 1 of the cloud pivot).

## Layout

```
supabase/
├── migrations/   # Numbered, append-only schema migrations (00000000_*, 00000001_*, ...).
├── functions/    # Reserved for Edge Functions (LLM gateway, etc.) — empty on Day 1.
└── tests/        # SQL smoke tests (multi-user RLS, etc.).
```

## Day-1 migrations

| File | Purpose | Spec task |
|------|---------|-----------|
| `migrations/00000000_initial_schema.sql` | v1 schema mirroring on-device Room tables; hybrid plaintext + reserved nullable `*_ct bytea` columns per [encryption contract](../specs/contracts/envelope-content-encryption-contract.md). | T013-023 |
| `migrations/00000001_rls_policies.sql` | RLS `auth.uid() = user_id` policies on every table per [supabase-rls-contract.md](../specs/013-cloud-llm-routing/contracts/supabase-rls-contract.md). | T013-024 |
| `migrations/00000002_cluster_membership_check.sql` | FR-032 server-side enforcement trigger per [cluster-membership-check contract](../specs/013-cloud-llm-routing/contracts/supabase-cluster-membership-check-contract.md). | T013-025 |
| `tests/multi_user_smoke.sql` | Multi-user RLS isolation smoke test — **alpha-install release gate** (ADR-007 / SC-008). | T013-026 / T013-028 |

## Operational notes

- **Secrets** (`SUPABASE_PROJECT_REF`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`,
  `SUPABASE_DB_URL`) live outside this repo (1Password / shared vault).
  The path/reference is recorded in `local.properties` (gitignored).
- Apply migrations in numeric order: `psql "$SUPABASE_DB_URL" -f migrations/00000000_*.sql`,
  then `00000001_*.sql`, then `00000002_*.sql`.
- The `service_role` key bypasses RLS and MUST NOT ship to the Android client.
  It is retained server-side for migrations only.

## ⚠️ `SECURITY DEFINER` warning

Any future migration that introduces a `SECURITY DEFINER` function (e.g.,
cross-tenant aggregations for cost telemetry) MUST explicitly re-check
`auth.uid()` inside the function body. Failure to do so silently bypasses
RLS for every caller. See the `SECURITY DEFINER` clause in
[supabase-rls-contract.md §4](../specs/013-cloud-llm-routing/contracts/supabase-rls-contract.md).

## Release gate

Per ADR-007 / SC-008, **no alpha install ships** until
`tests/multi_user_smoke.sql` prints `PASS` against the production project.
This is enforced manually in the release checklist.
