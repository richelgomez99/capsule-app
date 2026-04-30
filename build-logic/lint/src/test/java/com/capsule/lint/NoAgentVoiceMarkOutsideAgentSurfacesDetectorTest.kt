package com.capsule.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

/**
 * Unit test for [NoAgentVoiceMarkOutsideAgentSurfacesDetector].
 *
 * Verifies spec 010 FR-010-019 enforcement: AgentVoiceMark calls
 * outside the agent-voice surface allow-list produce an error,
 * while usage inside the allow-list (`ClusterSuggestionCard.kt`)
 * is allowed.
 */
class NoAgentVoiceMarkOutsideAgentSurfacesDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector =
        NoAgentVoiceMarkOutsideAgentSurfacesDetector()

    override fun getIssues(): List<Issue> =
        listOf(NoAgentVoiceMarkOutsideAgentSurfacesDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk()

    fun testAgentVoiceMarkInClusterSuggestionCard_allowed() {
        lint().files(
            kotlin(
                "src/com/capsule/app/diary/ui/ClusterSuggestionCard.kt",
                """
                package com.capsule.app.diary.ui

                fun AgentVoiceMark() {}

                fun render() {
                    AgentVoiceMark()
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testAgentVoiceMarkInRandomComposable_flagged() {
        lint().files(
            kotlin(
                "src/com/capsule/app/diary/ui/EnvelopeRow.kt",
                """
                package com.capsule.app.diary.ui

                fun AgentVoiceMark() {}

                fun render() {
                    AgentVoiceMark()
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/com/capsule/app/diary/ui/EnvelopeRow.kt:6: Error: AgentVoiceMark called from EnvelopeRow.kt — not on the agent-voice surface allow-list. See spec 010 FR-010-019. [OrbitNoAgentVoiceMarkOutsideAgentSurfaces]
                    AgentVoiceMark()
                    ~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    fun testAgentVoiceMarkInsideOwnFile_allowed() {
        // The primitive's own file may reference itself (preview/declaration).
        lint().files(
            kotlin(
                "src/com/capsule/app/ui/primitives/AgentVoiceMark.kt",
                """
                package com.capsule.app.ui.primitives

                fun AgentVoiceMark() {}

                fun preview() {
                    AgentVoiceMark()
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }
}
