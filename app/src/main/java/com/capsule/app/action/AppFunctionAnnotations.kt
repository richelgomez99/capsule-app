package com.capsule.app.action

import com.capsule.app.data.model.AppFunctionSideEffect
import com.capsule.app.data.model.Reversibility
import com.capsule.app.data.model.SensitivityScope

/**
 * Marks a function as an Orbit `AppFunction` — an agent-callable skill the
 * extractor LLM may propose and the user may confirm to invoke.
 *
 * Discovery: a KSP processor (the `androidx.appfunctions:appfunctions-compiler`
 * artifact wired up by spec 003 + a tiny Orbit-specific shim) walks every
 * `@AppFunction`-annotated declaration and emits a generated
 * `BUILT_IN_APP_FUNCTION_SCHEMAS` constant inside this package. The `:ml`
 * process picks that constant up at boot via [com.capsule.app.data.AppFunctionRegistry.registerAll].
 *
 * Until the KSP shim lands, the bootstrap path falls back to the hand-curated
 * list in [BuiltInAppFunctionSchemas]. Both paths are equivalent — the
 * runtime registry stores rows in `appfunction_skill` either way.
 *
 * Design notes:
 *  - `argsSchemaJson` references a `.json` resource bundled in `assets/` so
 *    the model and the registry agree byte-for-byte on the schema. The
 *    annotation only carries the resource path; the loader inflates it on
 *    first registration. This keeps the annotation Kotlin-source-compatible
 *    with the Jetpack `@AppFunction` we'll align to in v1.2.
 *  - Reversibility, side-effects, and sensitivity scope are mandatory
 *    because they drive the confirm-sheet warning copy and the audit row
 *    schema (`extraJson.sensitivity`).
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AppFunction(
    val functionId: String,
    val schemaVersion: Int,
    val displayName: String,
    val description: String,
    val argsSchemaAsset: String,
    val sideEffects: AppFunctionSideEffect,
    val reversibility: Reversibility,
    val sensitivityScope: SensitivityScope
)
