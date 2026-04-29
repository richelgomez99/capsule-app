# Supabase Cluster-Membership CHECK Contract

**Boundary**: Supabase Postgres internal — invariant on writes to `clusters`
**Status**: DRAFT — Day 1 of `013-cloud-llm-routing`
**Source ADRs / FRs**: ADR-002 (FR-032 server-side enforcement); Constitution Principle XIV (Bounded Observation)
**Migration file**: `supabase/migrations/00000002_cluster_membership_check.sql`

This contract is the cloud-side mirror of spec 002's FR-032 cluster-citation invariant: **every envelope ID cited in a `clusters.summary` MUST be a current member of that cluster**. Day 1 enforces this server-side so the rule cannot be bypassed by a buggy client or a malicious request that authenticates correctly but writes inconsistent data.

---

## 1. Why a trigger, not a CHECK constraint

A pure `CHECK` constraint on `clusters` cannot reference rows in another table (`cluster_members`). Postgres `CHECK` constraints are intra-row only. The structurally correct mechanism is a `BEFORE INSERT OR UPDATE` trigger that consults `cluster_members` for each cited envelope ID.

---

## 2. Cited-envelope-id format

The `clusters.summary` column stores natural-language summary text. Envelope citations within it use the format `[envelope:<uuid>]` — bracketed, lowercase keyword, colon, UUID.

Example summary content:
```text
On April 28 you saved a recipe for pasta carbonara [envelope:9f1d…] and a
review of the same dish [envelope:c4a2…]. Both were captured between
4pm and 6pm.
```

Two envelope IDs are cited (`9f1d…` and `c4a2…`); both MUST be members of this cluster.

---

## 3. Trigger implementation

```sql
-- 00000002_cluster_membership_check.sql

CREATE OR REPLACE FUNCTION enforce_cluster_membership_citations()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    cited_id uuid;
    cited_ids uuid[];
    member_count int;
BEGIN
    -- Skip if summary is null or empty
    IF NEW.summary IS NULL OR length(NEW.summary) = 0 THEN
        RETURN NEW;
    END IF;

    -- Extract every '[envelope:<uuid>]' citation as a uuid[].
    -- Regex captures the UUID body; uses the canonical 8-4-4-4-12 hex pattern.
    SELECT array_agg(DISTINCT (matches[1])::uuid)
      INTO cited_ids
      FROM regexp_matches(
        NEW.summary,
        '\[envelope:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\]',
        'g'
      ) AS matches;

    IF cited_ids IS NULL THEN
        RETURN NEW;   -- no citations to validate
    END IF;

    -- For each cited envelope id, verify membership in this cluster.
    FOREACH cited_id IN ARRAY cited_ids LOOP
        SELECT count(*) INTO member_count
          FROM cluster_members cm
         WHERE cm.cluster_id = NEW.id
           AND cm.envelope_id = cited_id;

        IF member_count = 0 THEN
            RAISE EXCEPTION
              'cluster summary cites envelope % which is not a member of cluster %',
              cited_id, NEW.id
              USING ERRCODE = 'check_violation',
                    HINT = 'add the envelope to cluster_members before referencing it in the summary';
        END IF;
    END LOOP;

    RETURN NEW;
END;
$$;

CREATE TRIGGER cluster_membership_citations_check
  BEFORE INSERT OR UPDATE OF summary ON clusters
  FOR EACH ROW
  EXECUTE FUNCTION enforce_cluster_membership_citations();
```

---

## 4. Acceptance probes

The smoke test (or a dedicated test SQL file) MUST exercise both directions:

| Probe | Expected |
|-------|----------|
| Insert/update a cluster with a summary citing an envelope ID that IS a current `cluster_members` row → succeeds | row written, no error |
| Insert/update a cluster with a summary citing an envelope ID that is NOT a member → fails | error code `23514` (`check_violation`), error message names the offending envelope id |
| Delete a `cluster_members` row, then update the cluster's summary to still cite that (now-removed) envelope → fails | same error |
| Update a cluster's `member_count` (no summary change) → succeeds even if the trigger function were re-evaluated | trigger fires only on `summary` updates by virtue of `OF summary` |

---

## 5. Performance & race-condition notes

- The trigger executes once per row write, with one `SELECT count(*)` per cited envelope ID. For typical clusters (≤ 10 cited envelopes per summary), overhead is negligible.
- Race: a concurrent transaction could delete a `cluster_members` row between the trigger's check and the commit. Postgres' default READ COMMITTED isolation does not prevent this. Per ADR-002, the trigger is best-effort consistency — the on-device cluster engine is the source of truth, and a brief inconsistency window during concurrent writes is acceptable. Stronger consistency would require SERIALIZABLE isolation or row-level locks; not justified at Day-1 scale.
- The `OF summary` predicate on the trigger means UPDATEs that don't change `summary` skip the check. Important for fast `member_count` updates from the cluster maintenance worker.

---

## 6. Constitution alignment (Principle XIV — Bounded Observation)

Principle XIV states that derived facts (cluster summaries, KG edges, profile facts) MUST be bounded by their source episodes. This trigger is the **server-side enforcement** of that principle for cluster summaries — a summary that cites an envelope it cannot trace back to via `cluster_members` is structurally rejected, even if the client is buggy or the JWT is correctly authenticated.

The on-device counterpart (spec 002 FR-032) is the client-side enforcement. Both layers must hold; the cloud one is the structural backstop.

---

## 7. What this contract does NOT cover

- **Non-citation summary content.** The trigger only validates that *cited* envelope IDs are cluster members. It does not validate that every cluster member is cited (over-membership is fine; under-citation is not enforced).
- **Citation format evolution.** If a future spec adds a different citation shape (e.g., `<a href="...">`), this trigger must be updated. The regex is intentionally narrow.
- **Cross-cluster citations.** A summary on cluster X citing an envelope that is a member of cluster Y but not X is rejected — citations are scoped to the cluster the summary belongs to. This matches FR-032's exact wording.
- **Provenance for non-cluster-summary fields.** The full provenance graph (Constitution Principle XII) is enforced separately in spec 006 (Orbit Cloud Storage); this trigger is just the cluster-summary slice.
