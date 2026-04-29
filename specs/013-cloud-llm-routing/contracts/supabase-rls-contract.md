# Supabase RLS Contract

**Boundary**: Supabase Postgres ↔ any client carrying a `auth.users` JWT
**Status**: DRAFT — Day 1 of `013-cloud-llm-routing`
**Source ADRs**: ADR-007 (RLS + multi-user smoke test prereq for alpha)
**Migration files**: `supabase/migrations/00000001_rls_policies.sql`, `supabase/tests/multi_user_smoke.sql`

This contract is the structural mechanism by which Orbit Cloud enforces tenant isolation. It is the **only** isolation mechanism (NFR-013-005); no application-layer `user_id` filter is relied upon as the sole gate.

---

## 1. Universal policy shape

For every table in the schema (envelopes, continuations, continuation_results, clusters, cluster_members, action_proposals, action_executions, audit_log_entries, user_profiles), the following four policies MUST exist:

```sql
ALTER TABLE <table> ENABLE ROW LEVEL SECURITY;

CREATE POLICY "select_own_rows" ON <table>
  FOR SELECT TO authenticated
  USING (auth.uid() = user_id);

CREATE POLICY "insert_own_rows" ON <table>
  FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "update_own_rows" ON <table>
  FOR UPDATE TO authenticated
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "delete_own_rows" ON <table>
  FOR DELETE TO authenticated
  USING (auth.uid() = user_id);
```

### 1.1 Special case: `audit_log_entries`
The audit table is **append-only**. Replace the UPDATE and DELETE policies with explicit denies:

```sql
CREATE POLICY "no_update_audit" ON audit_log_entries FOR UPDATE TO authenticated USING (false);
CREATE POLICY "no_delete_audit" ON audit_log_entries FOR DELETE TO authenticated USING (false);
```

The on-device audit log per Constitution Principle X is **never uploaded**. This server-side `audit_log_entries` table is reserved for cloud-action mirrors that downstream specs will write (e.g., the Edge Function recording cloud LLM call metadata once Principle IX-compliance lands).

### 1.2 No `service_role` policies in this spec
The `service_role` (Supabase superuser key) bypasses RLS by default. Day 1 does NOT add any policies for `service_role`, and the `service_role` key MUST NOT be used by the Android client. The key is retained server-side for migrations only. The Edge Function spec will define what role the Edge Function runs as and whether any `SECURITY DEFINER` function is required.

---

## 2. Coverage matrix

The 9 tables × 4 policies = **36 policies** on Day 1 (32 user-row policies + 2 explicit denies on audit + 2 normal SELECT/INSERT on audit). Every table has RLS enabled. None of them are exempt.

| Table | SELECT | INSERT | UPDATE | DELETE |
|-------|--------|--------|--------|--------|
| `envelopes` | ✅ own | ✅ own | ✅ own | ✅ own |
| `continuations` | ✅ own | ✅ own | ✅ own | ✅ own |
| `continuation_results` | ✅ own | ✅ own | ✅ own | ✅ own |
| `clusters` | ✅ own | ✅ own | ✅ own | ✅ own |
| `cluster_members` | ✅ own | ✅ own | ✅ own | ✅ own |
| `action_proposals` | ✅ own | ✅ own | ✅ own | ✅ own |
| `action_executions` | ✅ own | ✅ own | ✅ own | ✅ own |
| `audit_log_entries` | ✅ own | ✅ own | ❌ deny | ❌ deny |
| `user_profiles` | ✅ own | ✅ own | ✅ own | ✅ own |

---

## 3. Multi-user smoke test (alpha gate per ADR-007 / FR-013-026)

File: `supabase/tests/multi_user_smoke.sql`. Invocation: `psql "$SUPABASE_PROD_DB_URL" -f supabase/tests/multi_user_smoke.sql`.

### 3.1 Acceptance criteria

The script:

1. Creates two `auth.users` rows: user A and user B (via `auth.admin.createUser` or equivalent service-role insert).
2. Authenticates as user A; inserts:
   - One envelope (e.g. `body = 'A-envelope-1'`).
   - One cluster + one cluster member referencing that envelope.
   - One action proposal referencing that envelope.
3. Authenticates as user B; inserts an analogous set of rows owned by B.
4. **Cross-tenant probe (the actual test)** — authenticates as user B and runs:

   | Probe | MUST return |
   |-------|-------------|
   | `SELECT count(*) FROM envelopes WHERE id = '<A_envelope_id>'` | `0` |
   | `SELECT count(*) FROM envelopes` | `1` (only B's row) |
   | `UPDATE envelopes SET body = 'pwn' WHERE id = '<A_envelope_id>'` | `0 rows affected` (RLS hides the row from UPDATE) |
   | `DELETE FROM envelopes WHERE id = '<A_envelope_id>'` | `0 rows affected` |
   | Same four probes against `clusters`, `cluster_members`, `action_proposals` | identical: zero rows / zero affected |

5. Also probes that A's data is still intact (re-auths as A, counts A's envelopes — must be `1`, not `0`, ruling out an over-restrictive RLS policy that hides own rows).
6. Prints `PASS` and exits with status `0` if every probe passed; prints `FAIL <reason>` and exits non-zero otherwise.

### 3.2 Critical: `0 rows affected` is the correct answer for cross-tenant UPDATE/DELETE

When RLS denies a row, the row is structurally invisible — UPDATE and DELETE see zero rows to operate on, but the operation does NOT raise a permission error. This is intentional (preserves no-leak-on-existence) and the test asserts this exact shape. **A test that asserts a permission error on UPDATE/DELETE is wrong** — fix the test, not the policy.

### 3.3 Pgvector probe
The smoke test ALSO inserts a row into `clusters` with a `vector(1536)` embedding to verify pgvector is enabled and the dimensionality matches `text-embedding-3-small`. Failure here means migration `00000000` did not enable the extension.

### 3.4 Wiring into the alpha gate
- The release checklist (out of scope for this spec) MUST include a manual checkbox: "Multi-user smoke test passes on prod Supabase project".
- No alpha install ships until this checkbox is checked.

---

## 4. Future-proofing notes (for downstream specs)

- **`SECURITY DEFINER` functions**: the Edge Function spec MAY add functions that bypass RLS for legitimate cross-tenant operations (e.g., aggregating cost telemetry across users with consent). Every such function MUST explicitly re-check `auth.uid()` or be limited to admin contexts. The migration files include a comment block warning about this.
- **`auth.users` deletion**: the `on delete cascade` in the foreign key from each table's `user_id` to `auth.users(id)` ensures that a user account deletion fully cascades. This satisfies Constitution Principle X non-negotiable #4 (full delete propagates to every downstream system).
- **RLS performance**: `auth.uid() = user_id` is index-friendly. Every table's `user_id` column SHOULD have an index (added in `00000000_initial_schema.sql`). Without it, RLS becomes a sequential scan and breaks at scale; with it, it's an index seek.
