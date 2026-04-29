package com.capsule.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

/**
 * Lint detector that prevents [com.capsule.app.ui.primitives.AgentVoiceMark]
 * usage outside the agent-voice surface allow-list.
 *
 * Spec 010 FR-010-019 reserves the ✦ glyph exclusively for surfaces
 * where the agent is speaking. The initial allow-list is
 * `ClusterSuggestionCard.kt`. Adding an entry here is a deliberate
 * design decision; it should be reviewed in the same PR that adds a
 * new agent-voice surface.
 */
class NoAgentVoiceMarkOutsideAgentSurfacesDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "OrbitNoAgentVoiceMarkOutsideAgentSurfaces",
            briefDescription = "AgentVoiceMark used outside the agent-voice surface allow-list",
            explanation = """
                The ✦ AgentVoiceMark glyph is reserved exclusively for agent-voice \
                surfaces (spec 010 FR-010-019). Calling AgentVoiceMark from any file \
                outside the allow-list dilutes the agent-voice signal and breaks the \
                distinction between user-authored envelopes and agent-spoken cards.

                If you genuinely need an agent-voice surface, add the file's simple \
                name (without `.kt`) to the detector's ALLOWED_FILES set.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoAgentVoiceMarkOutsideAgentSurfacesDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        /**
         * File simple-names (without extension) allowed to reference
         * `AgentVoiceMark`. Any other call site is flagged.
         */
        private val ALLOWED_FILES = setOf(
            "ClusterSuggestionCard",
            // The primitive itself defines the symbol — its own
            // declaration / preview is not a banned reference.
            "AgentVoiceMark",
        )

        private const val SYMBOL = "AgentVoiceMark"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(SYMBOL)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod,
    ) {
        reportIfDisallowed(context, node)
    }

    override fun getApplicableReferenceNames(): List<String> = listOf(SYMBOL)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement,
    ) {
        // Catch `::AgentVoiceMark` references too.
        val fileName = currentFileSimpleName(context) ?: return
        if (fileName !in ALLOWED_FILES) {
            context.report(
                ISSUE,
                reference,
                context.getLocation(reference),
                "AgentVoiceMark referenced from `$fileName.kt` — not on the " +
                    "agent-voice surface allow-list. See spec 010 FR-010-019.",
            )
        }
    }

    private fun reportIfDisallowed(context: JavaContext, node: UCallExpression) {
        val name = node.methodName ?: return
        if (name != SYMBOL) return
        val fileName = currentFileSimpleName(context) ?: return
        if (fileName in ALLOWED_FILES) return
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "AgentVoiceMark called from `$fileName.kt` — not on the " +
                "agent-voice surface allow-list. See spec 010 FR-010-019.",
        )
    }

    private fun currentFileSimpleName(context: JavaContext): String? {
        val name = context.uastFile?.sourcePsi?.name ?: return null
        return name.substringBeforeLast('.')
    }
}
