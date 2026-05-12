# Quickstart: Spec 016 - Intent Set Extension

## TL;DR

Do not rename `WANT_IT` or `INTERESTING`. Add `READ_LATER`, preserve the product labels, align the cloud classifier to Android enum names, and keep contact-ref schema work additive.

## Mapping

| Stored enum | Display label | Action |
|---|---|---|
| `WANT_IT` | `Want it` | Preserve. |
| `REFERENCE` | `Reference` | Preserve. |
| `READ_LATER` | `Read later` | Add. |
| `FOR_SOMEONE` | `For someone` | Preserve. |
| `INTERESTING` | `Interesting` | Preserve. |
| `AMBIGUOUS` | fallback/sentinel | Preserve, not user-pickable. |

Chip order: `Want it`, `Reference`, `Read later`, `For someone`, `Interesting`.

## Implementation Order

1. Amend docs/tasks away from the stale rename plan.
2. Add `READ_LATER` to Android enum and label resolvers.
3. Add `READ_LATER` chip to `ChipRow` and `IntentChipPicker`.
4. Align Supabase classifier prompt, allowlist, and tests.
5. Defer ContactRef schema to its own follow-up migration slice.

## Gates

Android:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug
```

Supabase:

```bash
cd supabase/functions/llm_gateway
npm test
```

Search gates:

```bash
rg -n "REMIND_ME|INSPIRATION|intent-set rename" app/src/main supabase/functions/llm_gateway
rg -n "Intent\.READ_LATER|READ_LATER" app/src/main supabase/functions/llm_gateway
```

The first command should return no implementation references. The second should show the intended Android and cloud classifier coverage.

## Do Not Do

- Do not migrate `WANT_IT` rows to `REMIND_ME`.
- Do not migrate `INTERESTING` rows to `INSPIRATION`.
- Do not heuristically recategorize existing rows into `READ_LATER`.
- Do not add ContactRef entity fields in this branch. Future ContactRef work must include the matching Room migration.
- Do not move visual chip styling into this branch; spec 015 owns styling.

## Status

- Active branch: `016-intent-set-migration`.
- Current slice: product-label amendment plus `READ_LATER` surface alignment.
- Follow-on slice: additive ContactRef schema migration targeting the next migration after the then-current Room schema version.
