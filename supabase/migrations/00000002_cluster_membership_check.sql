-- supabase/migrations/00000002_cluster_membership_check.sql
--
-- Orbit Cloud — FR-032 cluster-membership citation invariant (server-side).
--
-- Spec:     specs/013-cloud-llm-routing/spec.md
-- Contract: specs/013-cloud-llm-routing/contracts/supabase-cluster-membership-check-contract.md
-- Task:     T013-025 (FR-013-025)
-- ADR:      ADR-002 (FR-032 server-side enforcement)
--          Constitution Principle XIV (Bounded Observation)
--
-- Invariant: every envelope id cited in clusters.summary as
--   [envelope:<uuid>]
-- MUST have a corresponding row in cluster_members for the same cluster.
--
-- A pure CHECK constraint cannot reference another table. The
-- structurally correct mechanism is a BEFORE INSERT OR UPDATE OF summary
-- trigger that consults cluster_members for each cited envelope id.
--
-- Citation format is intentionally narrow: bracketed lowercase keyword,
-- colon, canonical 8-4-4-4-12 hex UUID. If a future spec evolves the
-- citation shape (e.g. <a href="...">), this trigger must be updated.

BEGIN;

CREATE OR REPLACE FUNCTION enforce_cluster_membership_citations()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    cited_id uuid;
    cited_ids uuid[];
    member_count int;
BEGIN
    -- Fast-path: nothing to validate if summary is null/empty.
    IF NEW.summary IS NULL OR length(NEW.summary) = 0 THEN
        RETURN NEW;
    END IF;

    -- Extract every '[envelope:<uuid>]' citation as a uuid[].
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

COMMIT;
