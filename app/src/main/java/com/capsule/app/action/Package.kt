/**
 * No-network process scope: all classes in this package and its
 * sub-packages run in the `:capture` process. Per Principle VIII +
 * action-execution-contract.md §6 they MUST NOT make any outgoing
 * network call — handler dispatch is via Android Intents only, and
 * audit / execution rows are written by binding to `:ml`'s
 * [com.capsule.app.data.ipc.IEnvelopeRepository] (the sole DB writer).
 *
 * Code review checklist:
 *   - No `OkHttpClient`, no `URL.openConnection`, no Retrofit.
 *   - No `com.capsule.app.net.*` imports (lint-enforced via
 *     baseline + module visibility rules).
 *   - No `WRITE_*` permission additions in AndroidManifest.xml beyond
 *     what 002 already declared (T099 enforces).
 *
 * If you find yourself reaching for a network client here, you almost
 * certainly want to either (a) emit the work as a 002 continuation
 * targeting `:net`, or (b) refuse the call entirely.
 *
 * See also:
 *   - specs/003-orbit-actions/contracts/action-execution-contract.md §6
 *   - specs/003-orbit-actions/research.md §4 (zero new permissions)
 */
package com.capsule.app.action;
