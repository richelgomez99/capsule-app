// Spec 014 hotfix — closed-set allowlists for handler outputs.
//
// Background: Anthropic Haiku/Sonnet sometimes return strings outside the
// label sets declared in our system prompts (prompt-injection or normal
// model drift). Schema-only validation at the Zod layer is too permissive
// — it accepts any non-empty string. These allowlists collapse upstream
// output back to a known closed set before crossing the handler boundary.
//
// Used by:
//   - handlers/classify_intent.ts (INTENT_LABELS)
//   - handlers/scan_sensitivity.ts (SENSITIVITY_TAGS)
//   - handlers/extract_actions.ts (filter via per-request registeredFunctions)

/**
 * Six valid intent labels. Mirrors the system prompt in
 * handlers/classify_intent.ts. Anything outside this set falls back to OTHER.
 */
export const INTENT_VALUES: ReadonlySet<string> = new Set([
  "REMINDER",
  "NOTE",
  "QUESTION",
  "TASK",
  "EVENT",
  "OTHER",
]);

/** Fallback when the model returns an out-of-set intent. */
export const INTENT_FALLBACK = "OTHER";

/**
 * Seven valid sensitivity tags. Mirrors the system prompt in
 * handlers/scan_sensitivity.ts. Unknown tags are dropped (NOT collapsed
 * to NONE — dropping preserves the other valid tags in a multi-tag
 * response). If the resulting filtered+deduped array is empty, we
 * default to ["NONE"].
 */
export const SENSITIVITY_TAGS: ReadonlySet<string> = new Set([
  "PII",
  "FINANCIAL",
  "MEDICAL",
  "LEGAL",
  "CREDENTIAL",
  "CHILD_SAFETY",
  "NONE",
]);

/** Sanitise a raw tags array: keep only known tags, dedupe, default to NONE. */
export function sanitizeSensitivityTags(raw: readonly string[]): string[] {
  const filtered = Array.from(
    new Set(raw.filter((t) => SENSITIVITY_TAGS.has(t))),
  );
  return filtered.length === 0 ? ["NONE"] : filtered;
}

/** Clamp an intent label to the closed set; unknowns become OTHER. */
export function sanitizeIntent(raw: string): string {
  return INTENT_VALUES.has(raw) ? raw : INTENT_FALLBACK;
}
