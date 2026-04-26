package com.capsule.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
/**
 * Unit test for [NoHttpClientOutsideNetDetector].
 *
 * Verifies Principle VI enforcement: HTTP clients outside com.capsule.app.net
 * produce an error, while usage inside that package is allowed.
 */
class NoHttpClientOutsideNetDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = NoHttpClientOutsideNetDetector()

    override fun getIssues(): List<Issue> = listOf(NoHttpClientOutsideNetDetector.ISSUE)

    fun testOkHttpClientInCapturePackage_flagged() {
        lint().files(
            kotlin(
                """
                package com.capsule.app.capture

                import okhttp3.OkHttpClient

                class BadCapture {
                    val client = OkHttpClient()
                }
                """
            ).indented(),
            // Stub for OkHttpClient so lint resolves the type
            java(
                """
                package okhttp3;
                public class OkHttpClient {
                    public OkHttpClient() {}
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/com/capsule/app/capture/BadCapture.kt:6: Error: HTTP client okhttp3.OkHttpClient used outside com.capsule.app.net. Use INetworkGateway AIDL to request network I/O. [OrbitNoHttpClientOutsideNet]
                    val client = OkHttpClient()
                                 ~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    fun testOkHttpClientInNetPackage_allowed() {
        lint().files(
            kotlin(
                """
                package com.capsule.app.net

                import okhttp3.OkHttpClient

                class SafeClient {
                    val client = OkHttpClient()
                }
                """
            ).indented(),
            java(
                """
                package okhttp3;
                public class OkHttpClient {
                    public OkHttpClient() {}
                }
                """
            ).indented()
        )
            .run()
            .expectClean()
    }

    fun testSocketInUiPackage_flagged() {
        lint().files(
            kotlin(
                """
                package com.capsule.app.ui

                import java.net.Socket

                class Leaky {
                    fun connect() {
                        val s = Socket("example.com", 443)
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/com/capsule/app/ui/Leaky.kt:7: Error: HTTP client java.net.Socket used outside com.capsule.app.net. Use INetworkGateway AIDL to request network I/O. [OrbitNoHttpClientOutsideNet]
                        val s = Socket("example.com", 443)
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    /**
     * Spec 003 v1.1 (T032): action handlers run in `:capture` and MUST NOT
     * touch the network. The Calendar / Tasks / Share handlers all live in
     * `com.capsule.app.action.handler.*`; an OkHttp leak there would let a
     * compromised Action handler exfiltrate data without going through
     * the `:net` IUO permissions check.
     */
    fun testOkHttpInActionHandlerPackage_flagged() {
        lint().files(
            kotlin(
                """
                package com.capsule.app.action.handler

                import okhttp3.OkHttpClient

                class CalendarActionHandler {
                    private val client = OkHttpClient()
                }
                """
            ).indented(),
            java(
                """
                package okhttp3;
                public class OkHttpClient {
                    public OkHttpClient() {}
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/com/capsule/app/action/handler/CalendarActionHandler.kt:6: Error: HTTP client okhttp3.OkHttpClient used outside com.capsule.app.net. Use INetworkGateway AIDL to request network I/O. [OrbitNoHttpClientOutsideNet]
                    private val client = OkHttpClient()
                                         ~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    /**
     * The `:capture` action executor service itself is also forbidden from
     * direct network I/O — it can only round-trip back to `:ml` via binder.
     */
    fun testSocketInActionPackage_flagged() {
        lint().files(
            kotlin(
                """
                package com.capsule.app.action

                import java.net.Socket

                class ActionExecutorService {
                    fun ping() {
                        val s = Socket("example.com", 443)
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/com/capsule/app/action/ActionExecutorService.kt:7: Error: HTTP client java.net.Socket used outside com.capsule.app.net. Use INetworkGateway AIDL to request network I/O. [OrbitNoHttpClientOutsideNet]
                        val s = Socket("example.com", 443)
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    override fun lint(): TestLintTask = super.lint().allowMissingSdk()
}
